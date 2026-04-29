/**
 * Packets — all network message POJOs in one place.
 *
 * Rules for Kryonet packets:
 *   - Must be public static classes (or top-level)
 *   - Must have a public no-arg constructor
 *   - Fields must be public (Kryo reads/writes them directly)
 *
 * Kryonet will serialize/deserialize these automatically —
 * no manual byte packing needed.
 */
public class Packets {

    // ── Sent by client on connect ──────────────────────────────────────────────
    /** First thing a client sends after connecting. */
    public static class JoinRequest {
        public String playerId;   // chosen display name
    }

    // ── Sent by server in response to JoinRequest ──────────────────────────────
    /** Server confirms join and assigns a session ID. */
    public static class JoinResponse {
        public String assignedId;   // server-assigned unique id (e.g. "player_3")
        public float  spawnX;
        public float  spawnY;
    }

    // ── Sent by server when any player leaves ─────────────────────────────────
    public static class PlayerLeft {
        public String playerId;
    }

    // ── Position update — UDP, ~10 hz ─────────────────────────────────────────
    /**
     * Sent by each client every 6 ticks with their submarine's current state.
     * Server broadcasts this to all OTHER clients.
     */
    public static class SubmarineState {
        public String playerId;
        public float  x;
        public float  y;
        public float  vx;
        public float  vy;
        public float  angle;
        public float  rudderAngle;
        public int    health;
    }

    // ── Sound events — TCP (reliable) ─────────────────────────────────────────
    /**
     * Sent when a player spawns any sound (radar ping, engine burst, test sound).
     * Server broadcasts to all OTHER clients so their sonar reacts.
     */
    public static class SoundEvent {
        public String playerId;
        public float  x;
        public float  y;
        public float  strength;
        /** "radar", "engine", "background", "test" */
        public String type;
    }

    // ── Radar ping — TCP ──────────────────────────────────────────────────────
    /**
     * Sent when a player fires a radar ping.
     * Separate from SoundEvent so clients know to start the visual ping animation.
     */
    public static class RadarPing {
        public String playerId;
        public float  x;
        public float  y;
    }
}