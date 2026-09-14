# ExtraItems 0.1.0

Ein serverseitiges Plugin für eigene Vanilla-Items und Pflanzen. Diese erste Version liefert eine vollständige Tomatenpflanze mit Ressourcenpaket, Pflichtdownload, Crafting-Rechten und Speicherung in der Welt. Spieler benötigen Minecraft **Java Edition** und das Ressourcenpaket, keine Mods.

## Enthalten

- Tomaten als essbares Item; Tomatensamen als eigenes Item.
- Vier native 3D-Wachstumsmodelle: Keimling, junge Pflanze, unreife und reife Tomatenpflanze.
- Pflanzen auf Ackerboden, Licht-/Bewässerungsprüfung, Knochenmehl, Ernten und Nachwachsen.
- Getrennte Berechtigungen für Pflanzen und Craften. Essen und Ernten benötigen kein Anbaurecht; Grundstücksschutz gilt weiterhin.
- Pflicht-Ressourcenpaket mit eingebautem HTTP-Downloadserver, automatisch berechnetem SHA-1 und Ladebestätigung je Anfrage.
- Timeout und Kick bei Ablehnung, Fehlern oder verworfenem Paket. Während des Ladens sind normale Bewegung und Spielaktionen gesperrt.
- Konfigurierbare Items, Pflanzen und formlose Rezepte; deutsches Feedback.
- Speicherung je Chunk, Wiederherstellung der Darstellungs- und Interaktions-Entities, begrenzte Pflanzenzahl.

Eigene Erzblöcke, Erz-Weltgenerierung, Baumgeneratoren, Möbel, GUIs und ein Importer für Nexo/Oraxen/ItemsAdder sind **nicht Teil von 0.1.0**. Die erste Version ist auf den gewünschten Tomaten-Anfang ausgelegt; die getrennten Klassen für Items, Rezepte, Pflanzen und Paketauslieferung bilden die Grundlage für weitere Module.

## Versionen und Java

| Ziel | Server-Java | Stand |
|---|---|---|
| Paper / Spigot 1.21.11 | Java 21 | Basis-API des JARs |
| Paper / Spigot 26.1.x | Java 25 bei Paper | Gemeinsame API; keine eigenen Internals |
| Paper / Spigot 26.2 | Java 25 bei Paper | Zusätzliches Build-Ziel zur API-Prüfung |
| 1.20 bis 1.21.10 | — | Nicht unterstützt; `api-version: 1.21.11` |
| Spätere Versionen | Nach Serveranforderung | Erst nach erneuter API-, Pack- und Ingame-Prüfung zusagen |

Das JAR verwendet Java-21-Bytecode (`--release 21`) und ausschließlich öffentliche Bukkit/Spigot-APIs. Java-21-Bytecode kann unter Java 25 laufen; damit wird aber die Java-Anforderung des Servers nicht auf 21 herabgesetzt. Die niedrigste konkrete Versionsangabe aus dem Auftrag, 1.21.11, wurde als Untergrenze verwendet.

Das Pack deklariert Formate **75.0 bis 88.0** für 1.21.11 bis 26.2. Der Server und seine Clients sollten dieselbe Minecraft-Version verwenden. ViaVersion/ViaBackwards, Geyser/Bedrock, Folia und modifizierte Clients werden nicht zugesagt. `VALIDIERUNG.md` trennt ausgeführte Prüfungen von noch offenen Ingame-Tests.

## Installieren

1. Den Server vollständig stoppen. `ExtraItems-0.1.0.jar` nach `plugins/` kopieren.
2. Server starten und wieder stoppen. Das Plugin erzeugt `plugins/ExtraItems/config.yml`, `items.yml`, den Ordner `resourcepack/` und `generated/extraitems.zip`.
3. In `plugins/ExtraItems/config.yml` die **von den Spielern erreichbare** Downloadadresse eintragen:

   ```yaml
   resource-pack:
     public-url: 'http://DEINE-SERVER-IP:8123/extraitems.zip'
     prompt: 'Dieser Server benötigt ExtraItems. Bitte Ressourcenpaket laden.'
     timeout-seconds: 120
     http:
       enabled: true
       bind: '0.0.0.0'
       port: 8123
   ```

