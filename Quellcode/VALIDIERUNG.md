# Validierung — ExtraItems 0.8.0

Stand: 19. September 2026. Build und Komponenten sind automatisiert geprüft; die neuen GUI-, Hopper-, Entity- und Import-Interaktionen benötigen zusätzlich einen echten Minecraft-Ingame-Test.

## Ausgeführt

| Prüfung | Ergebnis |
|---|---|
| Sauberer Maven-Build gegen Spigot-API `1.21.11-R0.1-SNAPSHOT` | Erfolgreich |
| Sauberer Maven-Build gegen Spigot-API `26.2-R0.1-SNAPSHOT` | Erfolgreich |
| Sauberer Maven-Build gegen lokal erzeugte Paper-API `26.3.local-SNAPSHOT` unter Java 25 | Erfolgreich |
| Java-Compilerziel | `--release 21`, Classfile-Version 65 |
| Automatisierte Tests gegen Spigot 1.21.11 | 74 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| Automatisierte Tests gegen Spigot 26.2 | 74 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| Automatisierte Tests gegen Paper 26.3 Alpha | 74 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| JSON-/Manifest-/Modellreferenzprüfung | 142 Packdateien, 3 Pflanzen × 4 Stufen, 2 Maschinen, 10 Käsestufen, kontextabhängige 3D-Handmodelle und 24 Capybara-Frames vollständig |
| Bildprüfung | 15 eigene PNGs; quadratische RGBA-Zweierpotenzen mit Alphakanal; drei 64×64-Felltexturen liegen im Item-Atlas |
| ZIP-Paketlayout | `pack.mcmeta` direkt an der Wurzel, relative Assetpfade |

GitHub Actions baute erfolgreich gegen 1.21.11 unter Temurin 21, 26.2 unter Temurin 25 und Paper 26.3 Alpha unter Temurin 25. Weil Paper noch kein öffentliches 26.3-API-Artefakt ausliefert, wird die API reproduzierbar aus dem offiziellen Commit `cc95f009b7e4b5687feb0a068d9fc44b326dc8c3` gebaut und lokal als `26.3.local-SNAPSHOT` installiert. Das ausgelieferte JAR wird abschließend gegen die niedrigste API 1.21.11 erzeugt, sodass beim Bau keine ausschließlich in neueren APIs vorhandenen Methoden eingebunden werden. Das Ressourcenpaket deklariert den offiziellen Bereich 75.0 bis 97.1.

## Was die Tests tatsächlich prüfen

