/**
 * BottomRockLayer generates and renders the procedural seafloor.
 *
 * The layer consists of:
 *   - A jagged top silhouette (generated once, stored in world coordinates)
 *   - A solid fill down to SEAFLOOR_BASE_DEPTH
 *   - A stylized outlined top edge matching the Polygon rock aesthetic
 *   - Subtle layered highlights for a sense of depth
 */
public class BottomRockLayer {

    // ── Palette (matches the BG_* palette in Polygon) ─────────────────────────
    private static final int[] BASE      = { 55,  65,  82};
    private static final int[] SHADOW    = { 32,  40,  52};
    private static final int[] HILIT     = { 78,  92, 112};
    private static final int[] EDGE_DARK = { 20,  28,  38};

    private final float   seafloorTop;    // world Y of the silhouette spine
    private final float   seafloorBase;   // world Y of the absolute bottom
    private final float[] worldX;
    private final float[] worldY;
    private final int     points;

    /**
     * @param worldStart    leftmost world X to generate
     * @param worldEnd      rightmost world X to generate
     * @param numPoints     resolution of the silhouette (100–200 is fine)
     * @param seafloorTop   average world Y of the top surface (e.g. -1820)
     * @param seafloorBase  world Y of the solid bottom (e.g. -2400)
     */
    public BottomRockLayer(float worldStart, float worldEnd, int numPoints,
                           float seafloorTop, float seafloorBase) {
        this.seafloorTop  = seafloorTop;
        this.seafloorBase = seafloorBase;
        this.points       = Math.max(2, numPoints);
        this.worldX       = new float[this.points];
        this.worldY       = new float[this.points];
        generate(worldStart, worldEnd);
    }

    // ── Generation ─────────────────────────────────────────────────────────────