4. TCP-Port **8123** beim Hoster bzw. in Firewall und gegebenenfalls Router freigeben und zum Minecraft-Server weiterleiten. In Docker zusätzlich den Port veröffentlichen. `0.0.0.0` ist nur die Bind-Adresse; sie gehört **nicht** in `public-url`. Auch `localhost` funktioniert für entfernte Spieler nicht. Eine Minecraft-SRV-Adresse ersetzt diese HTTP-Konfiguration nicht.
5. Server neu starten. Die URL im Browser eines anderen Rechners öffnen: Es muss direkt eine ZIP-Datei heruntergeladen werden. In dieser ZIP liegt `pack.mcmeta` direkt im Wurzelverzeichnis.
6. In der Serverliste unter „Bearbeiten“ die Server-Ressourcenpakete auf „Abfragen“ oder „Aktiviert“ setzen. Beitreten und bestätigen. `/ei status` zeigt den Zustand; `/ei pack` fordert das Pack erneut an.

Solange `public-url` leer ist oder die Initialisierung fehlschlägt, werden Spieler nicht freigeschaltet. Die Konsole bleibt für die Einrichtung verfügbar. Das ist beabsichtigt, weil das Paket verpflichtend ist. Es gibt keinen Permission-Bypass für das Pack.

### Hoster ohne zusätzlichen Port / HTTPS

`http.enabled: false` setzen. Die **exakte** Datei `plugins/ExtraItems/generated/extraitems.zip` auf einen eigenen HTTPS-Webserver oder statischen Dateihost hochladen und dessen direkte Download-URL als `public-url` eintragen. Keine Loginseite, Vorschauseite oder zeitlich befristete Downloadadresse verwenden. Die ZIP nicht nachträglich anders komprimieren: Der automatisch gesendete Hash bezieht sich auf die vom Plugin erzeugten Bytes. Nach jeder Änderung das neu erzeugte Pack erneut hochladen. Alternativ kann ein vorhandener HTTPS-Reverse-Proxy auf den eingebauten Downloadserver zeigen.

ExtraItems fügt sein Pack zur Client-Paketliste hinzu. Ein weiteres Serverpack darf ExtraItems-Assets nicht überschreiben. Gleichzeitiger Einsatz anderer Pflichtpack-Plugins muss gemeinsam geprüft werden.

## Rechte an Gruppen vergeben

ExtraItems prüft Bukkit-Permissions. Die Gruppen verwaltet dein Rechteplugin, zum Beispiel LuckPerms; ExtraItems legt keine Benutzergruppen an.

| Permission | Erlaubt | Standard |
|---|---|---|
| `extraitems.plant.tomato` | Tomaten pflanzen und mit Knochenmehl düngen | OP |
| `extraitems.craft.tomato_seeds` | Beide Samenrezepte benutzen | OP |
| `extraitems.admin` | Items geben und Status abfragen | OP |
| Keine | Tomaten essen, reife Pflanzen ernten, eigene Packanfrage wiederholen | Alle Spieler mit geladenem Pack |

Beispiel für LuckPerms, in der Serverkonsole ohne führenden Schrägstrich:

```text
lp creategroup farmer
lp group farmer permission set extraitems.plant.tomato true
lp group farmer permission set extraitems.craft.tomato_seeds true
lp user SPIELERNAME parent add farmer
```

Zum Prüfen einen Spieler ohne OP und ohne übergeordnete `*`-Rechte verwenden. Pflanzrecht und Craftrecht sind unabhängig: Eine Gruppe kann Samen herstellen dürfen, ohne sie pflanzen zu dürfen. Für weitere konfigurierte Pflanzen/Rezepte stehen die Nodes direkt in `items.yml`; sie müssen nicht zusätzlich in `plugin.yml` stehen.

## Tomaten spielen

Als Administrator:

```text
/ei give SPIELERNAME tomato_seeds 16
/ei give SPIELERNAME tomato 16
```

Als Spieler mit Craftrecht:

| Zutaten, beliebige Anordnung | Ergebnis |
|---|---|
| 1 Weizensamen + 1 Rote Bete | 2 Tomatensamen |
| 1 ExtraItems-Tomate | 4 Tomatensamen |

Mit Tomatensamen in der Hand **oben auf Ackerboden rechtsklicken**. Es muss Luft über dem Ackerboden sein. Standardmäßig wächst die Pflanze bei Lichtlevel 9 oder höher und feuchtem Ackerboden alle 180 Sekunden um eine Stufe. Nach etwa 9 Minuten aktiver Wachstumszeit ist sie reif. Ein Knochenmehl erhöht die Stufe um eins; dafür gilt das Pflanzrecht. Pflanzen wachsen nur in geladenen Chunks, solange der Server läuft, und unter passenden Bedingungen. Bei Serverlag entsprechen 20 Ticks mehr als einer realen Sekunde.

