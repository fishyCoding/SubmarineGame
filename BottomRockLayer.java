import java.io.*;

/**
 * BottomRockLayer — seafloor made of 30 draggable control points.
 *
 * Rendering matches the Rock style: solid filled polygon + single dark outline
 * along the top edge. No drop shadows, depth bands, or crack textures.
 *
 * Control points are managed via SeafloorPoint sprites in the editor (Main).
 * Collision uses getFloorYAt(x) which linearly interpolates between points.
 */
public class BottomRockLayer {

    private static final java.awt.Color BASE    = java.awt.Color.decode("#131313");
    private static final java.awt.Color OUTLINE = java.awt.Color.decode("#000000");

    public  static final int    NUM_POINTS = 30;
    private static final int    CANVAS_W   = 1600;

    private final float   seafloorBase;
    private final float[] worldX;
    private final float[] worldY;
    private final String  saveFile;

    public BottomRockLayer(float worldStart, float worldEnd,
                           float seafloorTop, float seafloorBase,
                           String saveFile) {
        this.seafloorBase = seafloorBase;
        this.saveFile     = saveFile;
        this.worldX       = new float[NUM_POINTS];
        this.worldY       = new float[NUM_POINTS];

        float step = (worldEnd - worldStart) / (NUM_POINTS - 1);
        for (int i = 0; i < NUM_POINTS; i++) {
            worldX[i] = worldStart + i * step;
            worldY[i] = seafloorTop;
        }

        load();
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    public void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(saveFile))) {
            pw.println("# BottomRockLayer control points — worldX worldY per line");
            for (int i = 0; i < NUM_POINTS; i++)
                pw.printf("%.2f %.2f%n", worldX[i], worldY[i]);
        } catch (IOException e) {
            System.err.println("Could not save seafloor: " + e.getMessage());
        }
    }

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
        } catch (FileNotFoundException e) {
            System.out.println("No seafloor file — using defaults.");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error loading seafloor: " + e.getMessage());
        }
    }

    // ── Point access (used by SeafloorPoint) ───────────────────────────────────

    public float getPointWorldX(int i) { return worldX[i]; }
    public float getPointWorldY(int i) { return worldY[i]; }

    /** Move a control point's Y (X stays fixed). No clamp — move freely. */
    public void movePoint(int index, float wy) {
        if (index >= 0 && index < NUM_POINTS)
            worldY[index] = wy;
    }

    // ── Collision ──────────────────────────────────────────────────────────────

    /** Linearly interpolate the floor Y at world X. Used for collision. */
    public float getFloorYAt(float wx) {
        if (wx <= worldX[0])              return worldY[0];
        if (wx >= worldX[NUM_POINTS - 1]) return worldY[NUM_POINTS - 1];

        int lo = 0, hi = NUM_POINTS - 2;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (worldX[mid + 1] < wx) lo = mid + 1;
            else                      hi = mid;
        }
        float t = (wx - worldX[lo]) / (worldX[lo + 1] - worldX[lo]);
        return worldY[lo] + t * (worldY[lo + 1] - worldY[lo]);
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    public void draw(GameEngine engine) {
        int first = 0, last = NUM_POINTS - 1;
        while (first < last - 1 && engine.worldToScreenX(worldX[first + 1]) < -50) first++;
        while (last  > first + 1 && engine.worldToScreenX(worldX[last  - 1]) > CANVAS_W + 50) last--;

        int visible = last - first + 1;
        if (visible < 2) return;

        int      total = visible * 2;
        double[] xs    = new double[total];
        double[] ys    = new double[total];

        for (int i = 0; i < visible; i++) {
            xs[i] = engine.worldToScreenX(worldX[first + i]);
            ys[i] = engine.worldToScreenY(worldY[first + i]);
        }
        double baseY = engine.worldToScreenY(seafloorBase);
        for (int i = 0; i < visible; i++) {
            xs[visible + i] = engine.worldToScreenX(worldX[last - i]);
            ys[visible + i] = baseY;
        }

        // Fill
        StdDraw.setPenColor(BASE);
        StdDraw.filledPolygon(xs, ys);

        // Single outline along the top edge only
        StdDraw.setPenColor(OUTLINE);
        StdDraw.setPenRadius(0.01);
        for (int i = 0; i < visible - 1; i++)
            StdDraw.line(xs[i], ys[i], xs[i + 1], ys[i + 1]);

        StdDraw.setPenRadius(0.002);
    }

    /** Green radar outline — called by Game during a ping. */
    public void drawRadarOutline(GameEngine engine, float alpha) {
        if (alpha <= 0f) return;
        int first = 0, last = NUM_POINTS - 1;
        while (first < last - 1 && engine.worldToScreenX(worldX[first + 1]) < -50) first++;
        while (last  > first + 1 && engine.worldToScreenX(worldX[last  - 1]) > CANVAS_W + 50) last--;

        int visible = last - first + 1;
        if (visible < 2) return;

        int a = Math.min(255, (int)(alpha * 255));
        StdDraw.setPenColor(new java.awt.Color(0, Math.min(255, a), 0));
        StdDraw.setPenRadius(0.003);
        for (int i = 0; i < visible - 1; i++) {
            StdDraw.line(
                engine.worldToScreenX(worldX[first + i]),
                engine.worldToScreenY(worldY[first + i]),
                engine.worldToScreenX(worldX[first + i + 1]),
                engine.worldToScreenY(worldY[first + i + 1]));
        }
        StdDraw.setPenRadius(0.002);
    }
}