#!/usr/bin/env python3
"""Export artifacts resolved by the real templates to a standard Maven repository."""
import hashlib,json,pathlib,shutil,sys
cache=pathlib.Path(sys.argv[1])/'caches/modules-2/files-2.1'
dest=pathlib.Path(sys.argv[2]);dest.mkdir(parents=True,exist_ok=True)
records=[]
for file in sorted(cache.glob('*/*/*/*/*')):
    if not file.is_file():continue
    group,module,version,_,name=file.relative_to(cache).parts
    # Android devices use the bundled ARM64 override, never Maven's host AAPT2 binary.
    if module=='aapt2' and name.endswith(('-linux.jar','-osx.jar','-windows.jar')):continue
    assert all(part not in ('','..','.') for part in (group,module,version,name))
    target=dest/pathlib.Path(*group.split('.'))/module/version/name
    digest=hashlib.file_digest(file.open('rb'),'sha256').hexdigest()
    if target.exists():assert hashlib.file_digest(target.open('rb'),'sha256').hexdigest()==digest
    else:
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(file,target)
    records.append({'path':target.relative_to(dest).as_posix(),'sha256':digest,'sizeBytes':file.stat().st_size})
assert records, 'Template build did not resolve any Maven artifacts'
(dest/'nova-resolved-artifacts.json').write_text(json.dumps({'source':'Real XML and Compose template builds; Gradle HTTPS repositories','artifacts':records},indent=2)+'\n')
print(f'Exported {len(records)} verified artifact files, {sum(x["sizeBytes"] for x in records)} bytes')
