package de.extraitems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefinitionFilesTest {
    @TempDir Path directory;

    @Test
    void resolvesOnlyRegularYamlInsidePluginDirectory() throws Exception {
        Path file = directory.resolve("items/tomato/item.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "type: item\nid: tomato\n");

        assertEquals(file.toRealPath(), DefinitionFiles.resolve(directory.toAbsolutePath(), "items/tomato/item.yml").toRealPath());
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionFiles.resolve(directory.toAbsolutePath(), "../config.yml"));
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionFiles.resolve(directory.toAbsolutePath(), "items/tomato/item.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionFiles.resolve(directory.toAbsolutePath(), "items/missing.yml"));
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionFiles.resolve(directory.toAbsolutePath(), file.toAbsolutePath().toString()));
    }

    @Test
    void rejectsSymlinkDefinitions() throws Exception {
        Path real = directory.resolve("real.yml");
        Files.writeString(real, "type: item\nid: tomato\n");
        Path link = directory.resolve("linked.yml");
        Files.createSymbolicLink(link, real);
        assertThrows(IllegalArgumentException.class,
                () -> DefinitionFiles.resolve(directory.toAbsolutePath(), "linked.yml"));
    }

    @Test
    void updateAddsBundledSourcesWithoutRemovingOrDuplicatingCustomOnes() {
        var merged = DefinitionFiles.mergeSources(
                List.of("items/server_custom/item.yml", "items/tomato/item.yml"),
                List.of("items/tomato/item.yml", "items/lettuce/item.yml", "items/onion/item.yml"));
        assertEquals(List.of("items/server_custom/item.yml", "items/tomato/item.yml",
                "items/lettuce/item.yml", "items/onion/item.yml"), merged);
    }
}
