# Abnahme auf einem separaten Testserver

Noch nicht ausgeführt. Diese Prüfung ergänzt den automatischen Build und die Komponententests. Für belastbare Freigabe dieselben Schritte auf Paper 1.21.11 und Paper 26.2 mit jeweils passendem Vanilla-Java-Client durchführen. Als zweiten Account einen Spieler ohne OP und ohne Wildcard-Rechte nutzen. Grundstücksschutz zusätzlich mit den auf dem Zielserver tatsächlich eingesetzten Plugins prüfen.

| Test | Erwartung |
|---|---|
| Erstbeitritt, Paket annehmen | Tomate und Pflanzen korrekt dargestellt; Bewegung nach Ladebestätigung möglich. |
| Paket ablehnen / in Serverliste deaktivieren | Kick; keine Freischaltung. |
| URL unerreichbar / Download beschädigt | Kick mit Fehlermeldung; kein Weiterspielen. |
| Client bestätigt innerhalb Timeout nicht | Kick spätestens nach konfigurierter Frist. |
| Anderes Plugin lädt ein weiteres Pack | Dessen Status schaltet ExtraItems nicht frei. |
| Erneuter Beitritt mit gleichem Pack | Clientcache verwendbar; ExtraItems wartet erneut auf erfolgreiche Meldung. |
| `/ei pack` mehrmals / Neuverbindung | Alte Meldungen und Timeouttasks beeinflussen die neue Anfrage nicht. |
| Samen ohne Pflanzrecht auf Acker verwenden | Keine Pflanze; Samenmenge unverändert. |
| Samen mit Pflanzrecht, auch Nebenhand | Genau eine Pflanze; genau ein Samen verbraucht. |
| Pflanze in verbotenem Claim / Spawnschutz | Kein Pflanzvorgang; keine verlorenen Samen oder zurückgelassenen Träger. |
| Düngen ohne Pflanzrecht | Keine Stufenänderung; kein Knochenmehlverbrauch. |
| Düngen mit Pflanzrecht | Genau eine Stufe und ein Knochenmehl pro Klick. |
| Wachstum bei Licht und feuchtem Acker | Stufen 0 → 1 → 2 → 3; keine Stufe außerhalb der Modelle. |
| Dunkelheit / trockener Acker | Fortschritt pausiert. |
| Ernten, mehrere schnelle Klicks | Einmal 1–3 Tomaten; Rücksetzung auf Stufe 1; kein Doppel-Drop. |
| Ernten/Abbauen im fremden Claim | Schutzplugin kann es verhindern; keine Drops. |
| Survival-Abbau unreif / reif | Ein Samen; reif zusätzlich Tomaten; keine zurückbleibenden Entities. |
| Creative-Abbau | Keine Drops. |
| Normales Blocksetzen, Eimer, Kolben, TNT, Wasser, Zertrampeln | Pflanze/Acker nicht überschrieben; keine Duplikate. |
| Samenrezept ohne Craftrecht | Kein Ergebnis und kein Craft, auch Shift-Klick, Zahlentaste und Rezeptbuch. |
| Craftrecht zwischen Vorschau und Klick entziehen | Endgültiger Craft wird abgelehnt. |
| Samenrezept mit Craftrecht | Exakte Zutaten und konfigurierte Menge. |
| Automatischer Crafter mit beiden Samenrezepten | Kein Craft und kein Verbrauch. |
| Tomate statt Apfel in Golden-Apple-Rezept | Kein Vanilla-Craft mit dem Custom-Item. |
| Tomate/Salat/Zwiebel essen | Jeweils 3 Nahrungspunkte = 1,5 Hungerkeulen. |
| Burger Buns essen | 1 Nahrungspunkt = 0,5 Hungerkeule. |
| Stop/Start mit unreifen Pflanzen und Samen im Inventar | IDs, Position, Stufe und Fortschritt bleiben erhalten. |
| Chunk entladen und später laden | Eine Darstellung und eine Hitbox je Pflanze; keine Duplikate. |
| Weltwechsel / zusätzliche Welt entladen und laden | Pflanzen werden dem richtigen Welt-UUID/Chunk zugeordnet. |
| Chunklimit erreichen | Weitere Aussaat abgelehnt, ohne Samenverbrauch. |
| `/ei give` bei vollem Inventar | Kein unkontrollierter Bodendrop; Meldung nennt nicht vergebene Menge. |
| Originalmodelle und Tomaten auf beiden Clients | Keine Missing-Texture-Flächen, brauchbare Skalierung/Hitbox, transparenter Hintergrund. |
| Tomaten-, Salat- und Zwiebelsamen nebeneinander | Drei eindeutig unterschiedliche Symbole, keine Vanilla-Samenanzeige. |
| Salat/Zwiebel pflanzen, düngen und ernten | Je vier sichtbare Stufen; definierte Erntemenge; Nachwuchs ab Stufe 0. |
| Messerrezept im 2×2-Feld | Stock links, Eisen rechts, Steinknopf unter dem Eisen ergibt ein Eisenmesser. |
| Messer + Brot normal und per Shift-Klick | Buns entstehen; Zutaten und exakt ein Messereinsatz pro Brot werden verbraucht. |
| Messer mit Haltbarkeit I/II/III | Insgesamt exakt 256/320/384 Brote schneidbar. |
| Mending-Messer mit Erfahrung reparieren | Schaden sinkt, eigenes Modell und ID bleiben erhalten. |
| Old-but-Gold-Buch auf anderes Item | Amboss zeigt kein Ergebnis. |
| Old-but-Gold-Buch auf Messer | Kostet 5 Level; Messer bleibt dauerhaft unzerstörbar. |
| Schlemmer-Burger herstellen und droppen | Rezept benötigt nur Buns, Salat, Zwiebel und gebratenes Rind; extrudiertes Pixelmodell sichtbar; füllt 9 Keulen. |
| Cheesy Schlemmer herstellen und droppen | Buns, Käsescheibe, Tomate, Salat und gebratenes Rind; kein Zwiebelbedarf; eigene Textur; füllt maximal die zehn Vanilla-Keulen plus Sättigung. |
| Käsestation manuell | Milcheimer im Eingang startet 60 Sekunden; Käserad und leerer Eimer erscheinen getrennt. |
| Käsestations-GUI | Dunkler Rahmen, farbig markierter Eingang/Ausgang, fünfteiliger Balken, Prozent und Restzeit aktualisieren sich. |
| Käsestationsrezept mit verschiedenen Brettern | Positionen entsprechen `·Fass· / Bretter-Werkbank-Bretter / Stock-·-Stock`; alle Brettarten funktionieren. |
| Kiste → Hopper → Station → Hopper | Milcheimer wird oben/seitlich eingezogen; beide Ausgaben landen ausschließlich unten. |
| Neustart während der Reifezeit | Verbleibende Reifezeit und Station bleiben erhalten. |
| Käserad in der Hand benutzen | Nicht essbar. |
| Käserad platzieren und leerhändig essen | Zehn sichtbare Portionen; jede füllt 2 Nahrungspunkte. |
| Käserad abbauen oder Unterlage entfernen | Rad verschwindet vollständig und droppt nichts. |
| Messer + Käserad | Zehn Käsescheiben entstehen; ein Messereinsatz wird verbraucht. |
| Käsescheibe essen | In der Hand essbar; füllt genau eine Hungerkeule. |

Die echte Grafik, Client-Handanimation, Reihenfolge mit Drittplugins und Verhalten unter hoher Spielerlast lassen sich mit reinen Unit-Tests nicht abnehmen. Erst nach dieser Prüfung produktiv einsetzen.
