/**
 * Submarine — player-controlled underwater vehicle.
 *
 * Controls:
 *   W / S   — engine forward / reverse (thrust along current heading)
 *   A / D   — deflect the rudder left / right (clamped to ±30°)
 *             The rudder gradually turns the sub's heading while moving.
 *             Releasing A/D lets the rudder drift back to neutral.
 *   Q / E   — dive / surface directly, independent of heading and rudder
 *
 * The rudder is drawn as a small angled fin at the stern so the player can
 * see its current deflection at a glance.
 */
public class Submarine extends Character {

    // ── Stats ──────────────────────────────────────────────────────────────────
    private int     maxHealth;
    private int     health;
    private boolean alive;

    // ── Physics tuning ─────────────────────────────────────────────────────────
    private static final float THRUST_ACCEL     = 0.35f;  // world-units / tick²
    private static final float VERTICAL_ACCEL   = 0.30f;  // Q/E  world-units / tick²
    private static final float DRAG             = 0.04f;
    private static final float VERTICAL_DRAG    = 0.06f;
    private static final float MAX_SPEED        = 6f;

    // Rudder
    private static final float RUDDER_RATE      = 2.0f;   // degrees deflected per tick while A/D held
    private static final float RUDDER_RETURN    = 1.5f;   // degrees returned to neutral per tick when released
    private static final float RUDDER_MAX       = 30f;    // hard clamp (degrees)

    // How strongly the rudder turns the heading — scales with forward speed
    // so a stationary sub can't spin in place
    private static final float RUDDER_TURN_GAIN = 0.08f;  // degrees of heading change per degree of rudder per unit speed

