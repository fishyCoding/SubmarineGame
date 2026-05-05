public class Submarine extends Character {

    //player state variablees
    private int maxHealth;
    private int health;
    private boolean alive;
    private float rudderAngle = 0f;

    // Death / respawn state
    private boolean dead        = false;   // true after die(), before respawn
    private long    deathTimeMs = 0L;      // System.currentTimeMillis() at death
    private static final long DEATH_PAUSE_MS = 5000L;  // 5-second countdown
    private boolean respawnReady = false;  // true once countdown expires

    // Physics constants
    private static final float THRUST_ACCEL = 0.35f;  // world-units / tick²
    private static final float VERTICAL_ACCEL = 0.30f;  // Q/E  world-units / tick²
    private static final float DRAG = 0.04f;
    private static final float VERTICAL_DRAG = 0.06f;
    private static final float MAX_SPEED = 7f;

    // Rudder constants
    private static final float RUDDER_RATE = 0.5f;   // dgs per tick for turning
    private static final float RUDDER_RETURN = 1.5f;   // dgs returning to neutral when released
    private static final float RUDDER_MAX = 30f;    // max turning angle 
    private static final float RUDDER_TURN_GAIN = 0.02f;  // degrees of heading change per degree of rudder per unit speed (way too much physics in this project ong)

    //we're replacing this soon don't worry
    private static final float BODY_HALF_W = 30f;
    private static final float BODY_HALF_H = 12f;



    public Submarine(String id, float x, float y, int maxHealth, String imagePath) {
        super(id, x, y, 28f,imagePath, BODY_HALF_W,BODY_HALF_H);
        this.maxHealth=maxHealth;
        this.health=maxHealth;
        this.alive=true;
    }

    private void engineInput(){
        if (StdDraw.isKeyPressed('W')) {
            double rad = Math.toRadians(angle);
            vx += (float) (Math.cos(rad)*THRUST_ACCEL);
            vy += (float) (Math.sin(rad)*THRUST_ACCEL);
        }
        if (StdDraw.isKeyPressed('S')) {
            double rad = Math.toRadians(angle);
            vx -= (float) (Math.cos(rad)*THRUST_ACCEL);
            vy -= (float) (Math.sin(rad)*THRUST_ACCEL);
        }

        if (StdDraw.isKeyPressed('Q'))
            vy -= VERTICAL_ACCEL;
        if (StdDraw.isKeyPressed('E'))
            vy += VERTICAL_ACCEL;

    }

    private void handleRudderInput(){
        boolean aHeld = StdDraw.isKeyPressed('A');
        boolean dHeld = StdDraw.isKeyPressed('D');

        //turn left
        if (aHeld && !dHeld) {
            rudderAngle = Math.min(rudderAngle+ RUDDER_RATE, RUDDER_MAX);
        } 
        
        //turn right
        else if (dHeld && !aHeld) {
            rudderAngle = Math.max(rudderAngle-RUDDER_RATE, -RUDDER_MAX);
        } 
        
        else {
            if (rudderAngle > 0){
                rudderAngle = Math.max(0, rudderAngle - RUDDER_RETURN);
            }
            else{                 
                rudderAngle = Math.min(0, rudderAngle + RUDDER_RETURN);
            }
        }
    }
 
    public void handleInput() {
        if (!alive) {
            // While on death screen, poll for respawn button click
            if (respawnReady && StdDraw.isMousePressed()) {
                respawn(0, -80);
            }
            return;
        }

        //rudder controls
        handleRudderInput();
        
        engineInput();

        //Random testing shit
        if (StdDraw.isKeyPressed('P'))
            takeDamage(50);
    }

    @Override
    public void update() {
        if (!alive) {
            // Advance death countdown
            if (dead && !respawnReady) {
                long elapsed = System.currentTimeMillis() - deathTimeMs;
                if (elapsed >= DEATH_PAUSE_MS) {
                    respawnReady = true;
                }
            }
            return;
        }

        //basic trig stuff for getting forward vector
        float forwardSpeed = (float)(vx * Math.cos(Math.toRadians(angle))+ vy * Math.sin(Math.toRadians(angle)));
        angle += rudderAngle * RUDDER_TURN_GAIN * forwardSpeed;
        
        //just in case if its bigger than 360 yk
        angle = angle%360;


        // Drag
        vx *= (1f-DRAG);
        vy *= (1f-VERTICAL_DRAG);

        // Max speed clamp
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
        alive         = false;
        dead          = true;
        respawnReady  = false;
        vx            = 0;
        vy            = 0;
        deathTimeMs   = System.currentTimeMillis();
        System.out.println(id + " has been destroyed.");
    }

    public void respawn(float rx, float ry) {
        // add slight random offset so you don't always spawn in the exact same spot
        float jitterX = (float)((Math.random() - 0.5) * 400);   // ±200 world units
        float jitterY = (float)((Math.random() - 0.5) * 80);    // ±40  world units (stays near surface)
        x           = rx + jitterX;
        y           = ry + jitterY;
        health      = maxHealth;
        alive       = true;
        dead        = false;
        respawnReady= false;
        vx          = 0;
        vy          = 0;
        angle       = 0;
        rudderAngle = 0;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    public void drawCentred(double cx, double cy) {
        if (!alive) { drawWreck(cx, cy); return; }

        drawSubBody(cx, cy);
        
    }

    private void drawSubBody(double sx, double sy) {
        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // ── Hull — approximated as a rotated ellipse via polygon ───────────────
        // StdDraw.filledEllipse is always axis-aligned, so we tessellate manually.
        int   SEGMENTS = 32;
        double[] hx = new double[SEGMENTS];
        double[] hy = new double[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            double t  = 2 * Math.PI * i / SEGMENTS;
            double lx = Math.cos(t) * BODY_HALF_W;   // local ellipse point
            double ly = Math.sin(t) * BODY_HALF_H;
            hx[i] = sx + lx * cos - ly * sin;         // rotate into world
            hy[i] = sy + lx * sin + ly * cos;
        }
        StdDraw.setPenColor(60, 80, 110);
        StdDraw.filledPolygon(hx, hy);
        StdDraw.setPenColor(30, 45, 65);
        StdDraw.setPenRadius(0.003);
        StdDraw.polygon(hx, hy);
        StdDraw.setPenRadius(0.002);

        // ── Rudder — fin at the stern, deflected by rudderAngle ───────────────
        // Step 1: find the stern in world space (directly behind centre)
        double sternX = sx - cos * BODY_HALF_W;
        double sternY = sy - sin * BODY_HALF_W;

        // Step 2: build the rudder's local axes by rotating the hull axes by
        //         rudderAngle. This keeps deflection relative to the hull, so
        //         it can never exceed ±30° visually regardless of world angle.
        double rudRad = Math.toRadians(rudderAngle);   // deflection only
        double rudCos = Math.cos(rudRad);
        double rudSin = Math.sin(rudRad);

        // Rudder local X axis = hull forward rotated by rudderAngle
        double rxAxisX =  cos * rudCos - sin * rudSin;
        double rxAxisY =  sin * rudCos + cos * rudSin;
        // Rudder local Y axis = hull up rotated by rudderAngle
        double ryAxisX = -sin * rudCos - cos * rudSin;
        double ryAxisY = -cos * rudCos + sin * rudSin;  // wait, keep it perpendicular
        // Actually: rudder Y = perpendicular to rudder X
        ryAxisX = -rxAxisY;
        ryAxisY =  rxAxisX;

        // Step 3: fin corners in rudder-local space
        //         hinge at (0,0), fin extends backward (negative local-X)
        double rW = 2.5, rH = 11;
        double[][] rc = {{-rW, 0},{rW, 0},{rW,-rH},{-rW,-rH}};
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
        if (dead) drawDeathScreen(engine);
    }

    /**
     * Fullscreen death overlay. Shows a countdown for 5 s, then a respawn button.
     * Call this every frame while dead=true.
     */
    private void drawDeathScreen(GameEngine engine) {
        // We need screen dimensions — derive them from StdDraw scale
        double W = StdDraw.getWidth();
        double H = StdDraw.getHeight();

        // Semi-transparent dark overlay
        StdDraw.setPenColor(new java.awt.Color(0, 0, 0, 160));
        StdDraw.filledRectangle(W / 2, H / 2, W / 2, H / 2);

        long elapsed = System.currentTimeMillis() - deathTimeMs;
        long secsLeft = Math.max(0, (DEATH_PAUSE_MS - elapsed + 999) / 1000);

        // ── "DESTROYED" title ─────────────────────────────────────────────────
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 52));
        // shadow
        StdDraw.setPenColor(new java.awt.Color(80, 0, 0, 200));
        StdDraw.text(W / 2 + 3, H / 2 + 83, "SUBMARINE DESTROYED");
        // main text
        StdDraw.setPenColor(new java.awt.Color(220, 50, 50));
        StdDraw.text(W / 2, H / 2 + 86, "SUBMARINE DESTROYED");

        if (!respawnReady) {
            // ── Countdown ─────────────────────────────────────────────────────
            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 28));
            StdDraw.setPenColor(new java.awt.Color(180, 180, 180));
            StdDraw.text(W / 2, H / 2 + 40, "Respawning in " + secsLeft + "...");

            // Progress bar background
            double barW = 220, barH = 10;
            double barX = W / 2 - barW / 2, barY = H / 2 + 18;
            StdDraw.setPenColor(new java.awt.Color(60, 60, 60));
            StdDraw.filledRectangle(W / 2, barY + barH / 2, barW / 2, barH / 2);

            // Progress bar fill
            double progress = Math.min(1.0, (double) elapsed / DEATH_PAUSE_MS);
            StdDraw.setPenColor(new java.awt.Color(200, 60, 60));
            double fillW = barW * progress;
            StdDraw.filledRectangle(barX + fillW / 2, barY + barH / 2, fillW / 2, barH / 2);

        } else {
            // ── Respawn button ────────────────────────────────────────────────
            double btnCX = W / 2, btnCY = H / 2 + 30;
            double btnW = 110, btnH = 22;

            double mx = StdDraw.mouseX(), my = StdDraw.mouseY();
            boolean hover = Math.abs(mx - btnCX) < btnW && Math.abs(my - btnCY) < btnH;

            // Button shadow
            StdDraw.setPenColor(new java.awt.Color(0, 0, 0, 120));
            StdDraw.filledRectangle(btnCX + 3, btnCY - 3, btnW, btnH);

            // Button body
            java.awt.Color btnColor = hover
                    ? new java.awt.Color(220, 80, 80)
                    : new java.awt.Color(160, 40, 40);
            StdDraw.setPenColor(btnColor);
            StdDraw.filledRectangle(btnCX, btnCY, btnW, btnH);

            // Button border
            StdDraw.setPenColor(new java.awt.Color(255, 120, 120));
            StdDraw.setPenRadius(0.002);
            StdDraw.rectangle(btnCX, btnCY, btnW, btnH);

            // Button label
            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 22));
            StdDraw.setPenColor(java.awt.Color.WHITE);
            StdDraw.text(btnCX, btnCY, "[ RESPAWN ]");

            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 15));
            StdDraw.setPenColor(new java.awt.Color(150, 150, 150));
            StdDraw.text(W / 2, H / 2 - 10, "click anywhere to respawn");
        }
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