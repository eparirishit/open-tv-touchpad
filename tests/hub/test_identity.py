"""Disposable identities and in-memory TLS only; never access native trust/keys."""
import importlib.util
import json
import os
from pathlib import Path
import ssl
import stat
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from ipaddress import IPv4Address, IPv4Network, IPv6Address
from unittest.mock import patch

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID


class MemoryStore:
    def __init__(self):
        self.values = {}
        self.denied = False

    def get_password(self, service, username):
        if self.denied:
            raise RuntimeError("denied")
        return self.values.get((service, username))

    def set_password(self, service, username, password):
        if self.denied:
            raise RuntimeError("denied")
        self.values[service, username] = password

    def delete_password(self, service, username):
        del self.values[service, username]


def handshake(server_context, root_pem, hostname):
    """Exercise OpenSSL chain/hostname validation without opening any sockets."""
    client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    client_context.load_verify_locations(cadata=root_pem)
    client_in, client_out, server_in, server_out = (ssl.MemoryBIO() for _ in range(4))
    client = client_context.wrap_bio(client_in, client_out, server_hostname=hostname)
    server = server_context.wrap_bio(server_in, server_out, server_side=True)
    done = set()
    for _ in range(20):
        for name, peer in (("client", client), ("server", server)):
            if name not in done:
                try:
                    peer.do_handshake()
                    done.add(name)
                except ssl.SSLWantReadError:
                    pass
        server_in.write(client_out.read())
        client_in.write(server_out.read())
        if len(done) == 2:
            return client.getpeercert(binary_form=True)
    raise AssertionError("in-memory TLS handshake did not finish")


