import java.awt.Color;

/**
 * Character — the base class for any living entity in the game world.
 *
 * Intentionally minimal: it only captures what every moving entity needs —
 * a position, velocity, rotation, and a visual representation.
 * Game-specific concerns (health, damage, weapons) belong in subclasses.
 *
 * Extend this for players, enemies, NPCs, projectiles, etc.
 */
public abstract class Character extends Sprite {

    // ── Physics state ──────────────────────────────────────────────────────────
    protected float vx;       // velocity x  (world units / tick)
    protected float vy;       // velocity y  (world units / tick)
    protected float angle;    // heading in degrees  (0 = right, 90 = up)

    // ── Visual ─────────────────────────────────────────────────────────────────
    /** Path to the image file used to render this character, or null for shape-only. */
    protected String imagePath;

    /** Display half-width and half-height in screen pixels (used for image scaling). */
    protected float imageHalfW;
    protected float imageHalfH;

    // ── Identity ───────────────────────────────────────────────────────────────
    protected String id;      // unique identifier (UUID, player name, etc.)

    // ── Collision ──────────────────────────────────────────────────────────────
    protected float collisionRadius;  // circle hitbox radius in world units

    // ── Constructor ────────────────────────────────────────────────────────────

    /**
     * @param id               unique identifier for this entity
     * @param x                initial world X
     * @param y                initial world Y
     * @param collisionRadius  circle hitbox radius in world units
     * @param imagePath        path to sprite image, or null
     * @param imageHalfW       half-width  for rendering (world units)
     * @param imageHalfH       half-height for rendering (world units)
     */
    public Character(String id,
                     float x, float y,
                     float collisionRadius,
                     String imagePath,
                     float imageHalfW, float imageHalfH) {
        super(x, y, Color.WHITE);
        this.id              = id;
        this.collisionRadius = collisionRadius;
        this.imagePath       = imagePath;
        this.imageHalfW      = imageHalfW;
        this.imageHalfH      = imageHalfH;
        this.vx              = 0;
        this.vy              = 0;
        this.angle           = 0;
    }

    // ── Physics ────────────────────────────────────────────────────────────────

    /**
     * Advance this character's position by one tick.
     * Override to add acceleration, gravity, steering, etc.
     */
    public void update() {
        x += vx;
        y += vy;
    }

    /**
     * Apply simple linear drag so the character doesn't drift forever.
     * @param dragCoefficient  fraction of velocity lost per tick (0 = no drag, 1 = instant stop)
     */
    public void applyDrag(float dragCoefficient) {
        vx *= (1f - dragCoefficient);
        vy *= (1f - dragCoefficient);
    }

    /** Instantly set velocity and update heading to match. */
    public void setVelocity(float vx, float vy) {
        this.vx = vx;
        this.vy = vy;
        if (Math.abs(vx) > 0.01f || Math.abs(vy) > 0.01f)
            this.angle = (float) Math.toDegrees(Math.atan2(vy, vx));
    }

    /** Add a velocity impulse in the character's current heading direction. */
    public void thrust(float magnitude) {
        double rad = Math.toRadians(angle);
        vx += (float) (Math.cos(rad) * magnitude);
        vy += (float) (Math.sin(rad) * magnitude);
    }

    /** Rotate heading by {@code degrees} (positive = counter-clockwise). */
    public void rotate(float degrees) {
        angle = (angle + degrees) % 360;
    }

    // ── Collision ──────────────────────────────────────────────────────────────

    /** Circle hitbox: true when the given world point is within collisionRadius. */
    @Override
    public boolean contains(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= collisionRadius * collisionRadius;
    }

    /** Circle-vs-circle overlap check against another Character. */
    public boolean overlaps(Character other) {
        float dx  = other.x - x, dy = other.y - y;
        float sum = collisionRadius + other.collisionRadius;
        return dx * dx + dy * dy < sum * sum;
    }

    /** Circle-vs-Rock (AABB pre-check, then precise point-in-polygon). */
    public boolean collidesWithRock(Rock rock) {
        float[] bounds = rock.getBounds();
        if (x + collisionRadius < bounds[0] || x - collisionRadius > bounds[1]) return false;
        if (y + collisionRadius < bounds[2] || y - collisionRadius > bounds[3]) return false;
        return rock.contains(x, y);
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    /**
     * Default rendering: draw the sprite image (if set) centred on the
     * character's world position, rotated to match the heading angle.
     * Subclasses may override for custom visuals.
     */
    @Override
    public void draw(GameEngine engine) {
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y);

        if (imagePath != null) {
            StdDraw.picture(sx, sy, imagePath, imageHalfW * 2, imageHalfH * 2, -angle);
        } else {
            // Fallback: draw a coloured circle
            StdDraw.setPenColor(color);
            StdDraw.filledCircle(sx, sy, collisionRadius);
        }
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    @Override
    public String serialize() {
        return String.format("CHARACTER %s %.2f %.2f %.2f %.2f %.2f",
                id, x, y, vx, vy, angle);
    }

    // ── Getters / setters ──────────────────────────────────────────────────────

    public String getId()              { return id; }
    public float  getVx()              { return vx; }
    public float  getVy()              { return vy; }
    public float  getAngle()           { return angle; }
    public float  getSpeed()           { return (float) Math.hypot(vx, vy); }
    public float  getCollisionRadius() { return collisionRadius; }
    public String getImagePath()       { return imagePath; }

    public void setAngle(float angle)  { this.angle = angle % 360; }
    public void setImagePath(String p) { this.imagePath = p; }

    @Override
    public String getType() { return "CHARACTER"; }

    @Override
    public String toString() {
        return String.format("Character[%s pos=(%.1f,%.1f) vel=(%.2f,%.2f) angle=%.1f]",
                id, x, y, vx, vy, angle);
    }
}