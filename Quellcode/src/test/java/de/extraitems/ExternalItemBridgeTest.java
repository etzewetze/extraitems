package de.extraitems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExternalItemBridgeTest {
    @Test void parsesAllSupportedProvidersAndNestedIds() {
        for (String token : new String[]{
                "nexo:food:tomato", "itemsadder:food:lettuce",
                "oraxen:ingredients:onion", "craftengine:food:steak"}) {
            ExternalItemBridge.Reference reference = ExternalItemBridge.parse(token);
            assertEquals(token, reference.token());
        }
    }

    @Test void normalizesProviderAndWhitespace() {
        ExternalItemBridge.Reference reference = ExternalItemBridge.parse("  ITEMSADDER:Food:Tomato  ");
        assertEquals("itemsadder:Food:Tomato", reference.token());
    }

    @Test void rejectsMalformedAndUnknownReferences() {
        for (String token : new String[]{null, "", "minecraft:bread", "nexo:", "unknown:item"}) {
            assertThrows(IllegalArgumentException.class, () -> ExternalItemBridge.parse(token));
        }
    }
}
