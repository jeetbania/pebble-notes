#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/assets"
WORK="$ROOT/../../work/branding"
mkdir -p "$WORK/Leaf.iconset" "$ROOT/mac/Resources" "$ROOT/android/app/src/main/res/drawable" "$ROOT/android/app/src/main/res/mipmap-anydpi-v26"
cp "$ASSETS"/*.tokens.json "$ROOT/mac/Resources/"
for n in 16 32 128 256 512; do
  sips -z "$n" "$n" "$ASSETS/LeafLogo-Mac.png" --out "$WORK/Leaf.iconset/icon_${n}x${n}.png" >/dev/null
  twice=$((n*2)); sips -z "$twice" "$twice" "$ASSETS/LeafLogo-Mac.png" --out "$WORK/Leaf.iconset/icon_${n}x${n}@2x.png" >/dev/null
done
python3 - "$WORK/Leaf.iconset" "$ROOT/mac/Resources/Leaf.icns" <<'PYTHON'
import sys,struct
from pathlib import Path
root=Path(sys.argv[1]);chunks=[]
for name,size in [('icp4',16),('icp5',32),('icp6',64),('ic07',128),('ic08',256),('ic09',512),('ic10',1024)]:
 filename='icon_32x32@2x.png' if size==64 else 'icon_512x512@2x.png' if size==1024 else f'icon_{size}x{size}.png'
 png=(root/filename).read_bytes();chunks.append(name.encode()+struct.pack('>I',len(png)+8)+png)
body=b''.join(chunks);Path(sys.argv[2]).write_bytes(b'icns'+struct.pack('>I',len(body)+8)+body)
PYTHON
sips -z 432 432 "$ASSETS/LeafLogo.png" --out "$ROOT/android/app/src/main/res/drawable/leaf_logo.png" >/dev/null
for spec in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
 density=${spec%:*}; n=${spec#*:}; mkdir -p "$ROOT/android/app/src/main/res/mipmap-$density"
 sips -z "$n" "$n" "$ASSETS/LeafLogo.png" --out "$ROOT/android/app/src/main/res/mipmap-$density/ic_launcher.png" >/dev/null
done
cat > "$ROOT/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml" <<'XML'
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/icon_background"/><foreground><inset android:drawable="@drawable/leaf_logo" android:inset="12dp"/></foreground></adaptive-icon>
XML
