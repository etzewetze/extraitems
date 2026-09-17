package de.extraitems;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KitchenDefinitionsTest {
    private static final Path ROOT = Path.of("src/main/resources/items");

    @Test void cheeseStationRecipeMatchesTheNineRequestedPositions() throws Exception {
        String recipe = Files.readString(ROOT.resolve("cheese_station/recipes/craft.yml"));
        assertTrue(recipe.contains("- ' B '") && recipe.contains("- 'PCP'") && recipe.contains("- 'S S'"));
        assertTrue(recipe.contains("P: 'minecraft:#planks'"));
        assertTrue(recipe.contains("C: minecraft:crafting_table"));
    }

    @Test void knifeRecipeUsesMiddleLeftStickMiddleIronAndBottomMiddleButton() throws Exception {
        String recipe = Files.readString(ROOT.resolve("knife/recipes/craft.yml"));
        assertTrue(recipe.contains("shape:\n  - '   '\n  - 'SI '\n  - ' B '\n"));
        assertTrue(recipe.contains("S: minecraft:stick"));
        assertTrue(recipe.contains("I: minecraft:iron_ingot"));
        assertTrue(recipe.contains("B: minecraft:stone_button"));
    }

    @Test void knifeUsesAxeDamageAtHighAttackSpeed() throws Exception {
        String tool = Files.readString(ROOT.resolve("knife/tool.yml"));
        assertTrue(tool.contains("attack-damage: 9.0"));
        assertTrue(tool.contains("attack-speed: 2.4"));
    }

    @Test void seedGeneratorDriesAllThreeVegetables() throws Exception {
        String generator = Files.readString(ROOT.resolve("seed_generator/generator.yml"));
        assertTrue(generator.contains("process-seconds: 30"));
        for (String vegetable : new String[]{"tomato", "lettuce", "onion"}) {
            assertTrue(generator.contains("  " + vegetable + ":"), vegetable);
            assertTrue(generator.contains("output: " + vegetable + "_seeds"), vegetable);
        }
        assertFalse(Files.exists(ROOT.resolve("tomato_seeds/recipes/from_tomato.yml")));
    }

    @Test void foodValuesUseHalfDrumstickUnits() throws Exception {
        assertNutrition("tomato/item.yml", 3);
        assertNutrition("lettuce/item.yml", 3);
        assertNutrition("onion/item.yml", 3);
        assertNutrition("burger_bun/item.yml", 1);
        assertNutrition("schlemmer_burger/item.yml", 18);
        assertNutrition("cheesy_schlemmer/item.yml", 20);
    }

    @Test void cheesyRecipeContainsExactlyTheRequestedFiveIngredients() throws Exception {
        String recipe = Files.readString(ROOT.resolve("cheesy_schlemmer/recipes/craft.yml"));
        for (String ingredient : new String[]{"extraitems:burger_bun", "extraitems:cheese_slice",
                "extraitems:tomato", "extraitems:lettuce", "minecraft:cooked_beef"}) {
            assertTrue(recipe.contains("- " + ingredient), ingredient);
        }
        assertFalse(recipe.contains("extraitems:onion"));
    }

    private static void assertNutrition(String relative, int nutrition) throws Exception {
        String item = Files.readString(ROOT.resolve(relative));
        assertTrue(item.contains("nutrition: " + nutrition), relative);
    }
}
