package de.extraitems;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CapybaraDefinitionsTest {
    private static final Path ENTITY = Path.of("src/main/resources/items/capybara/entity.yml");

    @Test void capybarasSpawnOnlyInAllThreeBadlandsVariants() throws Exception {
        String yaml = Files.readString(ENTITY);
        assertTrue(yaml.contains("minecraft:badlands"));
        assertTrue(yaml.contains("minecraft:wooded_badlands"));
        assertTrue(yaml.contains("minecraft:eroded_badlands"));
        assertFalse(yaml.contains("minecraft:plains"));
    }

    @Test void sweetBerriesAndThreeAgeSpecificVariantsAreConfigured() throws Exception {
        String yaml = Files.readString(ENTITY);
        assertTrue(yaml.contains("breed-material: SWEET_BERRIES"));
        for (String variant : new String[]{"brown", "dark", "patched"}) {
            assertTrue(yaml.contains("extraitems:capybara_" + variant + "_adult"), variant);
            assertTrue(yaml.contains("extraitems:capybara_" + variant + "_baby"), variant);
        }
        assertTrue(yaml.contains("baby-growth-ticks: 24000"));
        assertTrue(yaml.contains("feed-growth-ticks: 2400"));
    }

    @Test void walkingAndSwimmingFramesAreConfigured() throws Exception {
        String yaml = Files.readString(ENTITY);
        assertTrue(yaml.contains("walk-model-suffixes:"));
        assertTrue(yaml.contains("- _walk_1"));
        assertTrue(yaml.contains("- _walk_2"));
        assertTrue(yaml.contains("swim-model-suffix: _swim"));
        assertTrue(yaml.contains("step-ticks: 4"));
    }

    @Test void entityDefinitionIsBundledAndIndexed() throws Exception {
        assertTrue(Files.readString(Path.of("src/main/resources/items.yml"))
                .contains("items/capybara/entity.yml"));
        assertTrue(Files.readString(Path.of("src/main/resources/definition-files.txt"))
                .contains("items/capybara/entity.yml"));
    }
}
