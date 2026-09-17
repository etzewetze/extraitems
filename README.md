# ExtraItems 0.5.1

ExtraItems ist ein serverseitiges Paper-/Spigot-Plugin für eigene Vanilla-Items und Pflanzen. Spieler benötigen keine Mods, sondern nur das automatisch angeforderte Ressourcenpaket.

## Neu in 0.5.1

- Minecraft 26.3 wird über die aktuelle Paper-API unterstützt; die CI prüft dafür zusätzlich `paper-api:26.3-R0.1-SNAPSHOT` unter Java 25.
- Das Ressourcenpaket akzeptiert nun die Formate 75.0 bis 97.1 und deckt damit Minecraft 1.21.11 bis 26.3 ab.
- Die Untergrenze 1.21.11 und der Java-21-Bytecode bleiben erhalten.
- Hinweis: Paper führt den 26.3-Port derzeit noch im Alpha-Kanal. Vor dem Produktiveinsatz ist deshalb ein echter Test mit dem verwendeten Paper-26.3-Build erforderlich.

## Neu in 0.5.0

- Die Käsestation lässt den Milcheimer während der gesamten Reifezeit sichtbar im Eingang. Herausnehmen bricht den Vorgang ohne Verlust ab; erst beim erfolgreichen Abschluss entstehen Käserad und leerer Eimer.
- Neuer platzierbarer Samengenerator mit GUI und Hopper-Unterstützung: Tomate ergibt vier, Salat und Zwiebel jeweils drei Samen nach 30 Sekunden Trocknung.
- Die direkte Umwandlung von Tomate zu Samen wird beim Update automatisch aus `items.yml` entfernt. Die Starterrezepte bleiben für den Einstieg in neue Welten erhalten.
- Das Eisenmesser verursacht 9 Angriffsschaden bei 2,4 Angriffen pro Sekunde und verbraucht bei einem Treffer einen Einsatz. `Old but Gold` verhindert weiterhin jeden Verschleiß.

## Neu in 0.4.0

- Das Eisenmesser hat jetzt das gewünschte 3×3-Rezept: mittlere Reihe `Stick | Eisenbarren | ·`, darunter `· | Steinknopf | ·`; alle übrigen Felder bleiben leer.

## Neu in 0.3.2

- Zwiebel, Buns, Messer und beide Burger zeigen das vorhandene Icon im Inventar, aber echte native 3D-Kubusmodelle in Hand, Drop und Itemrahmen.
- Käsestationen speichern den Reifezustand jetzt vor der Inventaränderung und verwenden danach das Live-Inventar; Milcheimer werden zuverlässig verbraucht, Käserad und leerer Eimer ausgegeben.
- Custom-Rezepte nutzen eine robuste Material-Vorauswahl und prüfen anschließend die exakte ExtraItems-ID; dadurch funktionieren Schlemmer- und Cheesy-Schlemmer-Burger auch nach Metadaten-Updates.

## Neu in 0.3.1

- Neues Käsestationsrezept: oben mittig Fass; mittlere Reihe beliebige Holzbretter, Werkbank, beliebige Holzbretter; unten links und rechts je ein Stock.
- Moderne Käsestations-GUI mit dunklem Rahmen, markiertem Eingang, zwei Ausgängen, fünfteiliger Fortschrittsanzeige, Prozentwert und Restzeit.
- Neuer `Cheesy Schlemmer` aus Buns, Käsescheibe, Tomate, Salat und gebratenem Rindfleisch.
- Angepasste Nahrung: Tomate/Salat/Zwiebel je 1,5 Keulen, Buns 0,5 Keule und Schlemmer Burger 9 Keulen.
- Zwiebel, Buns, Messer und beide Burger verwenden eigene, zuverlässig extrudierte Pixelmodelle ohne externe Vanilla-Texturverweise.
- Der Cheesy Schlemmer füllt die vollständige Vanilla-Leiste und gibt zusätzliche Sättigung. Mehr als zehn sichtbare Hungerkeulen kann Minecraft nicht darstellen.

## Neu in 0.3.0

