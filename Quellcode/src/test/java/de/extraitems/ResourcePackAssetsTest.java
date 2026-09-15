package de.extraitems;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackAssetsTest {
    private static final Path PACK = Path.of("src/main/resources/resourcepack");

    @Test
    void customTexturesAreSquarePowerOfTwoRgba() throws Exception {
        for (String name : new String[]{"tomato", "tomato_seeds", "lettuce_seeds", "onion_seeds"}) {
            var image = ImageIO.read(PACK.resolve("assets/extraitems/textures/item/" + name + ".png").toFile());
            assertNotNull(image, name);
            assertEquals(image.getWidth(), image.getHeight(), name);
            assertTrue(image.getWidth() >= 16 && image.getWidth() <= 4096, name);
            assertEquals(0, image.getWidth() & (image.getWidth() - 1), name);
            assertTrue(image.getColorModel().hasAlpha(), name);
        }
    }

    @Test
    void ripeCropDoesNotDependOnTheCustomItemTexture() throws Exception {
        String model = Files.readString(PACK.resolve("assets/extraitems/models/block/tomato_stage_3.json"));
        assertTrue(model.contains("minecraft:block/red_concrete"));
        assertFalse(model.contains("extraitems:item/tomato"));
    }

    @Test
    void tomatoItemHasCuboidsAndDroppedItemTransform() throws Exception {
        String model = Files.readString(PACK.resolve("assets/extraitems/models/item/tomato.json"));
        assertTrue(model.contains("\"elements\""));
        assertTrue(model.contains("\"ground\""));
        assertTrue(model.contains("minecraft:block/red_concrete"));
    }

    @Test
    void allNewItemsHaveCuboidsAndDroppedItemTransform() throws Exception {
        for (String name : new String[]{"lettuce", "onion", "knife", "burger_bun",
                "schlemmer_burger", "cheese_slice", "cheese_station"}) {
            String model = Files.readString(PACK.resolve("assets/extraitems/models/item/" + name + ".json"));
            assertTrue(model.contains("\"elements\""), name);
            assertTrue(model.contains("\"ground\""), name);
        }
    }

    @Test
    void threeCropsAndTenCheesePortionsArePresent() {
        for (String crop : new String[]{"tomato", "lettuce", "onion"}) {
            for (int stage = 0; stage < 4; stage++) {
                assertTrue(Files.isRegularFile(PACK.resolve("assets/extraitems/models/block/" + crop + "_stage_" + stage + ".json")));
            }
        }
        for (int bites = 0; bites < 10; bites++) {
            assertTrue(Files.isRegularFile(PACK.resolve("assets/extraitems/models/block/cheese_wheel_" + bites + ".json")));
        }
    }
}
