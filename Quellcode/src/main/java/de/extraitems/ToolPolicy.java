package de.extraitems;

/** Pure durability rules, kept separate so exact-use behaviour is testable. */
final class ToolPolicy {
    private ToolPolicy() {}

    static int maxUses(int baseUses, int bonusPerLevel, int unbreakingLevel) {
        if (baseUses < 1 || bonusPerLevel < 0 || unbreakingLevel < 0) {
            throw new IllegalArgumentException("Ungültige Werkzeughaltbarkeit");
        }
        return Math.addExact(baseUses, Math.multiplyExact(bonusPerLevel, unbreakingLevel));
    }

    static int craftsAvailable(int maxUses, int damage, int damagePerCraft, boolean unbreakable) {
        if (unbreakable) return Integer.MAX_VALUE;
        if (maxUses < 1 || damage < 0 || damagePerCraft < 1) return 0;
        return Math.max(0, (maxUses - damage) / damagePerCraft);
    }
}
