/**
 * Square class - a rectangular sprite for the game engine.
 * Extends the abstract Sprite class with width and height properties.
 * Used for obstacles, static objects, etc. in the submarine game.
 */
public class Square extends Sprite {
    private float width;
    private float height;

    public Square(float x, float y, float width, float height, int r, int g, int b) {
        super(x, y, r, g, b);
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    // Getters
    public float getWidth() { return width; }
    public float getHeight() { return height; }

    // Setters
    public void setWidth(float width) { this.width = Math.max(1, width); }
    public void setHeight(float height) { this.height = Math.max(1, height); }
    public void setSize(float width, float height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    /**
     * Check if a point is within this square's bounds
     */
    @Override
    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Serialize square to text format
     * Format: SQUARE x y width height r g b
     */
    @Override
    public String serialize() {
        return String.format("SQUARE %.1f %.1f %.1f %.1f %d %d %d",
                x, y, width, height, r, g, b);
    }

    /**
     * Deserialize square from text line
     */
    public static Square deserialize(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) return null;
            
            float x = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float width = Float.parseFloat(parts[3]);
            float height = Float.parseFloat(parts[4]);
            int r = Integer.parseInt(parts[5]);
            int g = Integer.parseInt(parts[6]);
            int b = Integer.parseInt(parts[7]);
            
            return new Square(x, y, width, height, r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getType() {
        return "SQUARE";
    }

    @Override
    public String toString() {
        return String.format("Square at (%.0f, %.0f) size %.0f x %.0f color RGB(%d,%d,%d)",
                x, y, width, height, r, g, b);
    }
}