- **10 Pack-Statusfälle:** fremde Paket-ID schaltet nicht frei; „akzeptiert“/„heruntergeladen“ reichen nicht; erfolgreiche Meldung; Timeout; Ablehnung; Downloadfehler; ungültige URL; Reloadfehler; verworfenes Paket; unbekannte Statuswerte bleiben gesperrt. Späte Erfolgsmeldungen können einen gescheiterten Vorgang nicht nachträglich freischalten.
- **4 Crafting-Regelfälle:** Packfreigabe und aktuelles Recht nötig; gewöhnliche Äpfel gelten nicht als Tomaten; formlose Reihenfolge und leere Felder; korrekte Anzahl mehrfach benötigter Zutaten.
- **5 Speicher-/Wachstumsfälle:** verlustfreies Kodieren und Dekodieren einschließlich negativer Chunkkoordinaten; keine Einträge außerhalb ihres Chunks oder der Welthöhe; beschädigte Werte abgelehnt; Wachstum pausiert bei fehlenden Bedingungen; reife Pflanzen überschreiten die Modellzahl nicht.
- **5 Definitionsdateifälle:** reguläre YAML-Dateien sowie CraftEngine-ZIPs/-Ordner innerhalb des Pluginordners werden geladen; Traversal, absolute Pfade, falsche Endungen, fehlende Dateien und Symlinks werden abgelehnt; neue Standardpfade werden verlustfrei mit eigenen Indexeinträgen zusammengeführt; das alte direkte Tomaten-Samen-Rezept wird aus bestehenden Indizes entfernt.
- **10 Download-/Archivfälle:** reproduzierbare ZIPs und Hashänderung bei geänderten Inhalten; fehlende Metadaten und Symlinks abgelehnt; Import-Overlay wird konfliktgeprüft zusammengeführt; atomisches Ersetzen der Ausgabe; sichere Revisionserneuerung mit Backup; echte HTTP-Requests für GET, HEAD, 404 und 405; URL- und Hostvalidierung.
- **5 Importfälle:** Nexo-Maker-CraftEngine-ZIP und entpackter Ordner werden zu nativen Items, Modell-JSON und Pack-Overlay konvertiert; Kategorien werden ignoriert; ZIP-Traversal und widersprüchliche Assets werden abgelehnt; entfernte Quellen hinterlassen nach erfolgreichem Neustart keine alten Importassets.
- **8 Assetfälle:** fünfzehn eigene Zweierpotenz-RGBA-Texturen; reife Tomate ohne Abhängigkeit von der eigenen PNG; Ground-Transformationen für Quader-Items; fünf GUI-Icons mit `minecraft:display_context`-Auswahl und echten 3D-Handmodellen; drei vollständige Wachstumsreihen; zehn Käserad-Portionen; 24 Capybara-Stand-/Lauf-/Schwimmframes mit eigenen Item-Atlas-Felltexturen und echten Beinrotationen.
- **12 Capybara-Regelfälle:** ausschließliche Konfiguration der drei Badlands-Varianten; Süßbeeren und alle Fell-/Altersmodelle; konfigurierbare Lauf- und Schwimmframes; Definition im Index und JAR-Manifest; begrenztes Babywachstum; Fellvererbung mit Variantenchance; Gruppengröße respektiert Welt-/Nahbereichskapazität; mehrere Kandidaten je Spawnprüfung; exakte Wahrscheinlichkeitsgrenzen; Stand-, Lauf- und Schwimmzustände sowie ungültige Animationsgrenzen.
- **2 Werkzeugfälle:** 192 Grundnutzungen, exakt 64 zusätzliche Nutzungen je Haltbarkeitsstufe und unbegrenzte Nutzungen mit Old but Gold.
- **6 Küchen-Definitionsfälle:** exakte Käsestations- und Messerrezepte, gewünschte Nahrungswerte, fünf Zutaten des Cheesy Schlemmers, Samengenerator mit allen drei Umwandlungen sowie 9 Schaden/2,4 Angriffsgeschwindigkeit des Messers.
- **3 Maschinen-Zustandsfälle:** gültiger Eingang startet ohne Frühverbrauch; Entfernen vor oder nach Ablauf bricht ab; ein voller Ausgang wartet und ein freier Ausgang schließt exakt ab.
- **2 Fehlerkorrekturen:** Käsestationen verbrauchen den Milcheimer erst beim kapazitätsgeprüften Abschluss und schreiben keinen veralteten Fass-Snapshot zurück; Custom-Rezepte werden per Material vorselektiert und danach anhand der ExtraItems-ID geprüft.

Die Crafting-Tests prüfen die Entscheidungs- und Haltbarkeitslogik. Sie simulieren nicht die komplette Bukkit-Inventarverarbeitung, Hopper oder einen echten Shift-Klick-Client. Die Pflanzentests prüfen den gespeicherten Datensatz und den Wachstumsübergang, keine laufende Minecraft-Welt. Die Capybara-Tests prüfen Definitionen, Modelle, Wachstums-, Vererbungs- und Gruppenregeln, aber keine echte Mob-AI oder Chunk-Laufzeit. Diese Grenzen werden nicht durch die Zahl der Tests aufgehoben.

## Noch ausstehend

Echte Client-/Server-Durchläufe auf Paper 1.21.11, 26.2 und Paper 26.3 Alpha: Saatbilder und Modelle, Käserad-Hitbox, Messerrezept und Shift-Klick, Nahkampfschaden/-tempo, Amboss mit Mending/Haltbarkeit/Old but Gold, Käsestation mit Abbruch und Hopperkette, Samengenerator mit drei Eingängen, Capybara-Spawns/Zucht/Wasserverhalten/Fellwechsel, sichtbare Lauf-/Schwimmframewechsel, persistente Entities nach Chunk-Unload und Neustart sowie Kombination mit LuckPerms und Grundstücksschutz.

Die konkrete Abnahmeliste steht in `docs/INGAME-TEST.md`. Vor dem Einsatz in einer bestehenden Welt dort prüfen. Eine uneingeschränkte Kompatibilitätszusage für zukünftige Minecraft-Versionen wird nicht gegeben.

- Optionaler Laufzeit-Adapter für Nexo, ItemsAdder, Oraxen und CraftEngine; Provider bleiben ohne Fremdplugin-Abhängigkeit.
- Externe Rezepte werden nach dem Provider-Enable automatisch erneut registriert.
