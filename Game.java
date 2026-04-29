import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Game — the playable runtime for the submarine game.
 *
 * Multiplayer additions:
 *   - Pass --host to run as server+client (host mode)
 *   - Pass --join <ip> to connect to a host
 *   - Pass --solo for offline play (default)
 *
 *   java Game --solo
 *   java Game --host
 *   java Game --join 192.168.1.5
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

    // ── Networking ─────────────────────────────────────────────────────────────
    private static NetworkClient   netClient  = null;
    private static NetworkServer   netServer  = null;
    private static boolean         multiplayer = false;

    // ── Frame counter ──────────────────────────────────────────────────────────
    private static long tick = 0;

    // ── Cached screen centre ───────────────────────────────────────────────────
    private static final double CX = WIDTH  / 2.0;
    private static final double CY = HEIGHT / 2.0;

    // ── Entry point ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        parseArgs(args);
        setupWindow();
        setupWorld();
        spawnPlayer();
        setupSounds();
        setupNetwork(args);
        printControls();
        gameLoop();
    }

    // ── Argument parsing ───────────────────────────────────────────────────────

    private static void parseArgs(String[] args) {
        for (String a : args) {
            if (a.equals("--host") || a.equals("--join")) {
                multiplayer = true;
            }
        }
    }

    // ── Network setup ──────────────────────────────────────────────────────────

    private static void setupNetwork(String[] args) {
        if (!multiplayer) {
            System.out.println("Solo mode — no network.");
            return;
        }

        String mode = args.length > 0 ? args[0] : "--solo";

        try {
            if (mode.equals("--host")) {
                // Start embedded server then connect as first client
                netServer = new NetworkServer();
                netServer.start();
                System.out.println("Hosting — server started.");
                Thread.sleep(300); // give server a moment to bind
                netClient = new NetworkClient("localhost", "Host");
                netClient.connect();

            } else if (mode.equals("--join") && args.length > 1) {
                String ip = args[1];
                netClient = new NetworkClient(ip, "Player");
                netClient.connect();
                System.out.println("Joined server at " + ip);

            } else {
                System.out.println("Unknown mode — running solo.");
                multiplayer = false;
            }
        } catch (Exception e) {
            System.err.println("Network setup failed: " + e.getMessage());
            System.err.println("Falling back to solo mode.");
            multiplayer = false;
            netClient   = null;
            netServer   = null;
        }
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
        sounds.add(new BackgroundSound(player));
    }

    // ── Game loop ──────────────────────────────────────────────────────────────

    private static void gameLoop() {
        while (true) {
            handleInput();
            updateSounds();
            player.update();
            lockCamera();

            // ── Network tick ──────────────────────────────────────────────────
            if (multiplayer && netClient != null && netClient.isConnected()) {
                // Send position at ~10hz (every 6 frames)
                if (tick % 12 == 0) netClient.sendState(player);

                // Drain remote sounds into our local list so sonar reacts
                netClient.drainSounds(sounds);

                // Drain remote radar pings — start visual animation for each
                for (Packets.RadarPing ping : netClient.drainPings()) {
                    // Add the sound so our sonar hears it
                    sounds.add(new RadarSound(ping.x, ping.y,
                                              PING_SOUND_STRENGTH, ping.playerId));
                    // Note: we don't start a local visual ping for remote pings —
                    // the sound on the sonar is enough. Add visual if you want later.
                    System.out.println("Remote ping from " + ping.playerId);
                }
            }

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
            if (netClient != null) netClient.disconnect();
            if (netServer != null) netServer.stop();
            System.out.println("Goodbye.");
            System.exit(0);
        }

        player.handleInput();

        // ── R — radar ping ─────────────────────────────────────────────────────
        boolean rDown = StdDraw.isKeyPressed('R') || StdDraw.isKeyPressed('r');
        if (rDown && !rWasDown) {
            pingStartMs = System.currentTimeMillis();
            sounds.add(new RadarSound(player.getX(), player.getY(),
                                      PING_SOUND_STRENGTH, "player_ping"));

            // Tell other players about this ping
            if (multiplayer && netClient != null) {
                netClient.sendRadarPing(player.getX(), player.getY());
                netClient.sendSoundEvent(player.getX(), player.getY(),
                                         PING_SOUND_STRENGTH, "radar");
            }
            System.out.println("Radar ping!");
        }
        rWasDown = rDown;

        // ── Mouse hold — test sound ────────────────────────────────────────────
        boolean mouseDown = StdDraw.isMousePressed();
        if (mouseDown && !mouseWasDown) {
            mousePressedMs = System.currentTimeMillis();
        } else if (!mouseDown && mouseWasDown) {
            long  heldMs   = System.currentTimeMillis() - mousePressedMs;
            float strength = Math.min(8000f, heldMs * 3.5f);
            float wx = engine.screenToWorldX(StdDraw.mouseX());
            float wy = engine.screenToWorldY(StdDraw.mouseY());
            sounds.add(new Sound(wx, wy, strength, "test") {});

            // Broadcast test sound to other players
            if (multiplayer && netClient != null) {
                netClient.sendSoundEvent(wx, wy, strength, "test");
            }
            System.out.printf("Test sound spawned at (%.0f, %.0f) strength=%.0f%n",
                    wx, wy, strength);
        }
        mouseWasDown = mouseDown;
    }

    // ── Sound update ───────────────────────────────────────────────────────────

    private static void updateSounds() {
        for (Sound s : sounds) s.tick();
        Sound.pruneDead(sounds);
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

        // Local player submarine
        player.drawCentred(CX, CY);

        // Remote submarines
        if (multiplayer && netClient != null) {
            Map<String, Submarine> remotes = netClient.getRemoteSubs();
            for (Submarine remote : remotes.values()) {
                remote.draw(engine);
            }
        }

        // HUD
        HUD.drawHUD(WIDTH, HEIGHT, CX, CY, player);

        // Passive sonar
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
        Radar.drawRadarOutlines(alpha, engine);
        bottomLayer.drawRadarOutline(engine, alpha);
        StdDraw.setPenRadius(0.002);
    }

    // ── Misc ───────────────────────────────────────────────────────────────────

    private static void printControls() {
        System.out.println("=== Submarine Game ===");
        System.out.println("  W/S         → thrust forward/back");
        System.out.println("  A/D         → rudder left/right");
        System.out.println("  Q/E         → dive/surface");
        System.out.println("  R           → radar ping");
        System.out.println("  Click+Hold  → spawn test sound");
        System.out.println("  ESC         → quit");
        System.out.println();
        System.out.println("  Launch args:");
        System.out.println("    --solo          offline (default)");
        System.out.println("    --host          host a game");
        System.out.println("    --join <ip>     join a game");
    }
}