    // ── Rudder state ───────────────────────────────────────────────────────────
    private float rudderAngle = 0f;   // current deflection: + = port/left, - = starboard/right

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
     * Poll keyboard and queue forces. Call once per tick before update().
     */
    public void handleInput() {
        if (!alive) return;

        // ── Rudder (A/D) — deflect or return to neutral ────────────────────────
        boolean aHeld = StdDraw.isKeyPressed('A') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT);
        boolean dHeld = StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT);

        if (aHeld && !dHeld) {
            rudderAngle = Math.min(rudderAngle + RUDDER_RATE, RUDDER_MAX);
        } else if (dHeld && !aHeld) {
            rudderAngle = Math.max(rudderAngle - RUDDER_RATE, -RUDDER_MAX);
        } else {
            // Neither or both held — drift back to neutral
            if (rudderAngle > 0) rudderAngle = Math.max(0, rudderAngle - RUDDER_RETURN);
            else                 rudderAngle = Math.min(0, rudderAngle + RUDDER_RETURN);
        }

        // ── Engine (W/S) — thrust along current heading ────────────────────────
        if (StdDraw.isKeyPressed('W') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP)) {
            double rad = Math.toRadians(angle);
            vx += (float) (Math.cos(rad) * THRUST_ACCEL);
            vy += (float) (Math.sin(rad) * THRUST_ACCEL);
        }
        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN)) {
            double rad = Math.toRadians(angle);
            vx -= (float) (Math.cos(rad) * THRUST_ACCEL);
            vy -= (float) (Math.sin(rad) * THRUST_ACCEL);
        }

        // ── Q/E — vertical, ignores heading and rudder entirely ────────────────
        if (StdDraw.isKeyPressed('Q'))
            vy -= VERTICAL_ACCEL;
        if (StdDraw.isKeyPressed('E'))
            vy += VERTICAL_ACCEL;

        // ── Dev keys ───────────────────────────────────────────────────────────
        if (StdDraw.isKeyPressed('P'))
            takeDamage(50);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_O))
            respawn(0, -200);
    }

    // ── Update loop ────────────────────────────────────────────────────────────

    @Override
    public void update() {
        if (!alive) return;

        // Rudder turns the heading proportional to forward speed and deflection.
        // No speed = no turn, just like a real boat.
        float forwardSpeed = (float)(vx * Math.cos(Math.toRadians(angle))
                                   + vy * Math.sin(Math.toRadians(angle)));
        angle += rudderAngle * RUDDER_TURN_GAIN * forwardSpeed;
        angle  = angle % 360;

        // Drag
        vx *= (1f - DRAG);
        vy *= (1f - VERTICAL_DRAG);

        // Speed clamp
        float speed = getSpeed();
        if (speed > MAX_SPEED) {
            float scale = MAX_SPEED / speed;
            vx *= scale;
            vy *= scale;
        }

        super.update();
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
        x           = rx;
        y           = ry;
        health      = maxHealth;
        alive       = true;
        vx          = 0;
        vy          = 0;
        angle       = 0;
        rudderAngle = 0;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    public void drawCentred(double cx, double cy) {
        if (!alive) { drawWreck(cx, cy); return; }
        if (imagePath != null) {
            StdDraw.picture(cx, cy, imagePath, imageHalfW * 2, imageHalfH * 2, -angle);
        } else {
            drawSubBody(cx, cy);
        }
    }

    private void drawSubBody(double sx, double sy) {
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // ── Hull ───────────────────────────────────────────────────────────────
        StdDraw.setPenColor(60, 80, 110);
        StdDraw.filledEllipse(sx, sy, BODY_HALF_W, BODY_HALF_H);

        // ── Conning tower — sits on top of hull, rotated with heading ──────────
        double towerFwd = 5.0;
        double towerUp  = BODY_HALF_H * 0.85;
        double twrX = sx + cos * towerFwd - sin * towerUp;
        double twrY = sy + sin * towerFwd + cos * towerUp;
        double tw = 5, th = 9;
        double[] txs = new double[4];
        double[] tys = new double[4];
        double[][] tc = {{-tw,-th},{tw,-th},{tw,th},{-tw,th}};
        for (int i = 0; i < 4; i++) {
            txs[i] = twrX + tc[i][0] * cos - tc[i][1] * sin;
            tys[i] = twrY + tc[i][0] * sin + tc[i][1] * cos;
        }
        StdDraw.setPenColor(45, 65, 90);
        StdDraw.filledPolygon(txs, tys);

        // ── Rudder — small fin at the stern, angled by rudderAngle ────────────
        // Stern point is directly behind the hull centre along the heading
        double sternX = sx - cos * BODY_HALF_W;
        double sternY = sy - sin * BODY_HALF_W;

        // Rudder rotates around the stern, its angle is heading + deflection
        double rudRad = Math.toRadians(angle + rudderAngle);
        double rudCos = Math.cos(rudRad);
        double rudSin = Math.sin(rudRad);

        // Thin elongated fin, hinged at the front (stern end of hull)
        double rW = 2, rH = 10;
        double[] rxs = new double[4];
        double[] rys = new double[4];
        // Local corners: hinge at top, fin extends backward
        double[][] rc = {{-rW, 0},{rW, 0},{rW,-rH},{-rW,-rH}};
        for (int i = 0; i < 4; i++) {
            rxs[i] = sternX + rc[i][0] * rudCos - rc[i][1] * rudSin;
            rys[i] = sternY + rc[i][0] * rudSin + rc[i][1] * rudCos;
        }
        StdDraw.setPenColor(80, 110, 150);
        StdDraw.filledPolygon(rxs, rys);

        // ── Hull outline ───────────────────────────────────────────────────────
        StdDraw.setPenColor(30, 45, 65);
        StdDraw.setPenRadius(0.003);
        StdDraw.ellipse(sx, sy, BODY_HALF_W, BODY_HALF_H);
        StdDraw.setPenRadius(0.002);
    }

    private void drawWreck(double sx, double sy) {
        StdDraw.setPenColor(40, 40, 50);
        StdDraw.filledEllipse(sx, sy, BODY_HALF_W, BODY_HALF_H);
        StdDraw.setPenColor(60, 60, 70);
        StdDraw.setPenRadius(0.002);
        StdDraw.ellipse(sx, sy, BODY_HALF_W, BODY_HALF_H);
    }

    @Override
    public void draw(GameEngine engine) {
        drawCentred(engine.worldToScreenX(x), engine.worldToScreenY(y));
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    @Override
    public String serialize() {
        return String.format("SUBMARINE %s %.2f %.2f %.2f %.2f %.2f %d %d",
                id, x, y, vx, vy, angle, health, maxHealth);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public int     getHealth()     { return health; }
    public int     getMaxHealth()  { return maxHealth; }
    public boolean isAlive()       { return alive; }
    public float   getRudderAngle(){ return rudderAngle; }

    @Override public String getType() { return "SUBMARINE"; }

    @Override
    public String toString() {
        return String.format("Submarine[%s pos=(%.1f,%.1f) hp=%d/%d rudder=%.1f°]",
                id, x, y, health, maxHealth, rudderAngle);
    }
}