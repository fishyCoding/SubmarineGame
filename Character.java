// Stores position, velocity, heading, and a circular hitbox.
public abstract class Character extends Sprite {

    // physics state
    protected float vx;
    protected float vy;
    protected float angle; // degrees (0 = right, 90 = up)

    protected String id;
    protected float collisionRadius;

    public Character(String id, float x, float y, float collisionRadius, float imageHalfW, float imageHalfH) {
        super(x, y);
        this.id = id;
        this.collisionRadius = collisionRadius;
        this.vx = 0;
        this.vy = 0;
        this.angle = 0;
    }

    // ── Physics ──────────────────────────────────────────────────────────────────

    public void update() {
        x += vx;
        y += vy;
    }

    // dragCoefficient: 0 = no drag, 1 = instant stop
    public void applyDrag(float dragCoefficient) {
        vx *= (1f - dragCoefficient);
        vy *= (1f - dragCoefficient);
    }

    public void setVelocity(float vx, float vy) {
        this.vx = vx;
        this.vy = vy;
    }


    @Override
    public boolean contains(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= collisionRadius * collisionRadius;
    }

    public boolean overlaps(Character other) {
        float dx = other.x - x, dy = other.y - y;
        float sum = collisionRadius + other.collisionRadius;
        return dx * dx + dy * dy < sum * sum;
    }

    public boolean collidesWithRock(Rock rock) {
        float[] bounds = rock.getBounds();
        if (x + collisionRadius < bounds[0] || x - collisionRadius > bounds[1]) return false;
        if (y + collisionRadius < bounds[2] || y - collisionRadius > bounds[3]) return false;
        return rock.contains(x, y);
    }


    // getters and setters
    public String getId() { return id; }
    public float getVx() { return vx; }
    public float getVy() { return vy; }
    public float getAngle() { return angle; }
    public float getSpeed() { return (float) Math.hypot(vx, vy); }
    public float getCollisionRadius() { return collisionRadius; }

    public void setAngle(float angle) { this.angle = angle % 360; }

    @Override public String getType() { return "CHARACTER"; }
}