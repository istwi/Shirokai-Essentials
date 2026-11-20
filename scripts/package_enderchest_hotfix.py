#!/usr/bin/env python3
"""Preserve Zorykai labels and styling without replacing new application logic."""
from pathlib import Path
import json
import struct
import zipfile

def update_labels(data):
    offset, index = 10, 1
    out = bytearray(data[:10])
    changed = 0
    while index < struct.unpack_from('>H', data, 8)[0]:
        tag = data[offset]; offset += 1; out.append(tag)
        if tag == 1:
            size = struct.unpack_from('>H', data, offset)[0]; offset += 2
            value = data[offset:offset+size]; offset += size
            labels = {
                'Bem-vindo ao Shirokai, {player}!': 'Bem-vindo ao Zorykai, {player}!',
                'Servidor Shirokai — divirta-se!': 'Servidor Zorykai — divirta-se!',
                '✦ SHIROKAI ': '✦ ZORYKAI ',
                'Shirokai Essentials {version} / Minecraft 1.21.1': 'Zorykai Essentials {version} / Minecraft 1.21.1',
                'Use uma cópia dentro da pasta do mundo, fora dos dados Shirokai': 'Use uma cópia dentro da pasta do mundo, fora dos dados Zorykai',
            }
            replacement = {k.encode():v.encode() for k,v in labels.items()}.get(value, value)
            changed += replacement != value
            out.extend(struct.pack('>H', len(replacement))); out.extend(replacement)
        else:
            size = {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
            out.extend(data[offset:offset+size]); offset += size
            if tag in (5,6): index += 1
        index += 1
    assert changed > 0
    out.extend(data[offset:])
    return bytes(out)

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
        if name == 'dev/shirokai/essentials/Messages.class':
            assert current.read(name) == original.read(name), f'Message renderer changed; cannot reuse old styling: {name}'
    for entry in current.infolist():
        data = current.read(entry.filename)
        if entry.filename in visuals:
            if entry.filename == 'dev/shirokai/essentials/Messages.class': data = branded.read(entry.filename)
            else: data = update_labels(data)
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
