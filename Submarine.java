public class Submarine extends Character {

    private int maxHealth;
    private int health;
    private boolean alive;
    private float rudderAngle = 0f;

    // player state vars
    private boolean dead = false;
    private long deathTimeMs = 0L;
    private static final long DEATH_PAUSE_MS = 5000L;
    private boolean respawnReady = false;

    // physics consts
    private static final float THRUST_ACCEL = 0.35f;
    private static final float VERTICAL_ACCEL = 0.30f;
    private static final float DRAG = 0.04f;
    private static final float VERTICAL_DRAG = 0.06f;
    private static final float MAX_SPEED = 7f;

    // rudder consts
    private static final float RUDDER_RATE = 0.5f;
    private static final float RUDDER_RETURN = 1.5f;
    private static final float RUDDER_MAX = 30f;
    private static final float RUDDER_TURN_GAIN = 0.02f;

    private static final float BODY_HALF_W = 30f;
    private static final float BODY_HALF_H = 12f;

    public Submarine(String id, float x, float y, int maxHealth) {
        super(id, x, y, 28f, BODY_HALF_W, BODY_HALF_H);
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.alive = true;
    }

    private void engineInput() {
        if (StdDraw.isKeyPressed('W')) {
            double rad = Math.toRadians(angle);
            vx += (float)(Math.cos(rad) * THRUST_ACCEL);
            vy += (float)(Math.sin(rad) * THRUST_ACCEL);
        }
        if (StdDraw.isKeyPressed('S')) {
            double rad = Math.toRadians(angle);
            vx -= (float)(Math.cos(rad) * THRUST_ACCEL);
            vy -= (float)(Math.sin(rad) * THRUST_ACCEL);
        }
        if (StdDraw.isKeyPressed('Q')) vy -= VERTICAL_ACCEL;
        if (StdDraw.isKeyPressed('E')) vy += VERTICAL_ACCEL;
    }

    private void handleRudderInput() {
        boolean aHeld = StdDraw.isKeyPressed('A');
        boolean dHeld = StdDraw.isKeyPressed('D');

        if (aHeld && !dHeld) {
            rudderAngle = Math.min(rudderAngle + RUDDER_RATE, RUDDER_MAX);
        } else if (dHeld && !aHeld) {
            rudderAngle = Math.max(rudderAngle - RUDDER_RATE, -RUDDER_MAX);
        } else {
            if (rudderAngle > 0) rudderAngle = Math.max(0, rudderAngle - RUDDER_RETURN);
            else                 rudderAngle = Math.min(0, rudderAngle + RUDDER_RETURN);
        }
    }

    public void handleInput() {
        if (!alive) return;
        handleRudderInput();
        engineInput();
        if (StdDraw.isKeyPressed('P')) takeDamage(50);
    }

    public void handleRespawnClick() {
        if (respawnReady) {
            respawn(Spawner.getSpawnX(), Spawner.getSpawnY());
        }
    }

    @Override
    public void update() {
        if (!alive) {
            if (dead && !respawnReady) {
                long elapsed = System.currentTimeMillis() - deathTimeMs;
                if (elapsed >= DEATH_PAUSE_MS) respawnReady = true;
            }
            return;
        }

        float forwardSpeed = (float)(vx * Math.cos(Math.toRadians(angle)) + vy * Math.sin(Math.toRadians(angle)));
        angle += rudderAngle * RUDDER_TURN_GAIN * forwardSpeed;
        angle = angle % 360;

        vx *= (1f - DRAG);
        vy *= (1f - VERTICAL_DRAG);

        float speed = getSpeed();
        if (speed > MAX_SPEED) {
            float scale = MAX_SPEED / speed;
            vx *= scale;
            vy *= scale;
        }

        super.update();
    }

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
        dead = true;
        respawnReady = false;
        vx = 0;
        vy = 0;
        deathTimeMs = System.currentTimeMillis();
        System.out.println(id + " has been destroyed.");
    }

    public void respawn(float rx, float ry) {
        float jitterX = (float)((Math.random() - 0.5) * 400);
        float jitterY = (float)((Math.random() - 0.5) * 80);
        x = rx + jitterX;
        y = ry + jitterY;
        health = maxHealth;
        alive = true;
        dead = false;
        respawnReady = false;
        vx = 0;
        vy = 0;
        angle = 0;
        rudderAngle = 0;
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    public void drawCentred(double cx, double cy) {
        if (!alive) { drawWreck(cx, cy); return; }
        drawSubBody(cx, cy);
    }

    private void drawSubBody(double sx, double sy) {
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // hull — rotated ellipse via polygon (StdDraw.filledEllipse is always axis-aligned)
        int SEGMENTS = 32;
        double[] hx = new double[SEGMENTS];
        double[] hy = new double[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            double t = 2 * Math.PI * i / SEGMENTS;
            double lx = Math.cos(t) * BODY_HALF_W;
            double ly = Math.sin(t) * BODY_HALF_H;
            hx[i] = sx + lx * cos - ly * sin;
            hy[i] = sy + lx * sin + ly * cos;
        }
        StdDraw.setPenColor(60, 80, 110);
        StdDraw.filledPolygon(hx, hy);
        StdDraw.setPenColor(30, 45, 65);
        StdDraw.setPenRadius(0.003);
        StdDraw.polygon(hx, hy);
        StdDraw.setPenRadius(0.002);

        // rudder fin at the stern, deflected by rudderAngle
        double sternX = sx - cos * BODY_HALF_W;
        double sternY = sy - sin * BODY_HALF_W;

        double rudRad = Math.toRadians(rudderAngle);
        double rudCos = Math.cos(rudRad);
        double rudSin = Math.sin(rudRad);

        double rxAxisX = cos * rudCos - sin * rudSin;
        double rxAxisY = sin * rudCos + cos * rudSin;
        double ryAxisX = -rxAxisY;
        double ryAxisY = rxAxisX;

        double rW = 2.5, rH = 11;
        double[][] rc = {{-rW, 0}, {rW, 0}, {rW, -rH}, {-rW, -rH}};
        double[] rfx = new double[4];
        double[] rfy = new double[4];
        for (int i = 0; i < 4; i++) {
            rfx[i] = sternX + rc[i][0] * ryAxisX + rc[i][1] * rxAxisX;
            rfy[i] = sternY + rc[i][0] * ryAxisY + rc[i][1] * rxAxisY;
        }
        StdDraw.setPenColor(80, 110, 150);
        StdDraw.filledPolygon(rfx, rfy);
        StdDraw.setPenColor(50, 80, 120);
        StdDraw.setPenRadius(0.002);
        StdDraw.polygon(rfx, rfy);
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

    // Called from Game.java once per frame while dead. Draws fullscreen overlay.
    public void drawDeathScreen(int W, int H) {
        StdDraw.setPenColor(new java.awt.Color(0, 0, 0, 160));
        StdDraw.filledRectangle(W / 2.0, H / 2.0, W / 2.0, H / 2.0);

        long elapsed = System.currentTimeMillis() - deathTimeMs;
        long secsLeft = Math.max(0, (DEATH_PAUSE_MS - elapsed + 999) / 1000);

        double cx = W / 2.0;
        double cy = H / 2.0;

        int titleSize = Math.max(14, H / 8);
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, titleSize));
        StdDraw.setPenColor(new java.awt.Color(80, 0, 0, 200));
        StdDraw.text(cx + 2, cy + H * 0.22 + 2, "SUBMARINE DESTROYED");
        StdDraw.setPenColor(new java.awt.Color(220, 50, 50));
        StdDraw.text(cx, cy + H * 0.22, "SUBMARINE DESTROYED");

        if (!respawnReady) {
            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, Math.max(10, H / 14)));
            StdDraw.setPenColor(new java.awt.Color(180, 180, 180));
            StdDraw.text(cx, cy + H * 0.08, "Respawning in " + secsLeft + "...");

            double barW = W * 0.28, barH = H * 0.025;
            double barCY = cy + H * 0.02;
            double progress = Math.min(1.0, (double) elapsed / DEATH_PAUSE_MS);
            StdDraw.setPenColor(new java.awt.Color(60, 60, 60));
            StdDraw.filledRectangle(cx, barCY, barW / 2, barH / 2);
            StdDraw.setPenColor(new java.awt.Color(200, 60, 60));
            double fillW = barW * progress;
            StdDraw.filledRectangle(cx - barW / 2 + fillW / 2, barCY, fillW / 2, barH / 2);

        } else {
            double btnCX = cx, btnCY = cy + H * 0.07;
            double btnW = W * 0.14, btnH = H * 0.055;

            double mx = StdDraw.mouseX(), my = StdDraw.mouseY();
            boolean hover = Math.abs(mx - btnCX) < btnW && Math.abs(my - btnCY) < btnH;

            StdDraw.setPenColor(new java.awt.Color(0, 0, 0, 120));
            StdDraw.filledRectangle(btnCX + 2, btnCY - 2, btnW, btnH);
            StdDraw.setPenColor(hover ? new java.awt.Color(220, 80, 80) : new java.awt.Color(160, 40, 40));
            StdDraw.filledRectangle(btnCX, btnCY, btnW, btnH);
            StdDraw.setPenColor(new java.awt.Color(255, 120, 120));
            StdDraw.setPenRadius(0.002);
            StdDraw.rectangle(btnCX, btnCY, btnW, btnH);

            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, Math.max(10, H / 18)));
            StdDraw.setPenColor(java.awt.Color.WHITE);
            StdDraw.text(btnCX, btnCY, "[ RESPAWN ]");

            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, Math.max(8, H / 28)));
            StdDraw.setPenColor(new java.awt.Color(150, 150, 150));
            StdDraw.text(cx, cy - H * 0.05, "click anywhere to respawn");
        }
    }

    @Override
    public String serialize() {
        return String.format("SUBMARINE %s %.2f %.2f %.2f %.2f %.2f %d %d",
                id, x, y, vx, vy, angle, health, maxHealth);
    }

    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public boolean isAlive() { return alive; }
    public float getRudderAngle() { return rudderAngle; }

    @Override public String getType() { return "SUBMARINE"; }

    @Override
    public String toString() {
        return String.format("Submarine[%s pos=(%.1f,%.1f) hp=%d/%d rudder=%.1f°]",
                id, x, y, health, maxHealth, rudderAngle);
    }
}