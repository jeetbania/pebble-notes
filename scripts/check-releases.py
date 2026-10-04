"""Validate and copy release notes for both native apps before packaging."""
import json, plistlib, re
from pathlib import Path
root=Path(__file__).resolve().parent.parent
raw=(root/'releases.json').read_bytes(); notes=json.loads(raw)
mac=plistlib.loads((root/'mac/Info.plist').read_bytes())['CFBundleShortVersionString']
android=re.search(r'versionName\s*=\s*"([^"]+)"',(root/'android/app/build.gradle.kts').read_text()).group(1)
assert notes and notes[0]['version']==mac==android, 'Add the current release to releases.json before building either app'
assert len({n['version'] for n in notes})==len(notes), 'Release versions must be unique'
for n in notes:
    assert n['title'].strip() and n['changes'] and all(isinstance(c,str) and c.strip() for c in n['changes']), 'Each release needs readable change notes'
for dest in ['mac/Resources/releases.json','android/app/src/main/assets/releases.json']:(root/dest).write_bytes(raw)
print('Release notes validated for '+mac)

(root/"CHANGELOG.md").write_text("# Pebble Notes changelog\n\n"+"\n".join("## "+n["version"]+" · "+n["title"]+"\n\n"+"\n".join("- "+c for c in n["changes"])+"\n" for n in notes))
