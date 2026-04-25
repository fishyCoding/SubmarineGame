import java.util.List;

/**
 * Main class - Submarine Game Editor using StdDraw
 * 
 * Controls:
 * - Click: Add vertices for rock shape (Shift+Click to finish)
 * - Arrow Keys: Scroll/pan camera
 * - Spacebar: Toggle layer (foreground/background)
 * - D: Delete sprite under cursor
 * - C: Clear all sprites
 * - U: Undo last vertex
 * - S: Save sprites (auto-saved on finalization)
 * - ESC: Exit
 */
public class Main {
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 1000;
    private static final float METERS_PER_PIXEL = 1.0f;
    private static final float SURFACE_LEVEL = 0f;
    private static final float SEAFLOOR_TOP_DEPTH = -1820f;
    private static final float SEAFLOOR_BASE_DEPTH = -2400f;
    private static final String DATA_FILE = "sprites.txt";
    
    private static GameEngine engine;
    private static Rock currentRock;
    private static int currentDepth = 0; // 0 = background (far), 1 = foreground (close)
    private static boolean keyPressed = false;

    private static float[] bottomRockWorldX;
    private static float[] bottomRockWorldY;
    private static int bottomRockPoints;

    public static void main(String[] args) {
        // Initialize StdDraw window
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();
        
        // Initialize game engine
        engine = new GameEngine(DATA_FILE);
        engine.panCamera(0, -200); // show sky above surface and water below
        generateBottomRockLayer(-WIDTH, WIDTH * 4, 100);
        
        System.out.println("=== Submarine Game Editor ===");
        System.out.println("Controls:");
        System.out.println("  Click: Add vertex to rock shape");
        System.out.println("  Shift+Click: Finish rock shape");
        System.out.println("  Arrow Keys: Scroll camera");
        System.out.println("  Spacebar: Toggle layer (background/foreground)");
        System.out.println("  D: Delete sprite at cursor");
        System.out.println("  C: Clear all sprites");
        System.out.println("  U: Undo last vertex");
        System.out.println("  S: Save");
        System.out.println("  ESC: Exit");
        
        // Main loop
        while (true) {
            handleInput();
            update();
            render();
        }
    }

