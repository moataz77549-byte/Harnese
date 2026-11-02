#!/usr/bin/env python3
"""Fetch pinned bundles, verify hashes/bytes, add a portable TypeScript overlay."""
import base64,hashlib,json,pathlib,urllib.request,tarfile,subprocess,shutil,io
root=pathlib.Path(__file__).resolve().parents[2]
dest=root/'dist/runtime-bundles';dest.mkdir(parents=True,exist_ok=True)
manifest=json.loads((dest/'manifest.json').read_text())
base='https://github.com/techjarves/Mobile-Harness/releases/download/runtime-2026.09.4'
for name in ['core','python','android','claude','dsh']:
    bundle=manifest['bundles'][name];target=dest/bundle['file']
    if not target.is_file():
        tmp=target.with_suffix('.part')
        with urllib.request.urlopen(base+'/'+bundle['file'],timeout=120) as source,tmp.open('wb') as output:shutil.copyfileobj(source,output,1024*1024)
        assert hashlib.file_digest(tmp.open('rb'),'sha256').hexdigest()==bundle['sha256'],name+' checksum mismatch'
        assert tmp.stat().st_size==bundle['compressedBytes'],name+' size mismatch'
        tmp.replace(target)
    assert hashlib.file_digest(target.open('rb'),'sha256').hexdigest()==bundle['sha256']
    pipe=subprocess.Popen(['zstd','-dc',str(target)],stdout=subprocess.PIPE)
    with tarfile.open(fileobj=pipe.stdout,mode='r|') as archive:
        bundle['uncompressedBytes']=sum(entry.size for entry in archive if entry.isfile())
    pipe.stdout.read();assert pipe.wait()==0
# TypeScript's JS compiler is architecture-independent and runs with bundled ARM64 Node.
version='5.9.3'
metadata=json.load(urllib.request.urlopen('https://registry.npmjs.org/typescript/'+version,timeout=60))
blob=urllib.request.urlopen(metadata['dist']['tarball'],timeout=120).read()
algorithm,expected=metadata['dist']['integrity'].split('-',1)
assert algorithm=='sha512' and base64.b64encode(hashlib.sha512(blob).digest()).decode()==expected
stage=dest/'typescript-stage';shutil.rmtree(stage,ignore_errors=True);(stage/'opt/nova/typescript').mkdir(parents=True)
with tarfile.open(fileobj=io.BytesIO(blob),mode='r:gz') as archive:
    for entry in archive:
        rel=pathlib.PurePosixPath(entry.name)
        assert rel.parts[0]=='package' and '..' not in rel.parts
        if entry.isfile():
            file=stage/'opt/nova/typescript'/pathlib.Path(*rel.parts[1:]);file.parent.mkdir(parents=True,exist_ok=True)
            file.write_bytes(archive.extractfile(entry).read());file.chmod(entry.mode & 0o777)
binpath=stage/'usr/local/bin';binpath.mkdir(parents=True)
for name in ['tsc','tsserver']:(binpath/name).symlink_to('/opt/nova/typescript/bin/'+name)
output=dest/'nova-typescript-arm64-5.9.3.tar.zst'
subprocess.run(['tar','--sort=name','--mtime=2026-01-01T00:00:00Z','--owner=0','--group=0','--numeric-owner','-I','zstd -19 -T0','-cf',str(output),'-C',str(stage),'.'],check=True)
manifest['bundles']['typescript']={'version':version,'file':output.name,'sha256':hashlib.file_digest(output.open('rb'),'sha256').hexdigest(),'compressedBytes':output.stat().st_size,'uncompressedBytes':sum(p.stat().st_size for p in stage.rglob('*') if p.is_file()),'includes':['TypeScript compiler '+version,'tsserver']}
(dest/'offline-measured-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
# BuildConfig uses this generated manifest; no unverified optional agent in shared assets.
shutil.rmtree(stage)
print(json.dumps({k:{'compressedBytes':v['compressedBytes'],'uncompressedBytes':v['uncompressedBytes']} for k,v in manifest['bundles'].items() if k!='agy'},indent=2))
