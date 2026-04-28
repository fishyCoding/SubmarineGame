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

    //Basic math functions to keep values in between 0 and 255 for colors (int values) and 1 for floats

    private static float clampF(float v) { 
        return Math.max(0f,Math.min(1f,v)); 
    }

    private static int   clampC(int v) { 
        return Math.max(0,Math.min(255,v)); 
    }

    //Draws the water gradient by creating horizontal strips and coloring them based on their depth, with a line at the surface level
    public void drawWaterGradient() {
        int strips = 160;
        double surfaceScreenY = engine.worldToScreenY(SURFACE_LEVEL);

        // Draw horizontal strips with color based on depth
        for (int i = 0; i < strips; i++) {
            
            // Calculate the world Y coordinates of the top and bottom of the strip
            double sy1 = i*(HEIGHT / (double) strips);
            double sy2 = (i+1)*(HEIGHT/ (double) strips);
            double wy1=sy1+engine.getCameraY();
            double wy2=sy2+engine.getCameraY();

            //color valus
            int r,g,b;

            /*floats t: 0 at surface, 1 at max depth
             d*/
            if (sy1 >= surfaceScreenY) {
                // If the strip is entierly above the sky
                float t = clampF((float) ((wy1 - SURFACE_LEVEL) / 500.0));
                r = clampC((int) (200 + 30 * t));
                g = clampC((int) (228 + 15 * t));
                b = 255;
            } else if (sy2 <= surfaceScreenY) {
                // Completaly underwater strip

                float d1=Math.max(0f, (float) (SURFACE_LEVEL - wy1));
                float d2 = Math.max(0f, (float) (SURFACE_LEVEL - wy2));
                float t  = clampF(((d1 + d2) / 2f) / 1800f);

                //clamp to ensure values are between 0 and 255
                r = clampC((int) (42  - 28 * t));
                g = clampC((int) (68  - 45 * t));
                b = clampC((int) (102 - 60 * t));
            } else {
                // Split strip — draw sky top, water bottom
                double surf = surfaceScreenY;
                StdDraw.setPenColor(210, 235, 255);
                StdDraw.filledRectangle(WIDTH / 2.0, (surf + sy2) / 2.0,
                                        WIDTH / 2.0, (sy2 - surf) / 2.0);
                float d = Math.max(0f, (float) (SURFACE_LEVEL - wy1));
                float t = clampF(d / 1800f);
                r = clampC((int) (42 - 28 * t));
                g = clampC((int) (68 - 45 * t));
                b = clampC((int) (102 - 60 * t));
                StdDraw.setPenColor(r, g, b);
                StdDraw.filledRectangle(WIDTH / 2.0, (sy1 + surf) / 2.0,
                                        WIDTH / 2.0, (surf - sy1) / 2.0);
                continue;
            }
            StdDraw.setPenColor(r, g, b);
            StdDraw.filledRectangle(WIDTH / 2.0, (sy1 + sy2) / 2.0, WIDTH / 2.0, (sy2 - sy1) / 2.0);
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