#!/usr/bin/env python3
"""Compile and run the Java/JSP application. Python is a launcher, not the web backend."""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import platform
import shutil
import socket
import errno
import subprocess
import sys
import tarfile
import urllib.request
import webbrowser
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent
TOMCAT = ROOT / 'vendor' / 'apache-tomcat-10.1.61'
RUNTIME = ROOT / '.runtime'
BUILD = ROOT / '.build'
WEBAPP = BUILD / 'webapp'
SERVER = BUILD / 'server'


def java_home(setup=False):
    candidates = []
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']))
    if RUNTIME.exists():
        candidates.extend(sorted(RUNTIME.glob('jdk*/Contents/Home'), reverse=True))
        candidates.extend(sorted(RUNTIME.glob('jdk*'), reverse=True))
    javac = shutil.which('javac')
    if javac:
        candidates.append(Path(javac).resolve().parent.parent)
    if sys.platform == 'darwin':
        probe = subprocess.run(['/usr/libexec/java_home', '-v', '21+'], capture_output=True, text=True)
        if probe.returncode == 0:
            candidates.append(Path(probe.stdout.strip()))
    suffix = '.exe' if os.name == 'nt' else ''
    for home in candidates:
        exe = home / 'bin' / ('javac' + suffix)
        if exe.exists():
            p = subprocess.run([str(exe), '-version'], capture_output=True, text=True)
            try:
                if p.returncode == 0 and int((p.stdout + p.stderr).split()[1].split('.')[0]) >= 21:
                    return home
            except (ValueError, IndexError):
                pass
    if setup:
        install_java()
        return java_home()
    raise RuntimeError('JDK 21+ is required. Install it and set JAVA_HOME, or run: python3 run.py --setup-java')


def install_java():
    """Opt-in JDK download into this project only; no system installation."""
    if os.name == 'nt':
        raise RuntimeError('On Windows, install a JDK 21+ and set JAVA_HOME, then rerun py run.py.')
    system = {'Darwin': 'mac', 'Linux': 'linux'}.get(platform.system())
    arch = {'arm64': 'aarch64', 'aarch64': 'aarch64', 'x86_64': 'x64', 'AMD64': 'x64'}.get(platform.machine())
    if not system or not arch:
        raise RuntimeError('Install a JDK 21+ for your operating system and set JAVA_HOME.')
    RUNTIME.mkdir(exist_ok=True)
    url = f'https://api.adoptium.net/v3/binary/latest/21/ga/{system}/{arch}/jdk/hotspot/normal/eclipse'
    archive = RUNTIME / 'java-download.tar.gz'
    print('Downloading a JDK to .runtime/ (about 200 MB)...', flush=True)
    with urllib.request.urlopen(url, timeout=180) as response, archive.open('wb') as out:
        shutil.copyfileobj(response, out)
    with tarfile.open(archive) as tar:
        # Only extract safe, relative members (including safe in-tree symlinks).
        base = RUNTIME.resolve()
        for member in tar.getmembers():
            dest = (base / member.name).resolve()
            if not dest.is_relative_to(base):
                raise RuntimeError('Unsafe path in JDK archive')
            if member.issym() and not (dest.parent / member.linkname).resolve().is_relative_to(base):
                raise RuntimeError('Unsafe symlink in JDK archive')
            if member.islnk() and not (base / member.linkname).resolve().is_relative_to(base):
                raise RuntimeError('Unsafe hardlink in JDK archive')
        # Python 3.12+ applies an additional standard extraction filter.
        if sys.version_info >= (3, 12):
            tar.extractall(RUNTIME, filter='data')
        else:
            tar.extractall(RUNTIME)
    archive.unlink()


def compile_project(home):
    if not TOMCAT.is_dir():
        raise RuntimeError('The bundled Tomcat folder is missing. Extract the complete project ZIP.')
    print('Compiling Java sources...', flush=True)
    # Preserve server files while rebuilding only the generated webapp.
    if WEBAPP.exists():
        shutil.rmtree(WEBAPP)
    shutil.copytree(ROOT / 'src/main/webapp', WEBAPP)
    classes = WEBAPP / 'WEB-INF/classes'
    classes.mkdir(parents=True)
    jars = WEBAPP / 'WEB-INF/lib'
    jars.mkdir(parents=True)
    shutil.copy2(ROOT / 'lib/h2-2.5.252.jar', jars)
    cp = os.pathsep.join([str(TOMCAT / 'lib/servlet-api.jar'), str(ROOT / 'lib/h2-2.5.252.jar')])
    javac = home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
    sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
    subprocess.run([str(javac), '-encoding', 'UTF-8', '--release', '21', '-cp', cp, '-d', str(classes), *map(str, sources)], check=True)
    return classes


