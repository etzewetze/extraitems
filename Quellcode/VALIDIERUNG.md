# Validierung — ExtraItems 0.3.3

Stand: 15. September 2026. Build und Komponenten sind automatisiert geprüft; die neuen GUI-, Hopper- und Entity-Interaktionen benötigen zusätzlich einen echten Minecraft-Ingame-Test.

## Ausgeführt

| Prüfung | Ergebnis |
|---|---|
| Sauberer Maven-Build gegen Spigot-API `1.21.11-R0.1-SNAPSHOT` | Erfolgreich |
| Sauberer Maven-Build gegen Spigot-API `26.2-R0.1-SNAPSHOT` | Erfolgreich |
| Java-Compilerziel | `--release 21`, Classfile-Version 65 |
| Automatisierte Tests gegen Basis-API | 44 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| Dieselben Tests gegen neueste API | 44 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| JSON-/Manifest-/Modellreferenzprüfung | 86 Packdateien, 3 Pflanzen × 4 Stufen, 10 Käsestufen und kontextabhängige 3D-Handmodelle vollständig |
| Bildprüfung | 9 eigene PNGs; quadratische RGBA-Zweierpotenzen mit Alphakanal |
| ZIP-Paketlayout | `pack.mcmeta` direkt an der Wurzel, relative Assetpfade |

GitHub Actions baute gegen 1.21.11 unter Temurin 21 und gegen 26.2 unter Temurin 25. Das ausgelieferte JAR wird abschließend gegen die niedrigste API 1.21.11 erzeugt, sodass beim Bau keine ausschließlich in 26.2 vorhandenen Methoden eingebunden werden.

## Was die Tests tatsächlich prüfen

- **10 Pack-Statusfälle:** fremde Paket-ID schaltet nicht frei; „akzeptiert“/„heruntergeladen“ reichen nicht; erfolgreiche Meldung; Timeout; Ablehnung; Downloadfehler; ungültige URL; Reloadfehler; verworfenes Paket; unbekannte Statuswerte bleiben gesperrt. Späte Erfolgsmeldungen können einen gescheiterten Vorgang nicht nachträglich freischalten.
- **4 Crafting-Regelfälle:** Packfreigabe und aktuelles Recht nötig; gewöhnliche Äpfel gelten nicht als Tomaten; formlose Reihenfolge und leere Felder; korrekte Anzahl mehrfach benötigter Zutaten.
- **5 Speicher-/Wachstumsfälle:** verlustfreies Kodieren und Dekodieren einschließlich negativer Chunkkoordinaten; keine Einträge außerhalb ihres Chunks oder der Welthöhe; beschädigte Werte abgelehnt; Wachstum pausiert bei fehlenden Bedingungen; reife Pflanzen überschreiten die Modellzahl nicht.
- **3 Definitionsdateifälle:** nur reguläre YAML-Dateien innerhalb des Pluginordners werden geladen; Traversal, absolute Pfade, falsche Endungen, fehlende Dateien und Symlinks werden abgelehnt; neue Standardpfade werden verlustfrei mit eigenen Indexeinträgen zusammengeführt.
- **9 Download-/Archivfälle:** reproduzierbare ZIPs und Hashänderung bei geänderten Inhalten; fehlende Metadaten und Symlinks abgelehnt; atomisches Ersetzen der Ausgabe; sichere Revisionserneuerung mit Backup; echte HTTP-Requests für GET, HEAD, 404 und 405; URL- und Hostvalidierung.
- **6 Assetfälle:** neun eigene Zweierpotenz-RGBA-Texturen; reife Tomate ohne Abhängigkeit von der eigenen PNG; Ground-Transformationen für Quader-Items; fünf GUI-Icons mit `minecraft:display_context`-Auswahl und echten 3D-Handmodellen; drei vollständige Wachstumsreihen; zehn Käserad-Portionen.
- **2 Werkzeugfälle:** 192 Grundnutzungen, exakt 64 zusätzliche Nutzungen je Haltbarkeitsstufe und unbegrenzte Nutzungen mit Old but Gold.
- **4 Küchen-Definitionsfälle:** das exakte Käsestationsraster mit beliebigen Brettern, das Messerrezept mit Stick, Eisenbarren und Steinknopf, die gewünschten Nahrungswerte und die fünf Zutaten des Cheesy Schlemmers.
- **2 Fehlerkorrekturen:** Container-Snapshots werden vor der Stations-Inventaränderung gespeichert; Custom-Rezepte werden per Material vorselektiert und danach anhand der ExtraItems-ID geprüft.

Die Crafting-Tests prüfen die Entscheidungs- und Haltbarkeitslogik. Sie simulieren nicht die komplette Bukkit-Inventarverarbeitung, Hopper oder einen echten Shift-Klick-Client. Die Pflanzentests prüfen den gespeicherten Datensatz und den Wachstumsübergang, keine laufende Minecraft-Welt. Diese Grenzen werden nicht durch die Zahl der Tests aufgehoben.

## Noch ausstehend

Echte Client-/Server-Durchläufe auf Paper 1.21.11 und 26.2: neue Saatbilder und Modelle, Käserad-Hitbox, Messer im 2×2-Feld und per Shift-Klick, Amboss mit Mending/Haltbarkeit/Old but Gold, Käsestation mit manueller Eingabe und Hopperkette, persistente Entities nach Chunk-Unload und Neustart sowie Kombination mit LuckPerms und Grundstücksschutz.

Die konkrete Abnahmeliste steht in `docs/INGAME-TEST.md`. Vor dem Einsatz in einer bestehenden Welt dort prüfen. Eine uneingeschränkte Kompatibilitätszusage für zukünftige Minecraft-Versionen wird nicht gegeben.
