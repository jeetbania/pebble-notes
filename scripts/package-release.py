"""Package and sign a release. Run locally; private signing keys never leave this Mac."""
import base64,hashlib,json,plistlib,subprocess,tempfile,shutil
from pathlib import Path
root=Path(__file__).resolve().parent.parent
dist=root/'dist';version=json.loads((root/'releases.json').read_text())[0]['version']
mac=dist/f'PebbleNotes-Mac-{version}.zip';android=dist/f'PebbleNotes-{version}.apk'
subprocess.run(['codesign','--verify','--deep','--strict',str(dist/'Pebble Notes.app')],check=True)
if mac.exists():mac.unlink()
subprocess.run(['ditto','-c','-k','--sequesterRsrc','--keepParent',str(dist/'Pebble Notes.app'),str(mac)],check=True)
shutil.copyfile(root/'android/app/build/outputs/apk/debug/app-debug.apk',android)
import re
android_build=int(re.search(r'versionCode\s*=\s*(\d+)',(root/'android/app/build.gradle.kts').read_text())[1])
mac_build=int(plistlib.loads((root/'mac/Info.plist').read_bytes())['CFBundleVersion'])
def asset(path,build):return dict(url=f'https://github.com/jeetbania/pebble-notes/releases/download/v{version}/{path.name}',size=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),build=build)
payload=json.dumps(dict(schema=1,version=version,releases=json.loads((root/'releases.json').read_text()),mac=asset(mac,mac_build),android=asset(android,android_build)),sort_keys=True,separators=(',',':')).encode()
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'payload').write_bytes(payload)
 subprocess.run(['openssl','dgst','-sha256','-sign',str(root/'private/update-signing.pem'),'-out',str(p/'signature'),str(p/'payload')],check=True)
 envelope=dict(payload=base64.b64encode(payload).decode(),signature=base64.b64encode((p/'signature').read_bytes()).decode())
 (dist/'updates.json').write_text(json.dumps(envelope)+'\n')
(dist/'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n' for path in [mac,android,dist/'updates.json']))
print(f'Packaged Pebble Notes {version}; upload all four files together in a GitHub release.')
