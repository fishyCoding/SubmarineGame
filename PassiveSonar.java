import java.awt.Color;

//holy shit this should not have been this hard

public class PassiveSonar {

    //base panel vars
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 200;
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 90;

    //wave vars
    private static final int WAVE_POINTS = 80;
    private static final double MAX_AMP = 32.0;
    private static final float  MAX_INTENSITY = 3000f;
    private static final int HARMONICS = 2;


    //lower means less smoothing
    private static final int HISTORY = 6;
    //stores the last 6 raw intensity vals 
    private static final float[] intensityBuf = new float[HISTORY];
    //circular buffer head index
    private static int bufHead = 0;

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

        StdDraw.setPenRadius(0.002);
    }
}