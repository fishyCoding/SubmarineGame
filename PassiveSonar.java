import java.awt.Color;
import java.util.List;

public class PassiveSonar {

    private static final int   CENTER_X   = 115;
    private static final int   CENTER_Y   = 220;
    private static final double BASE_RADIUS = 45.0;
    private static final double MAX_STRETCH = 50.0;
    private static final int   RESOLUTION  = 180;  // finer ring = smoother curves


    private static final double PEAKNOISECOEF=1.6;

    // Smoothing passes: more passes = rounder, more organic contacts
    private static final int SMOOTH_PASSES = 4;

    private static final Color SONAR_GREEN      = new Color(0, 210, 90);
    private static final Color SONAR_GREEN_DIM  = new Color(0, 120, 50, 180);
    private static final Color SONAR_DARK       = new Color(0, 20, 8, 235);
    private static final Color SONAR_GLOW       = new Color(0, 255, 110, 30);

    public static void draw(float px, float py, List<Sound> sounds, long tick) {
        double[] raw      = new double[RESOLUTION];
        double[] smoothed = new double[RESOLUTION];
        double[] xPoints  = new double[RESOLUTION];
        double[] yPoints  = new double[RESOLUTION];

        // ── 1. OMNI-SURGE from own radar pings ───────────────────────────────
        // Spreads uniformly around the ring, fades quickly — creates the
        // "blooming circle" feel without making the ring spiky.
        double omniSurge = 0;
        for (Sound s : sounds) {
            if ("player_ping".equals(s.getOwner())) {
                float strength = s.perceivedAt(px, py);
                omniSurge += (1.0 - Math.exp(-strength / 6000.0));
            }
        }
        omniSurge = Math.min(omniSurge, 1.2); // cap so pings don't blow up the ring

        // ── 2. BUILD RAW INTENSITIES ─────────────────────────────────────────
        for (int i = 0; i < RESOLUTION; i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;

            // --- Organic noise floor ---
            // Several sine layers at inharmonic frequencies give a slowly
            // drifting, ocean-like baseline instead of a static circle.
            double noiseAmt = 0.018 + omniSurge * 0.35;
            double n1 = Math.sin(angle * 7.31  + tick * 0.006)  * 0.50;
            double n2 = Math.sin(angle * 13.97 - tick * 0.009)  * 0.30;
            double n3 = Math.sin(angle * 23.53 + tick * 0.014)  * 0.15;
            double n4 = Math.sin(angle * 3.17  - tick * 0.003)  * 0.20;
            double noise = (n1 + n2 + n3 + n4) * noiseAmt;

            // Slow global pulse (breathing effect at rest)
            double breathe = Math.sin(tick * 0.004) * 0.012;

            double intensity = omniSurge * 0.30 + noise + breathe;

            // --- Directional contacts ---
            for (Sound s : sounds) {
                if ("player_ping".equals(s.getOwner())) continue;

                float dx = s.getX() - px;
                float dy = s.getY() - py;
                double angleToSound = Math.atan2(dy, dx);
                if (angleToSound < 0) angleToSound += 2.0 * Math.PI;

                double diff = Math.abs(angle - angleToSound);
                if (diff > Math.PI) diff = 2.0 * Math.PI - diff;

                // Raised-cosine window: goes to exactly 0 at the edges,
                // has a smooth round top — no hard shoulders = no bulge lines.
                // halfWidth controls how wide the contact smears on the ring.
                double halfWidth = 1.1;  // ~63° total arc per contact
                if (diff < halfWidth) {
                    double t = diff / halfWidth;                     // 0..1
                    double window = 0.5 * (1.0 + Math.cos(Math.PI * t)); // 1..0
                    window = window * window;  // square it for a tighter, rounder peak

                    double strength = 1.0 - Math.exp(-s.perceivedAt(px, py) / 5000.0);
                    intensity += strength * window;
                }
            }

            // Extra noise that scales with the local intensity — loud peaks get
            // grainier / more turbulent texture, quiet directions stay smooth.
            double peakNoise = Math.sin(angle * 41.3 + tick * 0.031)
                             * Math.sin(angle * 67.7 - tick * 0.019)
                             * PEAKNOISECOEF;  // tweak 0.18 to taste: higher = grainier peaks
            intensity += intensity * peakNoise;

            raw[i] = Math.max(0, intensity);
        }

        // ── 3. MULTI-PASS SMOOTHING ───────────────────────────────────────────
        // Each pass is a simple weighted 5-point blur.  Multiple passes
        // approximate a Gaussian without any hard cutoff artifacts, and
        // they naturally merge nearby contacts into one smooth lobe rather
        // than leaving lumpy independent bumps next to each other.
        System.arraycopy(raw, 0, smoothed, 0, RESOLUTION);
        double[] tmp = new double[RESOLUTION];
        for (int pass = 0; pass < SMOOTH_PASSES; pass++) {
            for (int i = 0; i < RESOLUTION; i++) {
                int m2 = (i - 2 + RESOLUTION) % RESOLUTION;
                int m1 = (i - 1 + RESOLUTION) % RESOLUTION;
                int p1 = (i + 1) % RESOLUTION;
                int p2 = (i + 2) % RESOLUTION;
                // weights: 1 2 4 2 1  (normalised to sum = 10)
                tmp[i] = (smoothed[m2] + smoothed[m1] * 2.0
                        + smoothed[i] * 4.0
                        + smoothed[p1] * 2.0 + smoothed[p2]) / 10.0;
            }
            System.arraycopy(tmp, 0, smoothed, 0, RESOLUTION);
        }

        // ── 4. COORDINATE MAPPING ─────────────────────────────────────────────
        for (int i = 0; i < RESOLUTION; i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;
            double r = BASE_RADIUS + smoothed[i] * MAX_STRETCH;
            xPoints[i] = CENTER_X + Math.cos(angle) * r;
            yPoints[i] = CENTER_Y + Math.sin(angle) * r;
        }

        // ── 5. RENDER ─────────────────────────────────────────────────────────

        // Filled dark interior
        StdDraw.setPenColor(SONAR_DARK);
        StdDraw.filledPolygon(xPoints, yPoints);

        // Outer glow pass (slightly larger, very transparent)
        double[] gxPoints = new double[RESOLUTION];
        double[] gyPoints = new double[RESOLUTION];
        for (int i = 0; i < RESOLUTION; i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;
            double r = BASE_RADIUS + smoothed[i] * MAX_STRETCH + 2.5;
            gxPoints[i] = CENTER_X + Math.cos(angle) * r;
            gyPoints[i] = CENTER_Y + Math.sin(angle) * r;
        }
        StdDraw.setPenColor(SONAR_GLOW);
        StdDraw.setPenRadius(0.006);
        StdDraw.polygon(gxPoints, gyPoints);

        // Main ring line
        StdDraw.setPenRadius(0.003);
        StdDraw.setPenColor(SONAR_GREEN);
        StdDraw.polygon(xPoints, yPoints);

        // Dim inner reference circle so the operator can judge displacement
        StdDraw.setPenColor(SONAR_GREEN_DIM);
        StdDraw.setPenRadius(0.001);
        StdDraw.circle(CENTER_X, CENTER_Y, BASE_RADIUS);

        // Centre pip
        StdDraw.setPenColor(SONAR_GREEN);
        StdDraw.filledCircle(CENTER_X, CENTER_Y, 2);
    }
}
