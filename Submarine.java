import java.awt.Color;

/**
 * Submarine — a player-controlled or AI underwater vehicle.
 *
 * Extends {@link Character} with:
 *   - Health / damage / death
 *   - Submarine-specific physics (thrust, drag, buoyancy)
 *   - A HUD health bar drawn above the sprite
 *   - Keyboard-driven input helper (call {@link #handleInput} each tick)
 */
public class Submarine extends Character {

    // ── Stats ──────────────────────────────────────────────────────────────────
    private int   maxHealth;
    private int   health;
    private boolean alive;

    // ── Physics tuning ─────────────────────────────────────────────────────────
    private static final float THRUST_FORCE  = 0.35f;   // world-units / tick²
    private static final float DRAG          = 0.04f;   // fraction lost per tick
    private static final float TURN_SPEED    = 2.5f;    // degrees / tick
    private static final float MAX_SPEED     = 6f;      // world-units / tick

    // ── Visual ─────────────────────────────────────────────────────────────────
    /** Width and height of the sub body drawn when no image is loaded (world units). */
    private static final float BODY_HALF_W = 30f;
    private static final float BODY_HALF_H = 12f;

    // ── Constructor ────────────────────────────────────────────────────────────

    /**
     * @param id         unique identifier / player name
     * @param x          spawn world X
     * @param y          spawn world Y
     * @param maxHealth  starting and maximum HP
     * @param imagePath  path to sub sprite image, or null for the built-in shape
     */
    public Submarine(String id, float x, float y, int maxHealth, String imagePath) {
        super(id, x, y,
              /*collisionRadius=*/ 28f,
              imagePath,
              /*imageHalfW=*/ BODY_HALF_W,
              /*imageHalfH=*/ BODY_HALF_H);
        this.maxHealth = maxHealth;
        this.health    = maxHealth;
        this.alive     = true;
    }

    // ── Update loop ────────────────────────────────────────────────────────────

    /**
     * Advance physics one tick.  Must be called every frame by the game loop.
     * Physics: apply drag, clamp speed, then integrate position.
     */
    @Override
    public void update() {
        if (!alive) return;

        applyDrag(DRAG);

        // Clamp speed
        float speed = getSpeed();
        if (speed > MAX_SPEED) {
            float scale = MAX_SPEED / speed;
            vx *= scale;
            vy *= scale;
        }

        super.update(); // x += vx, y += vy
    }

    /**
     * Read keyboard state and apply thrust / rotation.
     * Call this once per tick before {@link #update()}.
     *
     * Controls:
     *   W / Up Arrow    → thrust forward
     *   S / Down Arrow  → thrust backward
     *   A / Left Arrow  → rotate left
     *   D / Right Arrow → rotate right
     */
    public void handleInput() {
        if (!alive) return;

        if (StdDraw.isKeyPressed('W') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP))
            thrust(THRUST_FORCE);
        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN))
            thrust(-THRUST_FORCE);
        if (StdDraw.isKeyPressed('A') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT))
            rotate(TURN_SPEED);
        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT))
            rotate(-TURN_SPEED);
    }

    // ── Health / damage ────────────────────────────────────────────────────────

    public void takeDamage(int amount) {
        if (!alive) return;
        health = Math.max(0, health - amount);
        if (health == 0) die();
    }

    public void heal(int amount) {
        if (!alive) return;
        health = Math.min(maxHealth, health + amount);
    }

    private void die() {
        alive = false;
        vx = 0;
        vy = 0;
        System.out.println(id + " has been destroyed.");
    }

    public void respawn(float rx, float ry) {
        x      = rx;
        y      = ry;
        health = maxHealth;
        alive  = true;
        vx     = 0;
        vy     = 0;
        angle  = 0;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    /**
     * Draw the submarine always centred at (cx, cy) in screen space.
     * The game loop passes WIDTH/2, HEIGHT/2 so the sub never moves on screen.
     */
    public void drawCentred(double cx, double cy) {
        if (!alive) {
            drawWreck(cx, cy);
            return;
        }
        if (imagePath != null) {
            StdDraw.picture(cx, cy, imagePath,
                            imageHalfW * 2, imageHalfH * 2, -angle);
        } else {
            drawSubBody(cx, cy);
        }
    }

    /** Procedural hull + conning tower, drawn at the given screen position. */
    private void drawSubBody(double sx, double sy) {
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad), sin = Math.sin(rad);

        StdDraw.setPenColor(60, 80, 110);
        StdDraw.filledEllipse(sx, sy, BODY_HALF_W, BODY_HALF_H);

        double twrX = sx + cos * 4 - sin * BODY_HALF_H * 0.7;
        double twrY = sy + sin * 4 + cos * BODY_HALF_H * 0.7;
        StdDraw.setPenColor(45, 65, 90);
        StdDraw.filledRectangle(twrX, twrY, 6, 8);

        StdDraw.setPenColor(30, 45, 65);
        StdDraw.setPenRadius(0.003);
        StdDraw.ellipse(sx, sy, BODY_HALF_W, BODY_HALF_H);
        StdDraw.setPenRadius(0.002);
    }

    /** Dim wreck silhouette when destroyed. */
    private void drawWreck(double sx, double sy) {
        StdDraw.setPenColor(40, 40, 50);
        StdDraw.filledEllipse(sx, sy, BODY_HALF_W, BODY_HALF_H);
        StdDraw.setPenColor(60, 60, 70);
        StdDraw.setPenRadius(0.002);
        StdDraw.ellipse(sx, sy, BODY_HALF_W, BODY_HALF_H);
    }

    // draw(engine) kept for compatibility — delegates to centred version
    @Override
    public void draw(GameEngine engine) {
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y);
        drawCentred(sx, sy);
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    @Override
    public String serialize() {
        return String.format("SUBMARINE %s %.2f %.2f %.2f %.2f %.2f %d %d",
                id, x, y, vx, vy, angle, health, maxHealth);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public int     getHealth()    { return health; }
    public int     getMaxHealth() { return maxHealth; }
    public boolean isAlive()      { return alive; }

    @Override
    public String getType() { return "SUBMARINE"; }

    @Override
    public String toString() {
        return String.format("Submarine[%s pos=(%.1f,%.1f) hp=%d/%d alive=%b]",
                id, x, y, health, maxHealth, alive);
    }
}