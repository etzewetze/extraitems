# Optionale Item-Integrationen

Es gibt zwei getrennte Arten der Integration:

1. **Laufzeit-Provider:** Ein Item bleibt Eigentum von Nexo, ItemsAdder, Oraxen oder CraftEngine. Das jeweilige Plugin muss installiert sein; ExtraItems kann dessen Item in eigenen Rezepten verwenden.
2. **Nexo-Maker-Import:** Eine für CraftEngine exportierte ZIP wird beim Serverstart in ein natives ExtraItems-Item und Ressourcenpaket-Assets umgewandelt. Dafür muss CraftEngine nicht installiert sein.

## Laufzeit-Provider

ExtraItems benötigt keines dieser Plugins zum Starten. In Rezeptdateien dürfen externe
Item-IDs als Zutaten oder Ergebnis verwendet werden. Die Syntax ist immer
`provider:id`; zusätzliche Doppelpunkte in der Provider-ID bleiben erhalten.

```yaml
type: recipe
id: cheesy_external
result: itemsadder:food:cheesy_burger
ingredients:
  - nexo:food:tomato
  - itemsadder:food:lettuce
  - oraxen:ingredients:onion
  - craftengine:food:steak
permission: extraitems.craft.external
```

Unterstützte Provider und die verwendeten Laufzeit-APIs:

- [Nexo API](https://docs.nexomc.com/community-guides/api): `NexoItems.itemFromId(id).build()` und `NexoItems.idFromItem(stack)`.
- [ItemsAdder API](https://wiki.itemsadder.com/developers/java-api/examples/): `CustomStack.getInstance(id).getItemStack()` und `CustomStack.byItemStack(stack)`.
- [Oraxen API](https://github.com/oraxen/oraxen/blob/master/src/main/java/io/th0rgal/oraxen/api/OraxenItems.java): `OraxenItems.getItemById(id).build()` und `getIdByItem(stack)`.
- [CraftEngine API](https://github.com/Xiao-MoMi/craft-engine/blob/main/bukkit/src/main/java/net/momirealms/craftengine/bukkit/api/CraftEngineItems.java): `CraftEngineItems.byId(id).buildBukkitItem()` und `getCustomItemId(stack)`.

Die Provider werden zur Laufzeit optional reflektiv angesprochen. Fehlt ein Provider,
wird das Plugin trotzdem gestartet; betroffene Rezepte werden übersprungen und nach dem
Plugin-Enable automatisch erneut registriert. `/ei status` zeigt den Zustand.

Jedes Provider-Plugin muss sein eigenes Ressourcenpaket liefern. ExtraItems kann die
ItemStack-Identität für Rezepte prüfen, aber keine Fremd-Packs in sein eigenes ZIP kopieren.

## Nexo-Maker-Export für CraftEngine importieren

Die unveränderte ZIP oder der entpackte Ordner wird unter `plugins/ExtraItems/` abgelegt und als relativer Pfad in `items.yml` registriert:

```yaml
schema-version: 2
sources:
  - items/tomato/item.yml
  - imports/mein_craftengine_export.zip
```

Das ist keine Laufzeitverbindung zu CraftEngine. Normale Einträge aus dem Abschnitt `items:` werden zu ExtraItems-Items; Modellgenerierung und `resourcepack/assets/` landen automatisch im ExtraItems-Ressourcenpaket. Kategorien, Rezepte und anbieterspezifische Mechaniken werden bewusst nicht erraten. Die vollständigen Importregeln stehen in [DEFINITIONEN.md](DEFINITIONEN.md).
