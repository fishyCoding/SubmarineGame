public class Main {

    private static final int    WIDTH         = 1600;
    private static final int    HEIGHT        = 800;
    private static final float  SURFACE_LEVEL = 0f;
    private static final float  SEAFLOOR_TOP  = -1820f;
    private static final float  SEAFLOOR_BASE = -2400f;
    private static final String DATA_FILE     = "sprites.txt";
    private static final String SEAFLOOR_FILE = "seafloor.txt";
    private static final float  WORLD_START   = -WIDTH;
    private static final float  WORLD_END     = WIDTH * 4f;

    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Sprite          selectedSprite;
    private static int             currentDepth = 0;

    private static boolean mouseWasDown = false;
    private static float   lastMouseX, lastMouseY;

    static Water    watergradient;
    static EngineUI UI;

    public static void main(String[] args) {
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();

        engine      = new GameEngine(DATA_FILE);
        engine.panCamera(0, -200);
        bottomLayer = new BottomRockLayer(WORLD_START, WORLD_END, SEAFLOOR_TOP, SEAFLOOR_BASE, SEAFLOOR_FILE);

        // Add seafloor control points as sprites so they're always clickable/draggable
        for (int i = 0; i < BottomRockLayer.NUM_POINTS; i++)
            engine.addSeafloorPoint(new SeafloorPoint(bottomLayer, i));

        watergradient = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
        UI = new EngineUI(engine, WIDTH, HEIGHT);

        printHelp();

        while (true) {
            handleInput();
            render();
            StdDraw.show();
            StdDraw.pause(16);
        }
    }

    private static void handleCam() {
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT))  engine.panCamera(-10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT)) engine.panCamera( 10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP))    engine.panCamera(  0, 10);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN))  engine.panCamera(  0,-10);
    }

    private static void handleInput() {
        handleCam();

        if (StdDraw.isKeyPressed(' ')) {
            currentDepth = 1 - currentDepth;
            if (selectedSprite instanceof Rock)
                ((Rock) selectedSprite).setDepth(currentDepth);
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('U') || StdDraw.isKeyPressed('u')) {
            if (selectedSprite instanceof Rock)
                ((Rock) selectedSprite).removeLastVertex();
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed('d')) {
            engine.deleteSprite(worldMouseX(), worldMouseY());
            selectedSprite = null;
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('N') || StdDraw.isKeyPressed('n')) {
            Rock newRock = new Rock(worldMouseX(), worldMouseY(), currentDepth);
            engine.addRock(newRock);
            selectedSprite = newRock;
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

        boolean mouseDown = StdDraw.isMousePressed();
        boolean shiftDown = StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_SHIFT);
        float   mouseX    = worldMouseX();
        float   mouseY    = worldMouseY();

        if (mouseDown && !mouseWasDown) {
            if (shiftDown && selectedSprite instanceof Rock) {
                ((Rock) selectedSprite).addVertex(mouseX, mouseY);
            } else {
                selectedSprite = engine.getSpriteAt(mouseX, mouseY);
                if (selectedSprite instanceof Rock)
                    currentDepth = ((Rock) selectedSprite).getDepth();
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else if (mouseDown && mouseWasDown && selectedSprite != null && !shiftDown) {
            float dx = mouseX - lastMouseX;
            float dy = mouseY - lastMouseY;
            selectedSprite.setPosition(selectedSprite.getX() + dx, selectedSprite.getY() + dy);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else if (!mouseDown && mouseWasDown && selectedSprite instanceof SeafloorPoint) {
            bottomLayer.save();
        }

        mouseWasDown = mouseDown;
    }

    private static void render() {
        watergradient.drawWaterGradient();

        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);

        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);

        bottomLayer.draw(engine);

        // Draw seafloor handles on top of the floor
        for (Sprite s : engine.getSprites())
            if (s instanceof SeafloorPoint)
                s.draw(engine);

        // Highlight selected sprite
        if (selectedSprite != null) {
            double sx = engine.worldToScreenX(selectedSprite.getX());
            double sy = engine.worldToScreenY(selectedSprite.getY());
            StdDraw.setPenColor(255, 200, 100);
            StdDraw.setPenRadius(0.003);
            StdDraw.circle(sx, sy, 15);
        }

        UI.drawUI(null, currentDepth, 1.0f);
    }

    public  static float worldMouseX() { return engine.screenToWorldX(StdDraw.mouseX()); }
    private static float worldMouseY() { return engine.screenToWorldY(StdDraw.mouseY()); }

    private static void printHelp() {
        System.out.println("=== Submarine Game Editor ===");
        System.out.println("  Click        → select rock or floor point");
        System.out.println("  Shift+Click  → add vertex to selected rock");
        System.out.println("  Drag         → move selected rock or floor point");
        System.out.println("  Arrow Keys   → scroll camera");
        System.out.println("  Space        → toggle layer (bg / fg)");
        System.out.println("  U            → undo last vertex");
        System.out.println("  N            → create new rock");
        System.out.println("  D            → delete rock under cursor");
        System.out.println("  S            → save");
        System.out.println("  ESC          → save & exit");
    }
}