package de.extraitems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CraftEngineBundleImporterTest {
    @TempDir Path directory;

    @Test
    void importsMakerCraftEngineZipAsNativeItemAndPackOverlay() throws Exception {
        Path archive = directory.resolve("imports/maker-export.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("CraftEngine/resources/demo/configuration/items.yml", configuration().getBytes(StandardCharsets.UTF_8));
        entries.put("CraftEngine/resources/demo/resourcepack/assets/demo/textures/item/widget.png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        writeZip(archive, entries);

        List<DefinitionFiles.Definition> definitions;
        try (CraftEngineBundleImporter importer = CraftEngineBundleImporter.open(directory)) {
            definitions = importer.importSource(archive, "imports/maker-export.zip");
            importer.commit();
        }

        assertEquals(1, definitions.size());
        DefinitionFiles.Definition item = definitions.getFirst();
        assertEquals("item", item.type());
        assertEquals("demo_widget", item.id());
        assertEquals("PAPER", item.config().getString("material"));
        assertEquals("Demo Widget", item.config().getString("name"));
        assertEquals("demo:widget", item.config().getString("model"));

        Path overlay = directory.resolve(CraftEngineBundleImporter.OVERLAY);
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'},
                Files.readAllBytes(overlay.resolve("assets/demo/textures/item/widget.png")));
        String geometry = Files.readString(overlay.resolve("assets/demo/models/item/widget.json"));
        assertTrue(geometry.contains("\"parent\": \"minecraft:item/generated\""));
        assertTrue(geometry.contains("\"layer0\": \"demo:item/widget\""));
        String itemModel = Files.readString(overlay.resolve("assets/demo/items/widget.json"));
        assertTrue(itemModel.contains("\"model\": \"demo:item/widget\""));
    }

    @Test
    void importsAnUnpackedBundleFolderAndIgnoresCategories() throws Exception {
        Path bundle = directory.resolve("imports/unpacked");
        Path config = bundle.resolve("CraftEngine/resources/demo/configuration/items.yml");
        Path texture = bundle.resolve("CraftEngine/resources/demo/resourcepack/assets/demo/textures/item/widget.png");
        Files.createDirectories(config.getParent());
        Files.createDirectories(texture.getParent());
        Files.writeString(config, configuration() + "\ncategories:\n  demo:all:\n    name: Demo\n");
        Files.write(texture, new byte[]{1, 2, 3});

        try (CraftEngineBundleImporter importer = CraftEngineBundleImporter.open(directory)) {
            assertEquals(1, importer.importSource(bundle, "imports/unpacked").size());
            importer.commit();
        }
        assertTrue(Files.isRegularFile(directory.resolve(CraftEngineBundleImporter.OVERLAY)
                .resolve("assets/demo/items/widget.json")));
    }

    @Test
    void rejectsZipTraversalBeforeWritingAnythingOutsideTheOverlay() throws Exception {
        Path archive = directory.resolve("imports/unsafe.zip");
        writeZip(archive, Map.of("../escape.yml", "bad".getBytes(StandardCharsets.UTF_8)));

        try (CraftEngineBundleImporter importer = CraftEngineBundleImporter.open(directory)) {
            assertThrows(IllegalArgumentException.class,
                    () -> importer.importSource(archive, "imports/unsafe.zip"));
        }
        assertFalse(Files.exists(directory.resolve("escape.yml")));
    }

    @Test
    void rejectsDifferentAssetsWithTheSameTargetPath() throws Exception {
        Path first = directory.resolve("imports/first.zip");
        Path second = directory.resolve("imports/second.zip");
        Map<String, byte[]> one = bundleEntries("one".getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> two = bundleEntries("two".getBytes(StandardCharsets.UTF_8));
        writeZip(first, one);
        writeZip(second, two);

        try (CraftEngineBundleImporter importer = CraftEngineBundleImporter.open(directory)) {
            importer.importSource(first, "imports/first.zip");
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> importer.importSource(second, "imports/second.zip"));
            assertTrue(error.getMessage().contains("Asset-Konflikt"));
        }
    }

    @Test
    void successfulEmptySessionRemovesStaleGeneratedImportAssets() throws Exception {
        Path stale = directory.resolve(CraftEngineBundleImporter.OVERLAY).resolve("assets/demo/stale.json");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "stale");

        try (CraftEngineBundleImporter importer = CraftEngineBundleImporter.open(directory)) {
            importer.commit();
        }
        assertFalse(Files.exists(stale));
        assertTrue(Files.isDirectory(directory.resolve(CraftEngineBundleImporter.OVERLAY)));
    }

    private static String configuration() {
        return """
                items:
                  demo:widget:
                    material: PAPER
                    custom_model_data: 1001
                    data:
                      item_name: Demo Widget
                    model:
                      type: minecraft:model
                      path: demo:item/widget
                      generation:
                        parent: minecraft:item/generated
                        textures:
                          layer0: demo:item/widget
                """;
    }

    private static Map<String, byte[]> bundleEntries(byte[] texture) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("CraftEngine/resources/demo/configuration/items.yml", configuration().getBytes(StandardCharsets.UTF_8));
        entries.put("CraftEngine/resources/demo/resourcepack/assets/demo/textures/item/widget.png", texture);
        return entries;
    }

    private static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry("CraftEngine/"));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }
}
