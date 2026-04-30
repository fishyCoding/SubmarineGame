public class EngineSound extends Sound {

    private static final float IDLE_STRENGTH  = 5f;

    private static final float SPEED_GAIN     = 10f;

    private final Submarine sub;

    // ── Constructor ────────────────────────────────────────────────────────────

    public EngineSound(Submarine sub) {
        // Initial position and strength — will be updated every tick
        super(sub.getX(), sub.getY(), IDLE_STRENGTH, sub.getId() + "_engine");
        this.sub = sub;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Update position and strength to match the submarine's current state.
     * We deliberately do NOT call super.tick() so strength never decays.
     */
    @Override
    public void tick() {
        x = sub.getX();
        y = sub.getY();
        strength = IDLE_STRENGTH + sub.getSpeed()*SPEED_GAIN;
    }

    /**
     * Engine sound never dies on its own — only removed when the sub is
     * destroyed or the game ends.
     */
    @Override
    public boolean isDead() {
        return false;
    }

    @Override
    public String toString() {
        return String.format("EngineSound[sub=%s pos=(%.0f,%.0f) str=%.1f]",
                sub.getId(), x, y, strength);
    }
}