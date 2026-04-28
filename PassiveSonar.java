import java.awt.Color;

/**
 * PassiveSonar — the bottom-left HUD widget that visualises ambient sound.
 *
 * Visual design:
 *   - A dark rounded panel sits in the bottom-left corner.
 *   - A horizontal baseline runs across the centre of the panel.
 *   - The baseline erupts into a multi-line animated waveform whose amplitude
 *     scales with perceived sound intensity.
 *   - The wave is drawn in multiple passes (glow + sharp core) for a green
 *     phosphor CRT look consistent with the radar system.
 *   - A label "PASSIVE SONAR" and a live intensity readout sit above the panel.
 *
 * Usage — call once per tick:
 *   PassiveSonar.draw(totalIntensity, WIDTH, HEIGHT, tick);
 *
 * where tick is a monotonically increasing int from the game loop (drives animation).
 */
public class PassiveSonar {

    // ── Panel geometry ─────────────────────────────────────────────────────────
    private static final int    PANEL_X      = 10;    // left edge of panel (screen px)
    private static final int    PANEL_Y      = 200;   // bottom edge — must clear the fog's bottom strip (CY - FOG_HALF_H - rings*3)
    private static final int    PANEL_W      = 220;   // panel width
    private static final int    PANEL_H      = 90;    // panel height

    // ── Waveform ───────────────────────────────────────────────────────────────
    private static final int    WAVE_POINTS  = 80;    // horizontal resolution
    /** Max amplitude of the wave in pixels (at full intensity). */
    private static final double MAX_AMP      = 32.0;
    /** Intensity at which the wave is at maximum amplitude. */
    private static final float  MAX_INTENSITY = 3000f;
    /** Number of sine harmonics layered to make the wave look organic. */
    private static final int    HARMONICS    = 4;

    // ── History buffer — smooth the intensity over several frames ──────────────
    private static final int    HISTORY      = 24;
    private static final float[] intensityBuf = new float[HISTORY];
    private static int           bufHead      = 0;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Draw the passive sonar panel.
     *
     * @param rawIntensity  total perceived sound intensity this tick
     * @param screenH       canvas height (for bottom-anchoring)
     * @param tick          monotonic frame counter (drives wave animation)
     */
    public static void draw(float rawIntensity, int screenH, long tick) {

        // ── Smooth intensity ───────────────────────────────────────────────────
        intensityBuf[bufHead % HISTORY] = rawIntensity;
        bufHead++;
        float smoothed = 0f;
        for (float v : intensityBuf) smoothed += v;
        smoothed /= HISTORY;

        double amplitude = Math.min(1.0, smoothed / MAX_INTENSITY) * MAX_AMP;

        // ── Panel bounds in StdDraw coords ─────────────────────────────────────
        double px = PANEL_X;
        double py = PANEL_Y;
        double pw = PANEL_W;
        double ph = PANEL_H;
        double midY = py + ph / 2.0;   // baseline Y
        double left  = px + 10;
        double right = px + pw - 10;

        // ── Background panel ───────────────────────────────────────────────────
        StdDraw.setPenColor(new Color(5, 12, 8));
        StdDraw.filledRectangle(px + pw / 2.0, py + ph / 2.0, pw / 2.0, ph / 2.0);
        StdDraw.setPenColor(new Color(20, 60, 30));
        StdDraw.setPenRadius(0.002);
        StdDraw.rectangle(px + pw / 2.0, py + ph / 2.0, pw / 2.0, ph / 2.0);

        // ── Label ──────────────────────────────────────────────────────────────
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 10));
        StdDraw.setPenColor(new Color(0, 180, 80));
        StdDraw.textLeft(px + 4, py + ph + 6, "PASSIVE SONAR");

        // Intensity bar — thin strip above the wave panel
        double barW  = pw - 8;
        double fillW = barW * Math.min(1.0, smoothed / MAX_INTENSITY);
        StdDraw.setPenColor(new Color(0, 30, 10));
        StdDraw.filledRectangle(px + 4 + barW / 2.0, py + ph + 14, barW / 2.0, 3);
        StdDraw.setPenColor(new Color(0, 200, 80));
        if (fillW > 0)
            StdDraw.filledRectangle(px + 4 + fillW / 2.0, py + ph + 14, fillW / 2.0, 3);

        // ── Baseline (flat line, always visible) ───────────────────────────────
        StdDraw.setPenColor(new Color(0, 60, 25));
        StdDraw.setPenRadius(0.001);
        StdDraw.line(left, midY, right, midY);

        if (amplitude < 0.5) {
            // Nearly silent — just draw a faint flat line, no wave
            StdDraw.setPenColor(new Color(0, 90, 40));
            StdDraw.setPenRadius(0.001);
            StdDraw.line(left, midY, right, midY);
            return;
        }

        // ── Build waveform points ──────────────────────────────────────────────
        double[] wx = new double[WAVE_POINTS];
        double[] wy = new double[WAVE_POINTS];
        double phase = tick * 0.25;   // animation speed

        for (int i = 0; i < WAVE_POINTS; i++) {
            double t = (double) i / (WAVE_POINTS - 1);
            wx[i] = left + t * (right - left);

            // Layer HARMONICS sine waves with different frequencies and phases
            // to produce an organic, non-repeating looking trace
            double y = 0;
            for (int h = 1; h <= HARMONICS; h++) {
                double freq  = h * 1.8;
                double phShift = h * 1.1;
                double weight  = 1.0 / h;          // higher harmonics are quieter
                y += Math.sin(t * Math.PI * freq + phase + phShift) * weight;
            }
            // Normalise the stacked harmonics and scale by amplitude
            y /= 1.0 + 0.5 + 0.333 + 0.25;        // sum of weights
            wy[i] = midY + y * amplitude;
        }

        // ── Glow pass (thick, dim) ─────────────────────────────────────────────
        StdDraw.setPenColor(new Color(0, 80, 30));
        StdDraw.setPenRadius(0.008);
        for (int i = 0; i < WAVE_POINTS - 1; i++)
            StdDraw.line(wx[i], wy[i], wx[i + 1], wy[i + 1]);

        // ── Mid glow ──────────────────────────────────────────────────────────
        StdDraw.setPenColor(new Color(0, 160, 60));
        StdDraw.setPenRadius(0.004);
        for (int i = 0; i < WAVE_POINTS - 1; i++)
            StdDraw.line(wx[i], wy[i], wx[i + 1], wy[i + 1]);

        // ── Sharp bright core ──────────────────────────────────────────────────
        StdDraw.setPenColor(new Color(100, 255, 140));
        StdDraw.setPenRadius(0.0015);
        for (int i = 0; i < WAVE_POINTS - 1; i++)
            StdDraw.line(wx[i], wy[i], wx[i + 1], wy[i + 1]);

        // ── Reset pen ─────────────────────────────────────────────────────────
        StdDraw.setPenRadius(0.002);
    }
}