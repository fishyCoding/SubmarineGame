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
    private static final int    HEIGHT        = 800;
    private static final double CX = WIDTH  / 2.0;
    private static final double CY = HEIGHT / 2.0;

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
    private static final float  PING_SOUND_STRENGTH = 10000f;

    // ── Radar screen (top-right corner) ───────────────────────────────────────
    // Stores (playerId, worldX, worldY, pingAlphaAtDetection) for each contact
    // detected during the last radar ping. Fades out over PING_DURATION_MS.
    private static final java.util.Map<String, float[]> radarContacts =
            new java.util.concurrent.ConcurrentHashMap<>();
    // [0]=worldX [1]=worldY [2]=alpha (we recompute from pingAlpha each frame)

    /**
     * When true, passive sonar uses ray-casting to check line-of-sight through
     * rocks before adding a sound's contribution. Set false for the original
     * unobstructed behaviour.
     */
    private static final boolean USE_RAYTRACE_SONAR = true;

    // ── Systems ────────────────────────────────────────────────────────────────
    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Water           water;
    private static Submarine       player;

    // ── Sound system ───────────────────────────────────────────────────────────
    private static final List<Sound> sounds      = new ArrayList<>();
    private static EngineSound       engineSound;

    // ── Torpedo system ─────────────────────────────────────────────────────────
    private static TorpedoSystem torpedoSystem;
    private static final List<String>         contactIds  = new ArrayList<>();
    private static final Map<String, float[]> contactPos  = new java.util.LinkedHashMap<>();
    private static int                        selectedIdx  = -1;  // index into contactIds

    // Mouse-hold test sound tracking
    private static boolean  mouseWasDown   = false;

    // ── Networking ─────────────────────────────────────────────────────────────
    private static NetworkClient   netClient  = null;
    private static NetworkServer   netServer  = null;
    private static boolean         multiplayer = false;

    private static long tick = 0;

    public static void main(String[] args) {
        parseArgs(args);
        spawnPlayer();
        setupWorld();
        setupNetwork(args);
        setupWindow();
        setupSounds();
        torpedoSystem = new TorpedoSystem();
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
                netServer = new NetworkServer();
                netServer.start();
                System.out.println("Hosting — server started.");
                Thread.sleep(300);
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
            System.err.println("Fail " + e.getMessage());
            System.err.println("Running w/o network");
            multiplayer = false;
            netClient = null;
            netServer = null;
        }
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    private static void setupWindow() {
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();
        String title = multiplayer ? netClient.host : "Solo";
        StdDraw.setTitle(title);
    }

    private static final String SEAFLOOR_FILE = "seafloor.txt";

    private static void setupWorld() {
        engine = new GameEngine(DATA_FILE);
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, SEAFLOOR_TOP, SEAFLOOR_BASE, SEAFLOOR_FILE);
        water = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
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

            if (multiplayer && netClient != null && netClient.isConnected()) {
                if (tick % 12 == 0) netClient.sendState(player);

                if (tick % 12 == 0) {
                    netClient.sendSoundEvent(
                            player.getX(), player.getY(),
                            engineSound.getStrength(), "engine");
                }

                // ── Torpedo replication ────────────────────────────────────────
                if (torpedoSystem.hasTorpedo()) {
                    Torpedo t = torpedoSystem.getTorpedo();
                    netClient.sendTorpedoState(t.getX(), t.getY(), t.getAngle(), true);
                } else if (torpedoSystem.getTorpedo() != null
                        && torpedoSystem.getTorpedo().hasExploded()) {
                    // One final packet to tell others it's gone
                    Torpedo t = torpedoSystem.getTorpedo();
                    netClient.sendTorpedoState(t.getX(), t.getY(), t.getAngle(), false);
                    netClient.sendTorpedoDetonate(
                            t.getX(), t.getY(), t.getBlastRadius(), t.getDamage());
                    // Now safe to null — packet is already queued
                    torpedoSystem.resetTorpedo();
                }

                // ── Drain remote detonations — apply damage to local player ───
                for (Packets.TorpedoDetonate d : netClient.drainDetonations()) {
                    float dx = player.getX() - d.x;
                    float dy = player.getY() - d.y;
                    if (dx * dx + dy * dy <= d.blastRadius * d.blastRadius) {
                        player.takeDamage(d.damage);
                        System.out.println("Hit by remote torpedo from " + d.playerId + "!");
                    }
                    // Remove the visual torpedo for that player
                }

                netClient.drainSounds(sounds);

                for (Packets.RadarPing ping : netClient.drainPings()) {
                    sounds.add(new RadarSound(ping.x, ping.y, PING_SOUND_STRENGTH, ping.playerId));
                    System.out.println("Remote ping from " + ping.playerId);
                }
            }

            player.update();

            // ── Torpedo update ─────────────────────────────────────────────────
            if (torpedoSystem.hasTorpedo()) {
                List<Rock> fgRocks = new ArrayList<>();
                for (Sprite s : engine.getSprites())
                    if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                        fgRocks.add((Rock) s);

                Map<String, Submarine> remotes = (multiplayer && netClient != null)
                        ? netClient.getRemoteSubs()
                        : new java.util.HashMap<>();

                torpedoSystem.update(
                        StdDraw.mouseX(), StdDraw.mouseY(), CX, CY,
                        fgRocks, bottomLayer, remotes, player);
            }

            // ── Rock collision ─────────────────────────────────────────────────
            checkCollisions();

            lockCamera();
            render();
            StdDraw.show();
            StdDraw.pause(16);
            tick++;
        }
    }

    // ── Collision detection ────────────────────────────────────────────────────

    /**
     * Check player against all foreground rocks and the seafloor.
     * On collision, respawn at the spawn point.
     */
    private static void checkCollisions() {
        if (!player.isAlive()) return;

        // Check foreground rocks (depth == 1)
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock)) continue;
            Rock rock = (Rock) s;
            if (rock.getDepth() != 1) continue;
            if (player.collidesWithRock(rock)) {
                System.out.println("Hit a rock!");
                player.takeDamage(player.getHealth());   // triggers die() → death screen
                return;
            }
        }

        // Check seafloor
        float floorY = bottomLayer.getFloorYAt(player.getX());
        if (player.getY() <= floorY + player.getCollisionRadius()) {
            System.out.println("Hit the seafloor!");
            player.takeDamage(player.getHealth());
        }
    }

    /** Called by the death screen button once the player confirms respawn. */
    public static void triggerRespawn() {
        player.respawn(SPAWN_X, SPAWN_Y);
        engine.setCamera(SPAWN_X - (float) CX, SPAWN_Y - (float) CY);
    }

    private static void lockCamera() {
        engine.setCamera(player.getX() - (float) CX, player.getY() - (float) CY);
    }

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

            // Snapshot current remote sub positions as radar contacts
            updateRadarContacts();
            // Populate contact list — only refresh if no torpedo in flight
            if (!torpedoSystem.hasTorpedo()) {
                contactIds.clear();
                contactPos.clear();
                contactIds.addAll(radarContacts.keySet());
                contactPos.putAll(radarContacts);
                selectedIdx = -1;   // nothing selected until player presses a number key
            }

            if (multiplayer && netClient != null) {
                netClient.sendRadarPing(player.getX(), player.getY());
                netClient.sendSoundEvent(player.getX(), player.getY(),
                                         PING_SOUND_STRENGTH, "radar");
            }
            System.out.println("Radar ping!");
        }
        rWasDown = rDown;

        // ── Target selection (number keys) ────────────────────────────────────
        for (int i = 0; i < contactIds.size() && i < 9; i++) {
            if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_1 + i))
                selectedIdx = i;
        }

        // ── Mouse click — respawn (if dead) OR launch/detonate torpedo ──────────
        boolean mouseDown = StdDraw.isMousePressed();
        if (mouseDown && !mouseWasDown) {
            if (!player.isAlive()) {
                // Death screen is showing — check if respawn is ready and snap camera
                player.handleRespawnClick();
                if (player.isAlive()) {
                    engine.setCamera(player.getX() - (float) CX, player.getY() - (float) CY);
                }
            } else if (!torpedoSystem.hasTorpedo()) {
                torpedoSystem.launchTorpedo(player.getX(), player.getY(), player.getAngle());
            } else {
                Map<String, Submarine> remotes = (multiplayer && netClient != null)
                        ? netClient.getRemoteSubs()
                        : new java.util.HashMap<>();
                torpedoSystem.detonateManual(remotes, player);
            }
        }
        mouseWasDown = mouseDown;

        // Clear contact list once torpedo is gone.
        // Do NOT null the torpedo here — the network block later in the loop
        // still needs getTorpedo() to send the detonation packet.
        if (!torpedoSystem.hasTorpedo() && torpedoSystem.getTorpedo() != null
                && torpedoSystem.getTorpedo().hasExploded()) {
            contactIds.clear();
            contactPos.clear();
            selectedIdx = -1;
        }

        // Also clear contacts once the radar fades and no torpedo is in flight
        if (pingAlpha() == 0f && !torpedoSystem.hasTorpedo() && !contactIds.isEmpty()) {
            contactIds.clear();
            contactPos.clear();
            selectedIdx = -1;
        }
    }

    /**
     * Snapshot all known remote sub positions as radar contacts.
     * Called when the player fires a radar ping.
     */
    private static void updateRadarContacts() {
        radarContacts.clear();
        if (netClient == null) return;
        for (Map.Entry<String, Submarine> e : netClient.getRemoteSubs().entrySet()) {
            Submarine sub = e.getValue();
            radarContacts.put(e.getKey(),
            new float[]{sub.getX(), sub.getY()});
        }
    }

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

        if (multiplayer && netClient != null) {
            Map<String, Submarine> remotes = netClient.getRemoteSubs();
            for (Submarine remote : remotes.values()) {
                remote.draw(engine);
            }
            // Draw remote torpedoes
            for (Packets.TorpedoState t : netClient.getRemoteTorpedoStates().values()) {
                // Render a visual-only torpedo shell at the reported position
                new Torpedo(t.playerId, t.x, t.y, t.angle).draw(engine);
            }
        }

        // Fog of war
        HUD.drawFog(HEIGHT, WIDTH, CX, CY);

        // Radar outlines (rocks)
        float pingAlpha = pingAlpha();
        if (pingAlpha > 0f) drawRadarOutlines(pingAlpha);

        // Local player submarine
        player.drawCentred(CX, CY);

        // Torpedo
        torpedoSystem.draw(engine);

        // HUD
        HUD.drawHUD(WIDTH, HEIGHT, CX, CY, player);

        // Passive sonar (ray-traced)

        float perceived = Sound.totalPerceivedAt(sounds,player.getX(), player.getY());
        PassiveSonar.draw(perceived, HEIGHT, tick);

        // Radar screen — pass torpedo world pos if active
        List<Rock> foregroundRocks = new ArrayList<>();
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                foregroundRocks.add((Rock) s);

        float[] torpedoPos = torpedoSystem.hasTorpedo()
                ? new float[]{torpedoSystem.getTorpedo().getX(), torpedoSystem.getTorpedo().getY()}
                : null;
        RadarScreen.draw(WIDTH, 220, player.getX(), player.getY(),
                pingAlpha, radarContacts, foregroundRocks, torpedoPos);

        // ── Contact list UI ────────────────────────────────────────────────────
        drawContactUI();

        // ── Death screen overlay (drawn last so it's on top of everything) ─────
        if (!player.isAlive()) {
            player.drawDeathScreen(WIDTH, HEIGHT);
        }
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

    private static void drawContactUI() {
        if (contactIds.isEmpty()) return;

        int rightX = WIDTH - 10;
        int startY = 340;
        int lineH  = 14;

        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        StdDraw.setPenColor(new java.awt.Color(0, 180, 80));
        StdDraw.textRight(rightX, startY + lineH, "CONTACTS");

        for (int i = 0; i < contactIds.size() && i < 9; i++) {
            String label = String.format("[%d] %s", i + 1, contactIds.get(i));
            StdDraw.setPenColor(i == selectedIdx
                    ? new java.awt.Color(255, 220, 80)
                    : new java.awt.Color(0, 160, 70));
            StdDraw.textRight(rightX, startY - i * lineH, label);
        }

        // Instruction
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 10));
        StdDraw.setPenColor(new java.awt.Color(0, 120, 50));
        int below = startY - contactIds.size() * lineH - 4;
        StdDraw.textRight(rightX, below,
                torpedoSystem.hasTorpedo() ? "Click to detonate" : "Click to launch torpedo");

        // Distance readout — shown as long as torpedo is alive and target is selected
        if (torpedoSystem.hasTorpedo() && selectedIdx >= 0 && selectedIdx < contactIds.size()) {
            float[] pos = contactPos.get(contactIds.get(selectedIdx));
            if (pos != null) {
                Torpedo t = torpedoSystem.getTorpedo();
                float dx   = pos[0] - t.getX();
                float dy   = pos[1] - t.getY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                // Hot/cold colour
                float ratio = Math.min(1f, dist / 2000f);
                int r = (int)(255 * ratio);
                int g = (int)(255 * (1 - ratio));
                StdDraw.setPenColor(new java.awt.Color(r, g, 0));
                StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 13));
                StdDraw.textRight(rightX, below - 16, String.format("DIST: %.0f m", dist));
            }
        }
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
        System.out.println();
        System.out.println("  Ray-trace sonar: " + USE_RAYTRACE_SONAR);
    }
}