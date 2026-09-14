package de.extraitems;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlantRecordTest {
    @Test void restartRoundTripIncludingNegativeChunkCoordinates() {
        var p = new PlantRecord(-17,-32,-1,"tomato",2,179);
        assertEquals(p,PlantRecord.decode(p.encode(),-2,-1,-64,320));
    }
    @Test void storedEntryCannotWriteIntoAnotherChunk() {
        assertThrows(IllegalArgumentException.class,()->PlantRecord.decode("32,80,0;tomato;0;0",0,0,-64,320));
    }
    @Test void malformedAndOutOfWorldEntriesAreRejected() {
        for (String line : new String[]{"0,320,0;tomato;0;0","0,-64,0;tomato;0;0","0,80,0;tomato;-1;0","0,80,0;../bad;0;0","broken"})
            assertThrows(IllegalArgumentException.class,()->PlantRecord.decode(line,0,0,-64,320));
    }
    @Test void darknessOrDrySoilPausesGrowth() {
        var p = new PlantRecord(0,80,0,"tomato",1,179);
        assertEquals(p,p.tick(180,3,false)); assertEquals(2,p.tick(180,3,true).stage());
        assertEquals(0,p.tick(180,3,true).progress());
    }
    @Test void ripePlantsDoNotAdvancePastTheirLastModel() {
        var p = new PlantRecord(0,80,0,"tomato",3,0);
        assertEquals(p,p.tick(180,3,true));
        assertEquals(0,p.stage(1).progress()); assertEquals(1,p.stage(1).stage());
    }
}
