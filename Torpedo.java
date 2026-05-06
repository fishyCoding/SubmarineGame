import java.awt.Color;

/**
 * Torpedo — fired by the player, steered by mouse, detonated on second click.
 *
 * Physics:
 * Starts slow than accelerates to max speed
 * Steers toward mouse direction: angle from center of screen to mouse
 * Collision with rocks
 *
 * Lifetime breif:
 * 
 * Created at player position with player's angle
 * 
 * update(mouseWX, mouseWY) every tick for steering
 * 
 * explode() called on second click OR on collision
 * 
 * isAlive() returns false — caller removes it and applies damage
 */
public class Torpedo extends Character {

    private static final float MAXSPEED        = 13f;
    private static float speed           = 3f;  // world-units per tick
    private static final float ACCELERATION = 1.5f;  // world-units per tick squared
    private static final float TURN_RATE    = 3.5f;  // max degrees to turn per tick
    private static final float BLAST_RADIUS = 250f;   // world-units for damage check
    private static final int   DAMAGE       = 100;

    private boolean alive = true;
    private boolean exploded = false;

    // Track who fired it so we don't hit ourselves
    private final String ownerId;

    public Torpedo(String ownerId, float x, float y, float angleDeg) {
        super("torpedo", x, y, 8f, null, 8f, 4f);
        this.ownerId = ownerId;
        this.angle   = angleDeg;
        // Start with full speed in the launch direction
        double rad = Math.toRadians(angleDeg);
        this.vx = (float)(Math.cos(rad) * speed);
        this.vy = (float)(Math.sin(rad) * speed);
    }

    /**
     * Steer toward the direction defined by the angle from screen center to mouse,
     * then move. Call once per tick while the torpedo is alive.
     *
     * @param mouseScreenX  mouse X in screen pixels
     * @param mouseScreenY  mouse Y in screen pixels
     * @param screenCX      screen center X (WIDTH / 2)
     * @param screenCY      screen center Y (HEIGHT / 2)
     */
    public void update(double mouseScreenX, double mouseScreenY,
                       double screenCX, double screenCY) {
        if (!alive){
             return;
        }

        //adds to accelaration
        if (speed<MAXSPEED) {
            speed = Math.min(speed + ACCELERATION, MAXSPEED);
        }

        // Angle from screen center to mouse for sterring
        double targetAngle = Math.toDegrees(Math.atan2(mouseScreenY - screenCY, mouseScreenX - screenCX));

        // Shortest angular delta (should it rotate up or down)
        double delta = targetAngle - angle;
        while (delta >  180) delta -= 360;
        while (delta < -180) delta += 360;

        // Clamp turn by maximum turn rate
        if (delta >  TURN_RATE) delta =  TURN_RATE;
        if (delta < -TURN_RATE) delta = -TURN_RATE;

        //add angle change to angle, apply % bc it shouldnt be over 360
        angle += (float) delta;
        angle  = angle % 360;

        // Set velocity to vector
        double rad = Math.toRadians(angle);
        vx = (float)(Math.cos(rad) * speed);
        vy = (float)(Math.sin(rad) * speed);

        super.update();
    }

    /** Detonation. Game.java handles rest */
    public void explode() {
        alive    = false;
        exploded = true;
    }

    /**
     * Returns t if torpedo is in blasting radius, used for checking damage
     */
    public boolean inBlastRadius(float tx, float ty) {
        float dx = tx - x, dy = ty - y;
        return dx * dx + dy * dy <= BLAST_RADIUS * BLAST_RADIUS;
    }

    public boolean isAlive()    { return alive; }
    public boolean hasExploded(){ return exploded; }
    public int     getDamage()  { return DAMAGE; }
    public float   getBlastRadius() { return BLAST_RADIUS; }
    public String  getOwnerId() { return ownerId; }

    // Render

    @Override
    public void draw(GameEngine engine) {
        if (!alive) return;
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y);

        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // Body — a thin pointed oval, rotated
        int SEG = 12;
        double[] bx = new double[SEG];
        double[] by = new double[SEG];
        for (int i = 0; i < SEG; i++) {
            double t  = 2 * Math.PI * i / SEG;
            double lx = Math.cos(t) * 10;
            double ly = Math.sin(t) * 3;
            bx[i] = sx + lx * cos - ly * sin;
            by[i] = sy + lx * sin + ly * cos;
        }
        StdDraw.setPenColor(new Color(220, 200, 80));
        StdDraw.filledPolygon(bx, by);
        StdDraw.setPenColor(new Color(180, 160, 40));
        StdDraw.setPenRadius(0.002);
        StdDraw.polygon(bx, by);

        StdDraw.setPenRadius(0.002);
    }

    // ── Unused abstract stubs ──────────────────────────────────────────────────

    @Override public String serialize() { return ""; }
    @Override public String getType()   { return "TORPEDO"; }
    @Override public String toString()  { return "Torpedo[pos=(" + x + "," + y + ")]"; }
}