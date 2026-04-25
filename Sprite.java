import java.awt.Color;

/**
 * Abstract base class for all drawable game objects.
 * Subclasses implement rendering, collision, and serialization.
 */
public abstract class Sprite {
    protected float x;
    protected float y;
    protected Color color; // Use a single Color object

    // Constructor for RGB ints (converts them to Color)
    public Sprite(float x, float y, int r, int g, int b) {
        this.x = x;
        this.y = y;
        this.color = new Color(clamp(r), clamp(g), clamp(b));
    }

    // Constructor for Color object
    public Sprite(float x, float y, Color color) {
        this.x = x;
        this.y = y;
        this.color = (color != null) ? color : Color.WHITE;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    
    // Updated getters to pull from the Color object
    public Color getColor() { return color; }
    public int getR()   { return color.getRed(); }
    public int getG()   { return color.getGreen(); }
    public int getB()   { return color.getBlue(); }

    public void setX(float x)         { this.x = x; }
    public void setY(float y)         { this.y = y; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }

    public void setColor(Color color) { this.color = color; }
    public void setColor(int r, int g, int b) {
        this.color = new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public abstract void draw(GameEngine engine);
    public abstract boolean contains(float px, float py);
    public abstract String serialize();
    public abstract String getType();

    @Override
    public abstract String toString();
}