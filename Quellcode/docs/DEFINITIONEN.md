# Modulare Definitionen

`items.yml` enthält ausschließlich die Liste der zu ladenden YAML-Dateien. Dadurch kann eine fehlerhafte Definition anhand ihres Dateipfads gefunden, einzeln deaktiviert und separat versioniert werden.

## Item

```yaml
type: item
id: tomato
enabled: true
material: APPLE
name: '&cTomate'
model: extraitems:tomato
lore:
  - '&7Frisch vom Strauch.'
food:
  nutrition: 4
  saturation: 0.3
```

`food` ist optional. Das Basismaterial muss dann in Minecraft essbar sein. Essen benötigt keine zusätzliche Permission.

## Pflanze

```yaml
type: crop
id: tomato
seed: tomato_seeds
produce: tomato
plant-permission: extraitems.plant.tomato
models:
  - extraitems:tomato_stage_0
  - extraitems:tomato_stage_1
seconds-per-stage: 180
regrow-stage: 0
minimum-light: 9
require-hydrated-farmland: true
bonemeal: true
harvest-min: 1
harvest-max: 3
```

Itemdefinitionen werden unabhängig von der Reihenfolge in `items.yml` zuerst geladen. Danach folgen Pflanzen und Rezepte.

## Rezept

```yaml
type: recipe
id: tomato_to_seeds
result: tomato_seeds
amount: 4
permission: extraitems.craft.tomato_seeds
ingredients:
  - extraitems:tomato
```

Zutaten beginnen mit `minecraft:` oder `extraitems:`. Seit 0.3.0 sind formlose und geformte Rezepte mit 1–9 Zutaten implementiert. Für beliebige Holzbrettarten kann `minecraft:#planks` als Zutat verwendet werden. Ein formloses Schneiderezept kann zusätzlich `tool: knife` und `tool-damage: 1` angeben; das Werkzeug bleibt im Feld und verliert pro Herstellung exakt diese Zahl an Einsätzen.

## Neue Definition registrieren

1. Eigenen Ordner unter `plugins/ExtraItems/items/<id>/` anlegen.
2. YAML-Datei mit `type` und `id` erstellen.
3. Relativen Pfad in `items.yml` unter `sources` ergänzen.
4. Server vollständig neu starten.
5. Konsole und `/ei status` prüfen.

Eine Definition kann mit `enabled: false` vorübergehend übersprungen werden. Bereits in Welt-Chunks gespeicherte IDs nicht umbenennen oder löschen.

## Erweiterungspunkte

Die Loader-Pipeline verarbeitet Definitionen nach Typ. Implementiert sind `item`, `tool`, `crop`, `recipe`, `station` und `placeable_food`. `potion`, `effect`, `gui`, `tree` und `ore` bleiben reserviert. Werden reservierte Typen verwendet, bricht der Start mit einer eindeutigen Meldung ab, statt die Datei stillschweigend falsch zu laden.

Werkzeuge, Stationen und platzierbares Essen liegen ebenfalls getrennt im jeweiligen Itemordner. Beispiele sind `items/knife/tool.yml`, `items/cheese_station/station.yml` und `items/cheese_wheel/placeable_food.yml`.
