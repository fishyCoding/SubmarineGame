/**
 * Submarine — player-controlled underwater vehicle.
 *
 * Movement model:
 *   W / S          — accelerate / brake along the current heading
 *   A / D          — rotate the heading left / right
 *   Q / E          — dive / surface vertically, independent of heading
 *
 * The heading (angle) only tracks horizontal orientation. Vertical movement
 * via Q/E is layered on top as a separate velocity component, so the sub
 * can nose-level while still rising or sinking — much closer to how
 * ballast tanks actually work.
 */
public class Submarine extends Character {

    // ── Stats ──────────────────────────────────────────────────────────────────
    private int     maxHealth;
    private int     health;
    private boolean alive;

    // ── Physics tuning ─────────────────────────────────────────────────────────
    private static final float THRUST_ACCEL    = 0.35f;  // forward/back  (world-units / tick²)
    private static final float VERTICAL_ACCEL  = 0.25f;  // Q/E vertical  (world-units / tick²)
    private static final float HORIZONTAL_DRAG = 0.04f;  // fraction of vx lost per tick
    private static final float VERTICAL_DRAG   = 0.06f;  // fraction of vy lost per tick (more resistance)
    private static final float TURN_SPEED      = 2.5f;   // degrees / tick
    private static final float MAX_SPEED       = 6f;     // world-units / tick

    // ── Visual ─────────────────────────────────────────────────────────────────
    private static final float BODY_HALF_W = 30f;
    private static final float BODY_HALF_H = 12f;

    // ── Constructor ────────────────────────────────────────────────────────────

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

    // ── Input ──────────────────────────────────────────────────────────────────

    /**
     * Poll keyboard and apply forces. Call once per tick before update().
     *
     *   W / Up    → thrust forward along heading
     *   S / Down  → thrust backward along heading
     *   A / Left  → rotate heading left (CCW)
     *   D / Right → rotate heading right (CW)
     *   Q         → dive (add downward velocity, independent of heading)
     *   E         → surface (add upward velocity, independent of heading)
     */
    public void handleInput() {
        if (!alive) return;

        // ── Rotation ───────────────────────────────────────────────────────────
        if (StdDraw.isKeyPressed('A') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT))
            rotate(TURN_SPEED);
        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT))
            rotate(-TURN_SPEED);

        // ── Horizontal thrust along heading ───────────────────────────────────
        if (StdDraw.isKeyPressed('W') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP))
            thrustAlongHeading(THRUST_ACCEL);
        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN))
            thrustAlongHeading(-THRUST_ACCEL);

        // ── Vertical movement — ballast-style, ignores heading ─────────────────
        if (StdDraw.isKeyPressed('Q'))
            vy -= VERTICAL_ACCEL;   // dive
        if (StdDraw.isKeyPressed('E'))
            vy += VERTICAL_ACCEL;   // surface

        // ── Debug / dev keys ───────────────────────────────────────────────────
        if (StdDraw.isKeyPressed('P'))
            takeDamage(50);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_O))
            respawn(0, -200);
    }

    // ── Movement helpers ───────────────────────────────────────────────────────

    /**
     * Accelerate along the current heading. Only affects vx — vertical (vy)
     * is managed separately so Q/E feel independent of orientation.
     */
    private void thrustAlongHeading(float magnitude) {
        double rad = Math.toRadians(angle);
        vx += (float) (Math.cos(rad) * magnitude);
        // Deliberately not touching vy here — that's Q/E's job
    }

    /**
     * Rotate the horizontal heading by degrees (positive = left / CCW).
     */
    private void rotate(float degrees) {
        angle = (angle + degrees) % 360;
    }

    // ── Update loop ────────────────────────────────────────────────────────────

    /**
     * Physics step: apply separate drag to horizontal and vertical axes,
     * clamp total speed, then integrate position.
     */
    @Override
    public void update() {
        if (!alive) return;

        // Separate drag so vertical momentum bleeds off slightly faster than
        // horizontal — water resists diving/surfacing more than forward motion.
        vx *= (1f - HORIZONTAL_DRAG);
        vy *= (1f - VERTICAL_DRAG);

        // Clamp total speed
        float speed = getSpeed();
        if (speed > MAX_SPEED) {
            float scale = MAX_SPEED / speed;
            vx *= scale;
            vy *= scale;
        }

        super.update(); // x += vx, y += vy
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
        vx    = 0;
        vy    = 0;
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
     * Draw the sub centred at a fixed screen position.
     * The game loop should pass (WIDTH/2, HEIGHT/2) so the sub stays centred.
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

    /**
     * Procedural hull + conning tower. The tower is offset perpendicular to
     * the heading so it always sits on top of the hull regardless of rotation.
     */
    private void drawSubBody(double sx, double sy) {
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // Hull
        StdDraw.setPenColor(60, 80, 110);
        StdDraw.filledEllipse(sx, sy, BODY_HALF_W, BODY_HALF_H);

        // Conning tower — offset perpendicular (normal) to the heading,
        // centred slightly forward. In 2-D "perpendicular upward" in heading
        // space is (-sin, cos).
        double towerForward = 5.0;                  // forward along heading
        double towerUp      = BODY_HALF_H * 0.85;   // perpendicular (above hull)
        double twrX = sx + cos * towerForward - sin * towerUp;
        double twrY = sy + sin * towerForward + cos * towerUp;

        // Rotate a filled rectangle to match the heading using a rotated polygon
        double tw = 5, th = 9;  // half-extents of the tower box
        double[] xs = new double[4];
        double[] ys = new double[4];
        double[][] corners = {{-tw,-th},{tw,-th},{tw,th},{-tw,th}};
        for (int i = 0; i < 4; i++) {
            xs[i] = twrX + corners[i][0] * cos - corners[i][1] * sin;
            ys[i] = twrY + corners[i][0] * sin + corners[i][1] * cos;
        }
        StdDraw.setPenColor(45, 65, 90);
        StdDraw.filledPolygon(xs, ys);

        // Outline
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

    @Override public String getType() { return "SUBMARINE"; }

    @Override
    public String toString() {
        return String.format("Submarine[%s pos=(%.1f,%.1f) hp=%d/%d alive=%b]",
                id, x, y, health, maxHealth, alive);
    }
}