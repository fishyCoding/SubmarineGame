public class RadarSound extends Sound {
    
    public RadarSound(float x, float y, float strength, String owner) {
        super(x, y, strength, owner);
        DECAY_RATE = 0.05f; 
    }


}
