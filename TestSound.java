public class TestSound extends Sound {
    
    public TestSound(float x, float y, float strength, String owner) {
        super(x, y, strength, owner);
        DECAY_RATE = 0.05f;
    }


}