Rechtsklick auf die reife Pflanze gibt **1–3 Tomaten** und setzt sie auf Stufe 1 zurück. Linksklick entfernt sie: im Survival gibt es einen Samen zurück, bei reifen Pflanzen zusätzlich Tomaten. Creative-Abbau lässt keine Items fallen. Zum Entfernen des Ackerbodens zuerst die Pflanze entfernen. Pflanzen und ihr Ackerboden werden vor Wasser, Kolben, Explosionen und Zertrampeln geschützt; automatische Ernte per Wasser ist in 0.1.0 nicht vorgesehen.

Tomaten essen sich wie Vanilla-Nahrung bei Hunger; sie geben 4 Nahrungspunkte (= 2 Hungerkeulen) und einen Sättigungsmodifikator von 0,3. Es gibt dafür kein Anbau- oder Craftrecht.

## Crafting und Schutzgebiete

Die Rezeptvorschau **und** der tatsächliche Craft-Vorgang prüfen Rechte. Das gilt auch bei Shift-Klick und Hotbar-Klick. Automatische Crafter haben keine Spielergruppe und dürfen diese Rezepte nicht ausführen. ExtraItems-Items können nicht als ihre Vanilla-Grundmaterialien in normalen Werkbankrezepten verwendet werden.

Custom-Rezeptzutaten verwenden exakte Item-Metadaten. Eine am Amboss umbenannte Tomate ist weiterhin essbar, passt aber nicht mehr in das exakte Samenrezept. Standard-Tomaten funktionieren. Nur PDC-Markierungen kennzeichnen echte ExtraItems-Items; ein umbenannter normaler Apfel wird keine Tomate.

Pflanzen sendet ein Bukkit-`BlockPlaceEvent`; Ernten, Düngen und Abbau senden ein Bukkit-`BlockBreakEvent`. Dabei wird der unsichtbare `STRUCTURE_VOID`-Träger als Block verwendet. Schutzplugins können diese Events abbrechen. Das ist eine generische Integration, keine getestete WorldGuard-/GriefPrevention-Spezialintegration. Ernten braucht daher in einem Schutzgebiet die dort geltende **Abbauberechtigung**. Plugins, die nur natürliche Ereignisse unterstützen oder beim Break-Event schon externe Nebenwirkungen ausführen, müssen im Testserver geprüft werden. Der Vanilla-Spawnschutz wird ebenfalls berücksichtigt.

## Weitere Pflanzen und Texturen

`items.yml` enthält drei Bereiche: `items`, `crops`, `recipes`. Eine weitere Pflanze erhält eigene Item-IDs für Frucht und Samen, eine Crop-ID, eine Permission, Wachstumsmodelle und Rezepte. Die vorhandene Tomate dient als kopierbares Beispiel. Gegenwärtig gilt für alle Pflanzen derselbe Ackerboden-/Wachstumsablauf; Bäume und Erze benötigen eigene Logik.

Eigene PNGs kommen nach `plugins/ExtraItems/resourcepack/assets/extraitems/textures/`. Die JSONs unter `items/` verweisen auf Modelle unter `models/`. IDs und Dateinamen müssen klein geschrieben sein. Die enthaltenen JSON-Modelle können in Blockbench als Java-Modelle bearbeitet werden. PNGs und JSON-Modelle reichen; `.obj`/`.fbx` und Mods sind nicht erforderlich. Siehe `ASSETS.md`.

Beim Start exportiert das Plugin fehlende Standarddateien, überschreibt bestehende Dateien aber nicht. Anschließend baut es das gesamte `resourcepack/` neu und berechnet den Hash. Änderungen benötigen einen vollständigen Serverneustart; `/reload` und Hot-Unload mit Pluginmanagern werden nicht unterstützt. Beim Bearbeiten des Quellprojekts nach neuen Assets `python3 tools/build_models.py` bzw. das Manifest aktualisieren, dann neu bauen. Achtung: `build_models.py` erzeugt die Standardmodell-JSONs neu und überschreibt Änderungen an diesen Dateien.

## Speicherung und Betrieb

Pflanzenposition, Crop-ID, Wachstumsstufe und Fortschritt liegen im PersistentDataContainer des jeweiligen Welt-Chunks. Item-IDs liegen im PDC der ItemStacks. Zu jeder Pflanze gehören ein persistentes `ItemDisplay` und eine `Interaction`-Entity. Keine Datenbank und kein zusätzlicher Plugin-Download sind nötig. Der Ressourcenpackserver läuft auf eigenen Threads; Bukkit-Weltzugriffe bleiben im Serverthread.

Die Daten werden mit den normalen Welt-Chunks gespeichert. Für Backups: vollständige Welten inklusive Entities **und** `plugins/ExtraItems/` sichern, vorzugsweise bei gestopptem Server oder nach einer koordinierten Serversicherung. Ein harter Prozessabbruch kann seit der letzten Weltspeicherung entstandenen Fortschritt verlieren, genau wie andere noch nicht gespeicherte Weltänderungen.

