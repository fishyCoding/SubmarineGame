import java.awt.Color;
import java.util.*;

/**
 * Rock — an underwater polygon sprite with procedural silhouette rendering.
 *
 * The old Polygon base class has been folded in here: Rock now owns all
 * vertex management, rendering, collision, and serialization logic directly.
 *
 * Depth layers:
 *   0 = background (darker, less rough)
 *   1 = foreground (lighter, rougher)
 */
public class Rock extends Sprite {

    // ── Vertex storage ─────────────────────────────────────────────────────────
    private final List<Float> vertices;
    private int               depth;
    private boolean           closed;

    // ── Palette ────────────────────────────────────────────────────────────────
    private static final Color FG_BASE   = Color.decode("#19202A");
    private static final Color FG_SHADOW = Color.decode("#0F161E");
    private static final Color FG_HILIT  = Color.decode("#2A3646");

    private static final Color BG_BASE   = Color.decode("#101316");
    private static final Color BG_SHADOW = Color.decode("#0c0f12");
    private static final Color BG_HILIT  = Color.decode("#1e2126");

    private static final int OUTLINE_ALPHA_APPROX = 0x37;

    // ── Constructors ───────────────────────────────────────────────────────────

    public Rock(float startX, float startY, int depth) {
        super(startX, startY, new Color(170, 170, 170));
        this.vertices = new ArrayList<>();
        this.depth    = Math.max(0, Math.min(1, depth));
        this.closed   = false;
        addVertex(startX, startY);
    }

    public Rock(float startX, float startY, int r, int g, int b, int depth) {
        super(startX, startY, new Color(clampC(r), clampC(g), clampC(b)));
        this.vertices = new ArrayList<>();
        this.depth    = Math.max(0, Math.min(1, depth));
        this.closed   = false;
        addVertex(startX, startY);
    }

    private static int clampC(int v) { return Math.max(0, Math.min(255, v)); }

    // ── Vertex API ─────────────────────────────────────────────────────────────

    public void addVertex(float x, float y)  { vertices.add(x); vertices.add(y); }
    public void closePath()                  { closed = true; }

    public void removeLastVertex() {
        if (vertices.size() >= 4) {
            vertices.remove(vertices.size() - 1);
            vertices.remove(vertices.size() - 1);
        }
    }

    public List<Float> getVertices()  { return new ArrayList<>(vertices); }
    protected void    clearVertices() { vertices.clear(); }

    public int     getDepth()       { return depth; }
    public boolean isClosed()       { return closed; }
    public int     getVertexCount() { return vertices.size() / 2; }

