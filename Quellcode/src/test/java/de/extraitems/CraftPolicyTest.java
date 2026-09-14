package de.extraitems;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftPolicyTest {
    @Test void permissionIsCheckedAtEachCraftEvenWhenIngredientsMatch() {
        var recipe = List.of("extraitems:tomato");
        assertTrue(CraftPolicy.mayCraft(true,true,recipe,recipe));
        assertFalse(CraftPolicy.mayCraft(true,false,recipe,recipe));
        assertFalse(CraftPolicy.mayCraft(false,true,recipe,recipe));
    }
    @Test void vanillaAppleDoesNotMasqueradeAsTomato() {
        assertFalse(CraftPolicy.mayCraft(true,true,List.of("extraitems:tomato"),List.of("minecraft:apple")));
    }
    @Test void shapelessOrderAndEmptySlotsAreSupported() {
        assertTrue(CraftPolicy.mayCraft(true,true,List.of("minecraft:beetroot","minecraft:wheat_seeds"),Arrays.asList(null,"minecraft:wheat_seeds",null,"minecraft:beetroot")));
    }
    @Test void repeatedIngredientsMustOccupyTheRequiredNumberOfSlots() {
        var recipe = List.of("extraitems:tomato","extraitems:tomato");
        assertFalse(CraftPolicy.mayCraft(true,true,recipe,List.of("extraitems:tomato")));
        assertFalse(CraftPolicy.mayCraft(true,true,List.of("extraitems:tomato"),recipe));
    }
}
