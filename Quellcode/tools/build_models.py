#!/usr/bin/env python3
"""Build all native Minecraft item/crop/machine JSON and the embedded pack manifest."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
PACK = ROOT / 'resourcepack'
ASSETS = PACK / 'assets/extraitems'

ITEM_DISPLAY = {
    'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.85, .85, .85]},
    'ground': {'translation': [0, 3, 0], 'scale': [.5, .5, .5]},
    'fixed': {'rotation': [0, 180, 0], 'scale': [.75, .75, .75]},
    'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.45, .45, .45]},
    'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [.45, .45, .45]},
    'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [.65, .65, .65]},
    'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [.65, .65, .65]},
}

def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

def cube(start, end, texture):
    return {'from': start, 'to': end, 'faces': {
        side: {'uv': [0, 0, 16, 16], 'texture': '#' + texture}
        for side in ('north', 'south', 'east', 'west', 'up', 'down')
    }}

def item(name, model):
    write(ASSETS / 'items' / f'{name}.json', {
        'model': {'type': 'minecraft:model', 'model': model if ':' in model else 'extraitems:' + model}
    })

def generated(name, texture):
    item(name, 'item/' + name)
    write(ASSETS / 'models/item' / f'{name}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': texture}
    })

def contextual_geometry(name, elements, textures, display=None):
    """Keep the established sprite in inventories, but use real cuboids elsewhere."""
    cases = [{
        'when': 'gui',
        'model': {'type': 'minecraft:model', 'model': f'extraitems:item/{name}_icon'}
    }]
    for context in ('ground', 'fixed', 'thirdperson_righthand', 'thirdperson_lefthand',
                    'firstperson_righthand', 'firstperson_lefthand'):
        cases.append({
            'when': context,
            'model': {'type': 'minecraft:model', 'model': f'extraitems:item/{name}_3d'}
        })
    write(ASSETS / 'items' / f'{name}.json', {'model': {
        'type': 'minecraft:select',
        'property': 'minecraft:display_context',
        'cases': cases,
        'fallback': {'type': 'minecraft:model', 'model': f'extraitems:item/{name}_3d'}
    }})
    write(ASSETS / 'models/item' / f'{name}_icon.json', {
        'parent': 'minecraft:item/generated',
        'textures': {'layer0': f'extraitems:item/{name}'}
    })
    geometry(ASSETS / 'models/item' / f'{name}_3d.json', elements, textures,
             ITEM_DISPLAY if display is None else display)

def geometry(path, elements, textures, display=None):
    obj = {'parent': 'minecraft:block/block', 'ambientocclusion': False,
           'textures': textures, 'elements': elements}
    if display is not None:
        obj['display'] = display
    write(path, obj)

write(PACK / 'pack.mcmeta', {'pack': {
    'description': 'ExtraItems 0.5.1 • 3D-Küche, Käsestation, Samengenerator und Integrationen • 1.21.11–26.3',
    'min_format': [75, 0], 'max_format': [97, 1]
}})

# Distinct seed sprites.
generated('tomato_seeds', 'extraitems:item/tomato_seeds')
generated('lettuce_seeds', 'extraitems:item/lettuce_seeds')
generated('onion_seeds', 'extraitems:item/onion_seeds')

# 3D produce and kitchen items.
item('tomato', 'item/tomato')
geometry(ASSETS / 'models/item/tomato.json', [
    cube([4, 3, 4], [12, 13, 12], 'red'), cube([3, 5, 4], [13, 11, 12], 'red'),
    cube([4, 5, 3], [12, 11, 13], 'red'), cube([4, 13, 7], [12, 13.5, 9], 'leaf'),
    cube([7, 13, 4], [9, 13.5, 12], 'leaf'), cube([7.25, 13.5, 7.25], [8.75, 16, 8.75], 'stem')
], {'particle': 'minecraft:block/red_concrete', 'red': 'minecraft:block/red_concrete',
    'leaf': 'minecraft:block/green_wool', 'stem': 'minecraft:block/lime_terracotta'}, ITEM_DISPLAY)

item('lettuce', 'item/lettuce')
geometry(ASSETS / 'models/item/lettuce.json', [
    cube([3, 4, 3], [13, 11, 13], 'dark'), cube([4, 2, 4], [12, 13, 12], 'leaf'),
    cube([2, 5, 5], [14, 10, 11], 'lime'), cube([5, 5, 2], [11, 10, 14], 'lime'),
    cube([6, 4, 6], [10, 14, 10], 'heart')
], {'particle': 'minecraft:block/moss_block', 'dark': 'minecraft:block/moss_block',
    'leaf': 'minecraft:block/green_wool', 'lime': 'minecraft:block/lime_concrete',
    'heart': 'minecraft:block/lime_wool'}, ITEM_DISPLAY)

# Inventory slots retain the approved sprites. Hands, dropped items and item frames use
# true cuboid models selected through minecraft:display_context.
contextual_geometry('onion', [
    cube([4, 3, 4], [12, 12, 12], 'skin'), cube([3, 5, 5], [13, 10, 11], 'skin'),
    cube([5, 5, 3], [11, 10, 13], 'skin'), cube([7, 12, 7], [9, 15, 9], 'shoot'),
    cube([6, 14, 7], [8, 16, 8], 'shoot'), cube([8, 14, 8], [10, 16, 9], 'shoot')
], {'particle': 'minecraft:block/calcite', 'skin': 'minecraft:block/calcite',
    'shoot': 'minecraft:block/lime_terracotta'})

contextual_geometry('knife', [
    cube([6.75, 1, 6.75], [9.25, 6, 9.25], 'handle'),
    cube([7.25, 6, 7.25], [8.75, 7, 8.75], 'guard'),
    cube([6.5, 7, 7.25], [9.5, 15, 8.75], 'blade'),
    cube([7, 15, 7.25], [9, 16, 8.75], 'blade')
], {'particle': 'minecraft:block/iron_block', 'handle': 'minecraft:block/dark_oak_planks',
    'guard': 'minecraft:block/polished_andesite', 'blade': 'minecraft:block/iron_block'})

contextual_geometry('burger_bun', [
    cube([2, 1, 2], [14, 4, 14], 'crust'), cube([3, 4, 3], [13, 5, 13], 'crumb'),
    cube([2, 7, 2], [14, 10, 14], 'crust'), cube([4, 10, 4], [12, 12, 12], 'crust'),
    cube([3, 7, 3], [13, 8, 13], 'crumb')
], {'particle': 'minecraft:block/yellow_terracotta', 'crust': 'minecraft:block/orange_terracotta',
    'crumb': 'minecraft:block/yellow_terracotta'})

schlemmer = [
    cube([2, 1, 3], [14, 4, 13], 'bun'),
    cube([2.5, 4, 2.5], [13.5, 6.5, 13.5], 'patty'),
    cube([2, 6.5, 4], [14, 7.5, 12], 'lettuce'), cube([4, 6.5, 2], [12, 7.5, 14], 'lettuce'),
    cube([3, 7.5, 3], [13, 8.5, 13], 'onion'),
    cube([2, 8.5, 3], [14, 11.5, 13], 'bun'), cube([4, 11.5, 5], [12, 13, 11], 'bun')
]
burger_textures = {
    'particle': 'minecraft:block/orange_terracotta', 'bun': 'minecraft:block/orange_terracotta',
    'patty': 'minecraft:block/brown_wool', 'lettuce': 'minecraft:block/lime_concrete',
    'onion': 'minecraft:block/calcite', 'cheese': 'minecraft:block/yellow_concrete',
    'tomato': 'minecraft:block/red_concrete'
}
contextual_geometry('schlemmer_burger', schlemmer, burger_textures)

cheesy = [
    cube([2, .5, 3], [14, 3.5, 13], 'bun'),
    cube([2.5, 3.5, 2.5], [13.5, 6, 13.5], 'patty'),
    cube([2, 6, 4], [14, 7, 12], 'lettuce'), cube([4, 6, 2], [12, 7, 14], 'lettuce'),
    cube([2, 7, 2], [14, 8, 14], 'cheese'), cube([3, 8, 3], [13, 9.5, 13], 'tomato'),
    cube([2, 9.5, 3], [14, 12.5, 13], 'bun'), cube([4, 12.5, 5], [12, 14, 11], 'bun')
]
contextual_geometry('cheesy_schlemmer', cheesy, burger_textures)

item('cheese_slice', 'item/cheese_slice')
geometry(ASSETS / 'models/item/cheese_slice.json', [
    cube([2, 3, 3], [14, 6, 13], 'cheese'), cube([4, 6, 4], [14, 9, 12], 'cheese'),
    cube([7, 9, 5], [14, 12, 11], 'light'), cube([10, 12, 6], [14, 14, 10], 'light')
], {'particle': 'minecraft:block/yellow_concrete', 'cheese': 'minecraft:block/yellow_concrete',
    'light': 'minecraft:block/gold_block'}, ITEM_DISPLAY)

item('cheese_station', 'item/cheese_station')
geometry(ASSETS / 'models/item/cheese_station.json', [
    cube([0, 0, 0], [16, 3, 16], 'wood'), cube([1, 3, 1], [15, 12, 15], 'copper'),
    cube([2, 11, 2], [14, 14, 14], 'rim'), cube([3, 12, 3], [13, 13, 13], 'milk'),
    cube([3, 14, 7], [13, 15, 9], 'iron'), cube([7, 13, 3], [9, 16, 13], 'iron')
], {'particle': 'minecraft:block/barrel_side', 'wood': 'minecraft:block/dark_oak_planks',
    'copper': 'minecraft:block/exposed_copper', 'rim': 'minecraft:block/barrel_side',
    'milk': 'minecraft:block/calcite', 'iron': 'minecraft:block/iron_block'}, ITEM_DISPLAY)

item('seed_generator', 'item/seed_generator')
geometry(ASSETS / 'models/item/seed_generator.json', [
    cube([0, 0, 0], [16, 3, 16], 'wood'), cube([1, 3, 1], [15, 11, 15], 'frame'),
    cube([2, 5, 2], [14, 6, 14], 'tray'), cube([3, 6, 3], [5, 6.6, 5], 'seed'),
    cube([7, 6, 4], [9, 6.6, 6], 'seed'), cube([11, 6, 8], [13, 6.6, 10], 'seed'),
    cube([4, 6, 10], [6, 6.6, 12], 'seed'), cube([2, 11, 2], [14, 13, 14], 'glass'),
    cube([6, 13, 6], [10, 15, 10], 'vent')
], {'particle': 'minecraft:block/barrel_side', 'wood': 'minecraft:block/dark_oak_planks',
    'frame': 'minecraft:block/stripped_oak_log', 'tray': 'minecraft:block/copper_block',
    'seed': 'minecraft:block/hay_block', 'glass': 'minecraft:block/tinted_glass',
    'vent': 'minecraft:block/iron_block'}, ITEM_DISPLAY)

item('old_but_gold_book', 'minecraft:item/enchanted_book')

# Tomato crop: mature fruit uses a vanilla atlas texture for maximum robustness.
for stage, height in enumerate((4, 8, 12, 12)):
    elements = [cube([7.5, 0, 7.5], [8.5, height, 8.5], 'stem')]
    leaves = [([4, 2, 7], [8, 2.6, 9]), ([8, 3, 7], [12, 3.6, 9])]
    if stage >= 1:
        leaves += [([7, 5, 3], [9, 5.7, 8]), ([7, 7, 8], [9, 7.7, 13])]
        elements += [cube([7.7, 3, 7.7], [8.3, 8, 8.3], 'stem')]
    if stage >= 2:
        leaves += [([2.5, 9, 7], [8, 9.7, 9]), ([8, 10.5, 7], [13.5, 11.2, 9])]
    elements += [cube(a, b, 'leaf') for a, b in leaves]
    if stage >= 2:
        for x, y, z in ((4, 6, 7), (10, 8, 7), (7, 4, 10), (7, 8, 4)):
            texture = 'ripe' if stage == 3 else 'unripe'
            elements.append(cube([x, y, z], [x + 2.5, y + 2.5, z + 2.5], texture))
            elements.append(cube([x + .6, y + 2.5, z + .6], [x + 1.9, y + 2.8, z + 1.9], 'leaf'))
    name = f'tomato_stage_{stage}'
    item(name, 'block/' + name)
    geometry(ASSETS / 'models/block' / f'{name}.json', elements, {
        'particle': 'minecraft:block/green_wool', 'stem': 'minecraft:block/lime_terracotta',
        'leaf': 'minecraft:block/green_wool', 'unripe': 'minecraft:block/lime_concrete',
        'ripe': 'minecraft:block/red_concrete'
    })

# Lettuce crop: progressively denser nested leaves.
lettuce_stages = [
    [cube([6, 0, 6], [10, 2, 10], 'lime')],
    [cube([4, 0, 4], [12, 3, 12], 'leaf'), cube([6, 1, 6], [10, 5, 10], 'lime')],
    [cube([3, 0, 3], [13, 4, 13], 'dark'), cube([4, 1, 4], [12, 6, 12], 'leaf'), cube([6, 2, 6], [10, 8, 10], 'lime')],
    [cube([2, 0, 4], [14, 5, 12], 'dark'), cube([4, 0, 2], [12, 5, 14], 'dark'),
     cube([3, 1, 3], [13, 7, 13], 'leaf'), cube([5, 2, 5], [11, 9, 11], 'lime'),
     cube([6, 3, 6], [10, 10, 10], 'heart')]
]
for stage, elements in enumerate(lettuce_stages):
    name = f'lettuce_stage_{stage}'
    item(name, 'block/' + name)
    geometry(ASSETS / 'models/block' / f'{name}.json', elements, {
        'particle': 'minecraft:block/moss_block', 'dark': 'minecraft:block/moss_block',
        'leaf': 'minecraft:block/green_wool', 'lime': 'minecraft:block/lime_concrete',
        'heart': 'minecraft:block/lime_wool'
    })

# Onion crop: green shoots and a visible mature bulb.
for stage, height in enumerate((4, 7, 10, 12)):
    elements = []
    stems = 2 + stage
    for n in range(stems):
        x = 6 + (n % 3) * 1.5
        z = 6 + (n // 3) * 2
        elements.append(cube([x, 0, z], [x + .8, height - (n % 2), z + .8], 'green'))
    if stage >= 2:
        texture = 'bulb' if stage == 3 else 'young'
        elements += [cube([5, 0, 5], [11, 4, 11], texture), cube([6, 4, 6], [10, 5, 10], texture)]
    name = f'onion_stage_{stage}'
    item(name, 'block/' + name)
    geometry(ASSETS / 'models/block' / f'{name}.json', elements, {
        'particle': 'minecraft:block/lime_terracotta', 'green': 'minecraft:block/lime_terracotta',
        'young': 'minecraft:block/lime_concrete', 'bulb': 'minecraft:block/calcite'
    })

# Ten edible portions. Each stage removes one blocky radial slice from the wheel.
for bites in range(10):
    remaining = 10 - bites
    elements = []
    margins = (4, 3, 2, 2, 2, 2, 2, 2, 3, 4)
    for n in range(remaining):
        x0 = 2 + n * 1.2
        margin = margins[n]
        elements.append(cube([x0, 1, margin], [x0 + 1.2, 6, 16 - margin], 'cheese'))
        elements.append(cube([x0, 6, margin + .5], [x0 + 1.2, 7, 15.5 - margin], 'top'))
    name = f'cheese_wheel_{bites}'
    item(name, 'block/' + name)
    geometry(ASSETS / 'models/block' / f'{name}.json', elements, {
        'particle': 'minecraft:block/yellow_concrete', 'cheese': 'minecraft:block/yellow_concrete',
        'top': 'minecraft:block/gold_block'
    })
item('cheese_wheel', 'block/cheese_wheel_0')

paths = sorted(str(path.relative_to(PACK)).replace('\\', '/') for path in PACK.rglob('*') if path.is_file())
(ROOT / 'pack-files.txt').write_text('\n'.join(paths) + '\n', encoding='utf-8')
print(f'{len(paths)} Pack-Dateien; 3 Pflanzen, 2 Maschinen, 10 Käsestufen und echte 3D-Handmodelle.')
