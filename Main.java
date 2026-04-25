import java.util.List;

/**
 * Main — Submarine Game Editor / Testbed
 *
 * Run this to build and inspect the world.
 * Run {@link Game} to play.
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

    // ── Settings ───────────────────────────────────────────────────────────────
    private static final int    WIDTH            = 1600;
    private static final int    HEIGHT           = 1000;
    private static final float  METERS_PER_PIXEL = 1.0f;
    private static final float  SURFACE_LEVEL    = 0f;
    private static final float  SEAFLOOR_TOP     = -1820f;
    private static final float  SEAFLOOR_BASE    = -2400f;
    private static final String DATA_FILE        = "sprites.txt";

    // ── State ──────────────────────────────────────────────────────────────────
    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Rock            currentRock;
    private static int             currentDepth = 0;   // 0 = bg, 1 = fg
    private static boolean         mouseWasDown = false;

    static Water    watergradient;
    static EngineUI UI;

    // ── Entry point ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();

        engine      = new GameEngine(DATA_FILE);
        engine.panCamera(0, -200);
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, 120, SEAFLOOR_TOP, SEAFLOOR_BASE);

        watergradient = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
        UI            = new EngineUI(engine, WIDTH, HEIGHT);

        printHelp();

        while (true) {
            handleInput();
            render();
            StdDraw.show();
            StdDraw.pause(16);
        }
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    private static void handleInput() {
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT))  engine.panCamera(-10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT)) engine.panCamera( 10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP))    engine.panCamera(  0, 10);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN))  engine.panCamera(  0,-10);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_O))     engine.setCamera(  0,-10);

        if (StdDraw.isKeyPressed(' ')) {
            currentDepth = 1 - currentDepth;
            System.out.println("Layer: " + (currentDepth == 0 ? "Background" : "Foreground"));
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed('d')) {
            engine.deleteSprite(worldMouseX(), worldMouseY());
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('C') || StdDraw.isKeyPressed('c')) {
            engine.clearAll();
            currentRock = null;
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('U') || StdDraw.isKeyPressed('u')) {
            if (currentRock != null) {
                currentRock.removeLastVertex();
                System.out.println("Undo — " + currentRock.getVertexCount() + " vertices");
            }
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed('s')) {
            engine.saveSprites();
            StdDraw.pause(200);
        }

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

    // ── Render ─────────────────────────────────────────────────────────────────

    private static void render() {
        watergradient.drawWaterGradient();

        // Background rocks (depth 0)
        for (Sprite s : engine.getSprites()) {
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);
        }

        // Foreground rocks (depth 1)
        for (Sprite s : engine.getSprites()) {
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);
        }

        // Non-rock sprites
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock))
                s.draw(engine);
        }

        bottomLayer.draw(engine);

        if (currentRock != null) drawRockPreview(currentRock);

        UI.drawUI(currentRock, currentDepth, METERS_PER_PIXEL);
    }

    // ── Rock preview ───────────────────────────────────────────────────────────

    private static void drawRockPreview(Rock rock) {
        List<Float> verts = rock.getVertices();
        int vc = verts.size() / 2;
        if (vc == 0) return;

        StdDraw.setPenColor(255, 210, 80);
        StdDraw.setPenRadius(0.012);
        for (int i = 0; i < vc; i++) {
            StdDraw.point(engine.worldToScreenX(verts.get(i * 2)),
                          engine.worldToScreenY(verts.get(i * 2 + 1)));
        }

        StdDraw.setPenColor(180, 255, 120);
        StdDraw.setPenRadius(0.003);
        for (int i = 0; i < vc - 1; i++) {
            StdDraw.line(engine.worldToScreenX(verts.get(i * 2)),
                         engine.worldToScreenY(verts.get(i * 2 + 1)),
                         engine.worldToScreenX(verts.get((i + 1) * 2)),
                         engine.worldToScreenY(verts.get((i + 1) * 2 + 1)));
        }

        StdDraw.setPenColor(120, 180, 255);
        StdDraw.setPenRadius(0.002);
        StdDraw.line(engine.worldToScreenX(verts.get((vc - 1) * 2)),
                     engine.worldToScreenY(verts.get((vc - 1) * 2 + 1)),
                     StdDraw.mouseX(), StdDraw.mouseY());

        StdDraw.setPenColor(255, 255, 200);
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        StdDraw.textLeft(10, 55, "Shift+Click to close shape");
        StdDraw.setPenRadius(0.002);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    public static float worldMouseX() { return engine.screenToWorldX(StdDraw.mouseX()); }
    private static float worldMouseY() { return engine.screenToWorldY(StdDraw.mouseY()); }

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
        System.out.println();
        System.out.println("  Run Game.java to play.");
    }
}