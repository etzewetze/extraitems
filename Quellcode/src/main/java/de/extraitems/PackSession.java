package de.extraitems;

import java.util.UUID;

/** Independent of Bukkit so UUID isolation, terminal states and timeout are testable. */
final class PackSession {
    enum State { WAITING, LOADED, FAILED }
    final UUID id;
    private State state = State.WAITING;
    PackSession(UUID id) { this.id = id; }
    State state() { return state; }
    boolean accept(UUID pack, String status) {
        if (!id.equals(pack) || state == State.FAILED) return false;
        switch (status) {
            case "ACCEPTED", "DOWNLOADED" -> { return false; }
            case "SUCCESSFULLY_LOADED" -> state = State.LOADED;
            default -> state = State.FAILED;
        }
        return true;
    }
    boolean timeout() {
        if (state != State.WAITING) return false;
        state = State.FAILED;
        return true;
    }
}