    private static void handleInput() {
        // Camera panning with arrow keys
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT)) {
            engine.panCamera(-10, 0);
        }
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT)) {
            engine.panCamera(10, 0);
        }
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP)) {
            engine.panCamera(0, 10);
        }
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN)) {
            engine.panCamera(0, -10);
        }

        // Toggle layer with spacebar
        if (StdDraw.isKeyPressed(' ')) {
            currentDepth = 1 - currentDepth;
            System.out.println("Layer: " + (currentDepth == 0 ? "Background (Far)" : "Foreground (Close)"));
            StdDraw.pause(200);
            keyPressed = false;
        }

        // Delete sprite
        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed('d')) {
            float worldX = engine.screenToWorldX(StdDraw.mouseX());
            float worldY = engine.screenToWorldY(StdDraw.mouseY());
            engine.deleteSprite(worldX, worldY);
            StdDraw.pause(200);
        }

        // Clear all
        if (StdDraw.isKeyPressed('C') || StdDraw.isKeyPressed('c')) {
            engine.clearAll();
            currentRock = null;
            StdDraw.pause(200);
        }

        // Undo last vertex
        if (StdDraw.isKeyPressed('U') || StdDraw.isKeyPressed('u')) {
            if (currentRock != null) {
                currentRock.removeLastVertex();
                System.out.println("Undo: " + currentRock.getVertexCount() + " vertices");
            }
            StdDraw.pause(200);
        }

        // Save
        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed('s')) {
            engine.saveSprites();
            StdDraw.pause(200);
        }

        // Exit
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            if (currentRock != null) {
                currentRock.closePath();
                engine.addRock(currentRock);
                currentRock = null;
            }
            engine.saveSprites();
            System.out.println("Exiting...");
            System.exit(0);
        }

        // Mouse interaction for polygon creation
        if (StdDraw.isMousePressed()) {
            float worldX = engine.screenToWorldX(StdDraw.mouseX());
            float worldY = engine.screenToWorldY(StdDraw.mouseY());
            boolean shiftPressed = (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_SHIFT));

            if (!keyPressed) {
                if (currentRock == null) {
                    // Start new rock
                    currentRock = engine.createRock(worldX, worldY, currentDepth);
                    System.out.println("Started new rock at (" + worldX + ", " + worldY + ")");
                } else if (shiftPressed) {
                    // Finish rock
                    currentRock.closePath();
                    engine.addRock(currentRock);
                    System.out.println("Finished rock with " + currentRock.getVertexCount() + " vertices");
                    currentRock = null;
                } else {
                    // Add vertex
                    currentRock.addVertex(worldX, worldY);
                    System.out.println("Added vertex: " + currentRock.getVertexCount() + " total");
                }
                keyPressed = true;
                StdDraw.pause(100);
            }
        } else {
            keyPressed = false;
        }
    }

    private static void update() {
        // Update logic (can add animations, physics, etc. here)
    }

    private static void render() {
        // Draw water gradient background
        drawWaterGradient();
        drawBottomRockLayer();

        // Draw all sprites (sorted by depth)
        // Background layer first (depth 0)
        for (Sprite sprite : engine.getSprites()) {
            if (sprite instanceof Polygon) {
                Polygon poly = (Polygon) sprite;
                if (poly.getDepth() == 0) {
                    Polygon.drawPolygon(poly);
                }
            }
        }
        
        // Foreground layer (depth 1)
        for (Sprite sprite : engine.getSprites()) {
            if (sprite instanceof Polygon) {
                Polygon poly = (Polygon) sprite;
                if (poly.getDepth() == 1) {
                    Polygon.drawPolygon(poly);
                }
            }
        }

        // Draw polygon being created
        if (currentRock != null) {
            drawPolygonPreview(currentRock);
        }

        // Draw UI
        drawUI();

        StdDraw.show();
        StdDraw.pause(16); // ~60 FPS
    }

    /**
     * Draw realistic underwater gradient - darker/bluer at top (deep), lighter at bottom (shallower)
     */
    private static void drawWaterGradient() {
        int strips = 150; // More strips for smoother gradient over larger area
        double surfaceScreenY = engine.worldToScreenY(SURFACE_LEVEL);

        for (int i = 0; i < strips; i++) {
            double screenY1 = i * (HEIGHT / (double) strips);
            double screenY2 = (i + 1) * (HEIGHT / (double) strips);
            double worldY1 = screenY1 + engine.getCameraY();
            double worldY2 = screenY2 + engine.getCameraY();

            if (screenY1 >= surfaceScreenY) {
                // Sky above the water surface
                float t1 = Math.min(1f, Math.max(0f, (float) ((worldY1 - SURFACE_LEVEL) / 500.0)));
                float t2 = Math.min(1f, Math.max(0f, (float) ((worldY2 - SURFACE_LEVEL) / 500.0)));
                int r1 = clampColor((int) (200 + 30 * t1));
                int g1 = clampColor((int) (230 + 15 * t1));
                int b1 = 255;
                int r2 = clampColor((int) (200 + 30 * t2));
                int g2 = clampColor((int) (230 + 15 * t2));
                int b2 = 255;
                StdDraw.setPenColor((r1 + r2) / 2, (g1 + g2) / 2, (b1 + b2) / 2);
                StdDraw.filledRectangle(WIDTH / 2.0, (screenY1 + screenY2) / 2.0, WIDTH / 2.0, (screenY2 - screenY1) / 2.0);
            } else if (screenY2 <= surfaceScreenY) {
                // Water below the surface
                float depth1 = Math.max(0f, (float) (SURFACE_LEVEL - worldY1));
                float depth2 = Math.max(0f, (float) (SURFACE_LEVEL - worldY2));
                float t1 = Math.min(1f, depth1 / 1800f);
                float t2 = Math.min(1f, depth2 / 1800f);
                int r1 = clampColor((int) (45 - 30 * t1));
                int g1 = clampColor((int) (65 - 45 * t1));
                int b1 = clampColor((int) (90 - 55 * t1));
                int r2 = clampColor((int) (45 - 30 * t2));
                int g2 = clampColor((int) (65 - 45 * t2));
                int b2 = clampColor((int) (90 - 55 * t2));
                StdDraw.setPenColor((r1 + r2) / 2, (g1 + g2) / 2, (b1 + b2) / 2);
                StdDraw.filledRectangle(WIDTH / 2.0, (screenY1 + screenY2) / 2.0, WIDTH / 2.0, (screenY2 - screenY1) / 2.0);
            } else {
                // Split strip at surface
                double surfaceY = surfaceScreenY;
                // Top part = sky
                float tSky1 = Math.min(1f, Math.max(0f, (float) ((worldY2 - SURFACE_LEVEL) / 500.0)));
                int rSky = clampColor((int) (200 + 30 * tSky1));
                int gSky = clampColor((int) (230 + 15 * tSky1));
                StdDraw.setPenColor(rSky, gSky, 255);
                StdDraw.filledRectangle(WIDTH / 2.0, (surfaceY + screenY2) / 2.0, WIDTH / 2.0, (screenY2 - surfaceY) / 2.0);
                // Bottom part = water
                float depthWater = Math.max(0f, (float) (SURFACE_LEVEL - worldY1));
                float tWater = Math.min(1f, depthWater / 1800f);
                int rWater = clampColor((int) (45 - 30 * tWater));
                int gWater = clampColor((int) (65 - 45 * tWater));
                int bWater = clampColor((int) (90 - 55 * tWater));
                StdDraw.setPenColor(rWater, gWater, bWater);
                StdDraw.filledRectangle(WIDTH / 2.0, (screenY1 + surfaceY) / 2.0, WIDTH / 2.0, (surfaceY - screenY1) / 2.0);
            }
        }
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void drawBottomRockLayer() {
        if (bottomRockPoints == 0 || bottomRockWorldX == null || bottomRockWorldY == null) return;

        int totalPoints = bottomRockPoints * 2;
        double[] xs = new double[totalPoints];
        double[] ys = new double[totalPoints];

        for (int i = 0; i < bottomRockPoints; i++) {
            xs[i] = engine.worldToScreenX(bottomRockWorldX[i]);
            ys[i] = engine.worldToScreenY(bottomRockWorldY[i]);
        }

        for (int i = 0; i < bottomRockPoints; i++) {
            int j = bottomRockPoints + i;
            xs[j] = engine.worldToScreenX(bottomRockWorldX[bottomRockPoints - 1 - i]);
            ys[j] = engine.worldToScreenY(SEAFLOOR_BASE_DEPTH);
        }

        StdDraw.setPenColor(90, 90, 100);
        StdDraw.filledPolygon(xs, ys);
        StdDraw.setPenColor(120, 120, 130);
        StdDraw.setPenRadius(0.003);
        for (int i = 0; i < bottomRockPoints - 1; i++) {
            StdDraw.line(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        }
        StdDraw.setPenRadius(0.002);
    }

    private static void generateBottomRockLayer(float worldStart, float worldEnd, int points) {
        bottomRockPoints = Math.max(2, points);
        bottomRockWorldX = new float[bottomRockPoints];
        bottomRockWorldY = new float[bottomRockPoints];
        float step = (worldEnd - worldStart) / (bottomRockPoints - 1);

        for (int i = 0; i < bottomRockPoints; i++) {
            float worldX = worldStart + i * step;
            double variance = Math.sin(worldX * 0.005) * 16 + Math.cos(worldX * 0.009) * 12;
            float worldY = SEAFLOOR_TOP_DEPTH + (float) variance;
            float minDepth = SEAFLOOR_TOP_DEPTH - 18f;
            float maxDepth = SEAFLOOR_TOP_DEPTH + 18f;
            bottomRockWorldX[i] = worldX;
            bottomRockWorldY[i] = Math.max(minDepth, Math.min(maxDepth, worldY));
        }
    }

    /**
     * Draw a polygon sprite with layer-based coloring for water effect
     */



    private static void drawRockTexture(double[] xs, double[] ys, int baseR, int baseG, int baseB) {
        StdDraw.setPenColor(Math.max(0, baseR - 35), Math.max(0, baseG - 35), Math.max(0, baseB - 35));
        StdDraw.setPenRadius(0.004);
        int marks = Math.min(6, xs.length - 1);
        for (int i = 0; i < marks; i++) {
            int next = (i + 3) % xs.length;
            double midX = (xs[i] + xs[next]) / 2.0;
            double midY = (ys[i] + ys[next]) / 2.0;
            double offsetX = Math.sin(i * 1.9) * 6;
            double offsetY = Math.cos(i * 2.3) * 3;
            double radius = 3 + (i % 3);
            StdDraw.filledCircle(midX + offsetX, midY + offsetY, radius);
        }
        StdDraw.setPenRadius(0.002);
    }

    /**
     * Draw the polygon being created in real-time
     */

    private static void drawPolygonPreview(Polygon poly) {
        List<Float> vertices = poly.getVertices();
        if (vertices.size() < 2) return;

        int vertexCount = vertices.size() / 2;

        // Draw existing vertices
        StdDraw.setPenColor(255, 200, 100);
        StdDraw.setPenRadius(0.01);
        for (int i = 0; i < vertexCount; i++) {
            double screenX = engine.worldToScreenX(vertices.get(i * 2));
            double screenY = engine.worldToScreenY(vertices.get(i * 2 + 1));
            StdDraw.point(screenX, screenY);
        }

        // Draw lines connecting vertices
        StdDraw.setPenColor(200, 255, 100);
        StdDraw.setPenRadius(0.003);
        for (int i = 0; i < vertexCount - 1; i++) {
            double x1 = engine.worldToScreenX(vertices.get(i * 2));
            double y1 = engine.worldToScreenY(vertices.get(i * 2 + 1));
            double x2 = engine.worldToScreenX(vertices.get((i + 1) * 2));
            double y2 = engine.worldToScreenY(vertices.get((i + 1) * 2 + 1));
            StdDraw.line(x1, y1, x2, y2);
        }

        // Draw line from last vertex to mouse (preview)
        double lastX = engine.worldToScreenX(vertices.get((vertexCount - 1) * 2));
        double lastY = engine.worldToScreenY(vertices.get((vertexCount - 1) * 2 + 1));
        StdDraw.setPenColor(150, 200, 255);
        StdDraw.line(lastX, lastY, StdDraw.mouseX(), StdDraw.mouseY());

        // Draw instruction text
        StdDraw.setPenColor(255, 255, 255);
        StdDraw.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
        StdDraw.textLeft(10, 60, "Shift+Click to finish rock shape");
    }

    private static void drawUI() {
        StdDraw.setPenColor(255, 255, 255);
        StdDraw.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 14));
        
        // Mouse position and depth in meters
        float worldX = engine.screenToWorldX(StdDraw.mouseX());
        float worldY = engine.screenToWorldY(StdDraw.mouseY());
        float depthMeters = Math.max(0f, -worldY) * METERS_PER_PIXEL;
        String posText = String.format("World: (%.0f, %.0f)", worldX, worldY);
        StdDraw.textLeft(10, HEIGHT - 20, posText);
        String depthText = String.format("Depth: %.0f m", depthMeters);
        StdDraw.textLeft(10, HEIGHT - 40, depthText);

        // Rock count
        String countText = String.format("Rocks: %d", engine.getSprites().size());
        StdDraw.textLeft(10, HEIGHT - 60, countText);

        // Current layer
        String layerText = currentDepth == 0 ? "Layer: Background (Far)" : "Layer: Foreground (Close)";
        StdDraw.textLeft(10, HEIGHT - 60, layerText);

        // Current rock info
        if (currentRock != null) {
            String polyText = String.format("Current Rock: %d vertices", currentRock.getVertexCount());
            StdDraw.textLeft(10, HEIGHT - 80, polyText);
        }

        // Instructions
        StdDraw.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 12));
        StdDraw.textLeft(10, 20, "Click: Vertex | Shift+Click: Finish | Arrow Keys: Scroll | Space: Toggle Layer | U: Undo | D: Delete | C: Clear | S: Save | ESC: Exit");
    }
}
