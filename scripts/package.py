#!/usr/bin/env python3
"""Package the locally tested artifact; does not publish or deploy anything."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
version = json.loads((root / 'build/resources/main/fabric.mod.json').read_text())['version']
jar = root / f'build/libs/shirokai-essentials-{version}.jar'
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
jar_hash = digest(jar)
with zipfile.ZipFile(jar) as archive:
    assert not any('BattleFixture' in name for name in archive.namelist()), 'Test fixture in release'
    assert not any('/economy/' in name or b'net/impactdev/' in archive.read(name)
                   for name in archive.namelist() if name.endswith('.class')), 'Economy dependency in release'

units = []
for file in sorted((root / 'build/test-results/test').glob('TEST-*.xml')):
    suite = ET.parse(file).getroot()
    assert int(suite.attrib['failures']) == int(suite.attrib['errors']) == int(suite.attrib['skipped']) == 0
    units.append({'suite': suite.attrib['name'], 'passed': int(suite.attrib['tests'])})
assert units, 'Missing unit results'

integrations = []
for name, directory, installed in [
    ('regression', 'regression-integration', f'shirokai-essentials-{version}.jar'),
    ('expanded', 'expanded-integration', 'shirokai-essentials.jar'),
    ('module-failure', 'module-failure-integration', 'shirokai-essentials.jar'),
    ('real-battle', 'cobblemon-integration', 'shirokai-essentials.jar'),
]:
    folder = root / 'build' / directory
    assert digest(folder / 'run/mods' / installed) == jar_hash, f'{name}: wrong artifact'
    result = json.loads((folder / 'results.json').read_text())
    assert result.get('version', version) == version, f'{name}: stale results'
    assert result['passed'] == len(result['checks']) > 0
    integrations.append({'suite': name, **result, 'version': version})

report = {
    'version': version, 'artifact': jar.name, 'sha256': jar_hash,
    'unitPassed': sum(s['passed'] for s in units),
    'integrationPassed': sum(s['passed'] for s in integrations),
    'unitSuites': units, 'integrationSuites': integrations,
    'limits': [
        'Cobblemon: PvP real até turno 1 com clientes de protocolo; interface gráfica e vitória/derrota não testadas.',
        'Economia removida do Essentials; teleportes e kits gratuitos. Impactor independente.',
        'Nenhuma importação executada em dados reais do usuário; migração fora da etapa solicitada.',
    ],
}
(root / 'docs/RESULTADOS-INTEGRACAO.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
destination = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'build/distribution'
destination.mkdir(parents=True, exist_ok=True)
shutil.copy2(jar, destination / jar.name)
shutil.copy2(root / 'docs/TESTES.md', destination / f'shirokai-essentials-{version}-testes.md')

def files_in(directory):
    return (p for p in (root / directory).rglob('*') if p.is_file() and 'node_modules' not in p.parts and '__pycache__' not in p.parts)

with zipfile.ZipFile(destination / f'shirokai-essentials-{version}-fontes.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for file in sorted(root.iterdir()):
        if file.is_file(): archive.write(file, Path(root.name) / file.name)
    for directory in ['src','tests','docs','examples','gradle','scripts']:
        for file in sorted(files_in(directory)):
            archive.write(file, Path(root.name) / file.relative_to(root))
with zipfile.ZipFile(destination / f'shirokai-essentials-{version}-pacote.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.write(jar, jar.name)
    for name in ['README.md','LICENSE']:
        archive.write(root / name, name)
    for directory in ['docs','examples']:
        for file in sorted(files_in(directory)):
            archive.write(file, file.relative_to(root))
artifacts = sorted(destination.glob(f'shirokai-essentials-{version}*'))
checksums = destination / f'shirokai-essentials-{version}-SHA256SUMS.txt'
checksums.write_text(''.join(f'{digest(file)}  {file.name}\n' for file in artifacts if file != checksums))
print(f'{version}: {report["unitPassed"]} unit + {report["integrationPassed"]} integration checks; artifacts in {destination}')
