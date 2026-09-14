# ExtraItems 0.2.1

ExtraItems ist ein serverseitiges Paper-/Spigot-Plugin für eigene Vanilla-Items und Pflanzen. Spieler benötigen keine Mods, sondern nur das automatisch angeforderte Ressourcenpaket.

## Behoben in 0.2.1

- Die reife vierte Tomatenstufe verwendet jetzt eine sichere Vanilla-Blocktextur und erscheint nicht mehr als pink-schwarzes Fehlermodell.
- Die Tomate besitzt ein echtes Quader-Modell für Inventar, Hand und gedroppte Items.
- Die Tomaten-PNG hat jetzt die mipmap-taugliche Zweierpotenzgröße 256×256 statt 1254×1254.
- Bei einer neuen Pack-Revision aktualisiert das Plugin seine mitgelieferten Standardassets automatisch. Geänderte alte Dateien werden vorher unter `resourcepack-backups/` gesichert.

## Neu in 0.2.0

- Jede Definition liegt in einer eigenen YAML-Datei und einem übersichtlichen Item-Ordner.
- `items.yml` ist nur noch der zentrale Index mit den Pfaden zu diesen Dateien.
- Alte kombinierte `items.yml`-Dateien werden beim Start automatisch gesichert und aufgeteilt.
- Definitionen melden Fehler mit dem genauen Dateipfad.
- Die Typen `tool`, `potion`, `effect`, `gui`, `tree` und `ore` sind für spätere Module reserviert. 0.2.1 implementiert weiterhin `item`, `crop` und `recipe`.
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
    └── tomato_seeds/
        ├── item.yml
        └── recipes/
            ├── starter.yml
            └── from_tomato.yml
```

`items.yml`:

```yaml
schema-version: 2
sources:
  - items/tomato/item.yml
  - items/tomato/crop.yml
  - items/tomato_seeds/item.yml
  - items/tomato_seeds/recipes/starter.yml
  - items/tomato_seeds/recipes/from_tomato.yml
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
2. `ExtraItems-0.2.1.jar` nach `plugins/` kopieren und die alte JAR entfernen.
3. Server starten.
4. Bei einem Update wird die alte kombinierte `items.yml` einmalig als `items.legacy.yml` gesichert und in Unterdateien migriert. Veraltete Standard-Packdateien werden aktualisiert; vorherige geänderte Varianten bleiben unter `resourcepack-backups/` erhalten.
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
| `extraitems.craft.tomato_seeds` | Tomatensamen herstellen |

Tomaten essen und reife Pflanzen ernten benötigen weiterhin kein Anbau- oder Craftrecht. Grundstücksschutz gilt.

## Versionen und Build

- API-Untergrenze: Paper/Spigot 1.21.11.
- Java-Bytecode: Java 21.
- Der CI-Build prüft 1.21.11 mit Java 21 und 26.2 mit Java 25.
- Zukünftige Minecraft-Versionen benötigen eine erneute API- und Ingame-Prüfung.

Lokaler Build:

```sh
cd Quellcode
mvn clean verify
```

Ergebnis: `target/ExtraItems-0.2.1.jar`.

Automatisierte Tests ersetzen keinen Test mit einem echten Minecraft-Client. Die Checkliste dafür steht in [docs/INGAME-TEST.md](docs/INGAME-TEST.md).
