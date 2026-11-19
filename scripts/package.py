#!/usr/bin/env python3
"""Package compiled app and a clean portable distribution. Never includes local user data/JDK."""
from pathlib import Path
import hashlib
import zipfile
ROOT = Path(__file__).resolve().parents[1]
web = ROOT / '.build/webapp'
if not (web / 'WEB-INF/classes/blog/AppServlet.class').exists():
    raise SystemExit('Run python3 run.py --build-only first (with the server stopped).')
dist = ROOT / 'dist'
dist.mkdir(exist_ok=True)
war = dist / 'spam-blocker.war'
with zipfile.ZipFile(war, 'w', zipfile.ZIP_DEFLATED) as z:
    for file in sorted(web.rglob('*')):
        if file.is_file():
            z.write(file, file.relative_to(web))
archive = ROOT.parent / 'spam-blocker-project.zip'
exclude = {'.runtime', '.build', '.git', '__pycache__'}
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for file in sorted(ROOT.rglob('*')):
        rel = file.relative_to(ROOT)
        if any(part in exclude for part in rel.parts):
            continue
        if rel.parts[0] == 'data' and file.name != '.gitkeep':
            continue
        if file.is_file() and file.suffix not in ('.pyc', '.bak') and file.name != '.DS_Store':
            z.write(file, Path('spam-blocker') / rel)
checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix('.zip.sha256').write_text(f'{checksum}  {archive.name}\n')
print('WAR:', war)
print('ZIP:', archive, f'({archive.stat().st_size:,} bytes)')
