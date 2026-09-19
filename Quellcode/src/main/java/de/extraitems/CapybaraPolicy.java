package de.extraitems;

/** Deterministic rules shared by the live capybara service and unit tests. */
final class CapybaraPolicy {
    private CapybaraPolicy() {}

    static int fedBabyAge(int currentAge, int growthTicks) {
        if (currentAge >= 0) return currentAge;
        return Math.min(0, currentAge + Math.max(0, growthTicks));
    }

    static int inheritedVariant(int mother, int father, int variants, int roll) {
        if (variants < 1) throw new IllegalArgumentException("Mindestens eine Fellvariante erforderlich");
        int normalized = Math.floorMod(roll, 10);
        if (normalized < 4) return Math.floorMod(mother, variants);
        if (normalized < 8) return Math.floorMod(father, variants);
        return Math.floorMod(roll, variants);
    }

    static int groupSize(int minimum, int maximum, int available, int roll) {
        if (minimum < 1 || maximum < minimum || available < 0) {
            throw new IllegalArgumentException("Ungültige Gruppengrenzen");
        }
        if (available == 0) return 0;
        int wanted = minimum + Math.floorMod(roll, maximum - minimum + 1);
        return Math.min(wanted, available);
    }

    static int candidateAttempts(boolean loadedChunk) {
        return loadedChunk ? 8 : 12;
    }

    static boolean passesSpawnChance(double chance, double roll) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1
                || !Double.isFinite(roll) || roll < 0 || roll >= 1) {
            throw new IllegalArgumentException("Ungültiger Spawnwurf");
        }
        return roll < chance;
    }
}
