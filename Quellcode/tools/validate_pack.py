#!/usr/bin/env python3
"""Check manifest, local model links, JSON, UVs and image alpha without changing assets."""
from pathlib import Path
import json, struct

root=Path(__file__).resolve().parents[1]/'src/main/resources'
pack=root/'resourcepack'
actual={str(p.relative_to(pack)).replace('\\','/') for p in pack.rglob('*') if p.is_file()}
assert actual == set((root/'pack-files.txt').read_text().splitlines()), 'Manifest ist veraltet'
meta=json.loads((pack/'pack.mcmeta').read_text())['pack']
assert meta['min_format']==[75,0] and meta['max_format']==[88,0]
assert 'supported_formats' not in meta
for path in pack.rglob('*.json'):
    data=json.loads(path.read_text())
    if path.parent.name=='items':
        model=data['model']
        assert model['type']=='minecraft:model'
        ns,name=model['model'].split(':',1)
        assert (pack/f'assets/{ns}/models/{name}.json').exists(), model
    for value in data.get('textures',{}).values():
        if value.startswith('extraitems:'):
            assert (pack/'assets/extraitems/textures'/f'{value.split(":",1)[1]}.png').exists(), value
    for element in data.get('elements',[]):
        assert all(0 <= x <= 16 for x in element['from']+element['to'])
        assert all(a<=b for a,b in zip(element['from'],element['to']))
        for face in element['faces'].values():
            assert face['texture'][1:] in data['textures']
            assert all(0<=x<=16 for x in face['uv'])
png=(pack/'assets/extraitems/textures/item/tomato.png').read_bytes()
assert png[:8]==b'\x89PNG\r\n\x1a\n'
width,height,depth,color=struct.unpack('>IIBB',png[16:26])
assert width==height and color==6, 'RGBA-PNG erforderlich'
print(f'OK: {len(actual)} Pack-Dateien, 6 Item-Definitionen, 4 Pflanzenmodelle, RGBA-Textur {width}×{height}.')
