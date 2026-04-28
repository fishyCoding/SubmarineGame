/**
 * Main — Submarine Game Editor / Testbed
 *
 * Run this to build and inspect the world using an image-based rock system.
 * Run {@link Game} to play.
 *
 * Controls:
 *   Click              Select rock / edit mode
 *   Drag               Transform selected rock (translate/rotate/scale)
 *   1/2/3              Change editing mode (1=Translate, 2=Rotate, 3=Scale)
 *   Space              Toggle depth layer (background / foreground)
 *   D                  Delete sprite under cursor
 *   C                  Clear all sprites
 *   S                  Save
 *   V                  Duplicate selected rock
 *   N                  Create new rock at cursor
 *   ESC                Save & exit
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
    private static GameEngine       engine;
    private static BottomRockLayer  bottomLayer;
    private static Rock             selectedRock;
    private static Slider.Mode      editMode = Slider.Mode.TRANSLATE;
    private static int              currentDepth = 0;   // 0 = bg, 1 = fg
    private static boolean          mouseWasDown = false;
    private static float            lastMouseX, lastMouseY;

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

        // Editing mode selection
        if (StdDraw.isKeyPressed('1')) {
            editMode = Slider.Mode.TRANSLATE;
            System.out.println("Mode: TRANSLATE");
            StdDraw.pause(200);
        }
        if (StdDraw.isKeyPressed('2')) {
            editMode = Slider.Mode.ROTATE;
            System.out.println("Mode: ROTATE");
            StdDraw.pause(200);
        }
        if (StdDraw.isKeyPressed('3')) {
            editMode = Slider.Mode.SCALE;
            System.out.println("Mode: SCALE");
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed(' ')) {
            currentDepth = 1 - currentDepth;
            if (selectedRock != null) selectedRock.setDepth(currentDepth);
            System.out.println("Layer: " + (currentDepth == 0 ? "Background" : "Foreground"));
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed('d')) {
            engine.deleteSprite(worldMouseX(), worldMouseY());
            selectedRock = null;
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('C') || StdDraw.isKeyPressed('c')) {
            engine.clearAll();
            selectedRock = null;
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('N') || StdDraw.isKeyPressed('n')) {
            Rock newRock = new Rock(worldMouseX(), worldMouseY(), currentDepth);
            engine.addRock(newRock);
            selectedRock = newRock;
            System.out.printf("Created rock at (%.0f, %.0f)%n", worldMouseX(), worldMouseY());
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('V') || StdDraw.isKeyPressed('v')) {
            if (selectedRock != null) {
                // Duplicate with a small offset so it doesn't sit exactly on top
                Rock dupe = new Rock(
                        selectedRock.getX() + 20f,
                        selectedRock.getY() - 20f,
                        selectedRock.getRotation(),
                        selectedRock.getScaleX(),
                        selectedRock.getScaleY(),
                        selectedRock.getDepth());
                dupe.setColor(selectedRock.getR(), selectedRock.getG(), selectedRock.getB());
                engine.addRock(dupe);
                selectedRock = dupe;   // auto-select the new copy
                System.out.println("Duplicated: " + dupe);
            } else {
                System.out.println("V: nothing selected to duplicate.");
            }
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed('s')) {
            engine.saveSprites();
            System.out.println("Saved!");
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            engine.saveSprites();
            System.out.println("Goodbye.");
            System.exit(0);
        }

        boolean mouseDown = StdDraw.isMousePressed();
        float mouseX = worldMouseX();
        float mouseY = worldMouseY();

        if (mouseDown && !mouseWasDown) {
            // Click: select rock
            Sprite sprite = engine.getSpriteAt(mouseX, mouseY);
            if (sprite instanceof Rock) {
                selectedRock = (Rock) sprite;
                currentDepth = selectedRock.getDepth();
                System.out.println("Selected: " + selectedRock);
            } else {
                selectedRock = null;
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } else if (mouseDown && mouseWasDown && selectedRock != null) {
            // Drag: transform selected rock
            float deltaX = mouseX - lastMouseX;
            float deltaY = mouseY - lastMouseY;

            switch (editMode) {
                case TRANSLATE:
                    selectedRock.setPosition(selectedRock.getX() + deltaX,
                                             selectedRock.getY() + deltaY);
                    break;
                case ROTATE:
                    // Compute angle from rock centre to cursor, both in world space.
                    // Using world coords here avoids the screen/world mismatch that
                    // previously broke the rotation direction.
                    float rockWX = selectedRock.getX();
                    float rockWY = selectedRock.getY();
                    float currentAngle = (float) Math.atan2(mouseY    - rockWY, mouseX    - rockWX);
                    float lastAngle    = (float) Math.atan2(lastMouseY - rockWY, lastMouseX - rockWX);
                    float deltaRotation = (float) Math.toDegrees(currentAngle - lastAngle);
                    selectedRock.addRotation(deltaRotation);
                    break;
                case SCALE:
                    float scaleXDelta = 1.0f + deltaX * 0.01f;
                    float scaleYDelta = 1.0f - deltaY * 0.01f;
                    selectedRock.multiplyScale(scaleXDelta, scaleYDelta);
                    break;
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
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

        // Non-rock sprites (sliders, etc)
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock))
                s.draw(engine);
        }

        bottomLayer.draw(engine);

        // Highlight selected rock with mode-specific UI
        if (selectedRock != null) {
            double screenX = engine.worldToScreenX(selectedRock.getX());
            double screenY = engine.worldToScreenY(selectedRock.getY());
            float[] bounds = selectedRock.getBounds();

            double halfWidth  = Math.abs(engine.worldToScreenX(bounds[1]) - engine.worldToScreenX(bounds[0])) / 2;
            double halfHeight = Math.abs(engine.worldToScreenY(bounds[2]) - engine.worldToScreenY(bounds[3])) / 2;
            double maxRadius  = Math.max(halfWidth, halfHeight) * 1.3;

            if (editMode == Slider.Mode.ROTATE) {
                StdDraw.setPenColor(255, 150, 100);
                StdDraw.setPenRadius(0.004);
                StdDraw.circle(screenX, screenY, maxRadius);
            } else if (editMode == Slider.Mode.SCALE) {
                // Draw the rotated scale icon via a temporary Slider
                Slider scaleHandle = new Slider((float) screenX, (float) screenY);
                scaleHandle.setMode(Slider.Mode.SCALE);
                scaleHandle.setRockRotation(selectedRock.getRotation());
                // Draw manually at screen coords (Slider.draw() uses engine conversion,
                // but we need raw screen coords here — so draw the axes directly).
                StdDraw.setPenColor(150, 255, 100);
                StdDraw.setPenRadius(0.004);
                double rad  = Math.toRadians(selectedRock.getRotation());
                double cosR = Math.cos(rad), sinR = Math.sin(rad);
                double arm  = maxRadius * 1.1;
                StdDraw.line(screenX - arm * cosR, screenY - arm * sinR,
                             screenX + arm * cosR, screenY + arm * sinR);
                StdDraw.line(screenX + arm * sinR, screenY - arm * cosR,
                             screenX - arm * sinR, screenY + arm * cosR);
                StdDraw.setPenRadius(0.008);
                StdDraw.point(screenX, screenY);
            } else {
                StdDraw.setPenColor(100, 200, 255);
                StdDraw.setPenRadius(0.004);
                StdDraw.rectangle(screenX, screenY, halfWidth, halfHeight);
            }
        }

        UI.drawUI(null, currentDepth, METERS_PER_PIXEL);
        drawModeIndicator();
    }

    private static void drawModeIndicator() {
        StdDraw.setPenColor(255, 255, 255);
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14));
        StdDraw.textLeft(10, 30, "Mode: " + editMode + " (1/2/3 to change)");
        StdDraw.textLeft(10, 50, "N=New Rock, V=Duplicate, S=Save, D=Delete, C=Clear");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    public static float worldMouseX() { return engine.screenToWorldX(StdDraw.mouseX()); }
    private static float worldMouseY() { return engine.screenToWorldY(StdDraw.mouseY()); }

    private static void printHelp() {
        System.out.println("=== Submarine Game Editor (Image-Based) ===");
        System.out.println("  Click           → select rock");
        System.out.println("  Drag            → transform (translate/rotate/scale)");
        System.out.println("  1/2/3           → change mode (translate/rotate/scale)");
        System.out.println("  Arrow Keys      → scroll camera");
        System.out.println("  Space           → toggle layer (bg / fg)");
        System.out.println("  V               → duplicate selected rock");
        System.out.println("  N               → create new rock");
        System.out.println("  D               → delete sprite under cursor");
        System.out.println("  C               → clear all sprites");
        System.out.println("  S               → save");
        System.out.println("  ESC             → save & exit");
        System.out.println();
        System.out.println("  Run Game.java to play.");
    }
}