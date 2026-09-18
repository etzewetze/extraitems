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

## Entity

```yaml
type: entity
id: capybara
carrier: PIG
breed-material: SWEET_BERRIES
breed-permission: extraitems.breed.capybara
adult-models:
  - extraitems:capybara_brown_adult
  - extraitems:capybara_dark_adult
  - extraitems:capybara_patched_adult
baby-models:
  - extraitems:capybara_brown_baby
  - extraitems:capybara_dark_baby
  - extraitems:capybara_patched_baby
spawn-biomes:
  - minecraft:badlands
  - minecraft:wooded_badlands
  - minecraft:eroded_badlands
group-min: 2
group-max: 4
spawn-interval-seconds: 30
spawn-chance: 0.18
spawn-distance-min: 24
spawn-distance-max: 48
max-loaded-per-world: 36
max-near-player: 8
baby-growth-ticks: 24000
feed-growth-ticks: 2400
```

`adult-models` und `baby-models` müssen gleich viele Varianten enthalten. Beim natürlichen Spawn wird eine Variante zufällig gewählt; Nachwuchs erbt normalerweise das Fell eines Elternteils. Das aktuelle Vanilla-kompatible Entitymodul unterstützt `PIG` als unsichtbaren Träger. Die Trägerentity liefert Hitbox, Bewegung, Schwimmen, Flucht und Zucht-AI, während die angegebenen Itemmodelle sichtbar synchronisiert werden.

## Samengenerator

```yaml
type: seed_generator
id: seed_generator
item: seed_generator
use-permission: extraitems.use.seed_generator
process-seconds: 30
conversions:
  tomato:
    output: tomato_seeds
    amount: 4
  lettuce:
    output: lettuce_seeds
    amount: 3
```

Der Schlüssel unter `conversions` ist die ExtraItems-ID des Eingangs. Gemüse bleibt während der Trocknung sichtbar und wird erst bei erfolgreicher Ausgabe verbraucht. Ein fehlender oder ausgetauschter Eingang bricht den Vorgang ab.

## Rezept

```yaml
type: recipe
id: seed_generator
result: seed_generator
amount: 1
permission: extraitems.craft.seed_generator
shape:
  - 'GCG'
  - 'PBP'
  - 'S S'
keys:
  G: minecraft:glass
  C: minecraft:campfire
  P: 'minecraft:#planks'
  B: minecraft:barrel
  S: minecraft:stick
```

Zutaten beginnen mit `minecraft:` oder `extraitems:`. Seit 0.3.0 sind formlose und geformte Rezepte mit 1–9 Zutaten implementiert. Für beliebige Holzbrettarten kann `minecraft:#planks` als Zutat verwendet werden. Ein formloses Schneiderezept kann zusätzlich `tool: knife` und `tool-damage: 1` angeben; das Werkzeug bleibt im Feld und verliert pro Herstellung exakt diese Zahl an Einsätzen.

## Neue Definition registrieren

1. Eigenen Ordner unter `plugins/ExtraItems/items/<id>/` anlegen.
2. YAML-Datei mit `type` und `id` erstellen.
3. Relativen Pfad in `items.yml` unter `sources` ergänzen.
4. Server vollständig neu starten.
5. Konsole und `/ei status` prüfen.

Eine Definition kann mit `enabled: false` vorübergehend übersprungen werden. Bereits in Welt-Chunks gespeicherte IDs nicht umbenennen oder löschen.

## Nexo-Maker-CraftEngine-Export per Drag-and-drop

Ein im Nexo Maker für CraftEngine exportiertes Paket kann unverändert als ZIP geladen werden. Das Beispielitem aus der Entwicklung ist nicht Bestandteil des Plugins.

1. Die Export-ZIP nach `plugins/ExtraItems/imports/` kopieren, beispielsweise als `mein_export.zip`.
2. In `plugins/ExtraItems/items.yml` nur den relativen Pfad ergänzen:

```yaml
schema-version: 2
sources:
  # vorhandene ExtraItems-Dateien bleiben hier stehen
  - items/tomato/item.yml
  - imports/mein_export.zip
```

3. Den Server vollständig neu starten. `/reload` reicht nicht aus, weil das Ressourcenpaket neu gebaut und mit einem neuen Hash ausgeliefert wird.

Alternativ darf der Pfad auf einen entpackten Exportordner zeigen:

```yaml
  - imports/mein_entpackter_export
```

Der Importer sucht darin `configuration/*.yml` und `resourcepack/assets/`, auch wenn davor die üblichen Verzeichnisse `CraftEngine/resources/<paket>/` liegen. Jede Definition unter `items:` wird als natives ExtraItems-Item registriert. Aus `gems:green_gem` wird die interne ExtraItems-ID `gems_green_gem`; diese ID funktioniert anschließend beispielsweise mit `/ei give <Spieler> gems_green_gem` und in nativen Rezepten als `extraitems:gems_green_gem`.

Übernommen werden derzeit:

- `material`
- `data.item_name`
- `data.lore`
- `data.enchantment_glint_override`
- `model.type: minecraft:model`, `model.path` und `model.generation`
- alle Dateien unter dem exportierten `resourcepack/assets/`

`categories`, CraftEngine-Rezepte und CraftEngine-spezifische Aktionen oder Mechaniken werden nicht importiert. Dafür weiterhin eine native ExtraItems-Definitionsdatei anlegen. Die ZIP wird nicht entpackt oder verändert; das Plugin erzeugt bei jedem Start einen separaten Overlay unter `generated/imported-resourcepack/` und mischt ihn in `generated/extraitems.zip`.

Sicherheitsgrenzen: maximal 4096 Dateien, 16 MiB je Datei und 64 MiB entpackt je Quelle. Absolute Pfade, Traversal, Symlinks, doppelte ZIP-Einträge und widersprüchliche Assetpfade werden abgelehnt. Ein Import darf ein vorhandenes ExtraItems-Asset nur dann überlagern, wenn die Bytes identisch sind.

## Erweiterungspunkte

Die Loader-Pipeline verarbeitet Definitionen nach Typ. Implementiert sind `item`, `tool`, `crop`, `recipe`, `station`, `seed_generator`, `placeable_food` und `entity`. `potion`, `effect`, `gui`, `tree` und `ore` bleiben reserviert. Werden reservierte Typen verwendet, bricht der Start mit einer eindeutigen Meldung ab, statt die Datei stillschweigend falsch zu laden.

Werkzeuge, Stationen, Samengeneratoren und platzierbares Essen liegen ebenfalls getrennt im jeweiligen Itemordner. Beispiele sind `items/knife/tool.yml`, `items/cheese_station/station.yml`, `items/seed_generator/generator.yml` und `items/cheese_wheel/placeable_food.yml`. Werkzeuge können über `attack-damage` und `attack-speed` eigene Haupt-Hand-Kampfwerte erhalten.
