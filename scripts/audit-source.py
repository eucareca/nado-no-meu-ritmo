#!/usr/bin/env python3
"""Check the distributable tree without reading private runtime data or printing matches."""
from pathlib import Path
import hashlib
import re
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
SKIP = {'.git', '.gradle', 'build', '.venv', '__pycache__', 'artifacts', 'entrega-fdroid'}
checks = {
    'private key': re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'private service credential': re.compile(rb'sb_secret_[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[A-Z0-9]{16}'),
    'embedded hosted backend': re.compile(rb'https?://[a-z0-9-]+\.supabase\.co|sb_publishable_[A-Za-z0-9_-]{12,}'),
    'local private path': re.compile(rb'/home/[A-Za-z0-9_-]+/|/root/\.codex|RXC[Y0-9A-Z]{7,}'),
}
failures = []
for path in sorted(ROOT.rglob('*')):
    if not path.is_file() or any(part in SKIP for part in path.relative_to(ROOT).parts): continue
    rel = path.relative_to(ROOT)
    if path.name == 'local.properties' or path.suffix.lower() in {'.apk','.aab','.jks','.keystore','.zip','.log'}:
        failures.append(f'{rel}: non-source/private artifact'); continue
    data = path.read_bytes()
    for label, pattern in checks.items():
        if pattern.search(data): failures.append(f'{rel}: {label}')
manifest = ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml')
ns = '{http://schemas.android.com/apk/res/android}'
permissions = {node.get(ns+'name') for node in manifest.findall('uses-permission')}
if permissions != {'com.android.alarm.permission.SET_ALARM', 'android.permission.health.READ_EXERCISE', 'android.permission.health.READ_DISTANCE', 'android.permission.health.READ_HEART_RATE'}: failures.append('Unexpected Android permissions')
app = manifest.find('application')
alias = app.find('activity-alias')
if alias is None or alias.get(ns+'permission') != 'android.permission.START_VIEW_PERMISSION_USAGE': failures.append('Health privacy entry point is not protected')
if app.get(ns+'allowBackup') != 'false': failures.append('Automatic backup enabled')
provider = app.find('provider')
if provider is None or provider.get(ns+'exported') != 'false': failures.append('Attachment provider exposed')
html = (ROOT/'index.html').read_text()
for forbidden in ['cdn.jsdelivr.net', 'authScreen', 'googleSignInBtn', 'musicBtn']:
    if forbidden in html: failures.append('Removed feature remains in HTML')
wrapper = ROOT/'android/gradle/wrapper/gradle-wrapper.jar'
if not wrapper.is_file() or hashlib.sha256(wrapper.read_bytes()).hexdigest() != '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046': failures.append('Unexpected Gradle wrapper')
for lang in ['pt-BR','en-US']:
    p=ROOT/'fastlane/metadata/android'/lang
    if len((p/'short_description.txt').read_text().strip()) > 80: failures.append('Store summary exceeds 80 characters')
if failures:
    print('\n'.join(failures)); raise SystemExit(1)
print('PASS: checked source artifacts, credential patterns, backend references, Android permissions, attachment provider, removed features, wrapper checksum and metadata lengths.')
print('This automated scan is not an independent security audit or proof that no vulnerabilities exist.')
