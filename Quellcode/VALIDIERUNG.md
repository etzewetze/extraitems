# Validierung — ExtraItems 0.2.1

Stand: 14. September 2026. Dies ist eine erste Implementierung mit Build- und Komponententests; keine bereits auf einem Minecraft-Server mit echten Spielern abgenommene Veröffentlichung.

## Ausgeführt

| Prüfung | Ergebnis |
|---|---|
| Sauberer Maven-Build gegen Spigot-API `1.21.11-R0.1-SNAPSHOT` | Erfolgreich |
| Sauberer Maven-Build gegen Spigot-API `26.2-R0.1-SNAPSHOT` | Erfolgreich |
| Java-Compilerziel | `--release 21`, Classfile-Version 65 |
| Automatisierte Tests gegen Basis-API | 33 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| Dieselben Tests gegen neueste API | 33 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| JSON-/Manifest-/Modellreferenzprüfung | 14 Packdateien vollständig, 6 Itemdefinitionen, 4 Wachstumsmodelle und 3D-Tomatenitem |
| Bildprüfung | 256×256 RGBA, transparenter Alphakanal und mipmap-taugliche Zweierpotenzgröße |
| ZIP-Paketlayout | `pack.mcmeta` direkt an der Wurzel, relative Assetpfade |

Die Builds und Tests liefen unter Temurin JDK 25 mit Java-21-Compilerziel. Es wurde kein JDK-21-Server gestartet. Das ausgelieferte JAR wird abschließend gegen die niedrigste API 1.21.11 erzeugt, sodass beim Bau keine ausschließlich in 26.2 vorhandenen Methoden eingebunden werden.

## Was die Tests tatsächlich prüfen

- **10 Pack-Statusfälle:** fremde Paket-ID schaltet nicht frei; „akzeptiert“/„heruntergeladen“ reichen nicht; erfolgreiche Meldung; Timeout; Ablehnung; Downloadfehler; ungültige URL; Reloadfehler; verworfenes Paket; unbekannte Statuswerte bleiben gesperrt. Späte Erfolgsmeldungen können einen gescheiterten Vorgang nicht nachträglich freischalten.
- **4 Crafting-Regelfälle:** Packfreigabe und aktuelles Recht nötig; gewöhnliche Äpfel gelten nicht als Tomaten; formlose Reihenfolge und leere Felder; korrekte Anzahl mehrfach benötigter Zutaten.
- **5 Speicher-/Wachstumsfälle:** verlustfreies Kodieren und Dekodieren einschließlich negativer Chunkkoordinaten; keine Einträge außerhalb ihres Chunks oder der Welthöhe; beschädigte Werte abgelehnt; Wachstum pausiert bei fehlenden Bedingungen; reife Pflanzen überschreiten die Modellzahl nicht.
- **2 Definitionsdateifälle:** nur reguläre YAML-Dateien innerhalb des Pluginordners werden geladen; Traversal, absolute Pfade, falsche Endungen, fehlende Dateien und Symlinks werden abgelehnt.
- **9 Download-/Archivfälle:** reproduzierbare ZIPs und Hashänderung bei geänderten Inhalten; fehlende Metadaten und Symlinks abgelehnt; atomisches Ersetzen der Ausgabe; sichere Revisionserneuerung mit Backup; echte HTTP-Requests für GET, HEAD, 404 und 405; URL- und Hostvalidierung.
- **3 Assetfälle:** Zweierpotenz-RGBA-Textur; reife Pflanze ohne Abhängigkeit von der eigenen PNG; echtes Quader-Modell mit Ground-Transformation für gedroppte Tomaten.

Die Crafting-Tests prüfen die vom Eventadapter verwendete Entscheidungslogik. Sie simulieren nicht die komplette Bukkit-Inventarverarbeitung oder einen echten Shift-Klick-Client. Die Pflanzentests prüfen den gespeicherten Datensatz und den Wachstumsübergang, keine laufende Minecraft-Welt. Diese Grenzen werden nicht durch die Zahl der Tests aufgehoben.

## Noch ausstehend

Echte Client-/Server-Durchläufe auf Paper 1.21.11 und 26.2: Packdialog und Grafik, Hitboxen, Handinteraktionen, Crafting einschließlich Shift-/Hotbar-Klicks, persistente Entities nach Chunk-Unload und Neustart, Kombination mit LuckPerms und dem tatsächlichen Grundstücksschutz sowie Verhalten bei Last.

Die konkrete Abnahmeliste steht in `docs/INGAME-TEST.md`. Vor dem Einsatz in einer bestehenden Welt dort prüfen. Eine uneingeschränkte Kompatibilitätszusage für zukünftige Minecraft-Versionen wird nicht gegeben.
