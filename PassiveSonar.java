import java.awt.Color;


public class PassiveSonar {

    //base panel vars
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 200;
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 90;

    private static final float  MAX_INTENSITY = 3000f;


    public static void draw(float rawIntensity, int screenH, long tick) {

        double px = PANEL_X;
        double py = PANEL_Y;
        double pw = PANEL_W;
        double ph = PANEL_H;
 


        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 10));
        StdDraw.setPenColor(new Color(0, 180, 80));
        StdDraw.textLeft(px + 4, py + ph + 6, "PASSIVE SONAR");

        double barW  = pw - 8;
        double fillW = barW * Math.min(1.0, rawIntensity / MAX_INTENSITY);
        StdDraw.setPenColor(new Color(0, 30, 10));
        StdDraw.filledRectangle(px + 4 + barW / 2.0, py + ph + 14, barW / 2.0, 3);
        StdDraw.setPenColor(new Color(0, 200, 80));
        if (fillW > 0)
            StdDraw.filledRectangle(px + 4 + fillW / 2.0, py + ph + 14, fillW / 2.0, 3);

        
    }
}