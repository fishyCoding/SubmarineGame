/*

Main engine, allows for rocks to be moved and map to be edited.
Also supports clicking and dragging the 30 seafloor control points
to reshape the bottom rock layer.

Saved to txt files.

*/
public class Main {

    // Canvas
    private static final int WIDTH  = 1600;
    private static final int HEIGHT = 800;

    // Seafloor depths (world Y, negative = below surface)
    private static final float SURFACE_LEVEL  =    0f;
    private static final float SEAFLOOR_TOP   = -1820f;
    private static final float SEAFLOOR_BASE  = -2400f;

    // Save files
    private static final String DATA_FILE     = "sprites.txt";
    private static final String SEAFLOOR_FILE = "seafloor.txt";

    // World X range the seafloor spans
    private static final float WORLD_START = -WIDTH;
    private static final float WORLD_END   =  WIDTH * 4f;

    // Engine state
    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Rock            selectedRock;
    private static int             currentDepth = 0;

    // Mouse state
    private static boolean mouseWasDown = false;
    private static float   lastMouseX, lastMouseY;

    // Seafloor editing state
    private static int     draggedFloorPoint = -1;   // index being dragged, or -1
    private static boolean floorEditMode     = false; // 'F' toggles

    // UI / background
    static Water    watergradient;
    static EngineUI UI;

    public static void main(String[] args) {

        // Canvas setup
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();

        // Initialise subsystems
        engine       = new GameEngine(DATA_FILE);
        engine.panCamera(0, -200);
        bottomLayer  = new BottomRockLayer(WORLD_START, WORLD_END,
                                           SEAFLOOR_TOP, SEAFLOOR_BASE,
                                           SEAFLOOR_FILE);
        watergradient = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
        UI            = new EngineUI(engine, WIDTH, HEIGHT);

        printHelp();

        // Main loop
        while (true) {
            handleInput();
            render();
            StdDraw.show();
            StdDraw.pause(16);
        }
    }

    // ── Camera ─────────────────────────────────────────────────────────────────

