#!/usr/bin/env python3
"""Read-only LP metadata parser for the official CI Android system image.

Format reference: AOSP system/core/fs_mgr/liblp/include/liblp/metadata_format.h.
Only a single-device, linear/zero layout is supported; hashes and bounds are checked.
"""
import hashlib,pathlib,struct,sys


def extract_system(source, destination):
    with open(source,'rb') as image:
        image.seek(0,2);image_size=image.tell()
        def read(offset,size):
            if offset<0 or size<0 or offset+size>image_size:raise ValueError('LP read outside source image')
            image.seek(offset);data=image.read(size)
            if len(data)!=size:raise ValueError('Truncated LP image')
            return data
        geometry_prefix=read(4096,52)
        magic,size=struct.unpack_from('<II',geometry_prefix)
        if magic!=0x616c4467 or not 52<=size<=4096:raise ValueError('Unsupported LP geometry')
        geometry=bytearray(read(4096,size));expected=bytes(geometry[8:40]);geometry[8:40]=bytes(32)
        if hashlib.sha256(geometry).digest()!=expected:raise ValueError('LP geometry checksum mismatch')
        metadata_max,slots=struct.unpack_from('<II',geometry,40)
        if not 1<=slots<=8 or metadata_max<128:raise ValueError('Invalid LP geometry limits')
        offset=12288
        prefix=read(offset,128)
        magic,major,minor,header_size=struct.unpack_from('<IHHI',prefix)
        if magic!=0x414c5030 or major!=10 or minor>2 or not 128<=header_size<=4096:raise ValueError('Unsupported LP header')
        header=bytearray(read(offset,header_size));expected=bytes(header[12:44]);header[12:44]=bytes(32)
        if hashlib.sha256(header).digest()!=expected:raise ValueError('LP header checksum mismatch')
        table_size=struct.unpack_from('<I',header,44)[0]
        if header_size+table_size>metadata_max or table_size>4*1024*1024:raise ValueError('Invalid LP table size')
        tables=read(offset+header_size,table_size)
        if hashlib.sha256(tables).digest()!=bytes(header[48:80]):raise ValueError('LP tables checksum mismatch')
        def table(position,minimum_size):
            start,count,width=struct.unpack_from('<III',header,position)
            if width<minimum_size or start+count*width>len(tables):raise ValueError('LP table outside metadata')
            return [tables[start+i*width:start+(i+1)*width] for i in range(count)]
        partitions=table(80,52);extents=table(92,24);devices=table(116,64)
        if len(devices)!=1:raise ValueError('Multi-device super layout is not supported')
        selected=None
        for partition in partitions:
            name=partition[:36].split(b'\0',1)[0].decode('ascii')
            first,count=struct.unpack_from('<II',partition,40)
            print('LP partition:',name,'extents:',count)
            if name in ('system','system_a') and count>0:
                if first+count>len(extents):raise ValueError('Invalid system extent range')
                selected=extents[first:first+count];break
        if selected is None:raise ValueError('No populated system logical partition')
        with open(destination,'wb') as output:
            total=0
            for extent in selected:
                sectors,kind,sector,device=struct.unpack_from('<QIQI',extent)
                length=sectors*512
                if device!=0 or total+length>image_size:raise ValueError('Unsupported system extent')
                if kind==1:output.seek(length,1)
                elif kind==0:
                    remaining=length;position=sector*512
                    while remaining:
                        size=min(1024*1024,remaining);output.write(read(position,size));position+=size;remaining-=size
                else:raise ValueError('Unknown LP extent type')
                total+=length
            output.truncate(total)
        print('Extracted system bytes:',total)


if __name__=='__main__':
    extract_system(sys.argv[1],sys.argv[2])
