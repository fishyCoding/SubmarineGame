public class TorpedoSound extends Sound {
    
    public TorpedoSound(float x, float y, float strength, String owner) {
        super(x, y, strength, owner);
        DECAY_RATE = 0.03f; 
    }


}
