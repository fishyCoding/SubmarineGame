/**
 * StaticSound — a fixed-position sound source that never moves or decays.
 * Useful for testing: place one in the world and swim toward/away from it
 * to verify the passive sonar reacts to distance.
 */
public class StaticSound extends Sound {

    private final float baseStrength;

    public StaticSound(float x, float y, float strength, String owner) {
        super(x, y, strength, owner);
        this.baseStrength = strength;
    }

    /** Hold strength constant — no decay. */
    @Override
    public void tick() {
        strength = baseStrength;  // reset each tick, never decays
    }

    /** Never dies. */
    @Override
    public boolean isDead() { return false; }

    @Override
    public String toString() {
        return String.format("StaticSound[owner=%s pos=(%.0f,%.0f) str=%.1f]",
                owner, x, y, baseStrength);
    }
}