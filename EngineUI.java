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
        java.awt.Font mono = new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 13);
        StdDraw.setFont(mono);

        float wx = worldMouseX(), wy = worldMouseY();
        float depthM = Math.max(0f, -wy) * METERS_PER_PIXEL;

        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textLeft(11, HEIGHT - 19, String.format("World: (%.0f, %.0f)", wx, wy));
        StdDraw.textLeft(11, HEIGHT - 35, String.format("Depth: %.0f m", depthM));
        StdDraw.textLeft(11, HEIGHT - 51, String.format("Sprites: %d", engine.getSprites().size()));
        StdDraw.textLeft(11, HEIGHT - 67, "Layer: " + (currentDepth == 0 ? "Background" : "Foreground"));
        if (currentRock != null)
            StdDraw.textLeft(11, HEIGHT - 83,
                    String.format("In progress: %d vertices", currentRock.getVertexCount()));

        StdDraw.setPenColor(220, 220, 220);
        StdDraw.textLeft(10, HEIGHT - 18, String.format("World: (%.0f, %.0f)", wx, wy));
        StdDraw.textLeft(10, HEIGHT - 34, String.format("Depth: %.0f m", depthM));
        StdDraw.textLeft(10, HEIGHT - 50, String.format("Sprites: %d", engine.getSprites().size()));
        StdDraw.textLeft(10, HEIGHT - 66, "Layer: " + (currentDepth == 0 ? "Background" : "Foreground"));
        if (currentRock != null)
            StdDraw.textLeft(10, HEIGHT - 82,
                    String.format("In progress: %d vertices", currentRock.getVertexCount()));

        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        String hint = "Click:Vertex  Shift+Click:Finish  Arrows:Scroll  "
                    + "Space:Layer  U:Undo  D:Delete  C:Clear  S:Save  ESC:Exit";
        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textLeft(11, 15, hint);
        StdDraw.setPenColor(200, 220, 255);
        StdDraw.textLeft(10, 16, hint);
    }
    public float worldMouseX() { 
        return engine.screenToWorldX(StdDraw.mouseX()); 
    }
    private float worldMouseY() { 
        return engine.screenToWorldY(StdDraw.mouseY()); 
    }

}
