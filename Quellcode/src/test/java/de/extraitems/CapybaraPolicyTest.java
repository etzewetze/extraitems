package de.extraitems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapybaraPolicyTest {
    @Test void sweetBerriesAdvanceBabiesWithoutCrossingPastAdult() {
        assertEquals(-21600, CapybaraPolicy.fedBabyAge(-24000, 2400));
        assertEquals(0, CapybaraPolicy.fedBabyAge(-1000, 2400));
        assertEquals(6000, CapybaraPolicy.fedBabyAge(6000, 2400));
    }

    @Test void offspringUsuallyInheritsEitherParentAndCanMutate() {
        assertEquals(0, CapybaraPolicy.inheritedVariant(0, 1, 3, 2));
        assertEquals(1, CapybaraPolicy.inheritedVariant(0, 1, 3, 6));
        assertEquals(2, CapybaraPolicy.inheritedVariant(0, 1, 3, 8));
    }

    @Test void naturalGroupsRespectAvailableCapacity() {
        assertEquals(4, CapybaraPolicy.groupSize(2, 4, 10, 2));
        assertEquals(1, CapybaraPolicy.groupSize(2, 4, 1, 2));
        assertEquals(0, CapybaraPolicy.groupSize(2, 4, 0, 2));
    }

    @Test void invalidGroupBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CapybaraPolicy.groupSize(0, 4, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> CapybaraPolicy.inheritedVariant(0, 0, 0, 1));
    }
}
