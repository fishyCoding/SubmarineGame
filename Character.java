/**
 * Character — a living game entity (submarine, enemy, NPC, etc.).
 *
 * Separates "things that move and act" from static terrain sprites.
 * In a multiplayer game each client will receive Character state updates
 * over WebSockets; the server is authoritative for position and health.
 *
 * Extend this class for specific character types (PlayerSub, EnemySub, etc.).
 */
public abstract class Character extends Sprite {

    // ── Physics state ──────────────────────────────────────────────────────────
    protected float vx;          // velocity x  (world units / tick)
    protected float vy;          // velocity y
    protected float angle;       // heading in degrees (0 = right, 90 = up)
    protected float speed;       // current speed magnitude

    // ── Stats ──────────────────────────────────────────────────────────────────
    protected int   maxHealth;
    protected int   health;
    protected boolean alive;

    // ── Identity ───────────────────────────────────────────────────────────────
    protected String id;         // unique identifier (UUID / player name)
    protected String team;       // team tag for multiplayer ("red", "blue", etc.)

    // ── Collision geometry ─────────────────────────────────────────────────────
    protected float collisionRadius; // simple circle hitbox radius (world units)

    // ── Constructor ────────────────────────────────────────────────────────────

    public Character(String id, String team,
                     float x, float y,
                     float collisionRadius,
                     int maxHealth,
                     int r, int g, int b) {
        super(x, y, r, g, b);
        this.id               = id;
        this.team             = team;
        this.collisionRadius  = collisionRadius;
        this.maxHealth        = maxHealth;
        this.health           = maxHealth;
        this.alive            = true;
        this.vx               = 0;
        this.vy               = 0;
        this.angle            = 0;
        this.speed            = 0;
    }

    // ── Physics ────────────────────────────────────────────────────────────────

    /**
     * Advance physics one tick. Override to add thrust, drag, etc.
     */
    public void update() {
        x += vx;
        y += vy;
    }

    /**
     * Apply a force in the character's current heading direction.
     */
    public void thrust(float magnitude) {
        double rad = Math.toRadians(angle);
        vx += (float) (Math.cos(rad) * magnitude);
        vy += (float) (Math.sin(rad) * magnitude);
    }

    /**
     * Apply simple linear drag so the character doesn't drift forever.
     */
    public void applyDrag(float dragCoefficient) {
        vx *= (1f - dragCoefficient);
        vy *= (1f - dragCoefficient);
        speed = (float) Math.hypot(vx, vy);
    }

    public void setVelocity(float vx, float vy) {
        this.vx = vx;
        this.vy = vy;
        this.speed = (float) Math.hypot(vx, vy);
        if (speed > 0.01f) this.angle = (float) Math.toDegrees(Math.atan2(vy, vx));
    }

    public void rotate(float degrees) {
        angle = (angle + degrees) % 360;
    }

    // ── Health ─────────────────────────────────────────────────────────────────

    public void takeDamage(int amount) {
        if (!alive) return;
        health = Math.max(0, health - amount);
        if (health == 0) die();
    }

    public void heal(int amount) {
        if (!alive) return;
        health = Math.min(maxHealth, health + amount);
    }

    protected void die() {
        alive = false;
        vx = 0;
        vy = 0;
    }

    public void respawn(float x, float y) {
        this.x      = x;
        this.y      = y;
        this.health = maxHealth;
        this.alive  = true;
        this.vx     = 0;
        this.vy     = 0;
    }

    // ── Collision ──────────────────────────────────────────────────────────────

    /** Circle-based hit test (fast, good enough for a submarine game). */
    @Override
    public boolean contains(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= collisionRadius * collisionRadius;
    }

    /** Circle vs circle overlap check against another Character. */
    public boolean overlaps(Character other) {
        float dx  = other.x - x, dy = other.y - y;
        float sum = collisionRadius + other.collisionRadius;
        return dx * dx + dy * dy < sum * sum;
    }

    /** Circle vs polygon (rough AABB pre-check then defer to polygon). */
    public boolean collidesWithPolygon(Polygon poly) {
        float[] bounds = poly.getBounds();
        // Quick AABB guard
        if (x + collisionRadius < bounds[0] || x - collisionRadius > bounds[1]) return false;
        if (y + collisionRadius < bounds[2] || y - collisionRadius > bounds[3]) return false;
        // Precise: test centre point inside polygon
        return poly.contains(x, y);
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    /**
     * Draw a health bar above the character (call from draw() implementations).
     */
    protected void drawHealthBar(GameEngine engine) {
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y) + collisionRadius + 10;

        double barW  = collisionRadius * 1.4;
        double barH  = 4;
        double fill  = barW * ((double) health / maxHealth);

        // Background
        StdDraw.setPenColor(50, 50, 50);
        StdDraw.filledRectangle(sx, sy, barW, barH);

        // Health fill (green → red)
        float ratio = (float) health / maxHealth;
        int hr = (int) (255 * (1 - ratio));
        int hg = (int) (255 * ratio);
        StdDraw.setPenColor(hr, hg, 0);
        StdDraw.filledRectangle(sx - barW + fill, sy, fill, barH);

        // Border
        StdDraw.setPenColor(180, 180, 180);
        StdDraw.setPenRadius(0.002);
        StdDraw.rectangle(sx, sy, barW, barH);
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    /**
     * Serialize for network transmission (position update packet).
     * Format: CHARACTER_UPDATE id team x y vx vy angle health
     */
    @Override
    public String serialize() {
        return String.format("CHARACTER_UPDATE %s %s %.2f %.2f %.2f %.2f %.2f %d",
                id, team, x, y, vx, vy, angle, health);
    }

    // ── Getters / setters ──────────────────────────────────────────────────────

    public String  getId()               { return id; }
    public String  getTeam()             { return team; }
    public float   getVx()               { return vx; }
    public float   getVy()               { return vy; }
    public float   getAngle()            { return angle; }
    public float   getSpeed()            { return speed; }
    public int     getHealth()           { return health; }
    public int     getMaxHealth()        { return maxHealth; }
    public boolean isAlive()             { return alive; }
    public float   getCollisionRadius()  { return collisionRadius; }

    public void setAngle(float angle)    { this.angle = angle % 360; }
    public void setAlive(boolean alive)  { this.alive = alive; }

    @Override
    public String getType() { return "CHARACTER"; }

    @Override
    public String toString() {
        return String.format("Character[%s team=%s pos=(%.1f,%.1f) hp=%d/%d alive=%b]",
                id, team, x, y, health, maxHealth, alive);
    }
}