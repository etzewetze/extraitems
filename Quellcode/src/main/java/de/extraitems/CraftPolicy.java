package de.extraitems;

import java.util.*;

/** Ingredient identities are per occupied crafting slot, not stack amounts. */
final class CraftPolicy {
    private CraftPolicy() {}
    static boolean mayCraft(boolean packLoaded, boolean hasPermission, List<String> expected, List<String> actual) {
        return packLoaded && hasPermission && ingredientsMatch(expected, actual);
    }
    static boolean ingredientsMatch(List<String> expected, List<String> actual) {
        return counts(expected).equals(counts(actual));
    }
    private static Map<String, Integer> counts(List<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        for (String value : values) if (value != null) counts.merge(value, 1, Integer::sum);
        return counts;
    }
}
