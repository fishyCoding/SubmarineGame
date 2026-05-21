public class EngineSound extends Sound {

    private static final float IDLE_STRENGTH = 5f;

    //Strength of engine increase per unit of speed
    private static final float SPEED_GAIN = 10f;

    private final Submarine sub;

    public EngineSound(Submarine sub) {
        super(sub.getX(), sub.getY(), IDLE_STRENGTH, sub.getId() + "_engine");
        this.sub = sub;
    }

    @Override
    public void tick() {
        x = sub.getX();
        y = sub.getY();
        strength = IDLE_STRENGTH + sub.getSpeed()*SPEED_GAIN;
    }

    @Override
    public boolean isDead() {
        return false;
    }

}