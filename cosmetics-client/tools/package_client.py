"""Package the client artifact exercised by the real local graphical client."""
import json,hashlib,shutil,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
version=json.loads((root/'build/resources/main/fabric.mod.json').read_text())['version']
jar=root/f'build/libs/zorykai-cosmetics-client-{version}.jar'
runtime=Path('/tmp/cosmetics-3d-visual-client')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(jar)==sha(runtime/'mods'/jar.name)
checks=json.loads((runtime/'visual-results.json').read_text());assert checks and all(c['passed'] for c in checks)
assert any(c['name']=='alive through walking recording' for c in checks)
units=[]
for p in (root/'build/test-results/test').glob('TEST-*.xml'):
 s=ET.parse(p).getroot();assert int(s.get('failures'))==int(s.get('errors'))==int(s.get('skipped'))==0;units.append({'name':s.get('name'),'passed':int(s.get('tests'))})
assert sum(u['passed'] for u in units)==5
with zipfile.ZipFile(jar) as z:
 assert z.testzip() is None
 assert not any('/test/' in n for n in z.namelist())
model=json.loads((root/'models/angel.bbmodel').read_text())
assert len(model['outliner'])==12 and all(g['name'].startswith('primary_') for g in model['outliner'])
assert len(set(e['uuid'] for e in model['elements']))==len(model['elements'])
release=root/f'releases/{version}';release.mkdir(parents=True,exist_ok=True)
shutil.copy2(jar,release/jar.name);shutil.copy2(root/'models/angel.bbmodel',release/'angel.bbmodel')
evidence=root/'docs/v0.1.1';evidence.mkdir(parents=True,exist_ok=True)
for tick,name in [(100,'angel'),(460,'walking-1'),(477,'walking-2'),(495,'walking-3'),(512,'walking-4'),(600,'overhead')]:shutil.copy2(runtime/f'screenshots/wings-{tick}.png',evidence/f'{name}.png')
shutil.copy2('/tmp/wings-walk111.mp4',evidence/'walking.mp4')
report={'version':version,'clientSha256':sha(jar),'graphicalChecks':checks,'unitSuites':units,'geometry':{'wingGroups':12,'cuboids':len(model['elements']),'extraLayers':0},'runtime':'Minecraft 1.21.1 / Fabric 0.17.2 / Java 21 / software OpenGL; actual menu clicks, movement, screenshots and X11 recording','limits':['Visual run did not include shaders or user modpack.','No server code changed; uses Essentials 0.6.2-guard.1.']}
(evidence/'results.json').write_text(json.dumps(report,indent=2)+'\n')
(release/'SHA256SUMS').write_text(sha(jar)+'  '+jar.name+'\n'+sha(release/'angel.bbmodel')+'  angel.bbmodel\n')
print(version,sha(jar),'checks',len(checks),'unit tests',sum(u['passed'] for u in units))
