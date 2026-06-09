public class Packets {

    // ── Client → Server ───────────────────────────────────────────────────────────

    /** Sent by client on connect, requesting an ID and spawn point. */
    public static class JoinRequest {
        public String playerId;
    }

    // ── Server → Client ───────────────────────────────────────────────────────────

    /** Server accepted the join — hands the client its assigned ID and spawn. */
    public static class JoinResponse {
        public String assignedId;
        public float spawnX;
        public float spawnY;
    }

    /**
     * Server rejected the join (e.g. lobby full).
     * Client should show reason and return to the menu.
     */
    public static class JoinRejected {
        public String reason;
    }

    /** Broadcast to remaining clients when a player disconnects. */
    public static class PlayerLeft {
        public String playerId;
    }

    // ── Gameplay sync ─────────────────────────────────────────────────────────────

    public static class SubmarineState {
        public String playerId;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float angle;
        public float rudderAngle;
        public int health;
    }

    public static class SoundEvent {
        public String playerId;
        public float x;
        public float y;
        public float strength;
        public String type;   // "radar", "engine", "torpedo_explosion", …
    }

    public static class RadarPing {
        public String playerId;
        public float x;
        public float y;
    }

    public static class TorpedoState {
        public String playerId;
        public float x;
        public float y;
        public float angle;
        public boolean alive;
    }

    public static class TorpedoDetonate {
        public String playerId;
        public float x;
        public float y;
        public float blastRadius;
        public int damage;
    }
}