Beim Laden eines Chunks stellt das Plugin fehlende Entities wieder her und bereinigt doppelte eigene Entities. Sind Einträge beschädigt oder Crop-IDs aus der Konfiguration entfernt worden, lässt das Plugin diese Chunk-Daten unangetastet und meldet den Fehler. Fehlende Definitionen wiederherstellen. Bereits verwendete IDs daher nicht umbenennen oder löschen. WorldEdit-/WorldGuard-Regionkopien transportieren diese Chunk-Daten nicht zuverlässig. Pflanzenfelder über das Plugin neu anlegen. Wird ein Träger extern überschrieben, räumt das Plugin seine Darstellung ohne Drops auf.

Standardlimits: 64 Pflanzen je Chunk und insgesamt 10.000 geladene Pflanzen. Das sind Schutzgrenzen, keine Leistungsgarantie; jede Pflanze erzeugt zwei Entities. Große Farmen vorab unter realer Spielerlast messen. Folia wird nicht unterstützt.

## Selbst bauen

Maven 3.9+ und JDK 21+ installieren. Im Projektordner:

```sh
mvn clean verify
```

Ergebnis: `target/ExtraItems-0.1.0.jar`. Das fertige JAR enthält alle Standard-Packdateien. Zum Prüfen gegen die neuere API mit JDK 25:

```sh
mvn clean verify -Dspigot.version=26.2-R0.1-SNAPSHOT
```

Für das auszuliefernde JAR anschließend wieder ohne Versions-Override gegen 1.21.11 bauen. Eine erfolgreiche Kompilierung ist kein vollständiger Ingame-Kompatibilitätstest. Python ist zum normalen Maven-Build nicht nötig; die Modell-JSONs und das Manifest liegen bereits im Projekt.

Optionale Pack-Strukturprüfung:

```sh
python3 tools/validate_pack.py
```

## Fehler finden

| Problem | Prüfen |
|---|---|
| Sofortiger Kick beim ersten Beitritt | `public-url` gesetzt? Konsole und `/ei status` prüfen. |
| Download schlägt fehl | URL von außerhalb erreichbar? TCP-Port offen? Direkte ZIP statt Webseite? Bei HTTPS-Hosting exakt die generierte ZIP hochgeladen? |
| Paket wird immer abgelehnt | Serverliste → Bearbeiten → Ressourcenpakete auf „Abfragen“ oder „Aktiviert“. |
| Tomate sieht wie Apfel aus | Pack bestätigt? Andere Packs überschreiben Assets? Native Clientversion verwenden. |
| Pflanze wächst nicht | Chunk geladen, Licht ≥ 9, Acker feucht, Server läuft? Testweise Knochenmehl mit Pflanzrecht. |
| Pflanzen/Craften klappt für jeden | OP, LuckPerms-Vererbung und Wildcard-Rechte prüfen. |
| Ernten im Claim geht nicht | Schutzplugin benötigt Abbaurecht, da ein Break-Event geprüft wird. |
| Rezept zeigt kein Ergebnis | Rechte, Standard-Item-Metadaten und Zutaten prüfen. Umbenannte Tomaten passen nicht in ExactChoice. |
| HTTP-Port belegt | Anderen freien Port konfigurieren und URL/Weiterleitung entsprechend ändern. |

## Quellen für Versions- und API-Entscheidungen

Abruf: 14. September 2026.

- [Paper-Versionen, offizieller Downloadservice](https://fill.papermc.io/v3/projects/paper)
- [Paper: Java-Anforderungen](https://docs.papermc.io/paper/getting-started/)
- [Minecraft 1.21.11: Ressourcenpaketformat 75.0](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11)
- [Minecraft 26.2: Ressourcenpaketformat 88.0](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2)
- [Minecraft 1.21.9: min_format/max_format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9)
- [Spigot: Player-Ressourcenpaket-API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/Player.html)
- [Spigot: Ladebestätigung und Pack-ID](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/player/PlayerResourcePackStatusEvent.html)
- [Spigot: ItemMeta und eigene Item-Modelle](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/inventory/meta/ItemMeta.html)
- [LuckPerms: Permission-Befehle](https://luckperms.net/wiki/Permission-Commands)

Die Pack-Prüfung beruht auf dem vom Client gemeldeten Ladezustand. Ein Server kann lokale Dateien eines Spielers nicht direkt durchsuchen und einen absichtlich manipulierten Client nicht allein durch diese Meldung verlässlich überprüfen. Normale Vanilla-Clients verwenden den SHA-1 zum Caching und werden bei erfolgreichem Laden freigeschaltet.
