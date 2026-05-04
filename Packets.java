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

    // ── Torpedo position — UDP (~every tick while in flight) ──────────────────
    /**
     * Broadcast by the firing client each tick so others can render the torpedo.
     * alive=false means the torpedo just died (detonated or hit something) and
     * receivers should remove their visual copy.
     */
    public static class TorpedoState {
        public String  playerId;  // who fired it
        public float   x;
        public float   y;
        public float   angle;
        public boolean alive;
    }

    // ── Torpedo detonation — TCP (reliable) ───────────────────────────────────
    /**
     * Sent once when the torpedo explodes. Receivers check if they are within
     * blastRadius and apply damage if so.
     */
    public static class TorpedoDetonate {
        public String playerId;    // who fired it
        public float  x;           // blast world X
        public float  y;           // blast world Y
        public float  blastRadius;
        public int    damage;
    }
}