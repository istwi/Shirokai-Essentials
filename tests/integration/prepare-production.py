"""Prepare a real Fabric launcher from Loom caches; optionally resolve an exact loader.
Run ./gradlew build generateRemapClasspath first. No third-party JAR is redistributed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import urllib.request
import zipfile

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--loader', default='0.16.14')
parser.add_argument('--output', default='build/integration/production-launch.json')
args = parser.parse_args()
if not all(c.isdigit() or c == '.' for c in args.loader):
    raise SystemExit('Versão de loader inválida')
cache_root = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
cache = cache_root / 'caches/modules-2/files-2.1'
paths = [p for p in (root / '.gradle/loom-cache/remapClasspath.txt').read_text().split(os.pathsep)
         if all(group not in p for group in ['/net.fabricmc.fabric-api/', '/net/minecraft/', '/net.luckperms/', '/net.kyori/', '/net.impactdev.impactor.api/', '/net.fabricmc/fabric-loader/', '/net.fabricmc/sponge-mixin/', '/org.ow2.asm/'])]
patterns = ['net.fabricmc/intermediary/1.21.1/*/intermediary-1.21.1-v2.jar']
if args.loader == '0.16.14':
    patterns += ['net.fabricmc/fabric-loader/0.16.14/*/fabric-loader-0.16.14.jar',
                 'net.fabricmc/sponge-mixin/0.15.5+mixin.0.8.7/*/sponge-mixin-0.15.5+mixin.0.8.7.jar',
                 'org.ow2.asm/*/9.7.1/*/*.jar']
else:
    target = root / 'build/integration/launcher-dependencies'
    target.mkdir(parents=True, exist_ok=True)
    loader = target / f'fabric-loader-{args.loader}.jar'
    if not loader.exists():
        urllib.request.urlretrieve(f'https://maven.fabricmc.net/net/fabricmc/fabric-loader/{args.loader}/{loader.name}', loader)
    paths.append(str(loader))
    with zipfile.ZipFile(loader) as archive:
        metadata = json.loads(archive.read('fabric-installer.json'))
    for item in metadata['libraries']['common']:
        group, artifact, version = item['name'].split(':')
        jar = target / f'{artifact}-{version}.jar'
        if not jar.exists():
            urllib.request.urlretrieve(item['url'] + group.replace('.', '/') + f'/{artifact}/{version}/{jar.name}', jar)
        if hashlib.sha256(jar.read_bytes()).hexdigest() != item['sha256']:
            raise SystemExit('Checksum divergente: ' + jar.name)
        paths.append(str(jar))
for pattern in patterns:
    found = [str(p) for p in cache.glob(pattern) if not p.name.endswith('-sources.jar')]
    if not found:
        raise SystemExit('Dependência ausente no cache: ' + pattern)
    paths.extend(found)
server = cache_root / 'caches/fabric-loom/1.21.1/minecraft-extracted_server.jar'
if not server.is_file():
    raise SystemExit('Minecraft server ausente no cache')
paths.append(str(server))
fabric_api = next(cache.glob('net.fabricmc.fabric-api/fabric-api/0.116.7+1.21.1/*/fabric-api-0.116.7+1.21.1.jar'))
output = (root / args.output).resolve()
if not output.is_relative_to(root / 'build'):
    raise SystemExit('O launcher de teste deve ficar em build/')
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'loader': args.loader, 'classpath': os.pathsep.join(dict.fromkeys(paths)), 'fabricApi': str(fabric_api)}, indent=2))
print('Launcher local preparado em', output)