    private static void handleCam() {
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT))  engine.panCamera(-10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT)) engine.panCamera( 10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP))    engine.panCamera(  0, 10);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN))  engine.panCamera(  0,-10);
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    private static void handleInput() {
        handleCam();

        // ── Keyboard shortcuts ─────────────────────────────────────────────────

        // Toggle floor-edit mode
        if (StdDraw.isKeyPressed('F') || StdDraw.isKeyPressed('f')) {
            floorEditMode = !floorEditMode;
            selectedRock  = null;
            draggedFloorPoint = -1;
            System.out.println("Floor edit mode: " + (floorEditMode ? "ON" : "OFF"));
            StdDraw.pause(200);
        }

        // Toggle depth layer (rock editing)
        if (StdDraw.isKeyPressed(' ') && !floorEditMode) {
            currentDepth = 1 - currentDepth;
            if (selectedRock != null) selectedRock.setDepth(currentDepth);
            StdDraw.pause(200);
        }

        if ((StdDraw.isKeyPressed('U') || StdDraw.isKeyPressed('u')) && !floorEditMode) {
            if (selectedRock != null) selectedRock.removeLastVertex();
            StdDraw.pause(200);
        }

        if ((StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed('d')) && !floorEditMode) {
            engine.deleteSprite(worldMouseX(), worldMouseY());
            selectedRock = null;
            StdDraw.pause(200);
        }

        if ((StdDraw.isKeyPressed('N') || StdDraw.isKeyPressed('n')) && !floorEditMode) {
            Rock newRock = new Rock(worldMouseX(), worldMouseY(), currentDepth);
            engine.addRock(newRock);
            selectedRock = newRock;
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed('s')) {
            engine.saveSprites();
            bottomLayer.save();
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            engine.saveSprites();
            bottomLayer.save();
            System.out.println("Goodbye.");
            System.exit(0);
        }

        // ── Mouse ──────────────────────────────────────────────────────────────

        boolean mouseDown = StdDraw.isMousePressed();
        boolean shiftDown = StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_SHIFT);
        float   mouseX    = worldMouseX();
        float   mouseY    = worldMouseY();

        if (floorEditMode) {
            handleFloorMouse(mouseDown, mouseX, mouseY);
        } else {
            handleRockMouse(mouseDown, shiftDown, mouseX, mouseY);
        }

        mouseWasDown = mouseDown;
    }

    /** Mouse handling when floor-edit mode is active. */
    private static void handleFloorMouse(boolean mouseDown, float mouseX, float mouseY) {

        if (mouseDown && !mouseWasDown) {
            // Fresh click — find nearest floor point
            draggedFloorPoint = bottomLayer.getPointIndexAt(mouseX, mouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else if (mouseDown && mouseWasDown && draggedFloorPoint >= 0) {
            // Drag — move the grabbed point
            bottomLayer.movePoint(draggedFloorPoint, mouseX, mouseY);
        } else if (!mouseDown && mouseWasDown && draggedFloorPoint >= 0) {
            // Release — auto-save
            bottomLayer.save();
            draggedFloorPoint = -1;
        }
    }

    /** Mouse handling for the normal rock-editing mode. */
    private static void handleRockMouse(boolean mouseDown, boolean shiftDown,
                                        float mouseX, float mouseY) {
        if (mouseDown && !mouseWasDown) {
            if (shiftDown && selectedRock != null) {
                selectedRock.addVertex(mouseX, mouseY);
                System.out.println("Added vertex. Now " + selectedRock.getVertexCount() + " vertices.");
            } else {
                Sprite sprite = engine.getSpriteAt(mouseX, mouseY);
                if (sprite instanceof Rock) {
                    selectedRock = (Rock) sprite;
                    currentDepth = selectedRock.getDepth();
                    System.out.println("Selected: " + selectedRock);
                } else {
                    selectedRock = null;
                }
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else if (mouseDown && mouseWasDown && selectedRock != null && !shiftDown) {
            float dx = mouseX - lastMouseX;
            float dy = mouseY - lastMouseY;
            selectedRock.setPosition(selectedRock.getX() + dx, selectedRock.getY() + dy);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    private static void render() {
        watergradient.drawWaterGradient();

        // Background rocks (depth 0)
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);

        // Foreground rocks (depth 1)
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);

        // Any other sprite types
        for (Sprite s : engine.getSprites())
            if (!(s instanceof Rock))
                s.draw(engine);

        // Seafloor
        bottomLayer.draw(engine);

        // Floor-edit handles overlay
        if (floorEditMode) {
            bottomLayer.drawEditHandles(engine, draggedFloorPoint);

            // Label
            StdDraw.setPenColor(255, 220, 80);
            StdDraw.text(WIDTH / 2.0, HEIGHT - 20, "SEAFLOOR EDIT  [ click/drag points ]  F = exit");
        }

        // Highlight selected rock
        if (selectedRock != null && !floorEditMode) {
            double sx = engine.worldToScreenX(selectedRock.getX());
            double sy = engine.worldToScreenY(selectedRock.getY());
            StdDraw.setPenColor(255, 200, 100);
            StdDraw.setPenRadius(0.003);
            StdDraw.circle(sx, sy, 15);
        }

        UI.drawUI(null, currentDepth, 1.0f);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    public  static float worldMouseX() { return engine.screenToWorldX(StdDraw.mouseX()); }
    private static float worldMouseY() { return engine.screenToWorldY(StdDraw.mouseY()); }

    private static void printHelp() {
        System.out.println("=== Submarine Game Editor ===");
        System.out.println("  Click              → select rock");
        System.out.println("  Shift+Click        → add vertex to selected rock");
        System.out.println("  Drag               → move selected rock");
        System.out.println("  Arrow Keys         → scroll camera");
        System.out.println("  Space              → toggle layer (bg / fg)");
        System.out.println("  U                  → undo last vertex");
        System.out.println("  N                  → create new rock");
        System.out.println("  D                  → delete sprite under cursor");
        System.out.println("  S                  → save (rocks + seafloor)");
        System.out.println("  ESC                → save & exit");
        System.out.println("  F                  → toggle SEAFLOOR EDIT mode");
        System.out.println("     (drag the yellow handles to reshape the floor)");
    }
}