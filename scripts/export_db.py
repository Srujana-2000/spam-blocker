#!/usr/bin/env python3
"""Export a stopped demo database to data/backup.sql; contains sensitive local data."""
from pathlib import Path
import subprocess
import sys
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from run import java_home

if not (ROOT / 'data/blog.mv.db').exists():
    raise SystemExit('No database yet. Run the application first.')
home = java_home()
java = home / 'bin' / ('java.exe' if sys.platform == 'win32' else 'java')
subprocess.run([str(java), '-cp', str(ROOT / 'lib/h2-2.5.252.jar'), 'org.h2.tools.Script',
                '-url', 'jdbc:h2:file:' + str(ROOT / 'data/blog'), '-user', 'sa', '-password', '',
                '-script', str(ROOT / 'data/backup.sql')], check=True)
print('Exported:', ROOT / 'data/backup.sql')
