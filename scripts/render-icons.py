#!/usr/bin/env python3
"""Rebuild raster assets from the Apache-2.0 vector icon. Requires CairoSVG 2.9.1."""
from pathlib import Path
import cairosvg
ROOT = Path(__file__).resolve().parents[1]
for size in (192, 512):
    cairosvg.svg2png(url=str(ROOT / 'icon.svg'), write_to=str(ROOT / f'icon-{size}.png'), output_width=size, output_height=size)
for target in ('android/app/src/main/res/drawable-nodpi/ic_launcher.png', 'fastlane/metadata/android/pt-BR/images/icon.png', 'fastlane/metadata/android/en-US/images/icon.png'):
    path = ROOT / target
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((ROOT / 'icon-512.png').read_bytes())
