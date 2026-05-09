import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Game {

    // Canvas
    private static final int WIDTH = 1300;
    private static final int HEIGHT = 800;
    private static final double CX = WIDTH /2.0;
    private static final double CY = HEIGHT /2.0;

    // World setup
    private static final float SURFACE_LEVEL = 0f;
    private static final float SEAFLOOR_TOP  = -1820f;
    private static final float SEAFLOOR_BASE = -2400f;
    //rock data
    private static final String DATA_FILE = "sprites.txt";
    //seafloor data
    private static final String SEAFLOOR_FILE = "seafloor.txt";


    // Player Spawn 

    private static final int    PLAYER_MAX_HP = 100;

    //Radar Ping
    private static final long PING_DURATION_MS = 2500;
    private static long pingStartMs      = -1;
    private static final float PING_SOUND_STRENGTH = 10000f;

    //players the radar has detected
    private static final java.util.Map<String, float[]> radarContacts =new java.util.concurrent.ConcurrentHashMap<>();



    // System classes
    private static GameEngine engine;
    private static BottomRockLayer bottomLayer;
    private static Water water;
    private static Submarine player;

    // Sound 
    private static final List<Sound> sounds = new ArrayList<>();
    private static EngineSound engineSound; //gets attached to sub

    // Torpedo mechs
    private static TorpedoSystem torpedoSystem;
    private static final List<String> contactIds = new ArrayList<>();
    private static final Map<String, float[]> contactPos = new java.util.LinkedHashMap<>();
    private static int selectedIdx  = -1; //none are selected at -1


    // Network
    private static NetworkClient netClient  = null;
    private static NetworkServer netServer  = null;
    private static boolean multiplayer = false;

    //tick counter
    private static long tick = 0;
    private static boolean mouseWasDown = false;
    private static boolean rWasDown = false;


    public static void main(String[] args) {
        parseArgs(args);
        spawnPlayer();
        setupWorld();
        setupNetwork(args);
        setupWindow();
        setupSounds();
        torpedoSystem = new TorpedoSystem();
        gameLoop();
    }

    // Gets arguments

    private static void parseArgs(String[] args) {
        for (String a : args) {
            if (a.equals("--host") || a.equals("--join")) {
                multiplayer = true;
            }
        }
    }

    //set up netowrk. 
    private static void setupNetwork(String[] args) {
        
        //if its solo, skip all the network setup
        if (!multiplayer) {
            return;
        }

        //if the length is 0, then just use solo
        String mode = args.length > 0 ? args[0] : "--solo";

        try {
            if (mode.equals("--host")) {
                netServer = new NetworkServer();
                netServer.start();
                System.out.println("Hosting — server started.");

                //delay a bit
                Thread.sleep(300);

                //connects to new server with itself as the local host
                netClient = new NetworkClient("localhost", "Host");
                netClient.connect();
            } else if (mode.equals("--join") && args.length > 1) {
                //if joining
                //get ip
                String ip = args[1];
                netClient = new NetworkClient(ip, "Player");
                netClient.connect();
                System.out.println("Joined server at " + ip);
            } else {
                System.out.println("Unknown mode — running solo.");
                multiplayer = false;
            }
        } catch (Exception e) {
            //couldn't connect: run solo
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

        //print ip or localhost if multiplayer, otherwise solo
        String title = multiplayer ? netClient.host : "Solo";
        StdDraw.setTitle(title);
    }


    private static void setupWorld() {
        //engine handles sprites collision and rendering 
        engine = new GameEngine(DATA_FILE);
        //bottom rock layer
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, SEAFLOOR_TOP, SEAFLOOR_BASE, SEAFLOOR_FILE);
        //bckground water render
        water = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
    }

    private static void spawnPlayer() {
        //Spawner.getSpawnX/Y is its own function. the class stores the mechs for finding the random spawn
        player = new Submarine("Player", Spawner.getSpawnX(), Spawner.getSpawnY(), PLAYER_MAX_HP);
        System.out.println("Spawned: " + player);
    }

    private static void setupSounds() {
        //attach engine sound to player
        engineSound = new EngineSound(player);
        sounds.add(engineSound);
        //bckground noise
        sounds.add(new BackgroundSound(player));
    }

    private static void gameLoop() {
        while (true) {
            
            handleInput();
            updateSounds();
            drainNetwork();
            player.update();
            checkCollisions();
            lockCamera();
            render();


            StdDraw.show();
            StdDraw.pause(16);
            tick++;
        }
    }

    private static void drainNetwork(){

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
                        int currentDamage=Torpedo.getDamage((float) Math.sqrt(dx * dx + dy * dy));
                        System.out.println(currentDamage);
                        player.takeDamage(currentDamage);
                        System.out.println("Hit by remote torpedo from " + (float) Math.sqrt(dx * dx + dy * dy) + "!");
                    }
                    // Remove the visual torpedo for that player
                }

                netClient.drainSounds(sounds);

                for (Packets.RadarPing ping : netClient.drainPings()) {
                    sounds.add(new RadarSound(ping.x, ping.y, PING_SOUND_STRENGTH, ping.playerId));
                    System.out.println("Remote ping from " + ping.playerId);
                }
            }
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
        player.respawn(Spawner.getSpawnX(), Spawner.getSpawnY());
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
        if (rDown && !rWasDown && pingAlpha()==0) {
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

        // Bckground rocks
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);

        // Frground rocks
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);

        bottomLayer.draw(engine);

        if (multiplayer && netClient != null) {
            //Draw remote subs
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

        // Fog of war outlien around most of the screen
        HUD.drawFog(HEIGHT, WIDTH, CX, CY);

        // Radar outlines (rocks)
        if (pingAlpha() > 0f) {
            drawRadarOutlines(pingAlpha());
        }

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

        // snapshot remote torpedo positions for radar — only populated during an active ping
        List<float[]> remoteTorpedoPositions = new ArrayList<>();
        if (pingAlpha() > 0f && multiplayer && netClient != null) {
            for (Packets.TorpedoState t : netClient.getRemoteTorpedoStates().values())
                remoteTorpedoPositions.add(new float[]{t.x, t.y});
        }

        // pass selected contact so radar knows to draw the blast radius ring
        float[] selectedContact = (selectedIdx >= 0 && selectedIdx < contactIds.size())
                ? contactPos.get(contactIds.get(selectedIdx))
                : null;

        RadarScreen.draw(WIDTH, 220, player.getX(), player.getY(),
                pingAlpha(), radarContacts, foregroundRocks, torpedoPos, bottomLayer, remoteTorpedoPositions, selectedContact);

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

        // Distance readout — always uses live remote sub position, not the stale ping snapshot.
        if (torpedoSystem.hasTorpedo() && selectedIdx >= 0 && selectedIdx < contactIds.size()) {
            String targetId = contactIds.get(selectedIdx);
            float liveX, liveY;
            Submarine liveSub = (netClient != null) ? netClient.getRemoteSubs().get(targetId) : null;
            if (liveSub != null) {
                liveX = liveSub.getX();
                liveY = liveSub.getY();
            } else {
                float[] snapshot = contactPos.get(targetId);
                if (snapshot != null) { liveX = snapshot[0]; liveY = snapshot[1]; }
                else { liveX = liveY = 0; }
            }
            if (liveSub != null || contactPos.containsKey(targetId)) {
                Torpedo t = torpedoSystem.getTorpedo();
                float dx   = liveX - t.getX();
                float dy   = liveY - t.getY();
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

}