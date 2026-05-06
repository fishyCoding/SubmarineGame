// All network message POJOs.
// Kryonet rules: public static classes, public no-arg constructor, public fields.
public class Packets {

    // sent by client on connect
    public static class JoinRequest {
        public String playerId; // chosen display name
    }

    // server confirms join and assigns a session ID
    public static class JoinResponse {
        public String assignedId; // e.g. "player_3"
        public float spawnX;
        public float spawnY;
    }

    // sent by server when any player leaves
    public static class PlayerLeft {
        public String playerId;
    }

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
        public String type; // "radar", "engine", "background"
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