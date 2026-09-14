# Texturen und Modelle

`src/main/resources/resourcepack/assets/extraitems/textures/item/tomato.png` ist die finale Tomatentextur mit echtem Alphakanal. Sie wurde mit dem eingebauten Imagegen-Werkzeug erstellt. Das Original bleibt unverändert im Paket. Es ist eine hochauflösende PNG mit grober Pixeloptik, keine handgezeichnete 16×16-Datei. Bei hohen Mipmap-Einstellungen kann Minecraft die Mipmap-Stufe für diesen Atlas reduzieren.

Verwendeter Erzeugungsprompt:

> Use case: stylized-concept. Asset type: Minecraft Java resource pack item texture, project ExtraItems. Generate ONE square PNG texture with genuinely transparent alpha background, showing a single ripe red tomato with a small green five-point leafy calyx on top, centered and filling 80% of canvas. Classic Minecraft 16x16 pixel-art aesthetic, very coarse square pixels, hard stepped edges, limited reds and greens, one light red highlight on upper left. Front/three-quarter view, recognizable in a tiny inventory slot. No text, no labels, no border, no checkerboard drawn into the image, no drop shadow, no blur, no extra objects. This is the actual in-game item sprite, not a mockup or a presentation. Return the resulting local PNG file path as it is needed for embedding in the plugin resource pack.

Eine spätere Exportvariante wurde wegen fehlender Transparenz verworfen und ist nicht enthalten.

Die vier Pflanzenmodelle sind native Minecraft-JSON-Modelle. `tools/build_models.py` erzeugt ihre Stängel, Blätter und Früchte aus Quadern. Sie können in Blockbench als Java-Block-/Item-Modell geöffnet werden; `.obj`- oder `.fbx`-Dateien und Clientmods sind nicht nötig. Minecraft liefert die referenzierten Vanilla-Texturen selbst; diese werden nicht als kopierte Dateien mitgeliefert. Das Samensymbol verwendet zunächst die Vanilla-Weizensamentextur, mit eigener Item-ID und eigenem Namen.
