# Abnahme auf einem separaten Testserver

Noch nicht ausgeführt. Diese Prüfung ergänzt den automatischen Build und die Komponententests. Für belastbare Freigabe dieselben Schritte auf Paper 1.21.11, Paper 26.2 und einem aktuellen Paper-26.3-Alpha-Build mit jeweils passendem Vanilla-Java-Client durchführen. Als zweiten Account einen Spieler ohne OP und ohne Wildcard-Rechte nutzen. Grundstücksschutz zusätzlich mit den auf dem Zielserver tatsächlich eingesetzten Plugins prüfen.

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
| Messerrezept in der Werkbank | Mitte links Stock, Mitte Eisen, unten Mitte Steinknopf ergibt ein Eisenmesser; alle übrigen Felder leer. |
| Messer + Brot normal und per Shift-Klick | Buns entstehen; Zutaten und exakt ein Messereinsatz pro Brot werden verbraucht. |
| Messer mit Haltbarkeit I/II/III | Insgesamt exakt 256/320/384 Brote schneidbar. |
| Mending-Messer mit Erfahrung reparieren | Schaden sinkt, eigenes Modell und ID bleiben erhalten. |
| Old-but-Gold-Buch auf anderes Item | Amboss zeigt kein Ergebnis. |
| Old-but-Gold-Buch auf Messer | Kostet 5 Level; Messer bleibt dauerhaft unzerstörbar. |
| Messer im Nahkampf | Voller Treffer zeigt 9 Angriffsschaden und eine deutlich schnellere 2,4-Angriffsgeschwindigkeit; ein Einsatz wird verbraucht. |
| Schlemmer-Burger herstellen und droppen | Rezept benötigt nur Buns, Salat, Zwiebel und gebratenes Rind; extrudiertes Pixelmodell sichtbar; füllt 9 Keulen. |
| Cheesy Schlemmer herstellen und droppen | Buns, Käsescheibe, Tomate, Salat und gebratenes Rind; kein Zwiebelbedarf; eigene Textur; füllt maximal die zehn Vanilla-Keulen plus Sättigung. |
| Käsestation manuell | Milcheimer bleibt während der 60 Sekunden im Eingang; erst danach erscheinen Käserad und leerer Eimer getrennt. |
| Milcheimer während Reife herausnehmen | Vorgang bricht beim nächsten Stationstick ab; Eimer bleibt beim Spieler, kein Käse entsteht und ein neuer Eimer startet bei 0 %. |
| Käse- oder Eimerausgang voll | Verarbeitung wartet bei 100 %, ohne den Milcheimer zu verlieren; nach Freimachen entstehen beide Ausgaben genau einmal. |
| Käsestations-GUI | Dunkler Rahmen, farbig markierter Eingang/Ausgang, fünfteiliger Balken, Prozent und Restzeit aktualisieren sich. |
| Käsestationsrezept mit verschiedenen Brettern | Positionen entsprechen `·Fass· / Bretter-Werkbank-Bretter / Stock-·-Stock`; alle Brettarten funktionieren. |
| Kiste → Hopper → Station → Hopper | Milcheimer wird oben/seitlich eingezogen; beide Ausgaben landen ausschließlich unten. |
| Neustart während der Reifezeit | Verbleibende Reifezeit und Station bleiben erhalten. |
| Käserad in der Hand benutzen | Nicht essbar. |
| Käserad platzieren und leerhändig essen | Zehn sichtbare Portionen; jede füllt 2 Nahrungspunkte. |
| Käserad abbauen oder Unterlage entfernen | Rad verschwindet vollständig und droppt nichts. |
| Messer + Käserad | Zehn Käsescheiben entstehen; ein Messereinsatz wird verbraucht. |
| Käsescheibe essen | In der Hand essbar; füllt genau eine Hungerkeule. |
| Samengenerator-Rezept | `Glas-Lagerfeuer-Glas / Bretter-Fass-Bretter / Stock-·-Stock`; alle Brettarten funktionieren. |
| Gemüse manuell trocknen | Tomate/Salat/Zwiebel bleiben jeweils 30 Sekunden im Eingang und ergeben 4/3/3 passende Samen. |
| Gemüse während Trocknung entfernen/tauschen | Lauf bricht ohne Verlust ab; ein anderes Gemüse übernimmt nicht den alten Fortschritt. |
| Kiste → Hopper → Samengenerator → Hopper | Gemüse wird nur oben/seitlich eingezogen, fertige Samen ausschließlich unten ausgegeben. |

Die echte Grafik, Client-Handanimation, Reihenfolge mit Drittplugins und Verhalten unter hoher Spielerlast lassen sich mit reinen Unit-Tests nicht abnehmen. Erst nach dieser Prüfung produktiv einsetzen.
