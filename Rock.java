import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Rock — an image-based sprite with rotation and scale transforms.
 *
 * Performance strategy:
 *   1. SOURCE CACHE (static)  — each PNG is loaded from disk exactly once.
 *   2. ROTATED CACHE (per-instance) — the rotated+scaled BufferedImage is
 *      pre-rendered into a per-rock BufferedImage and only recalculated when
 *      rotation or scale actually changes (i.e. during editor drag, never
 *      during normal gameplay). Drawing becomes a single Graphics2D.drawImage()
 *      call with no per-frame transform math.
 */
public class Rock extends Sprite {

    // ── Static source cache ────────────────────────────────────────────────────
    private static final String IMAGE_PATH      = "rock1.png";
    private static final String IMAGE_PATH_DARK = "rock1dark.png";

    private static final Map<String, BufferedImage> SOURCE_CACHE = new HashMap<>();

    static {
        loadAndCache(IMAGE_PATH);
        loadAndCache(IMAGE_PATH_DARK);
    }

    private static void loadAndCache(String path) {
        try {
            File f = new File(path);
            if (f.exists()) {
                SOURCE_CACHE.put(path, toARGB(ImageIO.read(f)));
                System.out.println("Cached image: " + path);
            } else {
                System.err.println("Warning: " + path + " not found — using placeholder");
                SOURCE_CACHE.put(path, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
            }
        } catch (Exception e) {
            System.err.println("Error loading " + path + ": " + e.getMessage());
            SOURCE_CACHE.put(path, new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
        }
    }

    /** Ensures the image is TYPE_INT_ARGB so Graphics2D compositing works correctly. */
    private static BufferedImage toARGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                                             BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage source(String path) {
        BufferedImage img = SOURCE_CACHE.get(path);
        return (img != null) ? img : SOURCE_CACHE.get(IMAGE_PATH);
    }

    // ── Instance properties ────────────────────────────────────────────────────
    private float rotation;
    private float scaleX;
    private float scaleY;
    private int   depth;

    // ── Per-instance rotated image cache ──────────────────────────────────────
    // Rebuilt only when rotation, scale, or depth changes — never every frame.
    private BufferedImage rotatedCache;
    private float         cachedRotation = Float.NaN;
    private float         cachedScaleX   = Float.NaN;
    private float         cachedScaleY   = Float.NaN;
    private int           cachedDepth    = -1;

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

    public void setRotation(float r)   { this.rotation = r % 360; }
    public void addRotation(float d)   { this.rotation = (rotation + d) % 360; }
    public float getRotation()         { return rotation; }

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

    public int  getDepth()      { return depth; }
    public void setDepth(int d) { this.depth = Math.max(0, Math.min(1, d)); }

    // ── Rotated cache builder ─────────────────────────────────────────────────

    /**
     * Rebuilds rotatedCache only when something actually changed.
     * During gameplay nothing changes, so this never runs after load.
     * During editor drag it runs once per drag tick for the selected rock only.
     */
    private void rebuildCacheIfNeeded() {
        if (rotation == cachedRotation
                && scaleX  == cachedScaleX
                && scaleY  == cachedScaleY
                && depth   == cachedDepth
                && rotatedCache != null) {
            return; // nothing changed — reuse existing cache
        }

        String        srcPath = (depth == 0) ? IMAGE_PATH_DARK : IMAGE_PATH;
        BufferedImage src     = source(srcPath);

        int srcW = src.getWidth();
        int srcH = src.getHeight();

        // Scaled dimensions
        int dstW = Math.max(1, (int) Math.abs(srcW * scaleX));
        int dstH = Math.max(1, (int) Math.abs(srcH * scaleY));

        // After rotation the bounding box grows — compute its size
        double rad  = Math.toRadians(rotation);
        double cos  = Math.abs(Math.cos(rad));
        double sin  = Math.abs(Math.sin(rad));
        int    outW = (int) Math.ceil(dstW * cos + dstH * sin);
        int    outH = (int) Math.ceil(dstW * sin + dstH * cos);

        rotatedCache = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = rotatedCache.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        // Translate to centre, rotate, translate back, then draw scaled source
        g.translate(outW / 2.0, outH / 2.0);
        g.rotate(Math.toRadians(rotation));
        g.drawImage(src, -dstW / 2, -dstH / 2, dstW, dstH, null);
        g.dispose();

        cachedRotation = rotation;
        cachedScaleX   = scaleX;
        cachedScaleY   = scaleY;
        cachedDepth    = depth;
    }

    // ── Collision ──────────────────────────────────────────────────────────────

    public float[] getBounds() {
        BufferedImage img = source(IMAGE_PATH);
        float halfW = img.getWidth()  * scaleX / 2;
        float halfH = img.getHeight() * scaleY / 2;
        return new float[]{ x - halfW, x + halfW, y - halfH, y + halfH };
    }

    @Override
    public boolean contains(float px, float py) {
        BufferedImage img = source(IMAGE_PATH);
        float dx     = px - x;
        float dy     = py - y;
        float radius = Math.max(img.getWidth() * scaleX, img.getHeight() * scaleY) / 2;
        return dx * dx + dy * dy <= radius * radius;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void draw(GameEngine engine) {
        rebuildCacheIfNeeded();

        double screenX = engine.worldToScreenX(x);
        double screenY = engine.worldToScreenY(y);

        // Draw pre-rotated image directly onto StdDraw's offscreen canvas via
        // reflection — bypasses StdDraw.picture()'s per-frame rotation math entirely.
        // Falls back to StdDraw.picture() if internals aren't accessible.
        try {
            java.lang.reflect.Field offscreenField =
                    StdDraw.class.getDeclaredField("offscreenImage");
            offscreenField.setAccessible(true);
            BufferedImage canvas = (BufferedImage) offscreenField.get(null);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                               RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // StdDraw Y is flipped: 0 = bottom. Java2D Y: 0 = top.
            // Convert centre then offset by half the cached image size.
            int canvasH = canvas.getHeight();
            int drawX   = (int) Math.round(screenX - rotatedCache.getWidth()  / 2.0);
            int drawY   = canvasH - (int) Math.round(screenY)
                          - (int) Math.ceil(rotatedCache.getHeight() / 2.0);

            g.drawImage(rotatedCache, drawX, drawY, null);
            g.dispose();
        } catch (Exception e) {
            // Reflection unavailable — fall back to StdDraw.picture()
            String path = (depth == 0) ? IMAGE_PATH_DARK : IMAGE_PATH;
            BufferedImage img = source(path);
            double screenW = Math.max(Math.abs(img.getWidth()  * scaleX), 1.0);
            double screenH = Math.max(Math.abs(img.getHeight() * scaleY), 1.0);
            StdDraw.picture(screenX, screenY, path, screenW, screenH, rotation);
        }
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
            if (p[0].equals("ROCK")) return null;
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