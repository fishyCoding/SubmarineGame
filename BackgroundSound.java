/**
 * BackgroundSound — ambient ocean static that is always present.
 *
 * Simulates the constant low-frequency noise of the ocean itself:
 * distant currents, thermal layers, biological activity. It has no
 * world position that matters (it's always co-located with the listener)
 * so distance falloff never reduces it.
 *
 * Add one instance to the sound list at startup and never remove it.
 */
public class BackgroundSound extends Sound {

    private static final float AMBIENT_STRENGTH = 70f;


    public BackgroundSound(Submarine listener) {
        super(listener.getX(), listener.getY(), AMBIENT_STRENGTH, "ambient_ocean");
    }

    @Override
    public void tick() {

        strength = AMBIENT_STRENGTH;
    }

    @Override
    public boolean isDead() { return false; }

    @Override
    public String toString() {
        return String.format("BackgroundSound[str=%.1f]", AMBIENT_STRENGTH);
    }
}