import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Game — the playable runtime for the submarine game.
 *
 * Sound system additions:
 *   - EngineSound  : continuous emitter attached to the player sub, always live.
 *   - Mouse-hold   : click and hold anywhere to spawn a test Sound at the world
 *                    position of the cursor; longer hold = higher intensity.
 *   - Radar ping   : spawns a loud Sound at the sub's position when R is pressed.
 *   - PassiveSonar : bottom-left waveform display driven by total perceived intensity.
 */
public class Game {

    // ── Canvas ─────────────────────────────────────────────────────────────────
    private static final int    WIDTH         = 1300;
    private static final int    HEIGHT        = 1000;

    // ── World ──────────────────────────────────────────────────────────────────
    private static final float  SURFACE_LEVEL = 0f;
    private static final float  SEAFLOOR_TOP  = -1820f;
    private static final float  SEAFLOOR_BASE = -2400f;
    private static final String DATA_FILE     = "sprites.txt";

    // ── Player spawn ───────────────────────────────────────────────────────────
    private static final float  SPAWN_X       = 800f;
    private static final float  SPAWN_Y       = -100f;
    private static final int    PLAYER_MAX_HP = 100;

    // ── Radar ping ─────────────────────────────────────────────────────────────
    private static final long   PING_DURATION_MS = 2500;
    private static long         pingStartMs      = -1;
    private static boolean      rWasDown         = false;

    /** Strength of the sound burst spawned by a radar ping. */
    private static final float  PING_SOUND_STRENGTH = 8000f;

    // ── Systems ────────────────────────────────────────────────────────────────
    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Water           water;
    private static Submarine       player;

    // ── Sound system ───────────────────────────────────────────────────────────
    private static final List<Sound> sounds      = new ArrayList<>();
    private static EngineSound       engineSound;

    // Mouse-hold test sound tracking
    private static boolean  mouseWasDown   = false;
    private static long     mousePressedMs = 0;

    // ── Frame counter (drives sonar animation) ─────────────────────────────────
    private static long tick = 0;

    // ── Cached screen centre ───────────────────────────────────────────────────
    private static final double CX = WIDTH  / 2.0;
    private static final double CY = HEIGHT / 2.0;

