#!/usr/bin/env python3
"""
tvadb.py — minimal ADB-over-TCP client built on the adb-shell package.

Used when the stock adb binary cannot reach the TV (macOS Local Network privacy gives
"No route to host" even though port 5555 is open). It reuses ~/.android/adbkey so the TV's
"Allow USB debugging?" authorisation is shared with the stock adb.

  tvadb.py --host IP [--port 5555] status
  tvadb.py --host IP shell "<command>"
  tvadb.py --host IP push <local> <remote>      (base64 chunks through the shell, md5-verified)
  tvadb.py --host IP pull <remote> <local>
  tvadb.py --host IP screenshot <out.png>
  tvadb.py --host IP install <apk>
"""
import argparse
import base64
import hashlib
import os
import shlex
import sys

try:
    from adb_shell.adb_device import AdbDeviceTcp
    from adb_shell.auth.keygen import keygen
    from adb_shell.auth.sign_pythonrsa import PythonRSASigner
except ImportError:
    sys.exit("adb-shell is not installed. Run:  python3 -m venv .venv && .venv/bin/pip install adb-shell")

CHUNK = 24 * 1024  # raw bytes per shell command (~32 KB of base64): safe for the ADB OPEN packet


def signer():
    key = os.path.expanduser("~/.android/adbkey")
    if not os.path.exists(key):
        os.makedirs(os.path.dirname(key), exist_ok=True)
        keygen(key)
        print(f"generated a new ADB key at {key}", file=sys.stderr)
    with open(key) as f:
        priv = f.read()
    with open(key + ".pub") as f:
        pub = f.read()
    return PythonRSASigner(pub, priv)


def connect(host, port):
    dev = AdbDeviceTcp(host, port, default_transport_timeout_s=20.0)
    print("Connecting... if the TV shows 'Allow USB debugging?', select Allow (tick 'Always allow').",
          file=sys.stderr)
    ok = dev.connect(rsa_keys=[signer()], auth_timeout_s=60.0)
    if not ok:
        sys.exit("could not connect")
    return dev


def q(s):
    return shlex.quote(s)


def shell(dev, cmd):
    out = dev.shell(cmd, read_timeout_s=120.0, timeout_s=300.0)
    return out if out is not None else ""


def md5_local(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def push(dev, local, remote):
    size = os.path.getsize(local)
    # Try the native push first; it is fast when it works.
    try:
        dev.push(local, remote, read_timeout_s=30.0)
        if shell(dev, f"md5sum {q(remote)}").split()[0] == md5_local(local):
            print(f"pushed {local} -> {remote} ({size} bytes)")
            return
    except Exception as e:  # noqa: BLE001 — any failure means fall back
        print(f"native push failed ({e.__class__.__name__}); using base64 chunks", file=sys.stderr)
    shell(dev, f"rm -f {q(remote)}")
    sent = 0
    with open(local, "rb") as f:
        while True:
            chunk = f.read(CHUNK)
            if not chunk:
                break
            b64 = base64.b64encode(chunk).decode("ascii")
            shell(dev, f"echo {b64} | base64 -d >> {q(remote)}")
            sent += len(chunk)
            print(f"\r  {sent}/{size} bytes", end="", file=sys.stderr, flush=True)
    print(file=sys.stderr)
    remote_md5 = shell(dev, f"md5sum {q(remote)}").split()[0]
    if remote_md5 != md5_local(local):
        sys.exit(f"md5 mismatch after push: {remote_md5} != {md5_local(local)}")
    print(f"pushed {local} -> {remote} ({size} bytes, md5 ok)")


def pull(dev, remote, local):
    try:
        dev.pull(remote, local, read_timeout_s=60.0)
    except Exception:  # noqa: BLE001
        data = dev.exec_out(f"base64 {q(remote)}", decode=False, read_timeout_s=120.0)
        with open(local, "wb") as f:
            f.write(base64.b64decode(data))
    remote_md5 = shell(dev, f"md5sum {q(remote)}").split()[0]
    if remote_md5 != md5_local(local):
        sys.exit("md5 mismatch after pull")
    print(f"pulled {remote} -> {local}")


def screenshot(dev, out):
    data = b""
    try:
        data = dev.exec_out("screencap -p", decode=False, read_timeout_s=60.0)
    except Exception:  # noqa: BLE001
        data = b""
    if not data.startswith(b"\x89PNG"):
        shell(dev, "screencap -p /data/local/tmp/_shot.png")
        data = base64.b64decode(dev.exec_out("base64 /data/local/tmp/_shot.png", decode=False, read_timeout_s=120.0))
        shell(dev, "rm -f /data/local/tmp/_shot.png")
    if not data.startswith(b"\x89PNG"):
        sys.exit("screenshot did not return a PNG")
    with open(out, "wb") as f:
        f.write(data)
    print(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--port", type=int, default=5555)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status")
    p = sub.add_parser("shell"); p.add_argument("command")
    p = sub.add_parser("push"); p.add_argument("local"); p.add_argument("remote")
    p = sub.add_parser("pull"); p.add_argument("remote"); p.add_argument("local")
    p = sub.add_parser("screenshot"); p.add_argument("out")
    p = sub.add_parser("install"); p.add_argument("apk")
    a = ap.parse_args()

    dev = connect(a.host, a.port)
    try:
        if a.cmd == "status":
            print("device", a.host, shell(dev, "getprop ro.product.model").strip(),
                  "android", shell(dev, "getprop ro.build.version.release").strip())
        elif a.cmd == "shell":
            sys.stdout.write(shell(dev, a.command))
        elif a.cmd == "push":
            push(dev, a.local, a.remote)
        elif a.cmd == "pull":
            pull(dev, a.remote, a.local)
        elif a.cmd == "screenshot":
            screenshot(dev, a.out)
        elif a.cmd == "install":
            remote = "/data/local/tmp/" + os.path.basename(a.apk)
            push(dev, a.apk, remote)
            out = shell(dev, f"pm install -r -g -t {q(remote)}")
            shell(dev, f"rm -f {q(remote)}")
            print(out)
            if "Success" not in out:
                sys.exit(1)
    finally:
        dev.close()


if __name__ == "__main__":
    main()
