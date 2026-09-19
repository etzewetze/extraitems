# Texturen und Modelle

`src/main/resources/resourcepack/assets/extraitems/textures/item/tomato.png` ist eine 256×256-PNG mit echtem Alphakanal und grober Pixeloptik. Die Zweierpotenzgröße ist mipmap- und atlas-tauglich. Die frühere 1254×1254-Datei konnte beim Laden des Texturatlas die pink-schwarze Fehlerdarstellung auslösen.

Verwendeter Erzeugungsprompt:

> Use case: stylized-concept. Asset type: Minecraft Java resource pack item texture, project ExtraItems. Generate ONE square PNG texture with genuinely transparent alpha background, showing a single ripe red tomato with a small green five-point leafy calyx on top, centered and filling 80% of canvas. Classic Minecraft 16x16 pixel-art aesthetic, very coarse square pixels, hard stepped edges, limited reds and greens, one light red highlight on upper left. Front/three-quarter view, recognizable in a tiny inventory slot. No text, no labels, no border, no checkerboard drawn into the image, no drop shadow, no blur, no extra objects. This is the actual in-game item sprite, not a mockup or a presentation. Return the resulting local PNG file path as it is needed for embedding in the plugin resource pack.

Eine spätere Exportvariante wurde wegen fehlender Transparenz verworfen und ist nicht enthalten.

Pflanzen, Käsescheibe, Käsestation, Samengenerator und Käseräder sind native Minecraft-JSON-Quader-Modelle. Die reifen Tomaten verwenden `minecraft:block/red_concrete`, damit die letzte Wachstumsstufe nicht von einer eigenen Atlastextur abhängig ist. Zwiebel, Messer, Buns, Schlemmer Burger und Cheesy Schlemmer behalten ab 0.3.2 ihre vorhandenen transparenten Sprites im Inventar; per `minecraft:display_context` wechseln Hand, Drop und Itemrahmen auf echte native Quader-Modelle. `tools/build_models.py` erzeugt alle Modellverknüpfungen reproduzierbar; Clientmods sind nicht nötig.

Tomaten-, Salat- und Zwiebelsamen besitzen jeweils eine eigene 256×256-RGBA-PNG. Sie wurden im Modus `stylized-concept` als grobe Minecraft-Pixelgrafik auf transparentem Hintergrund erzeugt: Tomatensamen mit rotem Frucht-/Blatt-Hinweis, dunkle Salatsamen mit grünem Blatt und schwarze Zwiebelsamen mit heller Zwiebel und grünem Austrieb. Die Dateien liegen unter `assets/extraitems/textures/item/*_seeds.png`.

Die fünf neuen bzw. reparierten Sprites wurden ebenfalls mit dem eingebauten ImageGen im Modus `stylized-concept` erstellt: einzelne Zwiebel, offene Burger Buns, diagonales Eisen-Küchenmesser, Schlemmer Burger ohne Käse/Tomate und Cheesy Schlemmer mit Käse/Tomate ohne Zwiebel. Vorgaben waren grobe Minecraft-Pixel, transparente Hintergründe, keine Schrift und jeweils genau ein Item. Finale Pfade: `textures/item/onion.png`, `burger_bun.png`, `knife.png`, `schlemmer_burger.png` und `cheesy_schlemmer.png`.

Für Capybaras enthält das Paket drei 32×32-RGBA-Fellvorlagen unter `textures/entity/`: warmes Kastanienbraun, dunkles Kohlebraun und ein kastanienbraunes Fell mit schwarzen Flecken. Seit 0.7.1 dienen sie nur noch als Designreferenz. Die sechs Live-Modelle verwenden zuverlässige Vanilla-Atlasmaterialien aus `minecraft:block`; schwarze Flecken werden durch zusätzliche Modellflächen dargestellt. Dadurch bleibt die Variantenidee erhalten, ohne dass eine nicht registrierte Entity-Textur ein pink-schwarzes Fehlermodell auslösen kann. Erwachsenen- und Babymodell besitzen weiterhin eigenständige Proportionen.
