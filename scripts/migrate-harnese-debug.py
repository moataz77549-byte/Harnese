#!/usr/bin/env python3
"""Migrate Harnese DEBUG data between APKs signed by different CI keys.

Requires a COMPUTER with Python 3 and Android platform-tools (adb), USB debugging,
and a debuggable Harnese build on the phone. No root required. The uninstall phase
is NEVER automatic during backup and requires typing MIGRATE.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tarfile
import xml.etree.ElementTree as ET
import zipfile

PKG = "com.jarves.harnese"
DATA_PATHS = (
    "shared_prefs/pocket_preferences.xml",
    "files/workspaces",
    "files/chats",
    "files/terminal-history",
    "files/android-builds",
    "files/checkpoints",
)
RESET_BOOL = {
    "runtime_setup_complete", "background_setup_complete",
    "onboarding_complete", "agent_antigravity_signed_in",
}
RESET_STRING = {"agent_antigravity_account_email", "github_login", "dsh_version"}
ARCHIVE = "harnese-projects-and-chats.tar.gz"
ORIGINAL_APK = "harnese-previous-installed.apk"
METADATA = "backup-manifest.json"


def adb(*args, capture=True):
    cmd = ["adb", *args]
    return subprocess.run(cmd, check=True, stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE, text=True)


def ensure_device():
    output = adb("devices").stdout
    devices = [line for line in output.splitlines()[1:] if line.endswith("\tdevice")]
    if len(devices) != 1:
        raise RuntimeError("Connect exactly one unlocked Android device with USB debugging enabled.")


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def apk_cert_sha256(path):
    """Read APK Signature Scheme v2/v3 signer certificate without third-party modules."""
    raw = Path(path).read_bytes()
    eocd = raw.rfind(b"PK\x05\x06")
    if eocd < 0 or eocd + 20 > len(raw):
        raise ValueError("Invalid APK ZIP footer")
    central = struct.unpack_from("<I", raw, eocd + 16)[0]
    if raw[central - 16: central] != b"APK Sig Block 42":
        raise ValueError("APK v2/v3 signature block missing")
    size = struct.unpack_from("<Q", raw, central - 24)[0]
    start = central - size - 8
    if start < 0 or struct.unpack_from("<Q", raw, start)[0] != size:
        raise ValueError("Invalid APK signature block")
    pos = start + 8

    def lp(data, offset):
        if offset + 4 > len(data):
            raise ValueError("Short length-prefixed field")
        length = struct.unpack_from("<I", data, offset)[0]
        end = offset + 4 + length
        if end > len(data):
            raise ValueError("Invalid APK signing data")
        return data[offset + 4:end], end

    while pos < central - 24:
        pair_size = struct.unpack_from("<Q", raw, pos)[0]
        kind = struct.unpack_from("<I", raw, pos + 8)[0]
        payload = raw[pos + 12:pos + 8 + pair_size]
        pos += pair_size + 8
        if kind not in (0x7109871A, 0xF05368C0):
            continue
        signers, _ = lp(payload, 0)
        signer, _ = lp(signers, 0)
        signed_data, _ = lp(signer, 0)
        _, index = lp(signed_data, 0)  # digest list
        certificates, _ = lp(signed_data, index)
        cert, _ = lp(certificates, 0)
        return hashlib.sha256(cert).hexdigest()
    raise ValueError("No APK v2/v3 signer certificate found")


def safe_name(path):
    while path.startswith("./"):
        path = path[2:]
    pieces = path.rstrip("/").split("/")
    if not path or any(p in ("..", "", ".") for p in pieces):
        return None
    if path.startswith("/") or "\x00" in path:
        return None
    return path


def check_archive(path):
    counts = {"files": 0, "directories": 0}
    root_ok = False
    seen = set()
    with tarfile.open(path, "r:gz") as archive:
        for member in archive:
            name = safe_name(member.name)
            if name is None or name in seen or not (member.isfile() or member.isdir()):
                raise ValueError("Invalid, duplicate, or unsafe backup entry")
            seen.add(name)
            if name == "shared_prefs/pocket_preferences.xml":
                root_ok = member.isfile()
            if member.isfile():
                counts["files"] += 1
            else:
                counts["directories"] += 1
    if not root_ok:
        raise ValueError("Project metadata missing; do NOT uninstall the old app")
    return counts


def backup(directory):
    ensure_device()
    directory.mkdir(parents=True, exist_ok=True)
    if any((directory / f).exists() for f in (ARCHIVE, ORIGINAL_APK, METADATA)):
        raise RuntimeError("Backup folder already contains data. Use a fresh empty folder.")
    test = adb("exec-out", "run-as", PKG, "sh", "-c", "pwd")
    if not test.stdout.strip().startswith("/data/"):
        raise RuntimeError("run-as unavailable: the installed app may not be a debuggable build.")
    # Preserve the exact old signed APK for rollback. All split APKs would need
    # special handling, so fail safely rather than saving an incomplete set.
    paths = [line.removeprefix("package:").strip() for line in
             adb("shell", "pm", "path", PKG).stdout.splitlines() if line.startswith("package:")]
    if len(paths) != 1:
        raise RuntimeError("Expected one base APK; split installation is not supported.")
    adb("pull", paths[0], str(directory / ORIGINAL_APK), capture=False)
    if not zipfile.is_zipfile(directory / ORIGINAL_APK):
        raise RuntimeError("Old APK was not downloaded correctly.")
    signer = apk_cert_sha256(directory / ORIGINAL_APK)

    # The remote process runs as the old app UID. No root, no Android Backup API.
    # Saving only projects, chats, settings and review history avoids moving
    # Keystore-bound encrypted tokens or multi-GB Linux runtime installations.
    shell_script = (
        "set --; for p in " + " ".join(DATA_PATHS) +
        '; do [ ! -e "$p" ] || set -- "$@" "$p"; done; '
        '[ "$#" -gt 0 ] || exit 64; exec tar -cf - "$@"'
    )
    proc = subprocess.Popen(["adb", "exec-out", "run-as", PKG, "sh", "-c", shell_script],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    temp = directory / (ARCHIVE + ".partial")
    try:
        with tarfile.open(temp, "w:gz") as output:
            with tarfile.open(fileobj=proc.stdout, mode="r|") as incoming:
                for member in incoming:
                    name = safe_name(member.name)
                    if name is None:
                        raise ValueError("Unsafe path in app backup")
                    if not (member.isfile() or member.isdir()):
                        continue  # Never restore symlinks/hardlinks/unsupported nodes.
                    member.name = name
                    member.uid = member.gid = 0
                    member.uname = member.gname = ""
                    output.addfile(member, incoming.extractfile(member) if member.isfile() else None)
        error_output = proc.stderr.read().decode("utf-8", "replace")[:240]
        if proc.wait() != 0:
            raise RuntimeError("Could not export app data: " + error_output)
        stats = check_archive(temp)
        temp.replace(directory / ARCHIVE)
        metadata = {
            "package": PKG, "old_signature_sha256": signer,
            "backup_sha256": sha256_file(directory / ARCHIVE),
            "old_apk_sha256": sha256_file(directory / ORIGINAL_APK),
            "contents": stats,
            "omitted": ["Linux runtime", "Telegram token", "encrypted API keys", "Keystore keys"],
        }
        (directory / METADATA).write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
        os.chmod(directory / ARCHIVE, 0o600)
        os.chmod(directory / ORIGINAL_APK, 0o600)
        os.chmod(directory / METADATA, 0o600)
        print("Backup verified:", stats, "; old signer:", signer[:16], "…")
        print("Keep this folder PRIVATE. It contains project source files and chats.")
    except Exception:
        temp.unlink(missing_ok=True)
        raise
    finally:
        if proc.poll() is None:
            proc.kill()
            proc.wait()


def verify(directory):
    meta = json.loads((directory / METADATA).read_text())
    if meta.get("package") != PKG:
        raise RuntimeError("Wrong application in backup")
    for file, expected in ((ARCHIVE, meta["backup_sha256"]), (ORIGINAL_APK, meta["old_apk_sha256"])):
        if sha256_file(directory / file) != expected:
            raise RuntimeError("Backup SHA-256 mismatch: " + file)
    if apk_cert_sha256(directory / ORIGINAL_APK) != meta["old_signature_sha256"]:
        raise RuntimeError("Old APK signature changed")
    result = check_archive(directory / ARCHIVE)
    print("Verified backup:", result, "; old APK signature:", meta["old_signature_sha256"][:16], "…")
    return meta


def sanitize_preferences(content):
    root = ET.fromstring(content)
    for el in root:
        name = el.attrib.get("name")
        if name in RESET_BOOL and el.tag == "boolean":
            el.set("value", "false")
        if name in RESET_STRING and el.tag == "string":
            el.text = ""
        if name == "selected_dev_stacks" and el.tag == "string":
            el.text = "[]"
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def restore(directory, reset_runtime):
    proc = subprocess.Popen(
        ["adb", "shell", "-T", "run-as", PKG, "sh", "-c", "tar -xf -"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    try:
        with tarfile.open(directory / ARCHIVE, "r:gz") as stored:
            with tarfile.open(fileobj=proc.stdin, mode="w|") as outbound:
                for member in stored:
                    # Parsed and validated by verify(), but double-check.
                    if safe_name(member.name) != member.name or not (member.isfile() or member.isdir()):
                        raise RuntimeError("Unsafe backup entry")
                    if reset_runtime and member.name == "shared_prefs/pocket_preferences.xml":
                        content = sanitize_preferences(stored.extractfile(member).read())
                        member.size = len(content)
                        outbound.addfile(member, io.BytesIO(content))
                    else:
                        outbound.addfile(member, stored.extractfile(member) if member.isfile() else None)
        message = proc.stderr.read().decode("utf-8", "replace")[:240]
        if proc.wait() != 0:
            raise RuntimeError("Restore failed: " + message)
        adb("exec-out", "run-as", PKG, "sh", "-c",
            "test -s shared_prefs/pocket_preferences.xml")
    finally:
        if proc.poll() is None:
            proc.kill()
            proc.wait()


def upgrade(directory, new_apk):
    ensure_device()
    meta = verify(directory)
    if not zipfile.is_zipfile(new_apk) or zipfile.ZipFile(new_apk).testzip() is not None:
        raise RuntimeError("New APK is corrupt")
    new_signer = apk_cert_sha256(new_apk)
    old_signer = meta["old_signature_sha256"]
    print("Old signer:", old_signer)
    print("New signer:", new_signer)
    if new_signer == old_signer:
        print("Matching certificates: installing as a normal update, no uninstall.")
        adb("install", "-r", str(new_apk), capture=False)
        return
    print("Different certificates: data migration is required.")
    print("The backup and original APK will remain on this computer for recovery.")
    if input("Type MIGRATE to uninstall old version, install new one and restore data: ").strip() != "MIGRATE":
        print("Cancelled. Old app untouched.")
        return
    # This operation deletes the device's app sandbox. The verified backup and
    # original APK remain on the computer, and we attempt rollback on failure.
    adb("shell", "am", "force-stop", PKG)
    adb("uninstall", PKG, capture=False)
    try:
        adb("install", str(new_apk), capture=False)
        restore(directory, reset_runtime=True)
        print("Migration completed. Projects/chats restored. Reinstall runtime and sign in again.")
    except Exception as error:
        print("NEW INSTALL FAILED:", error, "— attempting rollback to the original APK.", file=sys.stderr)
        try:
            adb("uninstall", PKG, capture=False)
        except Exception:
            pass
        try:
            adb("install", str(directory / ORIGINAL_APK), capture=False)
            restore(directory, reset_runtime=False)
            print("Original version and saved data restored.")
        except Exception as rollback_error:
            print("AUTOMATIC ROLLBACK FAILED:", rollback_error, file=sys.stderr)
            print("Do not delete the backup folder. It contains your data and old APK.", file=sys.stderr)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for action in ("backup", "verify"):
        sub = commands.add_parser(action)
        sub.add_argument("--directory", type=Path, required=True)
    sub = commands.add_parser("upgrade")
    sub.add_argument("--directory", type=Path, required=True)
    sub.add_argument("--apk", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == "backup":
            backup(args.directory.resolve())
        elif args.command == "verify":
            verify(args.directory.resolve())
        else:
            upgrade(args.directory.resolve(), args.apk.resolve())
    except (RuntimeError, ValueError, OSError, subprocess.CalledProcessError, tarfile.TarError) as exc:
        print("ERROR:", str(exc)[:350], file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
