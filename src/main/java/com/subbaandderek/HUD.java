package com.subbaandderek;

import java.awt.Color;


public class HUD {
    // The visible oval half-axes (pixels).  World beyond this is hidden.
    private static final double FOG_HALF_W = 300.0;
    private static final double FOG_HALF_H  = 200.0;
    private static final int FOG_RINGS  = 40;   
    
    
    public static void drawFog(int HEIGHT, int WIDTH, double CX, double CY) {
        double featherDist = FOG_RINGS * 3.0;
        double outerW = FOG_HALF_W + featherDist;
        double outerH = FOG_HALF_H + featherDist;

        //draw black rect for to cover outside the elipse
        StdDraw.setPenColor(new Color(0, 0, 0, 255));

        //im not sure these ifs are needed bc the elipse should always be smaller than the screen but just in case 
        if (CX - outerW > 0)
            StdDraw.filledRectangle((CX - outerW) / 2.0, CY, (CX - outerW) / 2.0, HEIGHT / 2.0);
        if (CX + outerW < WIDTH)
            StdDraw.filledRectangle((CX + outerW + WIDTH) / 2.0, CY, (WIDTH - CX - outerW) / 2.0, HEIGHT / 2.0);
        if (CY + outerH < HEIGHT)
            StdDraw.filledRectangle(CX, (CY + outerH + HEIGHT) / 2.0, WIDTH / 2.0, (HEIGHT - CY - outerH) / 2.0);
        if (CY - outerH > 0)
            StdDraw.filledRectangle(CX, (CY - outerH) / 2.0, WIDTH / 2.0, (CY - outerH) / 2.0);
            
        //picture should lwok be cashed 
        StdDraw.picture(CX, CY, "fow.png", outerW*2 , outerH*2 );
    }

    // Draw main headsup display (some things like the sonar and contacts list are drawn seperately)

    public static void drawHUD(int WIDTH, int HEIGHT, double CX, double CY, Submarine player) {
        java.awt.Font mono = new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 13);
        StdDraw.setFont(mono);

        float depthM = Math.max(0f, -player.getY());
        float ratio  = (float) player.getHealth() / player.getMaxHealth();

        // ── Depth & speed readout (top-left) ──────────────────────────────────
        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textLeft(11, HEIGHT - 19, String.format("Depth: %.0f m",  depthM));
        StdDraw.textLeft(11, HEIGHT - 35, String.format("Speed: %.1f kn", player.getSpeed()));
                StdDraw.setPenColor(255, 255, 255);

        StdDraw.textLeft(11, HEIGHT - 51, String.format("Drop Cooldown: %d", player.dropCooldown));




        // Health bar
        StdDraw.setPenColor(200, 230, 255);
        StdDraw.textLeft(10, HEIGHT - 18, String.format("Depth: %.0f m",  depthM));
        StdDraw.textLeft(10, HEIGHT - 34, String.format("Speed: %.1f kn", player.getSpeed()));

        double barRight = WIDTH - 20;
        double barTop = HEIGHT - 20;
        double barW  = 160;
        double barH = 10;
        double fill = barW * ratio;

        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textRight(barRight + 1, barTop + 14, "HULL INTEGRITY");
        StdDraw.setPenColor(180, 210, 255);
        StdDraw.textRight(barRight,     barTop + 15, "HULL INTEGRITY");

        // Track
        StdDraw.setPenColor(20, 20, 25);
        StdDraw.filledRectangle(barRight - barW / 2.0, barTop, barW / 2.0, barH / 2.0);

        // Fill (green → red)
        int hr = (int)(255 * (1 - ratio));
        int hg = (int)(255 * ratio);
        StdDraw.setPenColor(hr, hg, 0);
        StdDraw.filledRectangle(barRight - barW + fill / 2.0, barTop, fill / 2.0, barH / 2.0);

        // Border
        StdDraw.setPenColor(120, 140, 160);
        StdDraw.setPenRadius(0.002);
        StdDraw.rectangle(barRight - barW / 2.0, barTop, barW / 2.0, barH / 2.0);


    }
}
