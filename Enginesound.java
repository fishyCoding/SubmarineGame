/**
 * EngineSound — a continuous sound emitter that tracks a moving object.
 *
 * Unlike a one-shot Sound (which decays and dies), EngineSound never decays.
 * Instead the game loop calls tick() every frame, and we keep refreshing
 * the position and holding strength constant at the set level.
 *
 * The engine sound is louder when the submarine is moving fast and drops to
 * a low idle hum when stationary.
 */
public class EngineSound extends Sound {

    // ── Tuning ─────────────────────────────────────────────────────────────────
    /** Minimum hum even when dead still (machinery noise, reactor hum). */
    private static final float IDLE_STRENGTH  = 5f;

    /** Extra strength added per unit of speed (so faster = louder). */
    private static final float SPEED_GAIN     = 10f;

    /** Reference to the submarine so we can read its position and speed. */
    private final Submarine sub;

    // ── Constructor ────────────────────────────────────────────────────────────

    public EngineSound(Submarine sub) {
        // Initial position and strength — will be updated every tick
        super(sub.getX(), sub.getY(),
              IDLE_STRENGTH,
              sub.getId() + "_engine");
        this.sub = sub;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Update position and strength to match the submarine's current state.
     * We deliberately do NOT call super.tick() so strength never decays.
     */
    @Override
    public void tick() {
        x        = sub.getX();
        y        = sub.getY();
        strength = IDLE_STRENGTH + sub.getSpeed() * SPEED_GAIN;
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