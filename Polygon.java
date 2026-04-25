import java.util.*;

/**
 * Polygon sprite for underwater rock structures and terrain.
 * Uses vertex-based polygons with two depth layers:
 *   depth 0 = background (far, darker)
 *   depth 1 = foreground (close, brighter, collidable)
 *
 * All rendering lives here — no drawing logic leaks into Main or GameEngine.
 */
public class Polygon extends Sprite {

    private final List<Float> vertices; // flat [x0,y0, x1,y1, ...]
    private int   depth;               // 0 = background, 1 = foreground
    private boolean closed;

    // ── Palette ────────────────────────────────────────────────────────────────
    // Foreground layer: cool blue-grey
    private static final int[] FG_BASE   = {160, 165, 180};
    private static final int[] FG_SHADOW = {100, 105, 118};
    private static final int[] FG_HILIT  = {205, 210, 225};

    // Background layer: deeper, more blue
    private static final int[] BG_BASE   = { 60,  72,  92};
    private static final int[] BG_SHADOW = { 38,  48,  63};
    private static final int[] BG_HILIT  = { 85, 100, 120};

    // Outline tint (semi-transparent illusion via darker edge)
    private static final int OUTLINE_ALPHA_APPROX = 55; // subtracted from base

    // ── Construction ───────────────────────────────────────────────────────────

    public Polygon(float startX, float startY, int r, int g, int b, int depth) {
        super(startX, startY, r, g, b);
        this.vertices = new ArrayList<>();
        this.depth    = Math.max(0, Math.min(1, depth));
        this.closed   = false;
        addVertex(startX, startY);
    }

    // ── Vertex management ──────────────────────────────────────────────────────

    public void addVertex(float x, float y)  { vertices.add(x); vertices.add(y); }
    public void closePath()                  { closed = true; }
    public void removeLastVertex() {
        if (vertices.size() >= 4) {  // keep at least first vertex
            vertices.remove(vertices.size() - 1);
            vertices.remove(vertices.size() - 1);
        }
    }

    public List<Float> getVertices()  { return new ArrayList<>(vertices); }
    protected void    clearVertices() { vertices.clear(); }

    public int  getDepth()       { return depth; }
    public boolean isClosed()    { return closed; }
    public int  getVertexCount() { return vertices.size() / 2; }

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