- Eigene Tomaten-, Salat- und Zwiebelsamen-Symbole sowie je vier 3D-Wachstumsstufen.
- Salat und Zwiebeln können auf Ackerboden gepflanzt, mit Knochenmehl beschleunigt und geerntet werden.
- Eisenmesser mit 3D-Modell: Grundhaltbarkeit 192 Schnitte. `Haltbarkeit I–III` ergänzt exakt 64 Schnitte je Stufe; Reparatur durch `Mending` bleibt möglich.
- Das eigene Amboss-Upgrade `Old but Gold` funktioniert ausschließlich auf dem ExtraItems-Messer und macht es unzerstörbar.
- Messer + Brot ergibt Burger Buns; Messer + Käserad ergibt zehn essbare Käsescheiben.
- Schlemmer-Burger aus Buns, Salat, Zwiebel und gebratenem Rindfleisch.
- Platzierbare Käsestation mit zwei Ausgängen, 60 Sekunden Reifezeit und Hopper-Automatisierung.
- Platzierbares Käserad mit zehn Portionen. Es ist nur platziert essbar und wird beim Abbauen wie Kuchen zerstört.

## Behoben seit 0.2.1

- Die reife vierte Tomatenstufe verwendet jetzt eine sichere Vanilla-Blocktextur und erscheint nicht mehr als pink-schwarzes Fehlermodell.
- Die Tomate besitzt ein echtes Quader-Modell für Inventar, Hand und gedroppte Items.
- Die Tomaten-PNG hat jetzt die mipmap-taugliche Zweierpotenzgröße 256×256 statt 1254×1254.
- Bei einer neuen Pack-Revision aktualisiert das Plugin seine mitgelieferten Standardassets automatisch. Geänderte alte Dateien werden vorher unter `resourcepack-backups/` gesichert.

## Neu in 0.2.0

- Jede Definition liegt in einer eigenen YAML-Datei und einem übersichtlichen Item-Ordner.
- `items.yml` ist nur noch der zentrale Index mit den Pfaden zu diesen Dateien.
- Alte kombinierte `items.yml`-Dateien werden beim Start automatisch gesichert und aufgeteilt.
- Definitionen melden Fehler mit dem genauen Dateipfad.
- Die Typen `potion`, `effect`, `gui`, `tree` und `ore` sind für spätere Module reserviert. Implementiert sind `item`, `tool`, `crop`, `recipe`, `station`, `seed_generator` und `placeable_food`.
- Ressourcenpaket-Modi sind jetzt eindeutig: `self-host`, `external` oder `disabled`.
- `self-host` bildet die Downloadadresse automatisch aus dem Hostnamen bzw. der IP, mit der ein Spieler beitritt.
- Administratoren können bei einer kaputten Pack-Konfiguration per Notfallzugang beitreten und `/ei status` verwenden.

## Ordnerstruktur

Nach dem ersten Start:

```text
plugins/ExtraItems/
├── config.yml
├── items.yml
├── items.legacy.yml          # nur nach Migration von 0.1.0
├── generated/
│   └── extraitems.zip
├── resourcepack/
└── items/
    ├── tomato/
    │   ├── item.yml
    │   └── crop.yml
    ├── tomato_seeds/
        ├── item.yml
        └── recipes/
            └── starter.yml
    ├── lettuce/              # item.yml + crop.yml
    ├── onion/                # item.yml + crop.yml
    ├── knife/                # tool.yml + Rezept
    ├── cheese_station/       # Item, Station und Rezept
    ├── seed_generator/       # Item, Trocknungsdefinition und Rezept
    └── cheese_wheel/         # Item und platzierbares Essen
```

`items.yml`:

```yaml
schema-version: 2
sources:
  - items/tomato/item.yml
  - items/tomato/crop.yml
  - items/tomato_seeds/item.yml
  - items/tomato_seeds/recipes/starter.yml
  - items/seed_generator/item.yml
  - items/seed_generator/generator.yml
```

