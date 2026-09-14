package de.extraitems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PackSessionTest {
    @Test void anotherPluginsPackCannotUnlockThePlayer() {
        var session = new PackSession(UUID.randomUUID());
        assertFalse(session.accept(UUID.randomUUID(),"SUCCESSFULLY_LOADED"));
        assertEquals(PackSession.State.WAITING,session.state());
    }
    @Test void acceptingOrDownloadingIsNotLoading() {
        var session = new PackSession(UUID.randomUUID());
        session.accept(session.id,"ACCEPTED"); session.accept(session.id,"DOWNLOADED");
        assertEquals(PackSession.State.WAITING,session.state()); assertTrue(session.timeout());
    }
    @Test void successUnlocksAndSurvivesOldTimeoutTask() {
        var session = new PackSession(UUID.randomUUID());
        assertTrue(session.accept(session.id,"SUCCESSFULLY_LOADED"));
        assertFalse(session.timeout()); assertEquals(PackSession.State.LOADED,session.state());
    }
    @ParameterizedTest @ValueSource(strings={"DECLINED","FAILED_DOWNLOAD","INVALID_URL","FAILED_RELOAD","DISCARDED","FUTURE_UNKNOWN_STATUS"})
    void errorsFailClosedAndCannotBeReversedByLateSuccess(String status) {
        var session = new PackSession(UUID.randomUUID());
        assertTrue(session.accept(session.id,status));
        assertFalse(session.accept(session.id,"SUCCESSFULLY_LOADED"));
        assertEquals(PackSession.State.FAILED,session.state());
    }
    @Test void discardingAnAlreadyLoadedPackRevokesAccess() {
        var session = new PackSession(UUID.randomUUID());
        session.accept(session.id,"SUCCESSFULLY_LOADED"); session.accept(session.id,"DISCARDED");
        assertEquals(PackSession.State.FAILED,session.state());
    }
}
