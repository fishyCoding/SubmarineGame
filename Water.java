//Draws the background water

public class Water{    
    private static float SURFACE_LEVEL;
    private static int HEIGHT;
    private static int WIDTH;
    private static GameEngine engine;

    public Water(int HE,int WIDTH,float SURFACE_LEVEL,GameEngine engine) {
        this.HEIGHT = HE;
        this.WIDTH = WIDTH;
        this.SURFACE_LEVEL = SURFACE_LEVEL;
        this.engine = engine;
    }

    //Basic math functs to keep values in between 0 and 255 for colors (int values) and 1 for floats

    private static float clampF(float v) { 
        return Math.max(0f,Math.min(1f,v)); 
    }

    private static int   clampC(int v) { 
        return Math.max(0,Math.min(255,v)); 
    }

    //Draws the water gradient by crting horizontal strips and coloring them based on depth, with a line at the surface level
    public void drawWaterGradient() {
        int strips = 160;
        double surfaceScreenY = engine.worldToScreenY(SURFACE_LEVEL);

        // Draw horizontal strips with color based on depth
        for (int i = 0; i < strips; i++) {
            
            // Convert pixel Y coordinates to screen coordinates (flip Y-axis)
            // Pixel Y: 0 at top, HEIGHT at bottom
            // Screen Y: 0 at bottom, HEIGHT at top (StdDraw coordinates)
            double pixelY1 = i*(HEIGHT / (double) strips);
            double pixelY2 = (i+1)*(HEIGHT/ (double) strips);
            double screenY1 = HEIGHT - pixelY1;
            double screenY2 = HEIGHT - pixelY2;
            
            // Convert screen Y to world Y
            double wy1 = screenY1 + engine.getCameraY();
            double wy2 = screenY2 + engine.getCameraY();

            //color valus
            int r,g,b;

            /*floats t: 0 at surface, 1 at max depth
             d*/
            if (screenY1 >= surfaceScreenY) {
                // If the strip is entierly above the sky
                float t = clampF((float) ((wy1 - SURFACE_LEVEL) / 500.0));
                r = clampC((int) (200 + 30 * t));
                g = clampC((int) (228 + 15 * t));
                b = 255;
            } else if (screenY2 <= surfaceScreenY) {
                // Completaly underwater strip

                float d1=Math.max(0f, (float) (SURFACE_LEVEL - wy1));
                float d2 = Math.max(0f, (float) (SURFACE_LEVEL - wy2));
                float t  = clampF(((d1 + d2) / 2f) / 1800f);

                //clamp to ensure values are between 0 and 255
                r = clampC((int) (42-28*t));
                g = clampC((int) (68-45*t));
                b = clampC((int) (102-60*t));
            } else {
                // Split: draw sky top, water bottom
                double surf = surfaceScreenY;

                //draw sky part
                StdDraw.setPenColor(210, 235, 255);
                StdDraw.filledRectangle(WIDTH / 2.0, (surf + screenY2) / 2.0, WIDTH / 2.0, Math.abs(screenY2 - surf) / 2.0);

                //set d to determine water color based on depth, then draw water part
                // set t to 0 at surface and 1 at max depth, then interpolate color between light blue and dark blue based on t
                float d = Math.max(0f, (float) (SURFACE_LEVEL - wy1));
                float t = clampF(d / 1800f);
                r = clampC((int) (42 - 28 * t));
                g = clampC((int) (68 - 45 * t));
                b = clampC((int) (102 - 60 * t));
                //draw water part
                StdDraw.setPenColor(r, g, b);
                StdDraw.filledRectangle(WIDTH / 2.0, (screenY1 + surf) / 2.0, WIDTH / 2.0, Math.abs(surf - screenY1) / 2.0);
                continue;
            }
            // draw the strip with the calculated color
            StdDraw.setPenColor(r, g, b);
            StdDraw.filledRectangle(WIDTH / 2.0, (screenY1 + screenY2) / 2.0, WIDTH / 2.0, Math.abs(screenY1 - screenY2) / 2.0);
        }

        //if surface level is visible based on current height
        if (surfaceScreenY>0 && surfaceScreenY<HEIGHT) {
            StdDraw.setPenColor(150, 210, 255);
            StdDraw.setPenRadius(0.003);
            StdDraw.line(0, surfaceScreenY, WIDTH, surfaceScreenY);
            StdDraw.setPenRadius(0.002);
        }
    }
}