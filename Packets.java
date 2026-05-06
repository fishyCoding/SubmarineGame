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

    // Packets
    //For gameplay updates
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

    public static class SoundEvent {
        public String playerId;
        public float  x;
        public float  y;
        public float  strength;
        // ie "radar" "engine" "background"
        public String type;
    }

    public static class RadarPing {
        public String playerId;
        public float  x;
        public float  y;
    }

    public static class TorpedoState {
        public String  playerId;
        public float x;
        public float   y;
        public float   angle;
        public boolean alive;
    }

    public static class TorpedoDetonate {
        public String playerId;    // who fired it
        public float  x;           // blast world X
        public float  y;           // blast world Y
        public float  blastRadius;
        public int    damage;
    }
}