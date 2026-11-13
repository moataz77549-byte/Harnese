#!/usr/bin/env python3
"""Measure only APK-owned guest files, before CI adds Android host libraries/mounts."""
import json,os,pathlib,stat,sys
root=pathlib.Path(sys.argv[1]);seen=set();logical=allocated=count=0
for directory,dirs,files in os.walk(root,followlinks=False):
    dirs[:]=[name for name in dirs if not (pathlib.Path(directory)/name).is_symlink()]
    for name in files:
        entry=pathlib.Path(directory)/name;info=entry.lstat()
        if not stat.S_ISREG(info.st_mode) or (info.st_dev,info.st_ino) in seen:continue
        seen.add((info.st_dev,info.st_ino));logical+=info.st_size;allocated+=info.st_blocks*512;count+=1
result={'runtimeLogicalBytes':logical,'runtimeAllocatedBytesOnCiFilesystem':allocated,'regularFiles':count,'excludes':'APK itself, Android installer/Dex storage, user workspaces and CI Android system libraries'}
pathlib.Path('offline-runtime-measurements.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
