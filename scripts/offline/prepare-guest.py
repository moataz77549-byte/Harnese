#!/usr/bin/env python3
"""Apply the same guest compatibility/configuration as RuntimeInstaller."""
import pathlib,re,textwrap,sys
root=pathlib.Path(sys.argv[1]).resolve()
source=pathlib.Path('app/src/main/java/com/jarves/mh/runtime/RuntimeInstaller.kt').read_text()
method=source[source.index('private fun writeAndroidGradleInitScript'):]
content=re.search(r'"""(.*?)"""',method,re.S).group(1)
assert '${' not in content
file=root/'root/.gradle/init.d/pocketdev-android.gradle';file.parent.mkdir(parents=True,exist_ok=True)
file.write_text(textwrap.dedent(content).strip()+'\n')
for path in [*list((root/'root/android-sdk/build-tools/35.0.0').glob('*')) ,root/'opt/gradle/gradle-8.14.3/bin/gradle']:
    if path.is_file() and not path.is_symlink():path.chmod(path.stat().st_mode|0o755)
