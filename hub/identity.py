"""Constrained local TLS identity. No trust installation or automatic CA recovery."""
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import fcntl
from ipaddress import IPv4Address, IPv4Network
import json
import os
from pathlib import Path
import re
import ssl
import stat
import sys
from uuid import uuid4

from cryptography import x509
from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID
from cryptography.x509.verification import PolicyBuilder, Store, VerificationError


class IdentityError(RuntimeError):
    """Identity is unusable; an owner must resolve the failure explicitly."""


def _now():
    return datetime.now(timezone.utc).replace(microsecond=0)


def _require(condition):
    if not condition:
        raise IdentityError("Identity unavailable or invalid; explicit owner recovery required")


def _address(address):
    _require(isinstance(address, IPv4Address) and any(address in IPv4Network(network)
             for network in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")))


def _json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            _require(key not in result)
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique)


def _private(info, directory=False):
    _require(info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == (0o700 if directory else 0o600))
    _require(stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode) and info.st_nlink == 1)


@contextmanager
def _directory(path, create=False):
    """Walk from / with openat/O_NOFOLLOW, including every ancestor."""
    path = Path(os.path.abspath(path))
    fd = os.open("/", os.O_RDONLY | os.O_DIRECTORY)
    try:
        for index, part in enumerate(path.parts[1:], start=1):
            if create and index == len(path.parts) - 1:
                try:
                    os.mkdir(part, mode=0o700, dir_fd=fd)
                except FileExistsError:
                    pass
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = child
        yield fd
    finally:
        os.close(fd)


def _new_file(directory, name, data):
    fd = os.open(name, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=directory)
    try:
        with os.fdopen(fd, "w+b", closefd=False) as stream:
            stream.write(data)
            stream.flush()
            os.fsync(fd)
        os.lseek(fd, 0, os.SEEK_SET)
        return fd
    except BaseException:
        try:
            os.unlink(name, dir_fd=directory)
        finally:
            os.close(fd)
        raise


@contextmanager
def _temporary_file(directory, contents):
    name = "tls-" + uuid4().hex + ".pem"
    fd = _new_file(directory, name, contents.encode("ascii"))
    try:
        yield fd
    finally:
        try:
            os.unlink(name, dir_fd=directory)
        finally:
            os.close(fd)


def _pem(cert):
    return cert.public_bytes(serialization.Encoding.PEM).decode("ascii")


def _secret(key):
    return key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                             serialization.NoEncryption()).decode("ascii")


def _usage(ca):
    return x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=False,
                         data_encipherment=False, key_agreement=False, key_cert_sign=ca,
                         crl_sign=ca, encipher_only=False, decipher_only=False)


def _certificate(subject, key, issuer, issuer_key, now, expires, *, path_length=None, address=None, dns=None):
    ca = address is None or dns is not None
    builder = (x509.CertificateBuilder().subject_name(subject).issuer_name(issuer)
               .public_key(key.public_key()).serial_number(x509.random_serial_number())
               .not_valid_before(now - timedelta(days=1)).not_valid_after(expires)
               .add_extension(x509.BasicConstraints(ca=ca, path_length=path_length), critical=True)
               .add_extension(_usage(ca), critical=True)
               .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
               .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()), critical=False)
               .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(issuer_key.public_key()), critical=False))
    if dns is not None:
        builder = builder.add_extension(x509.NameConstraints(
            permitted_subtrees=[x509.IPAddress(IPv4Network(f"{address}/32")), x509.DNSName(dns)],
            excluded_subtrees=None), critical=True)
    elif address is not None:
        builder = builder.add_extension(x509.SubjectAlternativeName([x509.IPAddress(address)]), critical=False)
    return builder.sign(issuer_key, hashes.SHA256())


