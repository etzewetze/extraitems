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

def referenced_models(model):
    kind=model.get('type')
    if kind=='minecraft:model':
        yield model['model']
    elif kind=='minecraft:select':
        assert model.get('property')=='minecraft:display_context'
        yield from referenced_models(model['fallback'])
        for case in model.get('cases',[]):
            assert case.get('when')
            yield from referenced_models(case['model'])
    else:
        raise AssertionError(f'Unbekannter Item-Modelltyp: {kind}')

for path in pack.rglob('*.json'):
    data=json.loads(path.read_text())
    if path.parent.name=='items':
        model=data['model']
        for reference in referenced_models(model):
            ns,name=reference.split(':',1)
            if ns != 'minecraft':
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
for png_path in pack.rglob('*.png'):
    png=png_path.read_bytes()
    assert png[:8]==b'\x89PNG\r\n\x1a\n', png_path
    width,height,depth,color=struct.unpack('>IIBB',png[16:26])
    assert width==height and color==6, f'RGBA-PNG erforderlich: {png_path}'
    assert 16 <= width <= 4096 and width & (width-1) == 0, f'Zweierpotenz erforderlich: {png_path}'
for name in ('tomato','lettuce','cheese_slice','cheese_station'):
    item_model=json.loads((pack/f'assets/extraitems/models/item/{name}.json').read_text())
    assert item_model.get('elements') and 'ground' in item_model.get('display',{}), f'3D-Modell fehlt: {name}'
for name in ('onion','knife','burger_bun','schlemmer_burger','cheesy_schlemmer'):
    selector=json.loads((pack/f'assets/extraitems/items/{name}.json').read_text())['model']
    assert selector.get('property') == 'minecraft:display_context', f'Kontextauswahl fehlt: {name}'
    icon=json.loads((pack/f'assets/extraitems/models/item/{name}_icon.json').read_text())
    assert icon.get('parent') == 'minecraft:item/generated', f'GUI-Icon fehlt: {name}'
    assert icon.get('textures',{}).get('layer0') == f'extraitems:item/{name}'
    hand=json.loads((pack/f'assets/extraitems/models/item/{name}_3d.json').read_text())
    assert hand.get('elements') and 'firstperson_righthand' in hand.get('display',{}), f'3D-Handmodell fehlt: {name}'
for crop in ('tomato','lettuce','onion'):
    for stage in range(4):
        assert (pack/f'assets/extraitems/models/block/{crop}_stage_{stage}.json').exists()
for bites in range(10):
    assert (pack/f'assets/extraitems/models/block/cheese_wheel_{bites}.json').exists()
ripe_model=json.loads((pack/'assets/extraitems/models/block/tomato_stage_3.json').read_text())
assert ripe_model['textures'].get('ripe') == 'minecraft:block/red_concrete', 'Reife Textur fehlt'
print(f'OK: {len(actual)} Pack-Dateien, 3 Saat-Sprites, 3 Pflanzen mit je 4 Stufen, 10 Käsestufen und 5 echte 3D-Handmodelle.')