    /** Returns [minX, maxX, minY, maxY] in world coordinates. */
    public float[] getBounds() {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < vertices.size(); i += 2) {
            minX = Math.min(minX, vertices.get(i));
            maxX = Math.max(maxX, vertices.get(i));
            minY = Math.min(minY, vertices.get(i + 1));
            maxY = Math.max(maxY, vertices.get(i + 1));
        }
        return new float[]{minX, maxX, minY, maxY};
    }

    // ── Collision ──────────────────────────────────────────────────────────────

    /** Point-in-polygon test via ray casting. */
    @Override
    public boolean contains(float px, float py) {
        if (vertices.size() < 6) return false;
        int     count  = vertices.size() / 2;
        boolean inside = false;
        for (int i = 0, j = count - 1; i < count; j = i++) {
            float xi = vertices.get(i * 2),     yi = vertices.get(i * 2 + 1);
            float xj = vertices.get(j * 2),     yj = vertices.get(j * 2 + 1);
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi)
                inside = !inside;
        }
        return inside;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void draw(GameEngine engine) {
        if (vertices.size() < 6) return;

        float      roughness = depth == 0 ? 3.5f : 7f;
        double[][] shape     = buildRockSilhouette(vertices, roughness, engine);
        double[]   xs        = shape[0];
        double[]   ys        = shape[1];

        Color base   = depth == 1 ? FG_BASE   : BG_BASE;
        Color shadow = depth == 1 ? FG_SHADOW : BG_SHADOW;
        Color hilit  = depth == 1 ? FG_HILIT  : BG_HILIT;

        drawOffsetFill(xs, ys, shadow, 4, -3);          // 1. drop-shadow
        StdDraw.setPenColor(base);
        StdDraw.filledPolygon(xs, ys);                  // 2. main fill
        drawHighlight(xs, ys, hilit);                   // 3. interior highlight
        Color outlineColor = new Color(
            Math.max(0, shadow.getRed()   - OUTLINE_ALPHA_APPROX),
            Math.max(0, shadow.getGreen() - OUTLINE_ALPHA_APPROX),
            Math.max(0, shadow.getBlue()  - OUTLINE_ALPHA_APPROX)
        );
        drawOutline(xs, ys, outlineColor);              // 4. crisp outline
    }

    // ── Private drawing helpers ────────────────────────────────────────────────

    private static void drawOffsetFill(double[] xs, double[] ys,
                                       Color c, double dx, double dy) {
        double[] sxs = new double[xs.length];
        double[] sys = new double[ys.length];
        for (int i = 0; i < xs.length; i++) { sxs[i] = xs[i] + dx; sys[i] = ys[i] + dy; }
        StdDraw.setPenColor(c);
        StdDraw.filledPolygon(sxs, sys);
    }

    private static void drawHighlight(double[] xs, double[] ys, Color hilit) {
        double cx = 0, cy = 0;
        for (double v : xs) cx += v;  cx /= xs.length;
        for (double v : ys) cy += v;  cy /= ys.length;

        double[] hxs = new double[xs.length];
        double[] hys = new double[ys.length];

        for (int i = 0; i < xs.length; i++) {
            hxs[i] = cx + (xs[i] - cx) * 0.72 - 3;
            hys[i] = cy + (ys[i] - cy) * 0.72 + 3;
        }
        StdDraw.setPenColor(hilit);
        StdDraw.filledPolygon(hxs, hys);

        for (int i = 0; i < xs.length; i++) {
            hxs[i] = cx + (xs[i] - cx) * 0.38 - 2;
            hys[i] = cy + (ys[i] - cy) * 0.38 + 2;
        }
        Color coreHilit = new Color(
            Math.min(255, hilit.getRed()   + 30),
            Math.min(255, hilit.getGreen() + 30),
            Math.min(255, hilit.getBlue()  + 30)
        );
        StdDraw.setPenColor(coreHilit);
        StdDraw.filledPolygon(hxs, hys);
    }

    private static void drawOutline(double[] xs, double[] ys, Color c) {
        int vCount = xs.length;
        StdDraw.setPenColor(c);
        StdDraw.setPenRadius(0.005);
        for (int i = 0; i < vCount; i++) {
            int next = (i + 1) % vCount;
            StdDraw.line(xs[i], ys[i], xs[next], ys[next]);
        }
        Color innerRim = new Color(
            Math.min(255, c.getRed()   + 25),
            Math.min(255, c.getGreen() + 25),
            Math.min(255, c.getBlue()  + 25)
        );
        StdDraw.setPenColor(innerRim);
        StdDraw.setPenRadius(0.002);
        for (int i = 0; i < vCount; i++) {
            int next = (i + 1) % vCount;
            StdDraw.line(xs[i], ys[i], xs[next], ys[next]);
        }
        StdDraw.setPenRadius(0.002);
    }

    /**
     * Subdivides each edge into {@code segments} pieces and displaces each
     * sub-point outward along the edge normal using multi-octave sine noise,
     * producing an organic rock silhouette from a rough convex hull.
     */
    static double[][] buildRockSilhouette(List<Float> vertices,
                                          float roughness,
                                          GameEngine engine) {
        int    count    = vertices.size() / 2;
        int    segments = 5;
        int    total    = count * segments;
        double[] xs     = new double[total];
        double[] ys     = new double[total];
        int idx = 0;

        for (int i = 0; i < count; i++) {
            float x1 = vertices.get(i * 2),            y1 = vertices.get(i * 2 + 1);
            float x2 = vertices.get(((i+1)%count) * 2), y2 = vertices.get(((i+1)%count) * 2 + 1);
            double dx = x2 - x1, dy = y2 - y1;
            double len = Math.hypot(dx, dy);
            double nx  = len > 0 ? -dy / len : 0;
            double ny  = len > 0 ?  dx / len : 0;

            for (int s = 0; s < segments; s++) {
                double t     = s / (double) segments;
                double px    = x1 + dx * t;
                double py    = y1 + dy * t;
                double phase = (x1 + y1) * 0.03 + i * 1.618 + t * Math.PI * 2;
                double noise = Math.sin(phase)           * roughness
                             + Math.cos(phase * 1.7 + 1) * roughness * 0.4
                             + Math.sin(phase * 3.1 + 2) * roughness * 0.15;
                px += nx * noise;
                py += ny * noise;
                xs[idx] = engine.worldToScreenX((float) px);
                ys[idx] = engine.worldToScreenY((float) py);
                idx++;
            }
        }
        return new double[][]{xs, ys};
    }

    // ── Serialization ──────────────────────────────────────────────────────────

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROCK ").append(depth).append(" ").append(getVertexCount());
        for (float v : vertices) sb.append(" ").append(String.format("%.1f", v));
        sb.append(" ").append(getR()).append(" ").append(getG()).append(" ").append(getB());
        return sb.toString();
    }

    public static Rock deserialize(String line) {
        try {
            String[] p = line.trim().split("\\s+");
            if (p.length < 6) return null;
            int depth       = Integer.parseInt(p[1]);
            int vertexCount = Integer.parseInt(p[2]);
            if (p.length < 3 + vertexCount * 2 + 3) return null;

            Rock rock = new Rock(Float.parseFloat(p[3]), Float.parseFloat(p[4]), depth);
            rock.clearVertices();

            int idx = 3;
            for (int i = 0; i < vertexCount; i++) {
                rock.addVertex(Float.parseFloat(p[idx++]), Float.parseFloat(p[idx++]));
            }
            rock.setColor(Integer.parseInt(p[idx]),
                          Integer.parseInt(p[idx + 1]),
                          Integer.parseInt(p[idx + 2]));
            rock.closePath();
            return rock;
        } catch (Exception e) { return null; }
    }

    @Override
    public String getType() { return "ROCK"; }

    @Override
    public String toString() {
        return String.format("Rock(%d vertices, depth=%d, color=RGB(%d,%d,%d))",
                getVertexCount(), depth, getR(), getG(), getB());
    }
}