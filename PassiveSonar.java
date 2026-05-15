import java.awt.Color;
import java.util.List;

public class PassiveSonar {

    private static final int CENTER_X = 115;
    private static final int CENTER_Y = 220;
    private static final double BASE_RADIUS = 45.0;
    private static final double MAX_STRETCH = 55.0; 
    private static final int RESOLUTION = 100; // Increased slightly for better curvature

    private static final Color SONAR_GREEN = new Color(0, 220, 100);
    private static final Color SONAR_DARK = new Color(0, 25, 10, 230);

    public static void draw(float px, float py, List<Sound> sounds, long tick) {
        double[] intensities = new double[RESOLUTION];
        double[] smoothed = new double[RESOLUTION];
        double[] xPoints = new double[RESOLUTION];
        double[] yPoints = new double[RESOLUTION];

        // 1. CALCULATE PLAYER PING (Self-Interference)
        double omniSurge = 0;
        for (Sound s : sounds) {
            if ("player_ping".equals(s.getOwner())) {
                float strength = s.perceivedAt(px, py);
                omniSurge += (1.0 - Math.exp(-strength / 5000.0));
            }
        }

        // 2. GENERATE RADIUS DATA
        for (int i = 0; i < RESOLUTION; i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;

            // BACKGROUND CHURN
            double wave = Math.sin(angle * 2.0 + tick * 0.005) * 0.05;
            
            // --- DYNAMIC NOISE FLOOR ---
            // Increased multiplier (0.8) and using irregular frequencies to break symmetry
            double noiseScale = 0.02 + (omniSurge * 0.8);
            double layer1 = Math.sin(angle * 13.73 + tick * 0.04) * 0.5;
            double layer2 = Math.sin(angle * 27.11 - tick * 0.07) * 0.3;
            double grain = (layer1 + layer2) * noiseScale;
            
            double dIntensity = (omniSurge * 0.45) + wave + grain;

            // 3. DIRECTIONAL CONTACTS
            for (Sound s : sounds) {
                if ("player_ping".equals(s.getOwner())) continue;

                float dx = s.getX() - px;
                float dy = s.getY() - py;
                double angleToSound = Math.atan2(dy, dx);
                if (angleToSound < 0) angleToSound += (2.0 * Math.PI);

                double diff = Math.abs(angle - angleToSound);
                if (diff > Math.PI) diff = (2.0 * Math.PI) - diff;

                // --- THE FIX: BROAD FALLOFF ---
                // windowSize is now more than double the sigma to ensure a 0-intensity landing
                double windowSize = 1.8; 
                if (diff < windowSize) {
                    double sigma = 0.8; 
                    double bellCurve = Math.exp(-(diff * diff) / (2 * sigma * sigma));
                    double strength = 1.0 - Math.exp(-s.perceivedAt(px, py) / 4500.0);
                    
                    dIntensity += strength * bellCurve;
                }
            }
            intensities[i] = dIntensity;
        }

        // 4. FINAL SMOOTHING (The Blur)
        for (int i = 0; i < RESOLUTION; i++) {
            int p1 = (i - 1 + RESOLUTION) % RESOLUTION;
            int n1 = (i + 1) % RESOLUTION;
            smoothed[i] = (intensities[p1] * 0.25) + (intensities[i] * 0.5) + (intensities[n1] * 0.25);
        }

        // 5. COORDINATE MAPPING
        for (int i = 0; i < RESOLUTION; i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;
            double r = BASE_RADIUS + (Math.max(0, smoothed[i]) * MAX_STRETCH);
            xPoints[i] = CENTER_X + Math.cos(angle) * r;
            yPoints[i] = CENTER_Y + Math.sin(angle) * r;
        }

        // Render
        StdDraw.setPenColor(SONAR_DARK);
        StdDraw.filledPolygon(xPoints, yPoints);
        StdDraw.setPenRadius(0.004);
        StdDraw.setPenColor(SONAR_GREEN);
        StdDraw.polygon(xPoints, yPoints);
        StdDraw.filledCircle(CENTER_X, CENTER_Y, 2);
    }
}