#!/usr/bin/env python3
"""Transfer a public APK signature, never a signing key, onto its verified CI base.

Only the APK signing block and ZIP end record can be inserted. APK payload and
central directory must remain byte-identical. Android apksigner is the final verifier.
"""
import argparse,base64,hashlib,json,pathlib,struct


def digest(path):
    with open(path,'rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def layout(path):
    size=pathlib.Path(path).stat().st_size
    with open(path,'rb') as stream:
        count=min(size,65557);stream.seek(size-count);tail=stream.read(count)
    index=tail.rfind(b'PK\x05\x06')
    if index<0 or index+22>len(tail):raise ValueError('ZIP end record missing')
    comment=struct.unpack_from('<H',tail,index+20)[0]
    if index+22+comment!=len(tail):raise ValueError('Invalid ZIP end record')
    disk,cd_disk,disk_entries,entries,cd_size,cd=struct.unpack_from('<HHHHII',tail,index+4)
    eocd=size-count+index
    if disk or cd_disk or entries!=disk_entries or cd==0xffffffff or cd+cd_size!=eocd:raise ValueError('Unsupported ZIP layout')
    return cd,eocd,size


def same_range(left,lo,right,ro,length):
    with open(left,'rb') as a,open(right,'rb') as b:
        a.seek(lo);b.seek(ro)
        while length:
            amount=min(length,1024*1024)
            if a.read(amount)!=b.read(amount):raise ValueError('Signing changed APK payload or directory')
            length-=amount


def create(base,signed,commit,run_id):
    cd,end,size=layout(base);signed_cd,signed_end,signed_size=layout(signed)
    if signed_cd<=cd or signed_cd-cd>1024*1024 or signed_end-signed_cd!=end-cd:raise ValueError('Unexpected APK signature layout')
    same_range(base,0,signed,0,cd);same_range(base,cd,signed,signed_cd,end-cd)
    with open(signed,'rb') as stream:
        stream.seek(cd);block=stream.read(signed_cd-cd)
        stream.seek(signed_end);footer=stream.read()
    return {'schemaVersion':1,'sourceCommit':commit,'verifiedRunId':int(run_id),
        'baseSha256':digest(base),'signedSha256':digest(signed),'signedSizeBytes':signed_size,
        'baseCentralDirectoryOffset':cd,'baseEndRecordOffset':end,'baseSizeBytes':size,
        'signingBlock':base64.b64encode(block).decode(),'endRecord':base64.b64encode(footer).decode(),
        'fileName':'Harnese-Nova-v1.3.2-Offline-ARM64.apk'}


def apply(base,patch,output):
    if patch.get('schemaVersion')!=1 or digest(base)!=patch['baseSha256']:raise ValueError('CI base checksum mismatch')
    cd,end,size=layout(base)
    if (cd,end,size)!=(patch['baseCentralDirectoryOffset'],patch['baseEndRecordOffset'],patch['baseSizeBytes']):raise ValueError('CI ZIP layout mismatch')
    block=base64.b64decode(patch['signingBlock'],validate=True);footer=base64.b64decode(patch['endRecord'],validate=True)
    if not 32<=len(block)<=1024*1024 or block[-16:]!=b'APK Sig Block 42' or not 22<=len(footer)<=65557:raise ValueError('Invalid signature patch')
    def copy(source,destination,length):
        while length:
            data=source.read(min(length,1024*1024))
            if not data:raise ValueError('Truncated CI base')
            destination.write(data);length-=len(data)
    temporary=pathlib.Path(str(output)+'.part')
    try:
        with open(base,'rb') as source,open(temporary,'wb') as target:
            copy(source,target,cd);target.write(block);copy(source,target,end-cd);target.write(footer)
        if temporary.stat().st_size!=patch['signedSizeBytes'] or digest(temporary)!=patch['signedSha256']:raise ValueError('Signed APK checksum mismatch')
        layout(temporary)
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser();commands=parser.add_subparsers(dest='command',required=True)
    make=commands.add_parser('create');make.add_argument('base');make.add_argument('signed');make.add_argument('commit');make.add_argument('run_id');make.add_argument('output')
    use=commands.add_parser('apply');use.add_argument('base');use.add_argument('patch');use.add_argument('output')
    args=parser.parse_args()
    if args.command=='create':pathlib.Path(args.output).write_text(json.dumps(create(args.base,args.signed,args.commit,args.run_id),indent=2)+'\n')
    else:apply(args.base,json.loads(pathlib.Path(args.patch).read_text()),args.output)
