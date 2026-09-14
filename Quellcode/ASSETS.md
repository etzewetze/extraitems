# Texturen und Modelle

`src/main/resources/resourcepack/assets/extraitems/textures/item/tomato.png` ist eine 256×256-PNG mit echtem Alphakanal und grober Pixeloptik. Die Zweierpotenzgröße ist mipmap- und atlas-tauglich. Die frühere 1254×1254-Datei konnte beim Laden des Texturatlas die pink-schwarze Fehlerdarstellung auslösen.

Verwendeter Erzeugungsprompt:

> Use case: stylized-concept. Asset type: Minecraft Java resource pack item texture, project ExtraItems. Generate ONE square PNG texture with genuinely transparent alpha background, showing a single ripe red tomato with a small green five-point leafy calyx on top, centered and filling 80% of canvas. Classic Minecraft 16x16 pixel-art aesthetic, very coarse square pixels, hard stepped edges, limited reds and greens, one light red highlight on upper left. Front/three-quarter view, recognizable in a tiny inventory slot. No text, no labels, no border, no checkerboard drawn into the image, no drop shadow, no blur, no extra objects. This is the actual in-game item sprite, not a mockup or a presentation. Return the resulting local PNG file path as it is needed for embedding in the plugin resource pack.

Eine spätere Exportvariante wurde wegen fehlender Transparenz verworfen und ist nicht enthalten.

Die vier Pflanzenmodelle und die gedroppte Tomate sind native Minecraft-JSON-Quader-Modelle. Die reifen Früchte verwenden `minecraft:block/red_concrete`, damit die letzte Wachstumsstufe nicht von einer eigenen Atlastextur abhängig ist. `tools/build_models.py` erzeugt alle Geometrien reproduzierbar. Sie können in Blockbench als Java-Block-/Item-Modell geöffnet werden; `.obj`-, `.fbx`-Dateien und Clientmods sind nicht nötig. Das Samensymbol verwendet zunächst die Vanilla-Weizensamentextur, mit eigener Item-ID und eigenem Namen.
