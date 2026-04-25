import java.util.List;

/**
 * Main — Submarine Game Editor / Testbed
 *
 * Controls:
 *   Click              Add vertex to current rock
 *   Shift+Click        Finish / close current rock
 *   Arrow Keys         Pan camera
 *   Space              Toggle depth layer (background / foreground)
 *   U                  Undo last vertex
 *   D                  Delete sprite under cursor
 *   C                  Clear all sprites
 *   S                  Save
 *   ESC                Finish current rock + exit
 */
public class Main {

    // Base Settings
    private static final int   WIDTH             = 1600;
    private static final int   HEIGHT            = 1000;
    private static final float METERS_PER_PIXEL  = 1.0f;
    private static final float SURFACE_LEVEL     = 0f;
    private static final float SEAFLOOR_TOP      = -1820f;
    private static final float SEAFLOOR_BASE     = -2400f;
    private static final String DATA_FILE        = "sprites.txt";

    // State variables for the game engine (not for runtime)
    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Rock            currentRock;
    private static int             currentDepth = 0;   // 0 = bg, 1 = fg
    private static boolean         mouseWasDown = false;

    static Water watergradient;
    static EngineUI UI;

    public static void main(String[] args) {
        
        //Setting up the canvas
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();

        //setting up the engine
        engine = new GameEngine(DATA_FILE);
        engine.panCamera(0, -200);
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, 120, SEAFLOOR_TOP, SEAFLOOR_BASE);

        //setting up sprites and other runtime classes

        watergradient = new Water(HEIGHT,WIDTH,SURFACE_LEVEL,engine);
        UI = new EngineUI(engine,WIDTH,HEIGHT);

        //Printing documentation. Imma get rid of this soon
        printHelp();

        while (true) {
            handleInput();
            render();
            StdDraw.show();
            StdDraw.pause(16); //tick rate
        }
    }


    private static void handleInput() {
        // Camera pan
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT))  engine.panCamera(-10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT)) engine.panCamera( 10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP))    engine.panCamera(  0, 10);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN))  engine.panCamera(  0,-10);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_O))  engine.setCamera(  0,-10);


        // Toggle layer
        if (StdDraw.isKeyPressed(' ')) {
            currentDepth = 1 - currentDepth;
            System.out.println("Layer: " + (currentDepth == 0 ? "Background" : "Foreground"));
            StdDraw.pause(200);
        }

        // Delete under cursor
        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed('d')) {
            engine.deleteSprite(worldMouseX(), worldMouseY());
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
                System.out.println("Undo — " + currentRock.getVertexCount() + " vertices");
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
            System.out.println("Goodbye.");
            System.exit(0);
        }

        boolean mouseDown = StdDraw.isMousePressed();
        if (mouseDown && !mouseWasDown) {
            boolean shift = StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_SHIFT);
            float wx = worldMouseX(), wy = worldMouseY();

            if (currentRock == null) {
                currentRock = engine.createRock(wx, wy, currentDepth);
                System.out.printf("Started rock at (%.0f, %.0f)%n", wx, wy);
            } else if (shift) {
                currentRock.closePath();
                engine.addRock(currentRock);
                System.out.println("Finished rock — " + currentRock.getVertexCount() + " vertices");
                currentRock = null;
            } else {
                currentRock.addVertex(wx, wy);
                System.out.println("Vertex added — total: " + currentRock.getVertexCount());
            }
        }
        mouseWasDown = mouseDown;
    }


    private static void render() {
        
        // Water
        watergradient.drawWaterGradient();

        // Background rocks
        for (Sprite s : engine.getSprites()) {
            if (s instanceof Polygon && ((Polygon) s).getDepth() == 0)
                s.draw(engine);
        }

        // Foreground rock sprites (depth 1) — rendered second
        for (Sprite s : engine.getSprites()) {
            if (s instanceof Polygon && ((Polygon) s).getDepth() == 1)
                s.draw(engine);
        }

        // All non-polygon sprites (squares, etc.)
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Polygon))
                s.draw(engine);
        }

        // Bottom rock layer (renders last — furthest in front)
        bottomLayer.draw(engine);

        // In-progress rock preview
        if (currentRock != null) drawRockPreview(currentRock);

        UI.drawUI(currentRock, currentDepth, METERS_PER_PIXEL);
    }





    private static void drawRockPreview(Polygon poly) {
        List<Float> verts = poly.getVertices();
        int vc = verts.size() / 2;
        if (vc == 0) return;

        // Vertex dots
        StdDraw.setPenColor(255, 210, 80);
        StdDraw.setPenRadius(0.012);
        for (int i = 0; i < vc; i++) {
            StdDraw.point(engine.worldToScreenX(verts.get(i * 2)),
                          engine.worldToScreenY(verts.get(i * 2 + 1)));
        }

        // Edge lines
        StdDraw.setPenColor(180, 255, 120);
        StdDraw.setPenRadius(0.003);
        for (int i = 0; i < vc - 1; i++) {
            StdDraw.line(engine.worldToScreenX(verts.get(i * 2)),
                         engine.worldToScreenY(verts.get(i * 2 + 1)),
                         engine.worldToScreenX(verts.get((i + 1) * 2)),
                         engine.worldToScreenY(verts.get((i + 1) * 2 + 1)));
        }

        // Preview line to mouse
        StdDraw.setPenColor(120, 180, 255);
        StdDraw.setPenRadius(0.002);
        StdDraw.line(engine.worldToScreenX(verts.get((vc - 1) * 2)),
                     engine.worldToScreenY(verts.get((vc - 1) * 2 + 1)),
                     StdDraw.mouseX(), StdDraw.mouseY());

        // Hint
        StdDraw.setPenColor(255, 255, 200);
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        StdDraw.textLeft(10, 55, "Shift+Click to close shape");
        StdDraw.setPenRadius(0.002);
    }


    public static float worldMouseX() { 
        return engine.screenToWorldX(StdDraw.mouseX()); 
    }
    private static float worldMouseY() { 
        return engine.screenToWorldY(StdDraw.mouseY()); 
    }


    private static void printHelp() {
        System.out.println("=== Submarine Game Editor ===");
        System.out.println("  Click           → add vertex");
        System.out.println("  Shift+Click     → finish / close rock");
        System.out.println("  Arrow Keys      → scroll camera");
        System.out.println("  Space           → toggle layer (bg / fg)");
        System.out.println("  U               → undo last vertex");
        System.out.println("  D               → delete sprite under cursor");
        System.out.println("  C               → clear all sprites");
        System.out.println("  S               → save");
        System.out.println("  ESC             → save & exit");
    }
}