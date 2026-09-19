package de.extraitems;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineCycleTest {
    @Test void startsWithoutConsumingAValidInput() {
        assertEquals(MachineCycle.Decision.START, MachineCycle.decide(0, 1000, true, true));
    }

    @Test void removingTheInputCancelsAnActiveProcess() {
        assertEquals(MachineCycle.Decision.CANCEL, MachineCycle.decide(2000, 1500, false, true));
        assertEquals(MachineCycle.Decision.CANCEL, MachineCycle.decide(1000, 1500, false, true));
    }

    @Test void completionWaitsForOutputCapacity() {
        assertEquals(MachineCycle.Decision.WAITING_OUTPUT, MachineCycle.decide(1000, 1500, true, false));
        assertEquals(MachineCycle.Decision.COMPLETE, MachineCycle.decide(1000, 1500, true, true));
    }
}