Jede Quelldatei enthält mindestens `type` und `id`. IDs dürfen nur Kleinbuchstaben, Zahlen und Unterstriche enthalten. Relative Pfade dürfen den Pluginordner nicht verlassen; doppelte Dateien und doppelte Definitionen werden abgelehnt. Einzelheiten und kopierbare Beispiele stehen in [docs/DEFINITIONEN.md](docs/DEFINITIONEN.md).

## Ressourcenpaket einrichten

### Eingebautes Hosting ohne manuell eingetragene Webadresse

Standard:

```yaml
resource-pack:
  required: true
  allow-admin-bypass-on-error: true
  mode: self-host
  self-host:
    bind: '0.0.0.0'
    port: 8123
    public-host: auto
    scheme: http
    advertised-port: 8123
```

`public-host: auto` verwendet automatisch dieselbe Domain oder IP, über die sich der jeweilige Spieler mit Minecraft verbindet. `localhost` wird dadurch für entfernte Spieler nicht mehr erzeugt.

TCP-Port 8123 muss trotzdem beim Hoster, in der Firewall und gegebenenfalls im Router erreichbar sein. Ein Vanilla-Client lädt Server-Ressourcenpakete per HTTP(S)-URL; das Plugin kann die ZIP nicht als normale Minecraft-Pakete durch dieselbe Verbindung senden. Wenn nur Port 80/443 erlaubt ist, einen HTTPS-Reverse-Proxy oder externen Dateihost verwenden.

### Externes Hosting

```yaml
resource-pack:
  required: true
  mode: external
  external-url: 'https://cdn.example.org/extraitems.zip'
```

Die URL muss direkt die unveränderte Datei `plugins/ExtraItems/generated/extraitems.zip` liefern.

### Paket vorübergehend deaktivieren

```yaml
resource-pack:
  required: false
  mode: disabled
```

`disabled` zusammen mit `required: true` ist absichtlich ein Konfigurationsfehler. Ohne geladenes Pack sehen eigene Modelle falsch aus.

Bei einem Fehler dürfen Spieler mit `extraitems.admin` standardmäßig trotzdem beitreten. Sie erhalten eine deutliche Warnung und können `/ei status` ausführen. Normale Spieler bleiben im Pflichtmodus gesperrt.

## Installation und Update

1. Server vollständig stoppen.
2. `ExtraItems-0.5.1.jar` nach `plugins/` kopieren und die alte JAR entfernen.
3. Server starten.
4. Bei einem Update wird die alte kombinierte `items.yml` einmalig als `items.legacy.yml` gesichert und in Unterdateien migriert. Ein vorhandener modularer Index behält eigene Pfade und erhält automatisch neue Standardpfade; davor entsteht `items.before-bundled-update.yml`. Veraltete Standard-Packdateien werden aktualisiert; vorherige geänderte Varianten bleiben unter `resourcepack-backups/` erhalten.
5. `/ei status` prüfen.
6. Bei `self-host` die dort angezeigte Pack-URL von einem anderen Rechner öffnen; es muss direkt eine ZIP laden.
7. Mit einem Nicht-OP-Spieler Ressourcenpaket, Rechte, Pflanzen und Rezepte testen.

Vor dem Update Welten und `plugins/ExtraItems/` sichern. `/reload` und Hot-Unload werden nicht unterstützt.

## Befehle und Rechte

