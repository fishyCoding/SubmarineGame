import java.io.*;

/**
 * BottomRockLayer — editable seafloor with 30 draggable control points.
 *
 * Points are stored in world coordinates and can be moved interactively
 * in the editor (Main.java). The seafloor shape is saved to / loaded from
 * a dedicated file so edits persist between sessions.
 *
 * Rendering:
 *   - A smooth silhouette connecting the control points (left→right)
 *   - Solid fill down to SEAFLOOR_BASE
 *   - Layered highlights + crack texture matching the old Rock aesthetic
 *   - Green radar-outline mode (called by Game when a ping is active)
 *
 * Interaction (used by Main):
 *   - getPointIndexAt(wx, wy)  — returns index of the closest point within
 *                                 HIT_RADIUS world units, or -1
 *   - movePoint(index, wx, wy) — clamps Y and moves the point, then saves
 */
public class BottomRockLayer {

    // ── Palette ────────────────────────────────────────────────────────────────
    private static final int[] BASE      = { 55,  65,  82};
    private static final int[] SHADOW    = { 32,  40,  52};
    private static final int[] HILIT     = { 78,  92, 112};
    private static final int[] EDGE_DARK = { 20,  28,  38};

    // ── Layout constants ───────────────────────────────────────────────────────
    public  static final int   NUM_POINTS  = 30;
    private static final float HIT_RADIUS  = 60f;   // world units for click detection
    private static final int   CANVAS_W    = 1600;

    // ── State ──────────────────────────────────────────────────────────────────
    private final float   seafloorBase;   // world Y of the absolute bottom
    private final float   minY;           // upper clamp for points
    private final float   maxY;           // lower clamp for points
    private final float[] worldX;
    private final float[] worldY;
    private final String  saveFile;       // where to persist

    // ── Constructor ────────────────────────────────────────────────────────────

