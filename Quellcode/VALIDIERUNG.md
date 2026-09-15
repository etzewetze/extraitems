# Validierung — ExtraItems 0.3.0

Stand: 15. September 2026. Build und Komponenten sind automatisiert geprüft; die neuen GUI-, Hopper- und Entity-Interaktionen benötigen zusätzlich einen echten Minecraft-Ingame-Test.

## Ausgeführt

| Prüfung | Ergebnis |
|---|---|
| Sauberer Maven-Build gegen Spigot-API `1.21.11-R0.1-SNAPSHOT` | Erfolgreich |
| Sauberer Maven-Build gegen Spigot-API `26.2-R0.1-SNAPSHOT` | Erfolgreich |
| Java-Compilerziel | `--release 21`, Classfile-Version 65 |
| Automatisierte Tests gegen Basis-API | 37 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| Dieselben Tests gegen neueste API | 37 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| JSON-/Manifest-/Modellreferenzprüfung | 73 Packdateien, 3 Pflanzen × 4 Stufen, 10 Käsestufen und 8 3D-Itemmodelle vollständig |
| Bildprüfung | 4 eigene PNGs; quadratische RGBA-Zweierpotenzen mit Alphakanal |
| ZIP-Paketlayout | `pack.mcmeta` direkt an der Wurzel, relative Assetpfade |

GitHub Actions baute gegen 1.21.11 unter Temurin 21 und gegen 26.2 unter Temurin 25. Das ausgelieferte JAR wird abschließend gegen die niedrigste API 1.21.11 erzeugt, sodass beim Bau keine ausschließlich in 26.2 vorhandenen Methoden eingebunden werden.

## Was die Tests tatsächlich prüfen

- **10 Pack-Statusfälle:** fremde Paket-ID schaltet nicht frei; „akzeptiert“/„heruntergeladen“ reichen nicht; erfolgreiche Meldung; Timeout; Ablehnung; Downloadfehler; ungültige URL; Reloadfehler; verworfenes Paket; unbekannte Statuswerte bleiben gesperrt. Späte Erfolgsmeldungen können einen gescheiterten Vorgang nicht nachträglich freischalten.
- **4 Crafting-Regelfälle:** Packfreigabe und aktuelles Recht nötig; gewöhnliche Äpfel gelten nicht als Tomaten; formlose Reihenfolge und leere Felder; korrekte Anzahl mehrfach benötigter Zutaten.
- **5 Speicher-/Wachstumsfälle:** verlustfreies Kodieren und Dekodieren einschließlich negativer Chunkkoordinaten; keine Einträge außerhalb ihres Chunks oder der Welthöhe; beschädigte Werte abgelehnt; Wachstum pausiert bei fehlenden Bedingungen; reife Pflanzen überschreiten die Modellzahl nicht.
- **2 Definitionsdateifälle:** nur reguläre YAML-Dateien innerhalb des Pluginordners werden geladen; Traversal, absolute Pfade, falsche Endungen, fehlende Dateien und Symlinks werden abgelehnt.
- **9 Download-/Archivfälle:** reproduzierbare ZIPs und Hashänderung bei geänderten Inhalten; fehlende Metadaten und Symlinks abgelehnt; atomisches Ersetzen der Ausgabe; sichere Revisionserneuerung mit Backup; echte HTTP-Requests für GET, HEAD, 404 und 405; URL- und Hostvalidierung.
- **5 Assetfälle:** vier eigene Zweierpotenz-RGBA-Texturen; reife Tomate ohne Abhängigkeit von der eigenen PNG; Ground-Transformationen für gedroppte 3D-Items; drei vollständige Wachstumsreihen; zehn Käserad-Portionen.
- **2 Werkzeugfälle:** 192 Grundnutzungen, exakt 64 zusätzliche Nutzungen je Haltbarkeitsstufe und unbegrenzte Nutzungen mit Old but Gold.

Die Crafting-Tests prüfen die Entscheidungs- und Haltbarkeitslogik. Sie simulieren nicht die komplette Bukkit-Inventarverarbeitung, Hopper oder einen echten Shift-Klick-Client. Die Pflanzentests prüfen den gespeicherten Datensatz und den Wachstumsübergang, keine laufende Minecraft-Welt. Diese Grenzen werden nicht durch die Zahl der Tests aufgehoben.

## Noch ausstehend

Echte Client-/Server-Durchläufe auf Paper 1.21.11 und 26.2: neue Saatbilder und Modelle, Käserad-Hitbox, Messer im 2×2-Feld und per Shift-Klick, Amboss mit Mending/Haltbarkeit/Old but Gold, Käsestation mit manueller Eingabe und Hopperkette, persistente Entities nach Chunk-Unload und Neustart sowie Kombination mit LuckPerms und Grundstücksschutz.

Die konkrete Abnahmeliste steht in `docs/INGAME-TEST.md`. Vor dem Einsatz in einer bestehenden Welt dort prüfen. Eine uneingeschränkte Kompatibilitätszusage für zukünftige Minecraft-Versionen wird nicht gegeben.
