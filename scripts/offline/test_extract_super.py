import hashlib,importlib.util,pathlib,struct,tempfile,unittest

spec=importlib.util.spec_from_file_location('lp',pathlib.Path(__file__).with_name('extract-super-system.py'))
lp=importlib.util.module_from_spec(spec);spec.loader.exec_module(lp)

class ExtractSuperTest(unittest.TestCase):
    def image(self):
        data=bytearray(131072)
        geometry=bytearray(struct.pack('<II32sIII',0x616c4467,52,bytes(32),4096,2,4096))
        geometry[8:40]=hashlib.sha256(geometry).digest();data[4096:4096+52]=geometry
        partition=struct.pack('<36sIIII',b'system_a',1,0,2,0)
        extents=struct.pack('<QIQI',1,0,128,0)+struct.pack('<QIQI',1,1,0,0)
        device=struct.pack('<QIIQ36sI',128,4096,0,len(data),b'super',0)
        tables=partition+extents+device
        header=bytearray(128)
        struct.pack_into('<IHHI',header,0,0x414c5030,10,0,128)
        struct.pack_into('<I',header,44,len(tables));header[48:80]=hashlib.sha256(tables).digest()
        struct.pack_into('<III',header,80,0,1,len(partition))
        struct.pack_into('<III',header,92,len(partition),2,24)
        struct.pack_into('<III',header,104,len(partition)+len(extents),0,48)
        struct.pack_into('<III',header,116,len(partition)+len(extents),1,64)
        header[12:44]=hashlib.sha256(header).digest()
        data[12288:12288+128]=header;data[12416:12416+len(tables)]=tables
        data[65536:66048]=b'A'*512
        return data
    def test_extracts_linear_and_zero_extents_without_modifying_source(self):
        with tempfile.TemporaryDirectory() as directory:
            source=pathlib.Path(directory)/'super.img';out=pathlib.Path(directory)/'system.img'
            source.write_bytes(self.image());before=hashlib.sha256(source.read_bytes()).digest()
            lp.extract_system(source,out)
            self.assertEqual(out.read_bytes(),b'A'*512+bytes(512))
            self.assertEqual(hashlib.sha256(source.read_bytes()).digest(),before)
    def test_refuses_corrupt_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            source=pathlib.Path(directory)/'super.img';out=pathlib.Path(directory)/'system.img'
            data=self.image();data[12420]^=1;source.write_bytes(data)
            with self.assertRaisesRegex(ValueError,'checksum'):lp.extract_system(source,out)
            self.assertFalse(out.exists())

if __name__=='__main__':unittest.main()
