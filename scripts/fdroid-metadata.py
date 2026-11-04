#!/usr/bin/env python3
"""Render build metadata only after the public repository URL is known."""
import argparse
from pathlib import Path
from urllib.parse import urlparse
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--repo-url', required=True, help='Public HTTPS Git repository URL')
p.add_argument('--source-url', help='Public source browsing URL; defaults to repo URL without .git')
p.add_argument('--issues-url', required=True, help='Public issue tracker URL')
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
values = {'REPO_URL': a.repo_url, 'SOURCE_URL': a.source_url or a.repo_url.removesuffix('.git'), 'ISSUES_URL': a.issues_url}
for value in values.values():
    parsed = urlparse(value)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or '\n' in value or any(c in value for c in "'\" "):
        p.error('Use public HTTPS URLs without credentials or whitespace.')
text = (ROOT / 'fdroid/br.com.tabinformatica.nadonomeritmo.yml.in').read_text()
for key, value in values.items(): text = text.replace('@' + key + '@', value)
a.output.parent.mkdir(parents=True, exist_ok=True)
a.output.write_text(text)
print(a.output)
