import java.awt.Color;
import java.util.List;

public class PassiveSonar {

    private static final int   CENTER_X   = 115;
    private static final int   CENTER_Y   = 220;
    private static final double BASE_RADIUS = 45.0;
    private static final double MAX_STRETCH = 50.0;
    private static final int   RESOLUTION  = 180;  // finer ring = smoother curves

    //Noise that comes on sound peaks
    private static final double PEAKNOISECOEF=1.6;

    //smoothing amount
    private static final int SMOOTH_PASSES = 4;

    private static final Color SONAR_GREEN = new Color(0, 210, 90);
    private static final Color SONAR_GREEN_DIM = new Color(0, 120, 50, 180);
    private static final Color SONAR_DARK = new Color(0, 20, 8, 235);
    private static final Color SONAR_GLOW = new Color(0, 255, 110, 30);

    public static void draw(float px, float py, List<Sound> sounds, long tick) {
        double[] raw = new double[RESOLUTION];
        double[] smoothed = new double[RESOLUTION];
        double[] xPoints = new double[RESOLUTION];
        double[] yPoints = new double[RESOLUTION];

        //omni for players own pings
        double omniSurge = 0;
        for (Sound s : sounds) {
            if ("player_ping".equals(s.getOwner())) {
                float strength = s.perceivedAt(px, py);
                omniSurge += (1.0 - Math.exp(-strength / 6000.0));
            }
        }
        omniSurge = Math.min(omniSurge, 1.2); //cap

        for (int i=0;i<RESOLUTION;i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;

            //organic noise floor
            double noiseAmt = 0.018 + omniSurge * 0.35;


            double n1 = Math.sin(angle * 7.30  +tick *0.006)*0.50;
            double n2 = Math.sin(angle *14.97 - tick*0.009)  *0.30;
            double n3 = Math.sin(angle *23.53 +tick *0.014)*0.15;
            double n4 = Math.sin(angle* 3.17- tick *0.003)* 0.20;
            double noise = (n1 + n2 + n3 + n4) * noiseAmt;

            // Pulse
            double breathe = Math.sin(tick * 0.004) * 0.012;

            double intensity = omniSurge * 0.30 + noise + breathe;
            //base intensity has been calculated


            for (Sound s : sounds) {
                if ("player_ping".equals(s.getOwner())) continue;

                float dx = s.getX() - px;
                float dy = s.getY() - py;
                double angleToSound = Math.atan2(dy, dx);
                if (angleToSound < 0) angleToSound += 2.0 * Math.PI;

                double diff = Math.abs(angle - angleToSound);
                if (diff > Math.PI) diff = 2.0 * Math.PI - diff;

                double halfWidth = 1.1;
                if (diff < halfWidth) {
                    double t = diff / halfWidth;  
                    double window = 0.5 * (1.0 + Math.cos(Math.PI * t));
                    window = window * window;

                    double strength = 1.0 - Math.exp(-s.perceivedAt(px, py) / 5000.0);
                    intensity += strength * window;
                }
            }


            double peakNoise = Math.sin(angle * 41.3 + tick * 0.031) * Math.sin(angle * 67.7 - tick * 0.019) * PEAKNOISECOEF;
            intensity += intensity * peakNoise;

            raw[i] = Math.max(0, intensity);
        }

        // Gaussian blur

        System.arraycopy(raw,0,smoothed,0,RESOLUTION);
        double[] tmp = new double[RESOLUTION];
        for (int pass=0;pass<SMOOTH_PASSES;pass++) {
            for (int i=0;i<RESOLUTION;i++) {
                int m2 = (i - 2 + RESOLUTION) % RESOLUTION;
                int m1 = (i - 1 + RESOLUTION) % RESOLUTION;
                int p1 = (i + 1) % RESOLUTION;
                int p2 = (i + 2) % RESOLUTION;
                tmp[i] = (smoothed[m2] + smoothed[m1] * 2.0
                        + smoothed[i] * 4.0
                        + smoothed[p1] * 2.0 + smoothed[p2]) / 10.0;
            }
            System.arraycopy(tmp,0,smoothed,0,RESOLUTION);
        }

        for (int i=0;i<RESOLUTION;i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;
            double r = BASE_RADIUS + smoothed[i] * MAX_STRETCH;
            xPoints[i] = CENTER_X + Math.cos(angle) * r;
            yPoints[i] = CENTER_Y + Math.sin(angle) * r;
        }


        //render

        StdDraw.setPenColor(SONAR_DARK);
        StdDraw.filledPolygon(xPoints, yPoints);

        // Glow
        double[] gxPoints = new double[RESOLUTION];
        double[] gyPoints = new double[RESOLUTION];
        for (int i=0;i<RESOLUTION;i++) {
            double angle = (i * 2.0 * Math.PI) / RESOLUTION;
            double r = BASE_RADIUS + smoothed[i] * MAX_STRETCH + 2.5;
            gxPoints[i] = CENTER_X + Math.cos(angle) * r;
            gyPoints[i] = CENTER_Y + Math.sin(angle) * r;
        }
        StdDraw.setPenColor(SONAR_GLOW);
        StdDraw.setPenRadius(0.006);
        StdDraw.polygon(gxPoints, gyPoints);

        StdDraw.setPenRadius(0.003);
        StdDraw.setPenColor(SONAR_GREEN);
        StdDraw.polygon(xPoints, yPoints);

        StdDraw.setPenColor(SONAR_GREEN_DIM);
        StdDraw.setPenRadius(0.001);
        StdDraw.circle(CENTER_X, CENTER_Y,BASE_RADIUS);

        StdDraw.setPenColor(SONAR_GREEN);
        StdDraw.filledCircle(CENTER_X, CENTER_Y, 2);
    }
}
