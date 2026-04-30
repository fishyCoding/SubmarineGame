public class EngineUI {

    GameEngine engine;
    int        WIDTH;
    int        HEIGHT;
    public EngineUI(GameEngine engine, int width, int height) {
        this.engine = engine;
        this.WIDTH  = width;
        this.HEIGHT = height;
    }

    public void drawUI(Rock currentRock, int currentDepth, float METERS_PER_PIXEL) {

        StdDraw.setPenColor(255, 255, 255);
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14));
        float wx = worldMouseX(), wy = worldMouseY();
        float depthM = Math.max(0f, -wy) * METERS_PER_PIXEL;
        StdDraw.textLeft(15, HEIGHT - 20, String.format("%.0f %.0f", wx, wy));
        StdDraw.textLeft(15, HEIGHT - 35, String.format("%.0f m", depthM));
        StdDraw.textLeft(15, HEIGHT - 50, String.format("Sprites: %d", engine.getSprites().size()));
        StdDraw.textLeft(15, HEIGHT - 70, "Layer: " + (currentDepth == 0 ? "Bg" : "Fg"));
        if (currentRock != null)
            StdDraw.textLeft(15, HEIGHT - 83,
                    String.format("In progress: %d vertices", currentRock.getVertexCount()));


        if (currentRock != null)
            StdDraw.textLeft(15, HEIGHT - 82,
                    String.format("In progress: %d vertices", currentRock.getVertexCount()));

        
    }
    public float worldMouseX() { 
        return engine.screenToWorldX(StdDraw.mouseX()); 
    }
    private float worldMouseY() { 
        return engine.screenToWorldY(StdDraw.mouseY()); 
    }

}