| Befehl/Recht | Bedeutung |
|---|---|
| `/ei status` | Initialisierung, Packmodus, SHA-1, Definitionen und persönliche Pack-URL |
| `/ei pack` | Ressourcenpaket erneut anfordern |
| `/ei give <Spieler> <ID> [Anzahl]` | eigenes Item vergeben |
| `extraitems.admin` | Administration und Notfallzugang bei Packfehler |
| `extraitems.plant.tomato` | Tomate pflanzen und mit Knochenmehl düngen |
| `extraitems.plant.lettuce` | Salat pflanzen und düngen |
| `extraitems.plant.onion` | Zwiebeln pflanzen und düngen |
| `extraitems.craft.tomato_seeds` | Tomatensamen herstellen |
| `extraitems.craft.knife` | Eisenmesser herstellen |
| `extraitems.craft.burger_bun` | Burger Buns schneiden |
| `extraitems.craft.cheese_slice` | Käserad schneiden |
| `extraitems.craft.schlemmer_burger` | Schlemmer-Burger herstellen |
| `extraitems.craft.cheesy_schlemmer` | Cheesy Schlemmer herstellen |
| `extraitems.craft.cheese_station` | Käsestation herstellen |
| `extraitems.craft.seed_generator` | Samengenerator herstellen |
| `extraitems.craft.old_but_gold` | Old-but-Gold-Buch herstellen |
| `extraitems.use.cheese_station` | Käsestation öffnen |
| `extraitems.use.seed_generator` | Samengenerator öffnen |
| `extraitems.place.cheese_wheel` | Käserad platzieren |

Essen und das Ernten reifer Pflanzen benötigen kein Craftrecht. Grundstücksschutz gilt.

## Küchenmechaniken

- Messerrezept (Werkbank): mittlere Reihe `Stock | Eisenbarren | ·`, darunter `· | Steinknopf | ·`; alle anderen Felder bleiben leer.
- Burger Buns: ein Brot und ein Messer formlos in das Craftingfeld legen.
- Käse schneiden: ein Käserad und ein Messer ergeben zehn Scheiben.
- `Old but Gold`: das hergestellte Buch im Amboss rechts neben das Messer legen; Kosten: 5 Level.
- Käsestation: Milcheimer manuell in den Eingang legen. Er bleibt dort 60 Sekunden sichtbar; Herausnehmen bricht den Vorgang ab. Erst bei freiem Käse- und Eimerausgang wird er verbraucht. Alternativ eine Kiste über einen Hopper stellen und den Hopper oben oder seitlich an die Station setzen. Ein Hopper direkt darunter zieht Käseräder und leere Eimer heraus.
- Stationsrezept: `· Fass ·` / `Bretter Werkbank Bretter` / `Stock · Stock`; jede Holzbrettart ist erlaubt.
- Samengenerator: Tomate, Salat oder Zwiebel links einlegen; nach 30 Sekunden erscheinen rechts 4/3/3 Samen. Rezept: `Glas Lagerfeuer Glas` / `Bretter Fass Bretter` / `Stock · Stock`.
- Messer im Kampf: 9 Schaden bei 2,4 Angriffen pro Sekunde; jeder erfolgreiche Nahkampftreffer verbraucht einen Einsatz.
- Cheesy Schlemmer: Buns + Käsescheibe + Tomate + Salat + gebratenes Rindfleisch.
- Das Käserad mit Rechtsklick auf einen soliden Block stellen und mit leerer Hand essen. Jede der zehn Portionen füllt eine Hungerkeule. Beim Abbauen gibt es keinen Drop.

## Versionen und Build

- API-Untergrenze: Paper/Spigot 1.21.11.
- Java-Bytecode: Java 21.
- Der CI-Build prüft 1.21.11 mit Java 21, 26.2 mit Java 25 und Paper 26.3 Alpha mit Java 25.
- Minecraft 26.3 verwendet Ressourcenpaketformat 97.1; Paper 26.3 ist zum Stand dieser Version noch Alpha.
- Zukünftige Minecraft-Versionen benötigen eine erneute API- und Ingame-Prüfung.

Lokaler Build:

```sh
cd Quellcode
mvn clean verify
```

Ergebnis: `target/ExtraItems-0.5.1.jar`.

Automatisierte Tests ersetzen keinen Test mit einem echten Minecraft-Client. Die Checkliste dafür steht in [docs/INGAME-TEST.md](docs/INGAME-TEST.md).

## Optionale Provider-Integrationen

Rezeptdateien können Item-IDs aus Nexo, ItemsAdder, Oraxen und CraftEngine über `provider:id` verwenden. Beispiele stehen in [Quellcode/docs/INTEGRATIONEN.md](Quellcode/docs/INTEGRATIONEN.md). Fehlende Provider verhindern den Start nicht; `/ei status` zeigt den Zustand.
