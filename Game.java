/**
 * Game — the playable runtime for the submarine game.
 *
 * This is separate from {@link Main} (the editor).
 * Run this class to actually play; run Main to build / edit the world.
 *
 * What it does each tick:
 *   1. Read player input → move submarine
 *   2. Update submarine physics
 *   3. Camera follows the submarine
 *   4. Render world (water → bg rocks → fg rocks → submarine → bottom layer → HUD)
 *
 * Controls:
 *   W / Up     → thrust forward
 *   S / Down   → thrust backward
 *   A / Left   → rotate left
 *   D / Right  → rotate right
 *   ESC        → quit
 */
public class Game {

    // ── Canvas / world settings ────────────────────────────────────────────────
    private static final int    WIDTH            = 1600;
    private static final int    HEIGHT           = 1000;
    private static final float  SURFACE_LEVEL    = 0f;
    private static final float  SEAFLOOR_TOP     = -1820f;
    private static final float  SEAFLOOR_BASE    = -2400f;
    private static final String DATA_FILE        = "sprites.txt";

    // ── Submarine spawn ────────────────────────────────────────────────────────
    private static final float  SPAWN_X          = 800f;
    private static final float  SPAWN_Y          = -100f;   // just below the surface
    private static final int    PLAYER_MAX_HP    = 100;

    // ── Systems ────────────────────────────────────────────────────────────────
    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Water           water;
    private static Submarine       player;

    // ── Entry point ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        setupWindow();
        setupWorld();
        spawnPlayer();
        printControls();
        gameLoop();
    }

    // ── Initialisation ─────────────────────────────────────────────────────────

    private static void setupWindow() {
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();
        StdDraw.setTitle("Submarine Game");
    }

    private static void setupWorld() {
        engine      = new GameEngine(DATA_FILE);
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, 120, SEAFLOOR_TOP, SEAFLOOR_BASE);
        water       = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);

        // Start the camera roughly centred on the spawn point
        engine.setCamera(SPAWN_X - WIDTH / 2f, SPAWN_Y - HEIGHT / 2f);
    }

    private static void spawnPlayer() {
        // null imagePath → uses the built-in procedural sub shape
        player = new Submarine("Player", SPAWN_X, SPAWN_Y, PLAYER_MAX_HP, null);
        System.out.println("Spawned: " + player);
    }

    // ── Game loop ──────────────────────────────────────────────────────────────

    private static void gameLoop() {
        while (true) {
            handleGlobalInput();

            // Update
            player.handleInput();
            player.update();
            followCamera();

            // Render
            render();

            StdDraw.show();
            StdDraw.pause(16);          // ~60 fps
        }
    }

    // ── Camera ─────────────────────────────────────────────────────────────────

    /**
     * Keep the submarine centred on screen.
     * The camera's top-left world coordinate is (playerX - halfW, playerY - halfH).
     */
    private static void followCamera() {
        float targetCamX = player.getX() - WIDTH  / 2f;
        float targetCamY = player.getY() - HEIGHT / 2f;

        // Smooth lerp so the camera eases toward the submarine
        float cx = engine.getCameraX() + (targetCamX - engine.getCameraX()) * 0.1f;
        float cy = engine.getCameraY() + (targetCamY - engine.getCameraY()) * 0.1f;
        engine.setCamera(cx, cy);
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    private static void handleGlobalInput() {
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            System.out.println("Goodbye.");
            System.exit(0);
        }

        // Quick respawn (R key)
        if (StdDraw.isKeyPressed('R') || StdDraw.isKeyPressed('r')) {
            player.respawn(SPAWN_X, SPAWN_Y);
            System.out.println("Respawned at (" + SPAWN_X + ", " + SPAWN_Y + ")");
            StdDraw.pause(300);
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    private static void render() {
        // 1. Water / sky gradient (clears the frame)
        water.drawWaterGradient();

        // 2. Background rocks (depth 0) — furthest back
        for (Sprite s : engine.getSprites()) {
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);
        }

        // 3. Player submarine
        player.draw(engine);

        // 4. Foreground rocks (depth 1) — in front of the sub
        for (Sprite s : engine.getSprites()) {
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);
        }

        // 5. Procedural seafloor — topmost terrain layer
        bottomLayer.draw(engine);

        // 6. HUD
        drawHUD();
    }

    // ── HUD ────────────────────────────────────────────────────────────────────

    private static void drawHUD() {
        java.awt.Font mono = new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 13);
        StdDraw.setFont(mono);

        float depthM = Math.max(0f, -player.getY());

        // Shadow pass for readability
        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textLeft(11, HEIGHT - 19, String.format("Depth: %.0f m", depthM));
        StdDraw.textLeft(11, HEIGHT - 35, String.format("Speed: %.1f", player.getSpeed()));
        StdDraw.textLeft(11, HEIGHT - 51, String.format("HP:    %d / %d", player.getHealth(), player.getMaxHealth()));

        // Foreground pass
        StdDraw.setPenColor(200, 230, 255);
        StdDraw.textLeft(10, HEIGHT - 18, String.format("Depth: %.0f m", depthM));
        StdDraw.textLeft(10, HEIGHT - 34, String.format("Speed: %.1f", player.getSpeed()));
        StdDraw.textLeft(10, HEIGHT - 50, String.format("HP:    %d / %d", player.getHealth(), player.getMaxHealth()));

        // Controls hint at the bottom
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        String hint = "WASD / Arrows: Move   R: Respawn   ESC: Quit";
        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textLeft(11, 15, hint);
        StdDraw.setPenColor(180, 210, 255);
        StdDraw.textLeft(10, 16, hint);

        // Dead notice
        if (!player.isAlive()) {
            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 32));
            StdDraw.setPenColor(0, 0, 0);
            StdDraw.text(WIDTH / 2.0 + 2, HEIGHT / 2.0 - 2, "DESTROYED — Press R to respawn");
            StdDraw.setPenColor(255, 80, 80);
            StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0, "DESTROYED — Press R to respawn");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void printControls() {
        System.out.println("=== Submarine Game ===");
        System.out.println("  W / Up      → thrust forward");
        System.out.println("  S / Down    → thrust backward");
        System.out.println("  A / Left    → rotate left");
        System.out.println("  D / Right   → rotate right");
        System.out.println("  R           → respawn");
        System.out.println("  ESC         → quit");
    }
}