    /**
     * @param worldStart    leftmost world X
     * @param worldEnd      rightmost world X
     * @param seafloorTop   default Y for a flat floor (e.g. -1820)
     * @param seafloorBase  Y of the solid bottom (e.g. -2400)
     * @param saveFile      file path for persistence (e.g. "seafloor.txt")
     */
    public BottomRockLayer(float worldStart, float worldEnd,
                           float seafloorTop, float seafloorBase,
                           String saveFile) {
        this.seafloorBase = seafloorBase;
        this.minY         = seafloorBase + 20f;   // can't go below the base
        this.maxY         = seafloorTop  + 300f;  // can't go too high
        this.saveFile     = saveFile;
        this.worldX       = new float[NUM_POINTS];
        this.worldY       = new float[NUM_POINTS];

        // Default: evenly spaced, flat at seafloorTop
        float step = (worldEnd - worldStart) / (NUM_POINTS - 1);
        for (int i = 0; i < NUM_POINTS; i++) {
            worldX[i] = worldStart + i * step;
            worldY[i] = seafloorTop;
        }

        load(); // override defaults if save file exists
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    /** Save current point positions to disk. */
    public void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(saveFile))) {
            pw.println("# BottomRockLayer control points — worldX worldY per line");
            for (int i = 0; i < NUM_POINTS; i++) {
                pw.printf("%.2f %.2f%n", worldX[i], worldY[i]);
            }
            System.out.println("Seafloor saved (" + NUM_POINTS + " points).");
        } catch (IOException e) {
            System.err.println("Could not save seafloor: " + e.getMessage());
        }
    }

    /** Load point positions from disk; silently keeps defaults on failure. */
    private void load() {
        try (BufferedReader br = new BufferedReader(new FileReader(saveFile))) {
            int idx = 0;
            String line;
            while ((line = br.readLine()) != null && idx < NUM_POINTS) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    worldX[idx] = Float.parseFloat(parts[0]);
                    worldY[idx] = Float.parseFloat(parts[1]);
                    idx++;
                }
            }
            System.out.println("Seafloor loaded (" + idx + " points).");
        } catch (FileNotFoundException e) {
            System.out.println("No seafloor file found — using defaults.");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error loading seafloor: " + e.getMessage());
        }
    }

    // ── Interaction ────────────────────────────────────────────────────────────

    /**
     * Return the index of the control point closest to (wx, wy) within
     * HIT_RADIUS world units, or -1 if none is close enough.
     */
    public int getPointIndexAt(float wx, float wy) {
        int   best = -1;
        float bestDist = HIT_RADIUS * HIT_RADIUS;
        for (int i = 0; i < NUM_POINTS; i++) {
            float dx = worldX[i] - wx;
            float dy = worldY[i] - wy;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDist) {
                bestDist = d2;
                best = i;
            }
        }
        return best;
    }

    /**
     * Move point [index] to the given world coords (Y is clamped).
     * Does NOT save automatically — call save() when the drag ends.
     */
    public void movePoint(int index, float wx, float wy) {
        if (index < 0 || index >= NUM_POINTS) return;
        // X is fixed — only Y is adjustable (keeps points in sorted order)
        worldY[index] = Math.max(minY, Math.min(maxY, wy));
    }

    public float getPointWorldX(int i) { return worldX[i]; }
    public float getPointWorldY(int i) { return worldY[i]; }

    /**
     * Return the floor's world Y at the given world X by linearly interpolating
     * between the two nearest control points. Use this for collision detection.
     *
     * If wx is outside the control-point range, returns the Y of the nearest
     * endpoint (no extrapolation).
     */
    public float getFloorYAt(float wx) {
        // Clamp to range
        if (wx <= worldX[0])               return worldY[0];
        if (wx >= worldX[NUM_POINTS - 1])  return worldY[NUM_POINTS - 1];

        // Binary search for the segment containing wx
        int lo = 0, hi = NUM_POINTS - 2;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (worldX[mid + 1] < wx) lo = mid + 1;
            else                      hi = mid;
        }

        // Linear interpolation within segment [lo, lo+1]
        float x0 = worldX[lo],     x1 = worldX[lo + 1];
        float y0 = worldY[lo],     y1 = worldY[lo + 1];
        float t  = (wx - x0) / (x1 - x0);
        return y0 + t * (y1 - y0);
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    /** Draw the full bottom rock layer. Call once per frame, before foreground sprites. */
    public void draw(GameEngine engine) {
        int first = 0, last = NUM_POINTS - 1;
        while (first < last - 1 && engine.worldToScreenX(worldX[first + 1]) < -50) first++;
        while (last > first + 1 && engine.worldToScreenX(worldX[last  - 1]) > CANVAS_W + 50) last--;

        int visible = last - first + 1;
        if (visible < 2) return;

        int    total = visible * 2;
        double[] xs = new double[total];
        double[] ys = new double[total];

        // Top silhouette left → right
        for (int i = 0; i < visible; i++) {
            xs[i] = engine.worldToScreenX(worldX[first + i]);
            ys[i] = engine.worldToScreenY(worldY[first + i]);
        }
        // Bottom edge right → left
        double baseScreenY = engine.worldToScreenY(seafloorBase);
        for (int i = 0; i < visible; i++) {
            xs[visible + i] = engine.worldToScreenX(worldX[last - i]);
            ys[visible + i] = baseScreenY;
        }

        // 1. Drop-shadow
        double[] sxs = new double[total], sys = new double[total];
        for (int i = 0; i < total; i++) { sxs[i] = xs[i] + 5; sys[i] = ys[i] - 4; }
        StdDraw.setPenColor(SHADOW[0], SHADOW[1], SHADOW[2]);
        StdDraw.filledPolygon(sxs, sys);

        // 2. Main fill
        StdDraw.setPenColor(BASE[0], BASE[1], BASE[2]);
        StdDraw.filledPolygon(xs, ys);

        // 3. Depth band
        drawDepthBand(engine, first, last, visible);

        // 4. Top edge: thick dark outline then lighter rim
        StdDraw.setPenColor(EDGE_DARK[0], EDGE_DARK[1], EDGE_DARK[2]);
        StdDraw.setPenRadius(0.006);
        for (int i = 0; i < visible - 1; i++)
            StdDraw.line(xs[i], ys[i], xs[i + 1], ys[i + 1]);

        StdDraw.setPenColor(HILIT[0], HILIT[1], HILIT[2]);
        StdDraw.setPenRadius(0.002);
        for (int i = 0; i < visible - 1; i++)
            StdDraw.line(xs[i], ys[i] + 2, xs[i + 1], ys[i + 1] + 2);

        // 5. Crack texture
        drawSilhouetteTexture(xs, ys, visible);

        StdDraw.setPenRadius(0.002);
    }

    /**
     * Draw control-point handles so the editor can see them.
     * @param selectedIndex index of the currently dragged point, or -1
     */
    public void drawEditHandles(GameEngine engine, int selectedIndex) {
        for (int i = 0; i < NUM_POINTS; i++) {
            double sx = engine.worldToScreenX(worldX[i]);
            double sy = engine.worldToScreenY(worldY[i]);

            if (i == selectedIndex) {
                // Highlighted
                StdDraw.setPenColor(255, 220, 80);
                StdDraw.setPenRadius(0.004);
                StdDraw.circle(sx, sy, 10);
                StdDraw.filledCircle(sx, sy, 5);
            } else {
                // Normal handle
                StdDraw.setPenColor(HILIT[0], HILIT[1], HILIT[2]);
                StdDraw.setPenRadius(0.002);
                StdDraw.circle(sx, sy, 7);
            }
        }
        StdDraw.setPenRadius(0.002);
    }

    /** Draw the silhouette edge in green; called by Game when a radar ping is active. */
    public void drawRadarOutline(GameEngine engine, float alpha) {
        if (alpha <= 0f) return;
        int first = 0, last = NUM_POINTS - 1;
        while (first < last - 1 && engine.worldToScreenX(worldX[first + 1]) < -50) first++;
        while (last > first + 1 && engine.worldToScreenX(worldX[last  - 1]) > CANVAS_W + 50) last--;

        int visible = last - first + 1;
        if (visible < 2) return;

        int a = Math.min(255, (int)(alpha * 255));
        StdDraw.setPenColor(new java.awt.Color(0, a / 3, 0));
        StdDraw.setPenRadius(0.012);
        for (int i = 0; i < visible - 1; i++) {
            double x1 = engine.worldToScreenX(worldX[first + i]);
            double y1 = engine.worldToScreenY(worldY[first + i]);
            double x2 = engine.worldToScreenX(worldX[first + i + 1]);
            double y2 = engine.worldToScreenY(worldY[first + i + 1]);
            StdDraw.line(x1, y1, x2, y2);
        }
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

    // ── Private helpers ────────────────────────────────────────────────────────

    private void drawDepthBand(GameEngine engine, int first, int last, int visible) {
        int    bandH = 28;
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