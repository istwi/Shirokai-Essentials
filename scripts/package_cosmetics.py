#!/usr/bin/env python3
"""Package only the artifact actually exercised by both isolated server suites."""
import argparse,hashlib,json,shutil,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('vanilla',type=Path);p.add_argument('raids',type=Path);args=p.parse_args()
root=Path(__file__).resolve().parents[1]
version=json.loads((root/'build/resources/main/fabric.mod.json').read_text())['version']
assert version=='0.6.6-continuous-trails.1'
release=root/f'releases/v{version}';release.mkdir(exist_ok=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
visual=root/f'build/libs/shirokai-essentials-{version}-zorykai-visual.jar'
plain=root/f'build/libs/shirokai-essentials-{version}.jar'
for path in [plain,visual]:
 with zipfile.ZipFile(path) as z:
  assert z.testzip() is None
  assert json.loads(z.read('fabric.mod.json'))['version']==version
  assert not any('dev/shirokai/test/' in n for n in z.namelist())
 shutil.copy2(path,release/path.name)
units=[]
for file in sorted((root/'build/test-results/test').glob('TEST-*.xml')):
 suite=ET.parse(file).getroot();assert all(int(suite.attrib[k])==0 for k in ['failures','errors','skipped']);units.append({'suite':suite.get('name'),'passed':int(suite.get('tests'))})
assert units
native=[]
for label,runtime in [('without-cobblemon',args.vanilla),('with-cobblemon-raids-guard',args.raids)]:
 assert sha(runtime/'mods/essentials.jar')==sha(visual),f'{label}: stale artifact'
 checks=json.loads((runtime/'enderchest-results.json').read_text());assert checks and all(c.get('passed') is True for c in checks),label
 mods={}
 for jar in (runtime/'mods').glob('*.jar'):
  with zipfile.ZipFile(jar) as z:
   m=json.loads(z.read('fabric.mod.json'));mods[m['id']]=m['version']
 native.append({'suite':label,'artifactSha256':sha(visual),'passed':len(checks),'mods':mods,'checks':checks})
report={'version':version,'artifacts':{file.name:sha(file) for file in [plain,visual]},'unitPassed':sum(s['passed'] for s in units),'nativePassed':sum(s['passed'] for s in native),'unitSuites':units,'nativeSuites':native,'runtime':{'minecraft':'1.21.1','fabricLoader':'0.17.2','java':'21.0.9'},'limits':['Jogadores simulados; nenhum cliente gráfico.','Raids: classe real transformada e callback injetado exercitado; batalha/vitória completa não executada.','Vitória em batalha Pokémon comum não integrada.','Teste não constitui medição de desempenho em servidor cheio.','Categorias removidas permanecem nos perfis antigos, mas ficam inativas.']}
(root/'docs/cosmetics/results.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
(release/'SHA256SUMS').write_text(''.join(f'{sha(file)}  {file.name}\n' for file in [plain,visual]))
print(json.dumps({k:report[k] for k in ['version','unitPassed','nativePassed','artifacts']},indent=2))
