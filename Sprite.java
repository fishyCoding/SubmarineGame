/**
 * Abstract base class for all drawable game objects.
 * Subclasses implement rendering, collision, and serialization.
 */
public abstract class Sprite {
    protected float x;
    protected float y;
    protected int r, g, b;

    public Sprite(float x, float y, int r, int g, int b) {
        this.x = x;
        this.y = y;
        setColor(r, g, b);
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public int getR()   { return r; }
    public int getG()   { return g; }
    public int getB()   { return b; }

    public void setX(float x)         { this.x = x; }
    public void setY(float y)         { this.y = y; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }

    public void setColor(int r, int g, int b) {
        this.r = clamp(r);
        this.g = clamp(g);
        this.b = clamp(b);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** Draw this sprite to the screen using the given camera offsets. */
    public abstract void draw(GameEngine engine);

    /** Point-in-shape test for click/collision detection. */
    public abstract boolean contains(float px, float py);

    /** Serialize to a single text line for file storage. */
    public abstract String serialize();

    /** Short type tag used in serialization (e.g. "ROCK", "SQUARE"). */
    public abstract String getType();

    @Override
    public abstract String toString();
}