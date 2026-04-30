import java.awt.Color;

/**
 * Character — base class for every living entity in the game world.
 *
 * Stores position, velocity, heading, and a circular hitbox.
 * Physics primitives that belong to ALL characters (drag, integration)
 * live here. Movement mechanics that are specific to a vehicle or creature
 * (thrust direction, turning rate, buoyancy) belong in subclasses.
 */
public abstract class Character extends Sprite {

    // ── Physics state ──────────────────────────────────────────────────────────
    protected float vx;       // velocity x  (world units / tick)
    protected float vy;       // velocity y  (world units / tick)
    protected float angle;    // heading in degrees  (0 = right, 90 = up)

    // ── Visual ─────────────────────────────────────────────────────────────────
    protected String imagePath;
    protected float  imageHalfW;
    protected float  imageHalfH;

    // ── Identity ───────────────────────────────────────────────────────────────
    protected String id;

    // ── Collision ──────────────────────────────────────────────────────────────
    protected float collisionRadius;


    public Character(String id,float x, float y,float collisionRadius,String imagePath,float imageHalfW, float imageHalfH) {
        super(x, y, Color.WHITE);
        this.id = id;
        this.collisionRadius = collisionRadius;
        this.imagePath = imagePath;
        this.imageHalfW = imageHalfW;
        this.imageHalfH = imageHalfH;
        this.vx = 0;
        this.vy = 0;
        this.angle = 0;
    }

    // ── Physics ────────────────────────────────────────────────────────────────

    /**
     * Integrate velocity into position. Override to add gravity, steering, etc.
     */
    public void update() {
        x += vx;
        y += vy;
    }

    /**
     * Apply linear drag — fraction of velocity lost per tick.
     * 0 = no drag, 1 = instant stop.
     */
    public void applyDrag(float dragCoefficient) {
        vx *= (1f - dragCoefficient);
        vy *= (1f - dragCoefficient);
    }


    public void setVelocity(float vx, float vy) {
        this.vx = vx;
        this.vy = vy;
    }

    // ── Collision ──────────────────────────────────────────────────────────────

    @Override
    public boolean contains(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= collisionRadius * collisionRadius;
    }

    public boolean overlaps(Character other) {
        float dx  = other.x - x, dy = other.y - y;
        float sum = collisionRadius + other.collisionRadius;
        return dx * dx + dy * dy < sum * sum;
    }

    public boolean collidesWithRock(Rock rock) {
        float[] bounds = rock.getBounds();
        if (x + collisionRadius < bounds[0] || x - collisionRadius > bounds[1]) return false;
        if (y + collisionRadius < bounds[2] || y - collisionRadius > bounds[3]) return false;
        return rock.contains(x, y);
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void draw(GameEngine engine) {
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y);

        if (imagePath != null) {
            StdDraw.picture(sx, sy, imagePath, imageHalfW * 2, imageHalfH * 2, -angle);
        } else {
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
    //just putting it in here if we need an id system
    public String getId() { 
        return id; 
    }
    
    public float  getVx() { return vx; }
    public float  getVy() { return vy; }
    public float  getAngle() { return angle; }
    public float  getSpeed() { return (float) Math.hypot(vx, vy); }
    public float  getCollisionRadius(){ return collisionRadius; }
    public String getImagePath() { return imagePath; }

    public void setAngle(float angle){ this.angle = angle % 360; }
    public void setImagePath(String p){ this.imagePath = p; }

    @Override public String getType() { return "CHARACTER"; }

    @Override
    public String toString() {
        return String.format("Character[%s pos=(%.1f,%.1f) vel=(%.2f,%.2f) angle=%.1f]",
                id, x, y, vx, vy, angle);
    }
}