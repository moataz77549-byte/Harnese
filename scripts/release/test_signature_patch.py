import importlib.util,json,pathlib,struct,tempfile,unittest,zipfile

spec=importlib.util.spec_from_file_location('patch',pathlib.Path(__file__).with_name('apk_signature_patch.py'))
patch=importlib.util.module_from_spec(spec);spec.loader.exec_module(patch)

class SignaturePatchTest(unittest.TestCase):
    def fixture(self,root):
        base=root/'base.zip';signed=root/'signed.zip'
        with zipfile.ZipFile(base,'w') as z:z.writestr('payload.txt',b'unchanged payload')
        cd,end,size=patch.layout(base);data=base.read_bytes();block=bytes(16)+b'APK Sig Block 42'
        footer=bytearray(data[end:]);struct.pack_into('<I',footer,16,cd+len(block))
        signed.write_bytes(data[:cd]+block+data[cd:end]+footer)
        return base,signed,patch.create(base,signed,'a'*40,1)
    def test_reconstructs_identical_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory);base,signed,payload=self.fixture(root);out=root/'out.zip'
            patch.apply(base,payload,out)
            self.assertEqual(out.read_bytes(),signed.read_bytes())
            with zipfile.ZipFile(out) as z:self.assertEqual(z.read('payload.txt'),b'unchanged payload')
    def test_refuses_changed_ci_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory);base,signed,payload=self.fixture(root);data=bytearray(base.read_bytes());data[1]^=1;base.write_bytes(data)
            with self.assertRaisesRegex(ValueError,'checksum'):patch.apply(base,payload,root/'out.zip')
    def test_refuses_forged_output_hash_without_leaving_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory);base,signed,payload=self.fixture(root);payload['signedSha256']='0'*64;out=root/'out.zip'
            with self.assertRaisesRegex(ValueError,'checksum'):patch.apply(base,payload,out)
            self.assertFalse(out.exists());self.assertFalse(pathlib.Path(str(out)+'.part').exists())

if __name__=='__main__':unittest.main()
