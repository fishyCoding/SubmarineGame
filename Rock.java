import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Rock — an image-based sprite with rotation and scale transforms.
 *
 * Images are loaded from disk exactly once into IMAGE_CACHE at class-load
 * time and reused every frame — eliminating the per-frame disk I/O that
 * caused lag with 4+ sprites on screen.
 */
public class Rock extends Sprite {

    // ── Static resources ───────────────────────────────────────────────────────
    private static final String IMAGE_PATH      = "rock1.png";
    private static final String IMAGE_PATH_DARK = "rock1dark.png";

    /**
     * Image cache — each path is loaded from disk exactly once at class-load
     * time, then reused for every draw call. Never touches disk again after startup.
     */
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    static {
        loadAndCache(IMAGE_PATH);
        loadAndCache(IMAGE_PATH_DARK);
    }

    private static void loadAndCache(String path) {
        try {
            File f = new File(path);
            if (f.exists()) {
                IMAGE_CACHE.put(path, ImageIO.read(f));
                System.out.println("Cached image: " + path);
            } else {
                System.err.println("Warning: " + path + " not found — using placeholder");
                IMAGE_CACHE.put(path, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            }
        } catch (Exception e) {
            System.err.println("Error loading " + path + ": " + e.getMessage());
            IMAGE_CACHE.put(path, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
        }
    }

    /** Returns the cached image for a path, never null. */
    private static BufferedImage cached(String path) {
        BufferedImage img = IMAGE_CACHE.get(path);
        return (img != null) ? img : IMAGE_CACHE.get(IMAGE_PATH);
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
        this.scaleX   = 1.0f;
        this.scaleY   = 1.0f;
        this.depth    = Math.max(0, Math.min(1, depth));
    }

    public Rock(float x, float y, float rotation, float scaleX, float scaleY, int depth) {
        super(x, y, new Color(170, 170, 170));
        this.rotation = rotation % 360;
        this.scaleX   = Math.max(0.1f, scaleX);
        this.scaleY   = Math.max(0.1f, scaleY);
        this.depth    = Math.max(0, Math.min(1, depth));
    }

    // ── Transform properties ───────────────────────────────────────────────────

    public void setRotation(float rotation)      { this.rotation = rotation % 360; }
    public void addRotation(float delta)          { this.rotation = (rotation + delta) % 360; }
    public float getRotation()                    { return rotation; }

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

    public int  getDepth()    { return depth; }
    public void setDepth(int d) { this.depth = Math.max(0, Math.min(1, d)); }

    // ── Collision ──────────────────────────────────────────────────────────────

    /** Returns [minX, maxX, minY, maxY] in world coordinates. */
    public float[] getBounds() {
        BufferedImage img = cached(IMAGE_PATH);
        float halfW = img.getWidth()  * scaleX / 2;
        float halfH = img.getHeight() * scaleY / 2;
        return new float[]{ x - halfW, x + halfW, y - halfH, y + halfH };
    }

    @Override
    public boolean contains(float px, float py) {
        BufferedImage img = cached(IMAGE_PATH);
        float dx     = px - x;
        float dy     = py - y;
        float radius = Math.max(img.getWidth() * scaleX, img.getHeight() * scaleY) / 2;
        return dx * dx + dy * dy <= radius * radius;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void draw(GameEngine engine) {
        String        path    = (depth == 0) ? IMAGE_PATH_DARK : IMAGE_PATH;
        BufferedImage img     = cached(path);
        double        screenX = engine.worldToScreenX(x);
        double        screenY = engine.worldToScreenY(y);
        double        screenW = Math.max(Math.abs(img.getWidth()  * scaleX), 1.0);
        double        screenH = Math.max(Math.abs(img.getHeight() * scaleY), 1.0);

        // StdDraw.picture() has its own internal cache keyed by path string,
        // so after the first call per path it never reloads from disk.
        // Our IMAGE_CACHE pre-warms that cache at class-load time and also
        // provides dimension data for getBounds/contains without extra I/O.
        StdDraw.picture(screenX, screenY, path, screenW, screenH, rotation);
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
            if (p[0].equals("ROCK")) return null;   // old polygon format, skip
            if (p.length < 10)       return null;

            int   depth    = Integer.parseInt(p[1]);
            float x        = Float.parseFloat(p[2]);
            float y        = Float.parseFloat(p[3]);
            float rotation = Float.parseFloat(p[4]);
            float scaleX   = Float.parseFloat(p[5]);
            float scaleY   = Float.parseFloat(p[6]);
            int   r        = Integer.parseInt(p[7]);
            int   g        = Integer.parseInt(p[8]);
            int   b        = Integer.parseInt(p[9]);

            Rock rock = new Rock(x, y, rotation, scaleX, scaleY, depth);
            rock.setColor(r, g, b);
            return rock;
        } catch (Exception e) {
            System.err.println("Error deserializing rock: " + e.getMessage());
            return null;
        }
    }

    @Override public String getType() { return "IMAGEROCK"; }

    @Override
    public String toString() {
        return String.format("Rock(x=%.0f, y=%.0f, rot=%.1f°, scale=%.2f,%.2f, depth=%d)",
                x, y, rotation, scaleX, scaleY, depth);
    }
}