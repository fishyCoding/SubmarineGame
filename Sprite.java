/**
 * Abstract Sprite class represents a generic drawable game object.
 * All sprites have position and color. Subclasses implement specific behavior.
 * Suitable for submarine game: submarines, torpedoes, obstacles, etc.
 */
public abstract class Sprite {
    protected float x;
    protected float y;
    protected int r; // red component
    protected int g; // green component
    protected int b; // blue component

    public Sprite(float x, float y, int r, int g, int b) {
        this.x = x;
        this.y = y;
        this.r = Math.max(0, Math.min(255, r));
        this.g = Math.max(0, Math.min(255, g));
        this.b = Math.max(0, Math.min(255, b));
    }

    // Getters
    public float getX() { return x; }
    public float getY() { return y; }
    public int getR() { return r; }
    public int getG() { return g; }
    public int getB() { return b; }

    // Setters
    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
    public void setColor(int r, int g, int b) {
        this.r = Math.max(0, Math.min(255, r));
        this.g = Math.max(0, Math.min(255, g));
        this.b = Math.max(0, Math.min(255, b));
    }

    /**
     * Check if a point is within this sprite's bounds (for click detection)
     * Abstract - subclasses define their own collision
     */
    public abstract boolean contains(float px, float py);

    /**
     * Serialize sprite to text format for file storage
     * Abstract - each sprite type defines its own format
     */
    public abstract String serialize();

    /**
     * Get the type identifier for this sprite
     */
    public abstract String getType();

    @Override
    public abstract String toString();
}
