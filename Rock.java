import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Rock — an image-based sprite with rotation and scale transforms.
 *
 * Each rock is defined by:
 *   - Position (x, y)
 *   - Rotation (angle in degrees)
 *   - Scale (scaleX, scaleY)
 *   - Depth layer (0 = background, 1 = foreground)
 *   - Color tint (for radar)
 *
 * The rock image is loaded from rock1.png and rendered with the transforms applied.
 */
public class Rock extends Sprite {

    // ── Static resources ───────────────────────────────────────────────────────
    private static BufferedImage rockImage;
    private static final String IMAGE_PATH      = "rock1.png";
    private static final String IMAGE_PATH_DARK = "rock1dark.png";

    static {
        try {
            File imageFile = new File(IMAGE_PATH);
            if (imageFile.exists()) {
                rockImage = ImageIO.read(imageFile);
            } else {
                System.err.println("Warning: " + IMAGE_PATH + " not found");
                // Create a placeholder image if file not found
                rockImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            }
        } catch (Exception e) {
            System.err.println("Error loading rock image: " + e.getMessage());
            rockImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        }
    }

    // ── Instance properties ────────────────────────────────────────────────────
    private float rotation; // in degrees, 0-360
    private float scaleX;
    private float scaleY;
    private int   depth;    // 0 = background, 1 = foreground

    // ── Constructors ───────────────────────────────────────────────────────────

    public Rock(float x, float y, int depth) {
        super(x, y, new Color(170, 170, 170));
        this.rotation = 0;
        this.scaleX = 1.0f;
        this.scaleY = 1.0f;
        this.depth = Math.max(0, Math.min(1, depth));
    }

    public Rock(float x, float y, float rotation, float scaleX, float scaleY, int depth) {
        super(x, y, new Color(170, 170, 170));
        this.rotation = rotation % 360;
        this.scaleX = Math.max(0.1f, scaleX);
        this.scaleY = Math.max(0.1f, scaleY);
        this.depth = Math.max(0, Math.min(1, depth));
    }

    // ── Transform properties ───────────────────────────────────────────────────

    public void setRotation(float rotation) {
        this.rotation = rotation % 360;
    }

    public void addRotation(float deltaRotation) {
        this.rotation = (rotation + deltaRotation) % 360;
    }

    public float getRotation() { return rotation; }

    public void setScale(float sx, float sy) {
        this.scaleX = Math.max(0.1f, sx);
        this.scaleY = Math.max(0.1f, sy);
    }

    public void multiplyScale(float sx, float sy) {
        this.scaleX = Math.max(0.1f, scaleX * sx);
        this.scaleY = Math.max(0.1f, scaleY * sy);
    }

    public float getScaleX() { return scaleX; }
    public float getScaleY() { return scaleY; }

    public int getDepth() { return depth; }
    public void setDepth(int d) { this.depth = Math.max(0, Math.min(1, d)); }

    // ── Collision ──────────────────────────────────────────────────────────────

    /**
     * Returns approximate bounds considering rotation and scale.
     * Returns [minX, maxX, minY, maxY] in world coordinates.
     */
    public float[] getBounds() {
        float width = rockImage.getWidth() * scaleX;
        float height = rockImage.getHeight() * scaleY;

        // Approximate bounds (doesn't account for rotation perfectly)
        float halfW = width / 2;
        float halfH = height / 2;

        float minX = x - halfW;
        float maxX = x + halfW;
        float minY = y - halfH;
        float maxY = y + halfH;

        return new float[]{minX, maxX, minY, maxY};
    }

    @Override
    public boolean contains(float px, float py) {
        // Simple circle collision for now
        float dx = px - x;
        float dy = py - y;
        float radius = Math.max(rockImage.getWidth() * scaleX,
                                rockImage.getHeight() * scaleY) / 2;
        return dx * dx + dy * dy <= radius * radius;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void draw(GameEngine engine) {
        double screenX = engine.worldToScreenX(x);
        double screenY = engine.worldToScreenY(y);
        double screenW = Math.abs(rockImage.getWidth()  * scaleX);
        double screenH = Math.abs(rockImage.getHeight() * scaleY);

        screenW = Math.max(screenW, 1.0);
        screenH = Math.max(screenH, 1.0);

        String img = (depth == 0) ? IMAGE_PATH_DARK : IMAGE_PATH;
        StdDraw.picture(screenX, screenY, img, screenW, screenH, rotation);
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    @Override
    public String serialize() {
        return String.format("IMAGEROCK %d %.1f %.1f %.1f %.2f %.2f %d %d %d",
                depth, x, y, rotation, scaleX, scaleY, getR(), getG(), getB());
    }

    public static Rock deserialize(String line) {
        try {
            String[] p = line.trim().split("\\s+");

            // Handle old ROCK format for backwards compatibility
            if (p[0].equals("ROCK")) {
                int depth = Integer.parseInt(p[1]);
                int vertexCount = Integer.parseInt(p[2]);
                // Skip loading old polygon rocks - they're now image-based
                return null;
            }

            // Parse new IMAGEROCK format
            if (p.length < 9) return null;

            int depth = Integer.parseInt(p[1]);
            float x = Float.parseFloat(p[2]);
            float y = Float.parseFloat(p[3]);
            float rotation = Float.parseFloat(p[4]);
            float scaleX = Float.parseFloat(p[5]);
            float scaleY = Float.parseFloat(p[6]);
            int r = Integer.parseInt(p[7]);
            int g = Integer.parseInt(p[8]);
            int b = Integer.parseInt(p[9]);

            Rock rock = new Rock(x, y, rotation, scaleX, scaleY, depth);
            rock.setColor(r, g, b);
            return rock;
        } catch (Exception e) {
            System.err.println("Error deserializing rock: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getType() { return "IMAGEROCK"; }

    @Override
    public String toString() {
        return String.format("Rock(x=%.0f, y=%.0f, rot=%.1f°, scale=%.2f,%.2f, depth=%d)",
                x, y, rotation, scaleX, scaleY, depth);
    }
}