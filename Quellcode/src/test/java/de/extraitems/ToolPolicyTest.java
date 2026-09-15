package de.extraitems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolPolicyTest {
    @Test void knifeGetsExactlyOneExtraStackPerUnbreakingLevel() {
        assertEquals(192, ToolPolicy.maxUses(192, 64, 0));
        assertEquals(256, ToolPolicy.maxUses(192, 64, 1));
        assertEquals(320, ToolPolicy.maxUses(192, 64, 2));
        assertEquals(384, ToolPolicy.maxUses(192, 64, 3));
    }

    @Test void everyCutConsumesExactlyOneUse() {
        assertEquals(192, ToolPolicy.craftsAvailable(192, 0, 1, false));
        assertEquals(1, ToolPolicy.craftsAvailable(192, 191, 1, false));
        assertEquals(0, ToolPolicy.craftsAvailable(192, 192, 1, false));
        assertEquals(Integer.MAX_VALUE, ToolPolicy.craftsAvailable(192, 191, 1, true));
    }
}
