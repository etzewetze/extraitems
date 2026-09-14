package de.extraitems;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePackAssetsTest {
    private static final Path PACK = Path.of("src/main/resources/resourcepack");

    @Test
    void tomatoTextureIsSquarePowerOfTwoRgba() throws Exception {
        var image = ImageIO.read(PACK.resolve("assets/extraitems/textures/item/tomato.png").toFile());
        assertNotNull(image);
        assertEquals(image.getWidth(), image.getHeight());
        assertTrue(image.getWidth() >= 16 && image.getWidth() <= 4096);
        assertEquals(0, image.getWidth() & (image.getWidth() - 1));
        assertTrue(image.getColorModel().hasAlpha());
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
}
