#!/usr/bin/env python3
"""Regenerate the checked-in Java lite messages with the pinned macOS ARM compiler."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('protoc', type=Path, help='Downloaded pinned protoc executable (see dependency ADR)')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
lock = json.loads((root / 'third_party/remote-dependencies.json').read_text())
if hashlib.sha256(args.protoc.read_bytes()).hexdigest() != lock['protoc.exe']['sha256']:
    raise SystemExit('Compiler checksum mismatch; no generation performed')
subprocess.run([str(args.protoc.resolve()), '--proto_path=' + str(root / 'src/main/proto'),
                '--java_out=lite:' + str(root / 'src/generated/java'),
                str(root / 'src/main/proto/polo.proto'),
                str(root / 'src/main/proto/remotemessage.proto')], check=True)
