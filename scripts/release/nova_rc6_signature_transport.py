#!/usr/bin/env python3
"""Transport only a public signed APK delta, never an owner private key."""
import argparse, base64, hashlib, json, pathlib, re, subprocess, tempfile, zipfile

NAME = "Harnese-Nova-2.0.0-rc.6-Android8Plus-ARM64.apk"
CERT = "d7f31fde154fc9b46fe8ef333fd12ecdfbd9b331d71a5284c24a6ad8ad45de51"
MAX_APK = 32 * 1024 * 1024
MAX_DELTA = 2 * 1024 * 1024
SIGNATURE = re.compile(r"^META-INF/(MANIFEST\.MF|[A-Za-z0-9_-]+\.(SF|RSA|DSA|EC))$", re.I)

def sha(path):
    with open(path, "rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def verify_payload(base, signed):
    with zipfile.ZipFile(base) as a, zipfile.ZipFile(signed) as b:
        if a.testzip() is not None or b.testzip() is not None:
            raise ValueError("APK ZIP integrity failure")
        if len(a.namelist()) != len(set(a.namelist())) or len(b.namelist()) != len(set(b.namelist())):
            raise ValueError("Duplicate ZIP entries")
        an = {x for x in a.namelist() if not SIGNATURE.fullmatch(x)}
        bn = {x for x in b.namelist() if not SIGNATURE.fullmatch(x)}
        if an != bn:
            raise ValueError("APK payload names changed")
        for name in an:
            if a.read(name) != b.read(name):
                raise ValueError(f"APK payload changed: {name}")
        if not any(SIGNATURE.fullmatch(x) and x.upper().endswith(".RSA") for x in b.namelist()):
            raise ValueError("Expected RSA v1 signature entry missing")

def create(base, signed, source, run_id, output):
    if not re.fullmatch(r"[a-f0-9]{40}", source) or int(run_id) <= 0:
        raise ValueError("Invalid CI source")
    if base.stat().st_size > MAX_APK or signed.stat().st_size > MAX_APK:
        raise ValueError("Unexpected APK size")
    verify_payload(base, signed)
    with tempfile.TemporaryDirectory() as work:
        delta = pathlib.Path(work) / "public-signature-delta.zst"
        subprocess.run(["zstd","-q","-19","--patch-from="+str(base),str(signed),"-o",str(delta)],check=True)
        if delta.stat().st_size > MAX_DELTA:
            raise ValueError("Public signature delta too large")
        data = delta.read_bytes()
    manifest = dict(schemaVersion=1,sourceCommit=source,verifiedRunId=int(run_id),
                    fileName=NAME,certificateSha256=CERT,baseSha256=sha(base),
                    baseSizeBytes=base.stat().st_size,signedSha256=sha(signed),
                    signedSizeBytes=signed.stat().st_size,deltaSha256=hashlib.sha256(data).hexdigest(),
                    delta=base64.b64encode(data).decode("ascii"))
    output.write_text(json.dumps(manifest,indent=2)+"\n")

def apply(base, manifest, output):
    d=json.loads(manifest.read_text())
    if d.get("schemaVersion")!=1 or d.get("fileName")!=NAME or d.get("certificateSha256")!=CERT:
        raise ValueError("Unexpected release identity")
    if d["baseSizeBytes"] != base.stat().st_size or sha(base)!=d["baseSha256"]:
        raise ValueError("CI base checksum mismatch")
    if not 0<d["signedSizeBytes"]<=MAX_APK:
        raise ValueError("Unexpected signed APK size")
    data=base64.b64decode(d["delta"],validate=True)
    if not 0<len(data)<=MAX_DELTA or hashlib.sha256(data).hexdigest()!=d["deltaSha256"]:
        raise ValueError("Public delta integrity failure")
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory() as work:
        delta=pathlib.Path(work)/"public-signature-delta.zst"
        delta.write_bytes(data)
        tmp=output.with_suffix(".apk.part")
        try:
            subprocess.run(["zstd","-q","-d","--patch-from="+str(base),str(delta),"-o",str(tmp),"-f"],check=True)
            if tmp.stat().st_size!=d["signedSizeBytes"] or sha(tmp)!=d["signedSha256"]:
                raise ValueError("Reconstructed signed APK checksum mismatch")
            verify_payload(base,tmp)
            tmp.replace(output)
        finally:
            tmp.unlink(missing_ok=True)

if __name__=="__main__":
    p=argparse.ArgumentParser()
    sub=p.add_subparsers(dest="command",required=True)
    a=sub.add_parser("create")
    for key in ("base","signed"):a.add_argument(key,type=pathlib.Path)
    a.add_argument("source")
    a.add_argument("run_id",type=int)
    a.add_argument("output",type=pathlib.Path)
    b=sub.add_parser("apply")
    for key in ("base","manifest","output"):b.add_argument(key,type=pathlib.Path)
    args=p.parse_args()
    if args.command=="create":create(args.base,args.signed,args.source,args.run_id,args.output)
    else:apply(args.base,args.manifest,args.output)
