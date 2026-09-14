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

Zutaten beginnen mit `minecraft:` oder `extraitems:`. In 0.2.1 sind formlose Rezepte mit 1–9 Zutaten implementiert.

## Neue Definition registrieren

1. Eigenen Ordner unter `plugins/ExtraItems/items/<id>/` anlegen.
2. YAML-Datei mit `type` und `id` erstellen.
3. Relativen Pfad in `items.yml` unter `sources` ergänzen.
4. Server vollständig neu starten.
5. Konsole und `/ei status` prüfen.

Eine Definition kann mit `enabled: false` vorübergehend übersprungen werden. Bereits in Welt-Chunks gespeicherte IDs nicht umbenennen oder löschen.

## Erweiterungspunkte

Die Loader-Pipeline verarbeitet Definitionen nach Typ. `tool`, `potion`, `effect`, `gui`, `tree` und `ore` sind reservierte Namen für spätere Module. Werden sie in 0.2.1 verwendet, bricht der Start mit einer eindeutigen Meldung ab, statt die Datei stillschweigend falsch zu laden.
