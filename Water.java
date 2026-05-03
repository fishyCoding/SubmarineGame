public class Water {

    private final float       SURFACE_LEVEL;
    private final int         HEIGHT;
    private final int         WIDTH;
    private final GameEngine  engine;

    public Water(int height, int width, float surfaceLevel, GameEngine engine) {
        this.HEIGHT        = height;
        this.WIDTH         = width;
        this.SURFACE_LEVEL = surfaceLevel;
        this.engine        = engine;
    }

    public void drawWaterGradient() {
        int strips = 160;

        for (int i = 0; i < strips; i++) {
            // StdDraw Y: 0 at bottom, HEIGHT at top.
            // We iterate from top to bottom in screen space.
            double screenY1 = HEIGHT - i       * (HEIGHT / (double) strips);
            double screenY2 = HEIGHT - (i + 1) * (HEIGHT / (double) strips);
            double worldY   = screenY1 + engine.getCameraY();

            int r, g, b;

            if (worldY >= SURFACE_LEVEL) {
                // Sky
                float t = clampF((float)((worldY - SURFACE_LEVEL) / 500.0));
                r = clampC((int)(200 + 30 * t));
                g = clampC((int)(228 + 15 * t));
                b = 255;
            } else {
                // Water — gets darker with depth
                float depth = Math.max(0f, (float)(SURFACE_LEVEL - worldY));
                float t = clampF(depth / 1800f);
                r = clampC((int)(42 - 28 * t));
                g = clampC((int)(68 - 45 * t));
                b = clampC((int)(102 - 60 * t));
            }

            StdDraw.setPenColor(r, g, b);
            double midY = (screenY1 + screenY2) / 2.0;
            double halfH = Math.abs(screenY1 - screenY2) / 2.0;
            StdDraw.filledRectangle(WIDTH / 2.0, midY, WIDTH / 2.0, halfH);
        }
    }

    private static float clampF(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static int   clampC(int   v) { return Math.max(0,  Math.min(255, v)); }
}