def configure_server(port):
    (SERVER / 'conf').mkdir(parents=True, exist_ok=True)
    for name in ['logs', 'temp', 'work', 'webapps']:
        (SERVER / name).mkdir(parents=True, exist_ok=True)
    for name in ['web.xml', 'catalina.properties', 'logging.properties']:
        shutil.copy2(TOMCAT / 'conf' / name, SERVER / 'conf' / name)
    # No remote shutdown socket, no AJP, no manager app, bind only to loopback.
    config = f'''<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1"><Service name="Catalina">
<Connector address="127.0.0.1" port="{port}" protocol="HTTP/1.1" connectionTimeout="20000" maxPostSize="2500000" maxParameterCount="100" />
<Engine name="Catalina" defaultHost="localhost"><Host name="localhost" appBase="webapps" unpackWARs="false" autoDeploy="false">
<Context path="" docBase="{escape(str(WEBAPP), {'"': '&quot;'})}" reloadable="false">
<CookieProcessor sameSiteCookies="lax" />
</Context></Host></Engine></Service></Server>'''
    (SERVER / 'conf/server.xml').write_text(config, encoding='utf-8')


def unit_tests(home, classes):
    javac = home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
    java = home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    test_classes = BUILD / 'test-classes'
    test_classes.mkdir(exist_ok=True)
    subprocess.run([str(javac), '-encoding', 'UTF-8', '--release', '21', '-cp', str(classes), '-d', str(test_classes), str(ROOT / 'tests/CoreTest.java')], check=True)
    subprocess.run([str(java), '-cp', os.pathsep.join([str(classes), str(test_classes)]), 'CoreTest'], check=True, cwd=ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8080)
    parser.add_argument('--open', action='store_true', help='Open the local project in your browser')
    parser.add_argument('--setup-java', action='store_true', help='Download a local JDK if no JDK 21+ is found (Mac/Linux)')
    parser.add_argument('--build-only', action='store_true')
    parser.add_argument('--test', action='store_true', help='Compile and run Java core tests, then exit')
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error('Choose a port between 1024 and 65535.')
    if not (args.test or args.build_only):
        with socket.socket() as check:
            try:
                check.bind(('127.0.0.1', args.port))
            except OSError as exc:
                if exc.errno not in (errno.EADDRINUSE, 10048):
                    raise RuntimeError(f'Cannot bind to the local port: {exc}') from exc
                raise RuntimeError(f'Port {args.port} is busy. Stop the existing server, or use --port 8081.')
    home = java_home(args.setup_java)
    classes = compile_project(home)
    if args.test:
        unit_tests(home, classes)
        return
    if args.build_only:
        return
    configure_server(args.port)
    java = home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    env = dict(os.environ, JAVA_HOME=str(home), BLOG_HOME=str(ROOT))
    cmd = [str(java), '--add-opens=java.base/java.lang=ALL-UNNAMED', '--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED', '-Djava.awt.headless=true', '-Duser.timezone=Asia/Kolkata',
           f'-Dcatalina.home={TOMCAT}', f'-Dcatalina.base={SERVER}',
           f'-Djava.io.tmpdir={SERVER / "temp"}', '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
           f'-Djava.util.logging.config.file={SERVER / "conf/logging.properties"}',
           '-cp', os.pathsep.join([str(TOMCAT / 'bin/bootstrap.jar'), str(TOMCAT / 'bin/tomcat-juli.jar')]),
           'org.apache.catalina.startup.Bootstrap', 'start']
    print(f'\nSpam Blocker: http://127.0.0.1:{args.port}/app/forums\nPress Ctrl+C to stop.\n', flush=True)
    process = subprocess.Popen(cmd, env=env, cwd=ROOT)
    try:
        if args.open:
            import time
            for _ in range(80):
                if process.poll() is not None:
                    break
                try:
                    with urllib.request.urlopen(f'http://127.0.0.1:{args.port}/app/forums', timeout=1) as response:
                        if response.status == 200:
                            webbrowser.open(f'http://127.0.0.1:{args.port}/app/forums')
                            break
                except (OSError, urllib.error.URLError):
                    time.sleep(.25)
        sys.exit(process.wait())
    except KeyboardInterrupt:
        print('\nStopping...', flush=True)
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError) as exc:
        print(f'Error: {exc}', file=sys.stderr)
        sys.exit(1)