    @Override
    public boolean contains(float px, float py) {
        if (vertices.size() < 6) return false;
        int  count  = vertices.size() / 2;
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

    /**
     * Draw this polygon in full stylized rock style.
     * Call this from your render loop — it handles fill, outline, and texture.
     */
    @Override
    public void draw(GameEngine engine) {
        if (vertices.size() < 6) return;

        float    roughness = depth == 0 ? 3.5f : 7f;
        double[] xs, ys;

        // Build the jagged rock silhouette
        double[][] shape = buildRockSilhouette(vertices, roughness, engine);
        xs = shape[0];
        ys = shape[1];

        int[] base   = depth == 1 ? FG_BASE   : BG_BASE;
        int[] shadow = depth == 1 ? FG_SHADOW : BG_SHADOW;
        int[] hilit  = depth == 1 ? FG_HILIT  : BG_HILIT;

        // 1. Drop-shadow (slight offset, darkest tone)
        drawOffsetFill(xs, ys, shadow[0], shadow[1], shadow[2], 4, -3);

        // 2. Main rock fill
        StdDraw.setPenColor(base[0], base[1], base[2]);
        StdDraw.filledPolygon(xs, ys);

        // 3. Interior highlight (upper-left bevel illusion)
        drawHighlight(xs, ys, hilit);

        // 4. Rock surface texture (cracks / pits)
        drawRockTexture(xs, ys, base, shadow);

        // 5. Crisp outline — the key improvement
        drawOutline(xs, ys, shadow[0] - OUTLINE_ALPHA_APPROX,
                             shadow[1] - OUTLINE_ALPHA_APPROX,
                             shadow[2] - OUTLINE_ALPHA_APPROX);
    }

    // ── Private drawing helpers ────────────────────────────────────────────────

    /** Offset-fill pass to create a cheap drop-shadow. */
    private static void drawOffsetFill(double[] xs, double[] ys,
                                       int r, int g, int b,
                                       double dx, double dy) {
        double[] sxs = new double[xs.length];
        double[] sys = new double[ys.length];
        for (int i = 0; i < xs.length; i++) { sxs[i] = xs[i] + dx; sys[i] = ys[i] + dy; }
        StdDraw.setPenColor(r, g, b);
        StdDraw.filledPolygon(sxs, sys);
    }

    /** Upper-left quadrant bevel highlight. */
    private static void drawHighlight(double[] xs, double[] ys, int[] hilit) {
        // Compute centroid
        double cx = 0, cy = 0;
        for (double v : xs) cx += v;  cx /= xs.length;
        for (double v : ys) cy += v;  cy /= ys.length;

        // Shrink slightly toward centroid and shift up-left
        double[] hxs = new double[xs.length];
        double[] hys = new double[ys.length];
        for (int i = 0; i < xs.length; i++) {
            hxs[i] = cx + (xs[i] - cx) * 0.72 - 3;
            hys[i] = cy + (ys[i] - cy) * 0.72 + 3;
        }
        StdDraw.setPenColor(hilit[0], hilit[1], hilit[2]);
        StdDraw.filledPolygon(hxs, hys);

        // Tighter core highlight
        for (int i = 0; i < xs.length; i++) {
            hxs[i] = cx + (xs[i] - cx) * 0.38 - 2;
            hys[i] = cy + (ys[i] - cy) * 0.38 + 2;
        }
        int br = Math.min(255, hilit[0] + 30);
        int bg = Math.min(255, hilit[1] + 30);
        int bb = Math.min(255, hilit[2] + 30);
        StdDraw.setPenColor(br, bg, bb);
        StdDraw.filledPolygon(hxs, hys);

        // Re-draw base fill over the highlight so only edges show
        // (caller re-fills after this, so this is fine)
    }

    /** Scattered pits/cracks for surface texture. */
    private static void drawRockTexture(double[] xs, double[] ys, int[] base, int[] shadow) {
        double cx = 0, cy = 0;
        for (double v : xs) cx += v;  cx /= xs.length;
        for (double v : ys) cy += v;  cy /= ys.length;

        int marks = Math.min(8, xs.length);
        for (int i = 0; i < marks; i++) {
            double angle  = i * 2.39996;   // golden angle spacing
            double radius = 6 + (i % 4) * 4;
            double px     = cx + Math.cos(angle) * radius;
            double py     = cy + Math.sin(angle) * radius * 0.6;

            // Pit
            int pr = Math.max(0, shadow[0] - 10);
            int pg = Math.max(0, shadow[1] - 10);
            int pb = Math.max(0, shadow[2] - 10);
            StdDraw.setPenColor(pr, pg, pb);
            StdDraw.filledCircle(px, py, 2.2 + (i % 3) * 0.8);

            // Crack line
            if (i % 2 == 0) {
                double len = 8 + i * 1.5;
                StdDraw.setPenColor(shadow[0], shadow[1], shadow[2]);
                StdDraw.setPenRadius(0.0015);
                StdDraw.line(px, py,
                             px + Math.cos(angle + 0.9) * len,
                             py + Math.sin(angle + 0.9) * len * 0.5);
            }
        }
        StdDraw.setPenRadius(0.002);
    }

    /** Crisp multi-pass outline — thicker outer, thinner inner. */
    private static void drawOutline(double[] xs, double[] ys, int r, int g, int b) {
        int vCount = xs.length;
        r = Math.max(0, r);
        g = Math.max(0, g);
        b = Math.max(0, b);

        // Outer outline
        StdDraw.setPenColor(r, g, b);
        StdDraw.setPenRadius(0.005);
        for (int i = 0; i < vCount; i++) {
            int next = (i + 1) % vCount;
            StdDraw.line(xs[i], ys[i], xs[next], ys[next]);
        }

        // Inner rim (slightly lighter)
        StdDraw.setPenColor(Math.min(255, r + 25), Math.min(255, g + 25), Math.min(255, b + 25));
        StdDraw.setPenRadius(0.002);
        for (int i = 0; i < vCount; i++) {
            int next = (i + 1) % vCount;
            StdDraw.line(xs[i], ys[i], xs[next], ys[next]);
        }
        StdDraw.setPenRadius(0.002);
    }

    /**
     * Build a jagged rock silhouette by adding sinusoidal displacement
     * perpendicular to each edge.
     */
    static double[][] buildRockSilhouette(List<Float> vertices,
                                          float roughness,
                                          GameEngine engine) {
        int count    = vertices.size() / 2;
        int segments = 5;
        // We produce count*segments points (no duplicate closure point needed for filledPolygon)
        int total    = count * segments;
        double[] xs  = new double[total];
        double[] ys  = new double[total];
        int idx      = 0;

        for (int i = 0; i < count; i++) {
            float x1 = vertices.get(i * 2),              y1 = vertices.get(i * 2 + 1);
            float x2 = vertices.get(((i + 1) % count) * 2),
                  y2 = vertices.get(((i + 1) % count) * 2 + 1);
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
        sb.append("POLYGON ").append(depth).append(" ").append(getVertexCount());
        for (float v : vertices) sb.append(" ").append(String.format("%.1f", v));
        sb.append(" ").append(r).append(" ").append(g).append(" ").append(b);
        return sb.toString();
    }

    public static Polygon deserialize(String line) {
        try {
            String[] p = line.trim().split("\\s+");
            if (p.length < 6) return null;
            int depth       = Integer.parseInt(p[1]);
            int vertexCount = Integer.parseInt(p[2]);
            if (p.length < 3 + vertexCount * 2 + 3) return null;

            float x = Float.parseFloat(p[3]);
            float y = Float.parseFloat(p[4]);
            Polygon poly = new Polygon(x, y, 100, 150, 100, depth);
            poly.vertices.clear();

            int idx = 3;
            for (int i = 0; i < vertexCount; i++) {
                poly.addVertex(Float.parseFloat(p[idx++]), Float.parseFloat(p[idx++]));
            }
            poly.setColor(Integer.parseInt(p[idx]),
                          Integer.parseInt(p[idx + 1]),
                          Integer.parseInt(p[idx + 2]));
            poly.closePath();
            return poly;
        } catch (Exception e) { return null; }
    }

    @Override
    public String getType() { return "POLYGON"; }

    @Override
    public String toString() {
        return String.format("Polygon(%d vertices, depth=%d, color=RGB(%d,%d,%d))",
                getVertexCount(), depth, r, g, b);
    }
}