class IdentityTests(unittest.TestCase):
    def setUp(self):
        # A missing implementation is an explicit RED assertion, not an import error.
        self.assertIsNotNone(importlib.util.find_spec("hub.identity"), "identity slice is missing")
        from hub import identity
        self.module = identity
        from keyring.backends.macOS import Keyring
        for method in ("get_password", "set_password", "delete_password"):
            guard = patch.object(Keyring, method, side_effect=AssertionError("real Keychain access forbidden in tests"))
            guard.start()
            self.addCleanup(guard.stop)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.state = self.base / "state"
        self.store = MemoryStore()
        self.address = IPv4Address("192.168.50.7")
        self.identity = identity.Identity(self.state, store=self.store)

    def create(self):
        self.identity.create_identity(self.address)
        return self.metadata()

    def metadata(self):
        return json.loads((self.state / "identity.json").read_text())

    def certs(self):
        metadata = self.metadata()
        return [x509.load_pem_x509_certificate(metadata[name].encode())
                for name in ("root", "intermediate", "leaf")]

    def keys(self):
        return json.loads(next(iter(self.store.values.values())))

    def test_exact_chain_profile_and_no_root_secret_persistence(self):
        now = datetime.now(timezone.utc).replace(microsecond=0)
        with patch.object(self.module, "_now", return_value=now):
            self.create()
        root, intermediate, leaf = self.certs()
        root.verify_directly_issued_by(root)
        intermediate.verify_directly_issued_by(root)
        leaf.verify_directly_issued_by(intermediate)
        for cert, days in ((root, 1825), (intermediate, 1825), (leaf, 90)):
            self.assertIsInstance(cert.public_key().curve, ec.SECP256R1)
            self.assertEqual(cert.not_valid_before_utc, now - timedelta(days=1))
            self.assertEqual(cert.not_valid_after_utc, now + timedelta(days=days))
            self.assertEqual(list(cert.extensions.get_extension_for_class(x509.ExtendedKeyUsage).value),
                             [ExtendedKeyUsageOID.SERVER_AUTH])
        self.assertEqual(intermediate.extensions.get_extension_for_class(x509.BasicConstraints).value,
                         x509.BasicConstraints(ca=True, path_length=0))
        constraints = intermediate.extensions.get_extension_for_class(x509.NameConstraints)
        self.assertTrue(constraints.critical)
        self.assertEqual(constraints.value.permitted_subtrees[0],
                         x509.IPAddress(IPv4Network("192.168.50.7/32")))
        self.assertRegex(constraints.value.permitted_subtrees[1].value, r"^[0-9a-f]{32}\.invalid$")
        self.assertEqual(list(leaf.extensions.get_extension_for_class(x509.SubjectAlternativeName).value),
                         [x509.IPAddress(self.address)])
        self.assertEqual(set(self.keys()), {"intermediate", "leaf"})
        for path in self.state.rglob("*"):
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o700 if path.is_dir() else 0o600)
            if path.is_file():
                self.assertNotIn(b"PRIVATE KEY", path.read_bytes())
        self.assertEqual(stat.S_IMODE(self.state.stat().st_mode), 0o700)

    def test_real_openssl_accepts_chain_and_rejects_wrong_hostname(self):
        metadata = self.create()
        keylog = self.base / "forbidden-keylog"
        with patch.dict(os.environ, {"SSLKEYLOGFILE": str(keylog)}):
            context = self.identity.load_tls_context(self.address)
        self.assertGreaterEqual(context.minimum_version, ssl.TLSVersion.TLSv1_2)
        self.assertIsNone(context.keylog_filename)
        peer = handshake(context, metadata["root"], str(self.address))
        self.assertFalse(keylog.exists())
        self.assertEqual(peer, self.certs()[2].public_bytes(serialization.Encoding.DER))
        for hostname in ("192.168.50.8", "example.com"):
            with self.subTest(hostname=hostname), self.assertRaises(ssl.SSLCertVerificationError):
                handshake(context, metadata["root"], hostname)

    def rogue_context(self, san, *, expired=False, future=False):
        metadata = self.metadata()
        issuer = self.certs()[1]
        key = serialization.load_pem_private_key(self.keys()["intermediate"].encode(), None)
        leaf_key = ec.generate_private_key(ec.SECP256R1())
        now = datetime.now(timezone.utc)
        cert = (x509.CertificateBuilder()
                .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "fixture")]))
                .issuer_name(issuer.subject).public_key(leaf_key.public_key())
                .serial_number(x509.random_serial_number())
                .not_valid_before(now + timedelta(days=1) if future else now - timedelta(days=2))
                .not_valid_after(now - timedelta(days=1) if expired else now + timedelta(days=3))
                .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                .add_extension(x509.SubjectAlternativeName([san]), critical=False)
                .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
                .sign(key, hashes.SHA256()))
        certfile, keyfile = self.base / "fixture.pem", self.base / "fixture-key.pem"
        certfile.write_bytes(cert.public_bytes(serialization.Encoding.PEM) + metadata["intermediate"].encode())
        keyfile.write_bytes(leaf_key.private_bytes(serialization.Encoding.PEM,
                            serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        keyfile.chmod(0o600)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certfile, keyfile)
        keyfile.unlink()
        return context

    def test_real_openssl_enforces_intermediate_ip_and_dns_constraints(self):
        metadata = self.create()
        for san, hostname in ((x509.IPAddress(IPv4Address("192.168.50.8")), "192.168.50.8"),
                              (x509.DNSName("example.com"), "example.com")):
            with self.subTest(hostname=hostname):
                context = self.rogue_context(san)
                with self.assertRaises(ssl.SSLCertVerificationError) as failure:
                    handshake(context, metadata["root"], hostname)
                self.assertIn("permitted subtree", failure.exception.verify_message)

    def test_real_openssl_rejects_expired_and_future_certificates(self):
        metadata = self.create()
        for flags in ({"expired": True}, {"future": True}):
            with self.subTest(flags=flags):
                context = self.rogue_context(x509.IPAddress(self.address), **flags)
                with self.assertRaises(ssl.SSLCertVerificationError):
                    handshake(context, metadata["root"], str(self.address))

    def test_only_rfc1918_ipv4_is_accepted_before_state_creation(self):
        invalid = ["127.0.0.1", "169.254.1.1", "100.64.0.1", "192.0.2.1", "198.18.0.1",
                   "0.0.0.0", "8.8.8.8", "224.0.0.1", "172.15.255.255", "172.32.0.0"]
        for address in [*(IPv4Address(a) for a in invalid), IPv6Address("::1"), "192.168.50.7"]:
            with self.subTest(address=address), self.assertRaises(self.module.IdentityError):
                self.identity.create_identity(address)
        self.assertFalse(self.state.exists())
        self.assertEqual(self.store.values, {})
        for index, address in enumerate(("10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.0.1")):
            identity = self.module.Identity(self.base / str(index), store=MemoryStore())
            identity.create_identity(IPv4Address(address))

    def test_restart_same_address_renewal_threshold_preserves_trust_and_keys(self):
        now = datetime.now(timezone.utc).replace(microsecond=0)
        with patch.object(self.module, "_now", return_value=now):
            initial = self.create()
        keys = dict(self.store.values)
        restarted = self.module.Identity(self.state, store=self.store)
        with patch.object(self.module, "_now", return_value=now + timedelta(days=76, seconds=-1)):
            restarted.load_tls_context(self.address)
        self.assertEqual(initial, self.metadata())
        with patch.object(self.module, "_now", return_value=now + timedelta(days=76)):
            restarted.load_tls_context(self.address)
        renewed = self.metadata()
        self.assertNotEqual(initial["leaf"], renewed["leaf"])
        self.assertEqual(initial["root"], renewed["root"])
        self.assertEqual(initial["intermediate"], renewed["intermediate"])
        self.assertEqual(keys, self.store.values)
        self.assertEqual(self.certs()[2].not_valid_after_utc, now + timedelta(days=166))

    def test_renewal_caps_at_issuer_expiry_then_refuses(self):
        now = datetime.now(timezone.utc).replace(microsecond=0)
        with patch.object(self.module, "_now", return_value=now):
            self.create()
        with patch.object(self.module, "_now", return_value=now + timedelta(days=1820)):
            self.identity.load_tls_context(self.address)
        self.assertEqual(self.certs()[2].not_valid_after_utc, self.certs()[1].not_valid_after_utc)
        before = (self.state / "identity.json").read_bytes()
        for moment in (now + timedelta(days=1825), now - timedelta(days=2)):
            with patch.object(self.module, "_now", return_value=moment):
                with self.assertRaises(self.module.IdentityError):
                    self.identity.load_tls_context(self.address)
        self.assertEqual(before, (self.state / "identity.json").read_bytes())

    def test_public_fingerprint_export_and_no_overwrite(self):
        self.create()
        fingerprint = ":".join(f"{byte:02X}" for byte in self.certs()[0].fingerprint(hashes.SHA256()))
        self.store.denied = True
        self.assertEqual(self.identity.root_fingerprint, fingerprint)
        destination = self.base / "root.crt"
        self.assertEqual(self.identity.export_root(destination), fingerprint)
        self.assertEqual(destination.read_text(), self.metadata()["root"])
        with self.assertRaises(self.module.IdentityError):
            self.identity.export_root(destination)
        self.assertEqual(destination.read_text(), self.metadata()["root"])
        link = self.base / "link.crt"
        link.symlink_to(destination)
        with self.assertRaises(self.module.IdentityError):
            self.identity.export_root(link)

    def test_existing_identity_cannot_be_replaced_or_migrated_implicitly(self):
        self.create()
        before = (self.state / "identity.json").read_bytes(), dict(self.store.values)
        with self.assertRaises(self.module.IdentityError):
            self.identity.create_identity(self.address)
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(IPv4Address("192.168.50.8"))
        self.assertEqual(before, ((self.state / "identity.json").read_bytes(), self.store.values))

    def test_missing_corrupt_and_partial_metadata_fail_closed(self):
        self.state.mkdir(mode=0o700)
        metadata = self.state / "identity.json"
        for raw in (b"", b"{", b"{}", b'{"version":1,"version":2}'):
            metadata.write_bytes(raw)
            metadata.chmod(0o600)
            with self.subTest(raw=raw):
                for operation in (lambda: self.identity.create_identity(self.address),
                                  lambda: self.identity.load_tls_context(self.address),
                                  lambda: self.identity.root_fingerprint):
                    with self.assertRaises(self.module.IdentityError):
                        operation()
                self.assertEqual(metadata.read_bytes(), raw)
        metadata.unlink()
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)
        self.assertEqual(self.store.values, {})

    def test_keychain_denial_or_missing_key_never_replaces_identity(self):
        self.create()
        before = (self.state / "identity.json").read_bytes()
        self.store.denied = True
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)
        self.store.denied = False
        self.store.values.clear()
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)
        self.assertEqual(before, (self.state / "identity.json").read_bytes())

    def test_creation_denial_leaves_nonsecret_partial_state_and_refuses_retry(self):
        self.store.denied = True
        with self.assertRaises(self.module.IdentityError):
            self.create()
        self.store.denied = False
        with self.assertRaises(self.module.IdentityError):
            self.create()
        for path in self.state.rglob("*"):
            if path.is_file():
                self.assertNotIn(b"PRIVATE KEY", path.read_bytes())

    def test_native_backend_is_pinned_and_substitution_rejected_without_native_calls(self):
        import keyring
        import keyring.backends.macOS
        with patch.object(keyring.backends.macOS, "Keyring", MemoryStore):
            with patch.object(keyring, "get_keyring", return_value=object()):
                native = self.module.Identity(self.base / "substituted")
                with self.assertRaises(self.module.IdentityError):
                    native.create_identity(self.address)
            with patch.object(keyring, "get_keyring", return_value=MemoryStore()):
                native = self.module.Identity(self.base / "native")
                native.create_identity(self.address)
                self.assertIsInstance(native.load_tls_context(self.address), ssl.SSLContext)

    def test_symlinked_state_ancestor_and_metadata_are_rejected(self):
        target = self.base / "target"
        target.mkdir(mode=0o700)
        link = self.base / "link"
        link.symlink_to(target, target_is_directory=True)
        for state in (link, link / "child"):
            with self.subTest(state=state), self.assertRaises(self.module.IdentityError):
                self.module.Identity(state, store=self.store).create_identity(self.address)
        self.assertEqual(list(target.iterdir()), [])
        self.create()
        metadata = self.state / "identity.json"
        original = metadata.read_bytes()
        saved = self.base / "saved.json"
        metadata.rename(saved)
        metadata.symlink_to(saved)
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)
        self.assertEqual(saved.read_bytes(), original)

    def test_unsafe_permissions_and_hardlinked_metadata_fail_closed(self):
        self.state.mkdir(mode=0o755)
        with self.assertRaises(self.module.IdentityError):
            self.create()
        self.state.chmod(0o700)
        self.create()
        metadata = self.state / "identity.json"
        metadata.chmod(0o644)
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)
        metadata.chmod(0o600)
        os.link(metadata, self.base / "copy.json")
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)

    def test_temporary_key_files_exclusive_private_and_removed_on_success_and_error(self):
        self.create()
        original = ssl.SSLContext.load_cert_chain
        observed = []

        def inspect(context, certfile, keyfile):
            for name in (certfile, keyfile):
                path = Path(name)
                observed.append(path)
                self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
                self.assertEqual(stat.S_IMODE((self.state / "identity-tls").stat().st_mode), 0o700)
                with self.assertRaises(FileExistsError):
                    os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            return original(context, certfile, keyfile)

        with patch.object(ssl.SSLContext, "load_cert_chain", inspect):
            self.identity.load_tls_context(self.address)
        self.assertTrue(observed)
        self.assertTrue(all(not path.exists() for path in observed))

        def fail(context, certfile, keyfile):
            inspect(context, certfile, keyfile)
            raise ssl.SSLError("test failure")

        with patch.object(ssl.SSLContext, "load_cert_chain", fail):
            with self.assertRaises(self.module.IdentityError):
                self.identity.load_tls_context(self.address)
        self.assertTrue(all(not path.exists() for path in observed))

    def test_stale_cleanup_removes_only_owned_private_regular_temp_files(self):
        self.create()
        self.identity.load_tls_context(self.address)
        runtime = self.state / "identity-tls"
        stale = runtime / ("tls-" + "a" * 32 + ".pem")
        stale.write_text("disposable fixture")
        stale.chmod(0o600)
        unrelated = runtime / "keep.txt"
        unrelated.write_text("keep")
        link = runtime / ("tls-" + "b" * 32 + ".pem")
        link.symlink_to(unrelated)
        self.identity.load_tls_context(self.address)
        self.assertFalse(stale.exists())
        self.assertTrue(link.is_symlink())
        self.assertEqual(unrelated.read_text(), "keep")

    def test_interrupted_temporary_write_leaves_no_private_key_file(self):
        self.create()
        original = os.fsync

        def fail_key_write(fd):
            if b"PRIVATE KEY" in os.pread(fd, 4096, 0):
                raise OSError("simulated disk failure")
            return original(fd)

        with patch.object(self.module.os, "fsync", side_effect=fail_key_write):
            with self.assertRaises(self.module.IdentityError):
                self.identity.load_tls_context(self.address)
        self.assertEqual(list((self.state / "identity-tls").iterdir()), [])

    def test_stale_private_key_cleanup_precedes_keychain_denial(self):
        self.create()
        runtime = self.state / "identity-tls"
        runtime.mkdir(mode=0o700)
        stale = runtime / ("tls-" + "c" * 32 + ".pem")
        stale.write_text("disposable fixture")
        stale.chmod(0o600)
        self.store.denied = True
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)
        self.assertFalse(stale.exists())

    def test_missing_dependency_fails_closed_but_injected_store_still_works(self):
        with patch.dict("sys.modules", {"keyring": None}):
            self.create()
            self.identity.load_tls_context(self.address)
            native = self.module.Identity(self.base / "unavailable")
            with self.assertRaises(self.module.IdentityError):
                native.create_identity(self.address)

    def test_mismatched_or_corrupt_key_bundle_fails_closed(self):
        self.create()
        before = (self.state / "identity.json").read_bytes()
        slot = next(iter(self.store.values))
        bundle = self.keys()
        bundle["leaf"] = bundle["intermediate"]
        for raw in (json.dumps(bundle), "{}", "not-json"):
            self.store.values[slot] = raw
            with self.subTest(raw=raw[:1]), self.assertRaises(self.module.IdentityError):
                self.identity.load_tls_context(self.address)
        self.assertEqual(before, (self.state / "identity.json").read_bytes())

    def test_metadata_certificate_corruption_is_rejected_without_replacement(self):
        original = self.create()
        for field, replacement in (("leaf", original["intermediate"]), ("root", "corrupt"),
                                   ("address", "192.168.50.8"), ("dns", "example.com")):
            data = dict(original)
            data[field] = replacement
            raw = json.dumps(data)
            (self.state / "identity.json").write_text(raw)
            with self.subTest(field=field), self.assertRaises(self.module.IdentityError):
                self.identity.load_tls_context(self.address)
            self.assertEqual((self.state / "identity.json").read_text(), raw)

    def test_runtime_and_export_parent_symlinks_do_not_reach_target(self):
        self.create()
        target = self.base / "target"
        target.mkdir(mode=0o700)
        runtime = self.state / "identity-tls"
        runtime.symlink_to(target, target_is_directory=True)
        with self.assertRaises(self.module.IdentityError):
            self.identity.load_tls_context(self.address)
        with self.assertRaises(self.module.IdentityError):
            self.identity.export_root(runtime / "root.crt")
        self.assertEqual(list(target.iterdir()), [])

    def test_temporary_filename_collision_never_overwrites_or_removes_existing_file(self):
        self.create()
        original = os.open
        collisions = []

        def collide(name, flags, mode=0o777, *, dir_fd=None):
            if isinstance(name, str) and name.startswith("tls-") and flags & os.O_CREAT:
                fd = original(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600, dir_fd=dir_fd)
                with os.fdopen(fd, "wb") as stream:
                    stream.write(b"existing file")
                collisions.append(name)
            return original(name, flags, mode, dir_fd=dir_fd)

        with patch.object(self.module.os, "open", side_effect=collide):
            with self.assertRaises(self.module.IdentityError):
                self.identity.load_tls_context(self.address)
        self.assertEqual(len(collisions), 1)
        self.assertEqual((self.state / "identity-tls" / collisions[0]).read_bytes(), b"existing file")

    def test_extra_pem_or_trailing_content_is_rejected_before_export_or_tls(self):
        original = self.create()
        for field in ("root", "intermediate", "leaf"):
            for index, suffix in enumerate((original["root"], "unparsed trailing content")):
                with self.subTest(field=field, extra_certificate=suffix == original["root"]):
                    data = dict(original)
                    data[field] += suffix
                    (self.state / "identity.json").write_text(json.dumps(data))
                    destination = self.base / f"rejected-{field}-{index}.crt"
                    with self.assertRaises(self.module.IdentityError):
                        self.identity.export_root(destination)
                    self.assertFalse(destination.exists())
                    with self.assertRaises(self.module.IdentityError):
                        self.identity.load_tls_context(self.address)

    def test_export_serializes_only_the_parsed_root_certificate(self):
        data = self.create()
        canonical = data["root"]
        data["root"] = "\n\t" + canonical + "\n\t"
        (self.state / "identity.json").write_text(json.dumps(data))
        destination = self.base / "canonical.crt"
        fingerprint = self.identity.export_root(destination)
        self.assertEqual(destination.read_text(), canonical)
        self.assertEqual(fingerprint, self.identity.root_fingerprint)

    def test_unknown_critical_leaf_extension_fails_before_use_or_renewal(self):
        now = datetime.now(timezone.utc).replace(microsecond=0)
        with patch.object(self.module, "_now", return_value=now):
            data = self.create()
        _, intermediate, leaf = self.certs()
        signer = serialization.load_pem_private_key(self.keys()["intermediate"].encode(), None)
        builder = (x509.CertificateBuilder().subject_name(leaf.subject).issuer_name(intermediate.subject)
                   .public_key(leaf.public_key()).serial_number(x509.random_serial_number())
                   .not_valid_before(leaf.not_valid_before_utc).not_valid_after(leaf.not_valid_after_utc))
        for extension in leaf.extensions:
            builder = builder.add_extension(extension.value, critical=extension.critical)
        malformed = builder.add_extension(x509.UnrecognizedExtension(
            x509.ObjectIdentifier("1.2.3.4.5.6.7.8.1"), b"\x05\x00"), critical=True).sign(signer, hashes.SHA256())
        malformed.verify_directly_issued_by(intermediate)
        data["leaf"] = malformed.public_bytes(serialization.Encoding.PEM).decode("ascii")
        raw = json.dumps(data)
        (self.state / "identity.json").write_text(raw)
        for age in (0, 76, 91):
            with self.subTest(age_days=age), patch.object(self.module, "_now", return_value=now + timedelta(days=age)):
                (self.state / "identity.json").write_text(raw)
                with self.assertRaises(self.module.IdentityError):
                    self.identity.load_tls_context(self.address)
                self.assertEqual((self.state / "identity.json").read_text(), raw)

    def test_expired_valid_leaf_can_renew_without_replacing_trust(self):
        now = datetime.now(timezone.utc).replace(microsecond=0)
        with patch.object(self.module, "_now", return_value=now):
            original = self.create()
        with patch.object(self.module, "_now", return_value=now + timedelta(days=91)):
            self.identity.load_tls_context(self.address)
        renewed = self.metadata()
        self.assertEqual(renewed["root"], original["root"])
        self.assertEqual(renewed["intermediate"], original["intermediate"])
        self.assertNotEqual(renewed["leaf"], original["leaf"])
        self.assertEqual(self.certs()[2].not_valid_after_utc, now + timedelta(days=181))


if __name__ == "__main__":
    unittest.main()
