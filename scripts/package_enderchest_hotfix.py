#!/usr/bin/env python3
"""Preserve the published Zorykai visuals, refusing to reuse any class changed by this hotfix."""
from pathlib import Path
import json
import zipfile

root = Path(__file__).resolve().parents[1]
version = json.loads((root / 'build/resources/main/fabric.mod.json').read_text())['version']
base = root / 'releases/v0.3.2-guard.1'
source = root / f'build/libs/shirokai-essentials-{version}.jar'
target = source.with_name(f'shirokai-essentials-{version}-zorykai-visual.jar')
with zipfile.ZipFile(base / 'shirokai-essentials-0.3.2-guard.1.jar') as original, \
     zipfile.ZipFile(base / 'shirokai-essentials-0.3.2-guard.1-zorykai-visual.jar') as branded, \
     zipfile.ZipFile(source) as current, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as output:
    visuals = {name for name in original.namelist() if name.endswith('.class') and original.read(name) != branded.read(name)}
    assert len(visuals) == 5
    for name in visuals:
        assert current.read(name) == original.read(name), f'Class changed; cannot reuse old visuals: {name}'
    for entry in current.infolist():
        data = current.read(entry.filename)
        if entry.filename in visuals:
            data = branded.read(entry.filename)
        elif entry.filename == 'fabric.mod.json':
            metadata = json.loads(data)
            old_metadata = json.loads(branded.read(entry.filename))
            metadata['name'] = old_metadata['name']
            metadata['description'] = old_metadata['description']
            data = (json.dumps(metadata, ensure_ascii=False, indent=2) + '\n').encode()
        output.writestr(entry, data)
with zipfile.ZipFile(source) as original, zipfile.ZipFile(target) as branded:
    assert original.namelist() == branded.namelist()
    assert {n for n in original.namelist() if original.read(n) != branded.read(n)} == visuals | {'fabric.mod.json'}
    assert original.read('dev/shirokai/essentials/permissions/PermissionService.class') == branded.read('dev/shirokai/essentials/permissions/PermissionService.class')
    assert branded.testzip() is None
print(target)
