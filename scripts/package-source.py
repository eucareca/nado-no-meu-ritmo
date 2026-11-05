#!/usr/bin/env python3
"""Create a deterministic source archive from an allowlist; excludes private build/runtime artifacts."""
import argparse
import gzip
import hashlib
import io
from pathlib import Path
import tarfile
import zipfile
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--output', type=Path, required=True)
a = p.parse_args(); a.output.mkdir(parents=True, exist_ok=True)
NAMES = {'.gitignore','LICENSE','NOTICE','README.md','CONTRIBUTING.md','THIRD_PARTY_NOTICES.md','index.html','app.js','health-connect.js','training-planner.js','settings.js','local-store.js','styles.css','manifest.webmanifest','service-worker.js','icon.svg','icon-192.png','icon-512.png'}
DIRS = {'.github','android','scripts','tests','docs','fastlane','fdroid'}
EXCLUDE = {'.git','.gradle','build','.venv','__pycache__','artifacts'}
files=[]
for path in sorted(ROOT.rglob('*')):
    if not path.is_file(): continue
    rel=path.relative_to(ROOT)
    if any(part in EXCLUDE for part in rel.parts): continue
    if rel.parts[0] not in DIRS and str(rel) not in NAMES: continue
    if path.name == 'local.properties' or path.suffix in {'.apk','.aab','.jks','.keystore','.zip','.log','.pyc'}: continue
    files.append((path,rel))
prefix='nado-no-meu-ritmo-1.7.0'
archive=a.output/(prefix+'-source.zip')
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for path,rel in files:
        info=zipfile.ZipInfo(prefix+'/'+rel.as_posix(),(1980,1,1,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED
        info.external_attr=(0o100755 if path.stat().st_mode & 0o111 else 0o100644)<<16
        z.writestr(info,path.read_bytes())
archive2=a.output/(prefix+'-source.tar.gz')
with archive2.open('wb') as raw:
    with gzip.GzipFile(filename='',mode='wb',fileobj=raw,mtime=0) as gz:
        with tarfile.open(fileobj=gz,mode='w') as t:
            for path,rel in files:
                data=path.read_bytes();info=tarfile.TarInfo(prefix+'/'+rel.as_posix());info.size=len(data);info.mtime=0
                info.mode=0o755 if path.stat().st_mode & 0o111 else 0o644
                t.addfile(info,io.BytesIO(data))
manifest=a.output/'SOURCE-MANIFEST.sha256'
manifest.write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+rel.as_posix()+'\n' for path,rel in files))
print(f'Packaged {len(files)} source files: {archive.name}, {archive2.name}')
