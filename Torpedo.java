import java.awt.Color;

// Lifecycle: 
// created at player pos
//  update() each tick with steering from mouse
// explode() on click or collision
public class Torpedo extends Character {

    //physcs consts
    private static final float MAXSPEED = 13f;
    private static final float ACCELERATION = 1.5f;
    private static final float TURN_RATE = 3.5f;
    private static final float KILL_BLAST_RADIUS = 150f;
    private static final float BLAST_RADIUS = 350f;
    private static final int DAMAGE = 100;

    public static int getDamage(float dist){
        if (dist>BLAST_RADIUS){
             return 0;
        }
        else if (dist<KILL_BLAST_RADIUS){
         return DAMAGE;
        }
        else{
            return (int) ((BLAST_RADIUS-dist)/(BLAST_RADIUS-KILL_BLAST_RADIUS)*DAMAGE);
        }
    }
    

    //torpedo state vars
    private float speed = 3f;
    private boolean alive = true;
    private boolean exploded = false;

    private final String ownerId;

    public Torpedo(String ownerId, float x, float y, float angleDeg) {
        super("torpedo", x, y, 8f, 8f, 4f);
        this.ownerId = ownerId;
        this.angle = angleDeg;
        double rad = Math.toRadians(angleDeg);
        this.vx = (float)(Math.cos(rad) * speed);
        this.vy = (float)(Math.sin(rad) * speed);
    }

    // Steer toward the angle from screen center to mouse, then move.
    public void update(double mouseScreenX, double mouseScreenY, double screenCX, double screenCY) {
        if (!alive) return;

        if (speed < MAXSPEED) speed = Math.min(speed + ACCELERATION, MAXSPEED);

        // angle from screen center to mouse
        double targetAngle = Math.toDegrees(Math.atan2(mouseScreenY - screenCY, mouseScreenX - screenCX));

        double delta = targetAngle - angle;
        while (delta > 180) delta -= 360;
        while (delta < -180) delta += 360;
        if (delta > TURN_RATE) delta = TURN_RATE;
        if (delta < -TURN_RATE) delta = -TURN_RATE;

        angle += (float) delta;
        angle = angle % 360;

        double rad = Math.toRadians(angle);
        vx = (float)(Math.cos(rad) * speed);
        vy = (float)(Math.sin(rad) * speed);

        super.update();
    }

    public void explode() {
        alive = false;
        exploded = true;
    }

    public boolean inBlastRadius(float tx, float ty) {
        float dx = tx - x, dy = ty - y;
        return dx * dx + dy * dy <= BLAST_RADIUS * BLAST_RADIUS;
    }

    public boolean isAlive() { return alive; }
    public boolean hasExploded() { return exploded; }
    public int getDamage() { return DAMAGE; }
    public float getBlastRadius() { return BLAST_RADIUS; }
    public String getOwnerId() { return ownerId; }

    @Override
    public void draw(GameEngine engine) {
        if (!alive) return;
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y);

        double rad = Math.toRadians(angle);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        int SEG = 12;
        double[] bx = new double[SEG];
        double[] by = new double[SEG];
        for (int i = 0; i < SEG; i++) {
            double t = 2 * Math.PI * i / SEG;
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

    @Override public String serialize() { return ""; }
    @Override public String getType() { return "TORPEDO"; }
    @Override public String toString() { return "Torpedo[pos=(" + x + "," + y + ")]"; }
}