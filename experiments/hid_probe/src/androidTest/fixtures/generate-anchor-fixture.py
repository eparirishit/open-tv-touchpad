"""Generate PUBLIC, test-only TLS material. Never use these keys outside androidTest.
Run with a maintained OpenSSL executable: python3 generate-anchor-fixture.py /path/to/openssl
The leaf and signer have identical DNs but independent RSA keys. No DER mutation.
"""
import hashlib
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

openssl = sys.argv[1] if len(sys.argv) == 2 else "openssl"
out = Path(__file__).resolve().parents[1] / "assets" / "tls-anchor"
if out.exists():
    raise SystemExit("Refusing to replace existing fixture; review regeneration explicitly")

def run(*args, succeeds=True):
    result = subprocess.run([openssl, *args], capture_output=True)
    if (result.returncode == 0) != succeeds:
        raise RuntimeError("Unexpected OpenSSL fixture operation result: " + args[0])
    return result.stdout + result.stderr

with tempfile.TemporaryDirectory(prefix="public-test-anchor-") as temporary:
    root = Path(temporary)
    def f(name): return str(root / name)
    run("genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", f("server.pem"))
    run("genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", f("signer.pem"))
    dn = "/CN=PUBLIC TEST ONLY TLS ANCHOR"
    run("req", "-new", "-key", f("server.pem"), "-subj", dn, "-out", f("server.csr"))
    run("req", "-new", "-x509", "-key", f("signer.pem"), "-subj", dn, "-sha256",
        "-days", "36500", "-out", f("signer.crt"))
    (root / "index").write_text("")
    (root / "serial").write_text("1000\n")
    (root / "ca.cnf").write_text(f"""[ca]
default_ca=fixture
[fixture]
database={f('index')}
serial={f('serial')}
new_certs_dir={root}
certificate={f('signer.crt')}
private_key={f('signer.pem')}
default_md=sha256
policy=names
unique_subject=no
x509_extensions=leaf
[leaf]
basicConstraints=CA:FALSE
subjectKeyIdentifier=hash
authorityKeyIdentifier=none
[names]
commonName=supplied
""")
    for name in ("anchor", "altered"):
        run("ca", "-batch", "-notext", "-config", f("ca.cnf"), "-in", f("server.csr"),
            "-startdate", "20200101000000Z", "-enddate", "21000101000000Z", "-out", f(name + ".crt"))
        run("x509", "-in", f(name + ".crt"), "-outform", "DER", "-out", f(name + ".der"))
        rejection = run("verify", "-check_ss_sig", "-CAfile", f(name + ".crt"), f(name + ".crt"), succeeds=False)
        if b"certificate signature failure" not in rejection.lower():
            raise RuntimeError("Fixture did not fail for its self-signature: " + rejection.decode())
        # OpenSSL's explicit leaf-anchor behavior succeeds without the extra self-signature check.
        run("verify", "-CAfile", f(name + ".crt"), f(name + ".crt"))
    run("pkcs8", "-topk8", "-nocrypt", "-in", f("server.pem"), "-outform", "DER", "-out", f("server.pk8"))
    run("x509", "-in", f("signer.crt"), "-outform", "DER", "-out", f("signer.der"))
    out.mkdir(parents=True)
    for name in ("anchor.der", "altered.der", "signer.der", "server.pk8"):
        shutil.copyfile(root / name, out / name)
        print(name, hashlib.sha256((out / name).read_bytes()).hexdigest())
    # All temporary signing keys/certificates/CSR/database disappear here. Only the
    # deliberately published test server key and public certificates are retained.