    private void generate(float worldStart, float worldEnd) {
        float step  = (worldEnd - worldStart) / (points - 1);
        float range = 22f;

        for (int i = 0; i < points; i++) {
            float wx = worldStart + i * step;
            // Multi-frequency noise for a more natural silhouette
            double noise = Math.sin(wx * 0.0045) * 14
                         + Math.cos(wx * 0.0091) * 10
                         + Math.sin(wx * 0.021 + 1.3) *  5
                         + Math.cos(wx * 0.038 + 0.7) *  3;
            float wy = seafloorTop + (float) noise;
            worldX[i] = wx;
            worldY[i] = Math.max(seafloorTop - range, Math.min(seafloorTop + range, wy));
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    /**
     * Draw the full bottom rock layer.  Call once per frame, before foreground sprites.
     */
    public void draw(GameEngine engine) {
        // Cull: only draw points whose screen X is visible
        int w = 1600; // canvas width — adjust if Main.WIDTH changes
        int first = 0, last = points - 1;
        while (first < last - 1 && engine.worldToScreenX(worldX[first + 1]) < -50) first++;
        while (last > first + 1 && engine.worldToScreenX(worldX[last  - 1]) > w + 50)  last--;

        int visible = last - first + 1;
        if (visible < 2) return;

        // Build the closed polygon: top silhouette + bottom rectangle
        int   total = visible * 2;
        double[] xs = new double[total];
        double[] ys = new double[total];

        // Top silhouette (left → right)
        for (int i = 0; i < visible; i++) {
            xs[i] = engine.worldToScreenX(worldX[first + i]);
            ys[i] = engine.worldToScreenY(worldY[first + i]);
        }
        // Bottom edge (right → left)
        double baseScreenY = engine.worldToScreenY(seafloorBase);
        for (int i = 0; i < visible; i++) {
            xs[visible + i] = engine.worldToScreenX(worldX[last - i]);
            ys[visible + i] = baseScreenY;
        }

        // 1. Drop-shadow pass
        double[] sxs = new double[total], sys = new double[total];
        for (int i = 0; i < total; i++) { sxs[i] = xs[i] + 5; sys[i] = ys[i] - 4; }
        StdDraw.setPenColor(SHADOW[0], SHADOW[1], SHADOW[2]);
        StdDraw.filledPolygon(sxs, sys);

        // 2. Main fill
        StdDraw.setPenColor(BASE[0], BASE[1], BASE[2]);
        StdDraw.filledPolygon(xs, ys);

        // 3. Subtle lighter band just below the silhouette (depth banding)
        drawDepthBand(engine, first, last, visible);

        // 4. Top edge: thick dark outline then lighter inner rim
        StdDraw.setPenColor(EDGE_DARK[0], EDGE_DARK[1], EDGE_DARK[2]);
        StdDraw.setPenRadius(0.006);
        for (int i = 0; i < visible - 1; i++) {
            StdDraw.line(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        }
        // Inner rim
        StdDraw.setPenColor(HILIT[0], HILIT[1], HILIT[2]);
        StdDraw.setPenRadius(0.002);
        for (int i = 0; i < visible - 1; i++) {
            StdDraw.line(xs[i], ys[i] + 2, xs[i + 1], ys[i + 1] + 2);
        }

        // 5. Rock texture along the silhouette
        drawSilhouetteTexture(xs, ys, visible);

        StdDraw.setPenRadius(0.002);
    }

    /** A lighter band 0–40px below the silhouette to suggest a lit upper face. */
    private void drawDepthBand(GameEngine engine, int first, int last, int visible) {
        int bandH = 28;
        double[] bxs = new double[visible * 2];
        double[] bys = new double[visible * 2];
        for (int i = 0; i < visible; i++) {
            bxs[i] = engine.worldToScreenX(worldX[first + i]);
            bys[i] = engine.worldToScreenY(worldY[first + i]);
        }
        for (int i = 0; i < visible; i++) {
            bxs[visible + i] = engine.worldToScreenX(worldX[last - i]);
            bys[visible + i] = engine.worldToScreenY(worldY[last - i]) - bandH;
        }
        StdDraw.setPenColor(HILIT[0], HILIT[1], HILIT[2]);
        StdDraw.filledPolygon(bxs, bys);
    }

    /**
     * Draw only the silhouette edge in green, at the given alpha (0.0–1.0).
     * Called by Game when a radar ping is active.
     */
    public void drawRadarOutline(GameEngine engine, float alpha) {
        if (alpha <= 0f) return;
        int w = 1600;
        int first = 0, last = points - 1;
        while (first < last - 1 && engine.worldToScreenX(worldX[first + 1]) < -50) first++;
        while (last > first + 1 && engine.worldToScreenX(worldX[last  - 1]) > w + 50) last--;

        int visible = last - first + 1;
        if (visible < 2) return;

        int a = Math.min(255, (int)(alpha * 255));
        // Outer glow pass (wider, dimmer)
        StdDraw.setPenColor(new java.awt.Color(0, a / 3, 0));
        StdDraw.setPenRadius(0.012);
        for (int i = 0; i < visible - 1; i++) {
            double x1 = engine.worldToScreenX(worldX[first + i]);
            double y1 = engine.worldToScreenY(worldY[first + i]);
            double x2 = engine.worldToScreenX(worldX[first + i + 1]);
            double y2 = engine.worldToScreenY(worldY[first + i + 1]);
            StdDraw.line(x1, y1, x2, y2);
        }
        // Core green edge
        StdDraw.setPenColor(new java.awt.Color(0, Math.min(255, a), 0));
        StdDraw.setPenRadius(0.003);
        for (int i = 0; i < visible - 1; i++) {
            double x1 = engine.worldToScreenX(worldX[first + i]);
            double y1 = engine.worldToScreenY(worldY[first + i]);
            double x2 = engine.worldToScreenX(worldX[first + i + 1]);
            double y2 = engine.worldToScreenY(worldY[first + i + 1]);
            StdDraw.line(x1, y1, x2, y2);
        }
        StdDraw.setPenRadius(0.002);
    }

    /** Scatter cracks along the top silhouette for surface texture. */
    private void drawSilhouetteTexture(double[] xs, double[] ys, int visible) {
        int step = Math.max(1, visible / 30);
        for (int i = 0; i < visible; i += step) {
            double px = xs[i] + Math.sin(i * 1.7) * 8;
            double py = ys[i] - 6 - Math.abs(Math.cos(i * 2.1)) * 8;

            if (i % (step * 2) == 0 && i + step < visible) {
                StdDraw.setPenColor(EDGE_DARK[0], EDGE_DARK[1], EDGE_DARK[2]);
                StdDraw.setPenRadius(0.0012);
                StdDraw.line(px, py, px + Math.sin(i + 1.1) * 12, py - 5);
            }
        }
        StdDraw.setPenRadius(0.002);
    }
}