class Identity:
    """Public state in state_dir; injected stores are a test-only trust boundary.

    Production always uses the explicit native macOS backend. root_fingerprint
    and export_root read public data only. Methods serialize on an owned lock.
    """

    def __init__(self, state_dir: Path, store=None):
        self.state_dir = Path(os.path.abspath(state_dir))
        self._injected_store = store
        self._native_store = None

    def _store(self):
        if self._injected_store is not None:
            return self._injected_store
        try:
            _require(sys.platform == "darwin")
            import keyring
            from keyring.backends.macOS import Keyring
            _require(type(keyring.get_keyring()) is Keyring)
            if self._native_store is None:
                self._native_store = Keyring()
            return self._native_store
        except Exception:
            raise IdentityError("Native macOS Keychain unavailable or substituted") from None

    def _get_keys(self, installation):
        try:
            raw = self._store().get_password("open-tv-touchpad-hub.identity", installation)
        except Exception:
            raise IdentityError("Native identity keys unavailable") from None
        _require(isinstance(raw, str) and len(raw) < 16384)
        bundle = _json(raw)
        _require(isinstance(bundle, dict) and set(bundle) == {"intermediate", "leaf"})
        keys = [serialization.load_pem_private_key(bundle[name].encode("ascii"), None)
                for name in ("intermediate", "leaf")]
        _require(all(isinstance(key, ec.EllipticCurvePrivateKey) and isinstance(key.curve, ec.SECP256R1)
                     for key in keys))
        return keys

    @contextmanager
    def _state(self, create=False):
        try:
            with _directory(self.state_dir, create=create) as directory:
                _private(os.fstat(directory), directory=True)
                flags = os.O_RDWR | os.O_NOFOLLOW | os.O_NONBLOCK
                if create:
                    flags |= os.O_CREAT | os.O_EXCL
                lock = os.open("identity.lock", flags, 0o600, dir_fd=directory)
                try:
                    _private(os.fstat(lock))
                    fcntl.flock(lock, fcntl.LOCK_EX)
                    yield directory
                finally:
                    os.close(lock)
        except (OSError, ValueError, TypeError, KeyError, AttributeError, InvalidSignature,
                UnsupportedAlgorithm, VerificationError, x509.ExtensionNotFound):
            raise IdentityError("Identity unavailable or invalid; explicit owner recovery required") from None

    def _read(self, directory):
        fd = os.open("identity.json", os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        with os.fdopen(fd, "rb") as stream:
            _private(os.fstat(stream.fileno()))
            raw = stream.read(65537)
        _require(len(raw) <= 65536)
        data = _json(raw)
        _require(isinstance(data, dict) and set(data) ==
                 {"version", "installation", "address", "dns", "root", "intermediate", "leaf"})
        _require(type(data["version"]) is int and data["version"] == 1)
        _require(isinstance(data["installation"], str) and re.fullmatch(r"[0-9a-f]{32}", data["installation"]))
        _require(data["dns"] == data["installation"] + ".invalid")
        address = IPv4Address(data["address"])
        _address(address)
        certs = [x509.load_pem_x509_certificate(data[name].encode("ascii"))
                 for name in ("root", "intermediate", "leaf")]
        for name, cert in zip(("root", "intermediate", "leaf"), certs):
            _require(data[name].strip() == _pem(cert).strip())
        root, intermediate, leaf = certs
        for cert, issuer, ca, path_length in ((root, root, True, 1),
                (intermediate, root, True, 0), (leaf, intermediate, False, None)):
            cert.verify_directly_issued_by(issuer)
            _require(isinstance(cert.public_key(), ec.EllipticCurvePublicKey)
                     and isinstance(cert.public_key().curve, ec.SECP256R1)
                     and isinstance(cert.signature_hash_algorithm, hashes.SHA256))
            basic = cert.extensions.get_extension_for_class(x509.BasicConstraints)
            usage = cert.extensions.get_extension_for_class(x509.KeyUsage)
            _require(basic.critical and basic.value == x509.BasicConstraints(ca, path_length))
            _require(usage.critical and usage.value == _usage(ca))
            _require(list(cert.extensions.get_extension_for_class(x509.ExtendedKeyUsage).value)
                     == [ExtendedKeyUsageOID.SERVER_AUTH])
            _require(cert.not_valid_after_utc <= issuer.not_valid_after_utc)
        constraints = intermediate.extensions.get_extension_for_class(x509.NameConstraints)
        _require(constraints.critical and constraints.value == x509.NameConstraints(
            [x509.IPAddress(IPv4Network(f"{address}/32")), x509.DNSName(data["dns"])], None))
        _require(list(leaf.extensions.get_extension_for_class(x509.SubjectAlternativeName).value)
                 == [x509.IPAddress(address)])
        # Historical path validation permits expired-leaf renewal; TLS loading
        # separately enforces current issuer validity and refuses future leaves.
        (PolicyBuilder().store(Store([root])).max_chain_depth(1)
         .time(max(cert.not_valid_before_utc for cert in certs))
         .build_server_verifier(x509.IPAddress(address)).verify(leaf, [intermediate]))
        return data, certs

    def _save(self, directory, data):
        name = "identity-meta-" + uuid4().hex
        fd = _new_file(directory, name, json.dumps(data).encode("ascii"))
        try:
            os.replace(name, "identity.json", src_dir_fd=directory, dst_dir_fd=directory)
            os.fsync(directory)
        finally:
            os.close(fd)
            # Only this operation's exclusive, non-secret staging file is ours.
            try:
                os.unlink(name, dir_fd=directory)
            except FileNotFoundError:
                pass

    def create_identity(self, address: IPv4Address) -> None:
        _address(address)
        with self._state(create=True) as directory:
            # A crash/denial leaves a marker; setup must never silently replace a CA.
            os.close(_new_file(directory, "identity.json", b"{}"))
            now, installation = _now(), uuid4().hex
            dns = installation + ".invalid"
            root_key, intermediate_key, leaf_key = (ec.generate_private_key(ec.SECP256R1()) for _ in range(3))
            root_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Open TV root " + installation)])
            intermediate_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Open TV issuer " + installation)])
            leaf_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Open TV hub")])
            expires = now + timedelta(days=1825)
            root = _certificate(root_name, root_key, root_name, root_key, now, expires, path_length=1)
            intermediate = _certificate(intermediate_name, intermediate_key, root_name, root_key,
                                        now, expires, path_length=0, address=address, dns=dns)
            del root_key  # Python object release is not guaranteed memory erasure.
            leaf = _certificate(leaf_name, leaf_key, intermediate_name, intermediate_key,
                                now, now + timedelta(days=90), address=address)
            try:
                self._store().set_password("open-tv-touchpad-hub.identity", installation,
                                          json.dumps({"intermediate": _secret(intermediate_key), "leaf": _secret(leaf_key)}))
            except Exception:
                raise IdentityError("Native identity keys could not be stored") from None
            keys = self._get_keys(installation)
            _require(all(saved.public_key() == original.public_key()
                         for saved, original in zip(keys, (intermediate_key, leaf_key))))
            self._save(directory, {"version": 1, "installation": installation, "address": str(address),
                       "dns": dns, "root": _pem(root), "intermediate": _pem(intermediate), "leaf": _pem(leaf)})

    @property
    def root_fingerprint(self) -> str:
        """Full uppercase colon-separated SHA-256; no Keychain access."""
        with self._state() as directory:
            _, certs = self._read(directory)
            return ":".join(f"{byte:02X}" for byte in certs[0].fingerprint(hashes.SHA256()))

    def export_root(self, destination: Path) -> str:
        with self._state() as directory:
            _, certs = self._read(directory)
            destination = Path(os.path.abspath(destination))
            with _directory(destination.parent) as parent:
                os.close(_new_file(parent, destination.name, certs[0].public_bytes(serialization.Encoding.PEM)))
            return ":".join(f"{byte:02X}" for byte in certs[0].fingerprint(hashes.SHA256()))

    def load_tls_context(self, address: IPv4Address) -> ssl.SSLContext:
        _address(address)
        with self._state() as directory, _directory(self.state_dir / "identity-tls", create=True) as runtime:
            _private(os.fstat(runtime), directory=True)
            self._clean_stale(runtime)
            data, (root, intermediate, leaf) = self._read(directory)
            _require(data["address"] == str(address))
            now = _now()
            _require(all(cert.not_valid_before_utc <= now < cert.not_valid_after_utc for cert in (root, intermediate)))
            _require(leaf.not_valid_before_utc <= now)
            intermediate_key, leaf_key = self._get_keys(data["installation"])
            _require(intermediate_key.public_key() == intermediate.public_key() and leaf_key.public_key() == leaf.public_key())
            if leaf.not_valid_after_utc - now <= timedelta(days=14):
                leaf = _certificate(leaf.subject, leaf_key, intermediate.subject, intermediate_key, now,
                                    min(now + timedelta(days=90), intermediate.not_valid_after_utc), address=address)
                data["leaf"] = _pem(leaf)
                self._save(directory, data)
            context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            context.minimum_version = ssl.TLSVersion.TLSv1_2
            with (_temporary_file(runtime, data["leaf"] + data["intermediate"]) as certfile,
                  _temporary_file(runtime, _secret(leaf_key)) as keyfile):
                # OpenSSL reads pinned descriptors, never re-traverses caller paths.
                context.load_cert_chain(f"/dev/fd/{certfile}", f"/dev/fd/{keyfile}")
            return context

    @staticmethod
    def _clean_stale(runtime):
        # The state lock excludes live app loads; unrelated names/links stay intact.
        for name in os.listdir(runtime):
            if not re.fullmatch(r"tls-[0-9a-f]{32}\.pem", name):
                continue
            info = os.stat(name, dir_fd=runtime, follow_symlinks=False)
            if (stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
                    and stat.S_IMODE(info.st_mode) == 0o600 and info.st_nlink == 1):
                os.unlink(name, dir_fd=runtime)