    // ── Entry point ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        setupWindow();
        setupWorld();
        spawnPlayer();
        setupSounds();
        printControls();
        gameLoop();
    }

    // ── Init ───────────────────────────────────────────────────────────────────

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
        engine.setCamera(SPAWN_X - (float) CX, SPAWN_Y - (float) CY);
    }

    private static void spawnPlayer() {
        player = new Submarine("Player", SPAWN_X, SPAWN_Y, PLAYER_MAX_HP, null);
        System.out.println("Spawned: " + player);
    }

    private static void setupSounds() {
        engineSound = new EngineSound(player);
        sounds.add(engineSound);

        // Constant ocean static — always audible regardless of position
        sounds.add(new BackgroundSound(player));

        // Test static sound at (1200, -100) — 400 units right of spawn.
        // Swim toward it to hear the sonar grow, away to watch it fade.
        sounds.add(new StaticSound(1200f, -100f, 600f, "test_static"));
        System.out.println("Static test sound placed at (1200, -100) strength=600");
    }

    // ── Game loop ──────────────────────────────────────────────────────────────

    private static void gameLoop() {
        while (true) {
            handleInput();
            updateSounds();
            player.update();
            lockCamera();
            render();
            StdDraw.show();
            StdDraw.pause(16);
            tick++;
        }
    }

    // ── Camera ─────────────────────────────────────────────────────────────────

    private static void lockCamera() {
        engine.setCamera(player.getX() - (float) CX,
                         player.getY() - (float) CY);
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    private static void handleInput() {
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            System.out.println("Goodbye.");
            System.exit(0);
        }

        player.handleInput();

        // ── R — radar ping ─────────────────────────────────────────────────────
        boolean rDown = StdDraw.isKeyPressed('R') || StdDraw.isKeyPressed('r');
        if (rDown && !rWasDown) {
            pingStartMs = System.currentTimeMillis();
            // Spawn a loud sound burst at the sub's position
            sounds.add(new RadarSound(player.getX(), player.getY(),
                                 PING_SOUND_STRENGTH, "player_ping"));
            System.out.println("Radar ping!");
        }
        rWasDown = rDown;

        // ── Mouse hold — test sound ────────────────────────────────────────────
        // Press and hold anywhere on screen. Release spawns a Sound whose
        // strength scales with how long you held (capped at 3 seconds).
        boolean mouseDown = StdDraw.isMousePressed();
        if (mouseDown && !mouseWasDown) {
            // Mouse just pressed — record start time
            mousePressedMs = System.currentTimeMillis();
        } else if (!mouseDown && mouseWasDown) {
            // Mouse just released — compute hold duration → strength
            long heldMs   = System.currentTimeMillis() - mousePressedMs;
            float strength = Math.min(8000f, heldMs * 3.5f);   // 0–3000 over 0–2 s
            float wx = engine.screenToWorldX(StdDraw.mouseX());
            float wy = engine.screenToWorldY(StdDraw.mouseY());
            sounds.add(new TestSound(wx, wy, strength, "test"));
            System.out.printf("Test sound spawned at (%.0f, %.0f) strength=%.0f%n",
                    wx, wy, strength);
        }
        mouseWasDown = mouseDown;
    }

    // ── Sound update ───────────────────────────────────────────────────────────

    private static void updateSounds() {
        for (Sound s : sounds) s.tick();
        Sound.pruneDead(sounds);
        // Make sure engine sound is never removed by pruning (it's never dead)
        if (!sounds.contains(engineSound)) sounds.add(engineSound);
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    private static void render() {
        water.drawWaterGradient();

        // Background rocks
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);

        // Foreground rocks
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);

        bottomLayer.draw(engine);

        // Fog of war
        HUD.drawFog(HEIGHT, WIDTH, CX, CY);

        // Radar outlines
        float pingAlpha = pingAlpha();
        if (pingAlpha > 0f) drawRadarOutlines(pingAlpha);

        // Submarine
        player.drawCentred(CX, CY);

        // HUD
        HUD.drawHUD(WIDTH, HEIGHT, CX, CY, player);

        // ── Passive sonar display ──────────────────────────────────────────────
        float perceived = Sound.totalPerceivedAt(sounds, player.getX(), player.getY());
        PassiveSonar.draw(perceived, HEIGHT, tick);
    }

    // ── Radar ──────────────────────────────────────────────────────────────────

    private static float pingAlpha() {
        if (pingStartMs < 0) return 0f;
        long elapsed = System.currentTimeMillis() - pingStartMs;
        if (elapsed >= PING_DURATION_MS) return 0f;
        return 1f - (float) elapsed / PING_DURATION_MS;
    }

    private static void drawRadarOutlines(float alpha) {
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock)) continue;
            Rock rock = (Rock) s;
            if (rock.getDepth() == 0) continue;

            int a = Math.min(255, (int)(alpha * 255));

            double screenX = engine.worldToScreenX(rock.getX());
            double screenY = engine.worldToScreenY(rock.getY());

            // Draw green outline with glow
            StdDraw.setPenColor(new Color(0, a / 3, 0));
            StdDraw.setPenRadius(0.012);
            StdDraw.point(screenX, screenY);

            StdDraw.setPenColor(new Color(0, Math.min(255, a), 0));
            StdDraw.setPenRadius(0.003);
            StdDraw.point(screenX, screenY);

            // Draw rotation indicator line
            double rotRad = Math.toRadians(rock.getRotation());
            double indicatorLength = 20;
            StdDraw.line(screenX, screenY,
                        screenX + indicatorLength * Math.cos(rotRad),
                        screenY - indicatorLength * Math.sin(rotRad));
        }

        bottomLayer.drawRadarOutline(engine, alpha);
        StdDraw.setPenRadius(0.002);
    }

    // ── Misc ───────────────────────────────────────────────────────────────────

    private static void printControls() {
        System.out.println("=== Submarine Game ===");
        System.out.println("  W / Up      → thrust forward");
        System.out.println("  S / Down    → thrust backward");
        System.out.println("  A / Left    → rudder left");
        System.out.println("  D / Right   → rudder right");
        System.out.println("  Q / E       → dive / surface");
        System.out.println("  R           → radar ping (spawns sound)");
        System.out.println("  Click+Hold  → spawn test sound on release");
        System.out.println("  ESC         → quit");
    }
}