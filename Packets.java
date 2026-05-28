public class Packets {

    // sent by client on connect
    public static class JoinRequest {
        public String playerId; 
    }

    public static class JoinResponse {
        public String assignedId; 
        public float spawnX;
        public float spawnY;
    }

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
        public String type; 
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