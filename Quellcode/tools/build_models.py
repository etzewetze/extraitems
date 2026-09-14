#!/usr/bin/env python3
"""Build native Minecraft JSON geometry and the embedded resource manifest.
No image editing or third-party Python libraries. Existing PNG stays unchanged.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
PACK = ROOT / 'resourcepack'
ASSETS = PACK / 'assets/extraitems'

def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

def cube(start, end, texture):
    return {'from': start, 'to': end, 'faces': {side: {'uv': [0,0,16,16], 'texture': '#'+texture} for side in ('north','south','east','west','up','down')}}

def item(name, model):
    write(ASSETS / 'items' / f'{name}.json', {'model': {'type': 'minecraft:model', 'model': 'extraitems:'+model}})

write(PACK/'pack.mcmeta', {'pack': {'description': 'ExtraItems • Tomaten & Pflanzen • 1.21.11–26.2', 'min_format': [75,0], 'max_format': [88,0]}})
item('tomato', 'item/tomato')
item('tomato_seeds', 'item/tomato_seeds')
write(ASSETS/'models/item/tomato.json', {'parent':'minecraft:item/generated','textures':{'layer0':'extraitems:item/tomato'}})
write(ASSETS/'models/item/tomato_seeds.json', {'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/wheat_seeds'}})

for stage, height in enumerate((4,8,12,12)):
    elements = [cube([7.5,0,7.5],[8.5,height,8.5],'stem')]
    leaves = [([4,2,7],[8,2.6,9]), ([8,3,7],[12,3.6,9])]
    if stage >= 1:
        leaves += [([7,5,3],[9,5.7,8]), ([7,7,8],[9,7.7,13])]
        elements += [cube([7.7,3,7.7],[8.3,8,8.3],'stem')]
    if stage >= 2:
        leaves += [([2.5,9,7],[8,9.7,9]), ([8,10.5,7],[13.5,11.2,9])]
    elements += [cube(a,b,'leaf') for a,b in leaves]
    if stage >= 2:
        for x,y,z in ((4,6,7),(10,8,7),(7,4,10),(7,8,4)):
            texture = 'tomato' if stage == 3 else 'unripe'
            elements.append(cube([x,y,z],[x+2.5,y+2.5,z+2.5],texture))
            elements.append(cube([x+.6,y+2.5,z+.6],[x+1.9,y+2.8,z+1.9],'leaf'))
    name = f'tomato_stage_{stage}'
    item(name, 'block/'+name)
    write(ASSETS/'models/block'/f'{name}.json', {
        'ambientocclusion': False,
        'textures': {'particle':'minecraft:block/green_wool','stem':'minecraft:block/lime_terracotta',
                     'leaf':'minecraft:block/green_wool','unripe':'minecraft:block/lime_concrete','tomato':'extraitems:item/tomato'},
        'elements': elements
    })

paths = sorted(str(path.relative_to(PACK)).replace('\\','/') for path in PACK.rglob('*') if path.is_file())
(ROOT/'pack-files.txt').write_text('\n'.join(paths)+'\n', encoding='utf-8')
print(f'{len(paths)} Pack-Dateien; vier native 3D-Wachstumsmodelle; PNG unverändert.')
