import java.util.ArrayList;
import java.util.List;

/**
 * Sound — a point source of acoustic energy in the world.
 *
 * Each sound has a world position, a strength (intensity at the source),
 * and an owner tag so the sonar can distinguish friendly from unknown contacts.
 *
 * Sounds decay each tick. When strength drops below DEAD_THRESHOLD they are
 * considered expired and should be removed from the sound list.
 *
 * The passive sonar computes what the player's sub can hear by summing
 * perceived intensity from all live sounds:
 *
 *   perceived = strength / (1 + distance² * FALLOFF)
 *
 * Subclasses override tick() to implement continuous emitters.
 */
public class Sound {

    // ── Constants ──────────────────────────────────────────────────────────────
    /** How fast a one-shot sound decays per tick (fraction of strength lost). */
    public static final float DECAY_RATE     = 0.015f;

    /** Sound is considered dead below this threshold. */
    public static final float DEAD_THRESHOLD = 0.5f;

    /**
     * Acoustic falloff: perceived = strength / (1 + dist² * FALLOFF).
     * Smaller = sounds carry farther.
     */
    public static final float FALLOFF        = 0.00005f;

    // ── Fields ─────────────────────────────────────────────────────────────────
    protected float  x;
    protected float  y;
    protected float  strength;   // current intensity at the source
    protected String owner;      // "player", "enemy", "environment", etc.

    // ── Constructor ────────────────────────────────────────────────────────────

    /**
     * @param x        world X of the sound source
     * @param y        world Y of the sound source
     * @param strength initial intensity (try 100–2000 for noticeable results)
     * @param owner    tag identifying who made this sound
     */
    public Sound(float x, float y, float strength, String owner) {
        this.x        = x;
        this.y        = y;
        this.strength = strength;
        this.owner    = owner;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Advance this sound by one tick.
     * Base implementation decays strength. Continuous subclasses override this.
     */
    public void tick() {
        strength *= (1f - DECAY_RATE);
    }

    /** True once the sound is too quiet to matter. */
    public boolean isDead() {
        return strength < DEAD_THRESHOLD;
    }

    // ── Perception ─────────────────────────────────────────────────────────────

    /**
     * How loud this sound is perceived at listener position (lx, ly).
     * Uses inverse-square-ish falloff so distant sounds are much quieter.
     */
    public float perceivedAt(float lx, float ly) {
        float dx   = x - lx;
        float dy   = y - ly;
        float dist2 = dx * dx + dy * dy;
        return strength / (1f + dist2 * FALLOFF);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public float  getX()        { return x; }
    public float  getY()        { return y; }
    public float  getStrength() { return strength; }
    public String getOwner()    { return owner; }

    // ── Static helpers ─────────────────────────────────────────────────────────

    /**
     * Compute total perceived intensity at (lx, ly) from a list of sounds.
     * Call this once per frame to feed the passive sonar display.
     */
    public static float totalPerceivedAt(List<Sound> sounds, float lx, float ly) {
        float total = 0f;
        for (Sound s : sounds) total += s.perceivedAt(lx, ly);
        return total;
    }

    /**
     * Remove all dead sounds from the list in-place.
     */
    public static void pruneDead(List<Sound> sounds) {
        sounds.removeIf(Sound::isDead);
    }

    @Override
    public String toString() {
        return String.format("Sound[owner=%s pos=(%.0f,%.0f) str=%.1f]",
                owner, x, y, strength);
    }
}