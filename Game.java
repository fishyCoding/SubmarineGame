import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Game {

    // Canvas
    private static final int WIDTH = 12;
    private static final int HEIGHT = 800;
    private static final double CX = WIDTH / 2.0;
    private static final double CY = HEIGHT / 2.0;

    // World setup
    private static final float SURFACE_LEVEL = 0f;
    private static final float SEAFLOOR_TOP = -1820f;
    private static final float SEAFLOOR_BASE = -2400f;
    private static final String DATA_FILE = "sprites.txt";
    private static final String SEAFLOOR_FILE = "seafloor.txt";

    // Player
    private static final int PLAYER_MAX_HP = 100;

    // Radar ping
    private static final long PING_DURATION_MS = 2500;
    private static long pingStartMs = -1;
    private static final float PING_SOUND_STRENGTH = 10000f;
    private static RadarScreen rscreen = new RadarScreen();

    // contacts the radar has detected
    private static final java.util.Map<String, float[]> radarContacts = new java.util.concurrent.ConcurrentHashMap<>();
    private static float contactDist = 0f;

    // System classes
    private static GameEngine engine;
    private static BottomRockLayer bottomLayer;
    private static Water water;
    private static Submarine player;

    // Sound
    private static final List<Sound> sounds = new ArrayList<>();
    private static EngineSound engineSound;

    // Torpedo
    private static TorpedoSystem torpedoSystem;
    private static final List<String> contactIds = new ArrayList<>();
    private static final Map<String, float[]> contactPos = new java.util.LinkedHashMap<>();
    private static int selectedIdx = -1;

    // Network
    private static NetworkClient netClient = null;
    private static NetworkServer netServer = null;
    private static boolean multiplayer = false;
    private static String gameMode = "--solo"; // set in parseArgs, used in setupWindow

    // Tick / input state
    private static long tick = 0;
    private static boolean mouseWasDown = false;
    private static boolean rWasDown = false;
    private static boolean plusWasDown = false;
    private static boolean minusWasDown = false;


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

    private static void parseArgs(String[] args) {
        for(String a : args) {
            if(a.equals("--host")) {
                multiplayer = true;
                gameMode = "--host";
            } else if(a.equals("--join")) {
                multiplayer = true;
                gameMode = "--join";
            }
        }
    }


    private static void setupNetwork(String[] args) {
        if(!multiplayer) return;

        String mode = args.length > 0 ? args[0] : "--solo";

        try {
            if(mode.equals("--host")) {
                netServer = new NetworkServer();
                netServer.start();
                System.out.println("Hosting — server started.");
                // Wait longer to ensure clients can connect
                Thread.sleep(1000);
                netClient = new NetworkClient("localhost", "Host");
                netClient.connect();
            } else if(mode.equals("--join") && args.length > 1) {
                String ip = args[1];
                netClient = new NetworkClient(ip, "Player");
                netClient.connect();
                System.out.println("Joined server at " + ip);
            } else {
                System.out.println("Unknown mode — running solo.");
                multiplayer = false;
            }
        } catch(Exception e) {
            System.err.println("Fail " + e.getMessage());
            System.err.println("Running w/o network");
            multiplayer = false;
            netClient = null;
            netServer = null;
        }
    }

    private static void setupWindow() {
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();

        // show IP on title so u can share it with friends
        String title;
        if(!multiplayer) {
            title = "Solo";
        } else if(gameMode.equals("--host")) {
            try {
                String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                title = "Hosting — " + localIp;
            } catch(Exception e) {
                title = "Hosting";
            }
        } else {
            title = "Connected — " + netClient.host;
        }
        StdDraw.setTitle(title);
    }

    private static void setupWorld() {
        engine = new GameEngine(DATA_FILE);
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, SEAFLOOR_TOP, SEAFLOOR_BASE, SEAFLOOR_FILE);
        water = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
    }

    private static void spawnPlayer() {
        player = new Submarine("Player", Spawner.getSpawnX(), Spawner.getSpawnY(), PLAYER_MAX_HP);
        System.out.println("Spawned: " + player);
    }

    private static void setupSounds() {
        engineSound = new EngineSound(player);
        sounds.add(engineSound);
    }

    private static void gameLoop() {
        while(true) {
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

    private static void drainNetwork() {
        if(multiplayer && netClient != null && netClient.isConnected()) {
            if(tick % 12 == 0) netClient.sendState(player);

            if(tick % 12 == 0) {
                netClient.sendSoundEvent(player.getX(), player.getY(),
                        engineSound.getStrength(), "engine");
            }

            // torpedo replication
            if(torpedoSystem.hasTorpedo()) {
                Torpedo t = torpedoSystem.getTorpedo();
                netClient.sendTorpedoState(t.getX(), t.getY(), t.getAngle(), true);
            } else if(torpedoSystem.getTorpedo() != null && torpedoSystem.getTorpedo().hasExploded()) {
                    Torpedo t = torpedoSystem.getTorpedo();
                    netClient.sendTorpedoState(t.getX(), t.getY(), t.getAngle(), false);
                    netClient.sendTorpedoDetonate(t.getX(), t.getY(), t.getBlastRadius(), t.getDamage());

                    float _dx = t.getX() - player.getX();
                    float _dy = t.getY() - player.getY();
                    float _distSq = _dx * _dx + _dy * _dy;
                    String torpSoundOwner = (_distSq < 400f * 400f) ? "player_ping" : "remote_ping";
                    sounds.add(new TorpedoSound(t.getX(), t.getY(), torpSoundOwner));
                    
                    if(multiplayer && netClient != null) {
                        netClient.sendSoundEvent(t.getX(), t.getY(), 25000f, "torpedo_explosion");
                    }

                    torpedoSystem.resetTorpedo();
                }

            // drain remote detonations — apply damage to local player
            for(Packets.TorpedoDetonate d : netClient.drainDetonations()) {
                float dx = player.getX() - d.x;
                float dy = player.getY() - d.y;
                if(dx * dx + dy * dy <= d.blastRadius * d.blastRadius) {
                    int currentDamage = Torpedo.getDamage((float) Math.sqrt(dx * dx + dy * dy));
                    player.takeDamage(currentDamage);
                }
                sounds.add(new TorpedoSound(d.x, d.y, "remote_torpedo"));
            }

            // drainSounds handles all types: "radar", "torpedo_explosion", etc.
            // The dispatch happens inside NetworkClient.buildSound().
            netClient.drainSounds(sounds);

            for(Packets.RadarPing ping : netClient.drainPings()) {
                sounds.add(new RadarSound(ping.x, ping.y, PING_SOUND_STRENGTH, ping.playerId));
            }
        }

        if(torpedoSystem.hasTorpedo()) {
            List<Rock> fgRocks = new ArrayList<>();
            for(Sprite s : engine.getSprites())
                if(s instanceof Rock && ((Rock) s).getDepth() == 1)
                    fgRocks.add((Rock) s);

            Map<String, Submarine> remotes = (multiplayer && netClient != null)
                    ? netClient.getRemoteSubs()
                    : new java.util.HashMap<>();

            torpedoSystem.update(StdDraw.mouseX(), StdDraw.mouseY(), CX, CY,
                    fgRocks, bottomLayer, remotes, player);
        }
    }

    private static void checkCollisions() {
        if(!player.isAlive()) return;

        for(Sprite s : engine.getSprites()) {
            if(!(s instanceof Rock)) continue;
            Rock rock = (Rock) s;
            if(rock.getDepth() != 1) continue;
            if(player.collidesWithRock(rock)) {
                System.out.println("Hit a rock!");
                player.takeDamage(player.getHealth());
                return;
            }
        }

        float floorY = bottomLayer.getFloorYAt(player.getX());
        if(player.getY() <= floorY + player.getCollisionRadius()) {
            System.out.println("Hit the seafloor!");
            player.takeDamage(player.getHealth());
        }
    }

    public static void triggerRespawn() {
        player.respawn(Spawner.getSpawnX(), Spawner.getSpawnY());
    }

    private static void lockCamera() {
        engine.setCamera(player.getX() - (float) CX, player.getY() - (float) CY);
    }

    private static void handleInput() {
        if(StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            if(netClient != null) netClient.disconnect();
            if(netServer != null) netServer.stop();
            System.out.println("Goodbye.");
            System.exit(0);
        }

        player.handleInput();

        boolean plusDown = StdDraw.isKeyPressed('=') || StdDraw.isKeyPressed('+');
        if(plusDown && !plusWasDown){
            rscreen.plus();
        }
        plusWasDown = plusDown;
        
        boolean minusDown = StdDraw.isKeyPressed('-') || StdDraw.isKeyPressed('_');
        if(minusDown && !minusWasDown){
            rscreen.minus();
        }
        minusWasDown = minusDown;


        boolean rDown = StdDraw.isKeyPressed('R') || StdDraw.isKeyPressed('r');
        if(rDown && !rWasDown && pingAlpha() == 0) {
            pingStartMs = System.currentTimeMillis();
            sounds.add(new RadarSound(player.getX(), player.getY(),
                    PING_SOUND_STRENGTH, "player_ping"));

            updateRadarContacts();

            if(!torpedoSystem.hasTorpedo()) {
                contactIds.clear();
                contactPos.clear();

                //only depth 1 rocks
                List<Rock> fgRocks = new ArrayList<>();
                for(Sprite s : engine.getSprites()) {
                    if(s instanceof Rock && ((Rock) s).getDepth() == 1) {
                        fgRocks.add((Rock) s);
                    }
                }

                for(Map.Entry<String, float[]> entry : radarContacts.entrySet()) {
                    String id = entry.getKey();
                    float[] pos = entry.getValue();
                    
                    if(RadarScreen.hasLineOfSight(player.getX(), player.getY(), pos[0], pos[1], fgRocks)) {
                        contactIds.add(id);
                        contactPos.put(id, pos);
                    }
                }
                selectedIdx = -1;
            }

            if(multiplayer && netClient != null) {
                netClient.sendRadarPing(player.getX(), player.getY());
                netClient.sendSoundEvent(player.getX(), player.getY(),
                        PING_SOUND_STRENGTH, "radar");
            }
        }
        rWasDown = rDown;

        for(int i=0; i<contactIds.size() && i<9; i++) {
            if(StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_1 + i))
                selectedIdx = i;
        }

        boolean mouseDown = StdDraw.isMousePressed();
        if(mouseDown && !mouseWasDown) {
            if(!player.isAlive()) {
                player.handleRespawnClick();
                if(player.isAlive())
                    engine.setCamera(player.getX() - (float) CX, player.getY() - (float) CY);
            } else if(!torpedoSystem.hasTorpedo()) {
                torpedoSystem.launchTorpedo(player.getX(), player.getY(), player.getAngle());
            } else {
                Map<String, Submarine> remotes = (multiplayer && netClient != null)
                        ? netClient.getRemoteSubs()
                        : new java.util.HashMap<>();
                torpedoSystem.detonateManual(remotes, player);
            }
        }
        mouseWasDown = mouseDown;

        if(!torpedoSystem.hasTorpedo() && torpedoSystem.getTorpedo() != null
                && torpedoSystem.getTorpedo().hasExploded()) {
            contactIds.clear();
            contactPos.clear();
            selectedIdx = -1;
        }

        // clear contacts once radar fades and no torpedo in flight
        if(pingAlpha() == 0f && !torpedoSystem.hasTorpedo() && !contactIds.isEmpty()) {
            contactIds.clear();
            contactPos.clear();
            selectedIdx = -1;
        }
    }

    private static void updateRadarContacts() {
        radarContacts.clear();
        if(netClient == null) return;
        for(Map.Entry<String, Submarine> e : netClient.getRemoteSubs().entrySet()) {
            Submarine sub = e.getValue();
            radarContacts.put(e.getKey(), new float[]{sub.getX(), sub.getY()});
        }
    }

    private static void updateSounds() {
        for(Sound s : sounds) s.tick();
        Sound.pruneDead(sounds);
        if(!sounds.contains(engineSound)) sounds.add(engineSound);
    }

    // Render (once every tick)

    private static void render() {
        water.drawWaterGradient();

        //rock draw
        for(Sprite s : engine.getSprites())
            if(s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);

        for(Sprite s : engine.getSprites())
            if(s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);

        // seafloor
        bottomLayer.draw(engine);

        //enemy subs and torpedos
        if(multiplayer && netClient != null) {
            for(Submarine remote : netClient.getRemoteSubs().values())
                remote.draw(engine);
            for(Packets.TorpedoState t : netClient.getRemoteTorpedoStates().values())
                new Torpedo(t.playerId, t.x, t.y, t.angle).draw(engine);
        }
        torpedoSystem.draw(engine);


        //fog of war outline
        HUD.drawFog(HEIGHT, WIDTH, CX, CY);

        //sonar ping outlines go above the fog 
        if(pingAlpha() > 0f)
            drawRadarOutlines(pingAlpha());

        //player and HUD
        player.drawCentred(CX, CY);
        HUD.drawHUD(WIDTH, HEIGHT, CX, CY, player);

        //tick number is passed b/c it gets used in noise calculations
        PassiveSonar.draw(player.getX(), player.getY(), sounds, tick);


        List<Rock> foregroundRocks = new ArrayList<>();
        for(Sprite s : engine.getSprites())
            if(s instanceof Rock && ((Rock) s).getDepth() == 1)
                foregroundRocks.add((Rock) s);

        float[] torpedoPos = torpedoSystem.hasTorpedo()
                ? new float[]{torpedoSystem.getTorpedo().getX(), torpedoSystem.getTorpedo().getY()}
                : null;

        List<float[]> remoteTorpedoPositions = new ArrayList<>();
        if(pingAlpha() > 0f && multiplayer && netClient != null) {
            for(Packets.TorpedoState t : netClient.getRemoteTorpedoStates().values())
                remoteTorpedoPositions.add(new float[]{t.x, t.y});
        }

        float[] selectedContact = (selectedIdx >= 0 && selectedIdx < contactIds.size())
                ? contactPos.get(contactIds.get(selectedIdx))
                : null;

        drawContactUI();
        List<Submarine> contactList = new ArrayList<>(netClient.getRemoteSubs().values());

        List<float[]> remoteTorpedoList = new ArrayList<>();
        for(Packets.TorpedoState ts : netClient.getRemoteTorpedoStates().values()) {
            remoteTorpedoList.add(new float[]{ts.x, ts.y});
        }
        rscreen.draw(
            WIDTH, HEIGHT, 
            player.getX(), player.getY(), 
            pingAlpha(), 
            contactList,
            foregroundRocks, 
            torpedoPos, 
            bottomLayer, 
            remoteTorpedoPositions,
            selectedContact, 
            contactDist
        );

        if(!player.isAlive())
            player.drawDeathScreen(WIDTH, HEIGHT);
    }

    // ── Radar ─────────────────────────────────────────────────────────────────────

    private static float pingAlpha() {
        if(pingStartMs < 0) return 0f;
        long elapsed = System.currentTimeMillis() - pingStartMs;
        if(elapsed >= PING_DURATION_MS) return 0f;
        return 1f - (float) elapsed / PING_DURATION_MS;
    }

    private static void drawRadarOutlines(float alpha) {
        Radar.drawRadarOutlines(alpha, engine);
        bottomLayer.drawRadarOutline(engine, alpha);
        StdDraw.setPenRadius(0.002);
    }
    private static void drawContactUI() {
        if(contactIds.isEmpty()) return;

        int rightX = WIDTH - 10;
        int startY = 410;
        int lineH = 14;

        List<Rock> currentRocks = new ArrayList<>();
        for(Sprite s : engine.getSprites()) {
            if(s instanceof Rock) {
                currentRocks.add((Rock) s);
            }
        }

        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        StdDraw.setPenColor(new java.awt.Color(0, 180, 80));
        StdDraw.textRight(rightX, startY + lineH, "CONTACTS");

        for(int i=0; i<contactIds.size() && i<9; i++) {
            String id = contactIds.get(i);
            
 

            if(i == selectedIdx) {
                StdDraw.setPenColor(new java.awt.Color(255, 220, 80));
            } else {
                StdDraw.setPenColor(new java.awt.Color(0, 160, 70));
            }

            String label = String.format("[%d] %s", i + 1, id);
            StdDraw.textRight(rightX, startY - i * lineH, label);
        }

        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 10));
        StdDraw.setPenColor(new java.awt.Color(0, 120, 50));
        int below = startY - contactIds.size() * lineH - 4;
        StdDraw.textRight(rightX, below,
                torpedoSystem.hasTorpedo() ? "Click to detonate" : "Click to launch torpedo");

        if(torpedoSystem.hasTorpedo() && selectedIdx >= 0 && selectedIdx < contactIds.size()) {
            String targetId = contactIds.get(selectedIdx);
            float liveX, liveY;
            Submarine liveSub = (netClient != null) ? netClient.getRemoteSubs().get(targetId) : null;
            if(liveSub != null) {
                liveX = liveSub.getX();
                liveY = liveSub.getY();
            } else {
                float[] snapshot = contactPos.get(targetId);
                if(snapshot != null) { liveX = snapshot[0]; liveY = snapshot[1]; }
                else { liveX = liveY = 0; }
            }
            if(liveSub != null || contactPos.containsKey(targetId)) {
                Torpedo t = torpedoSystem.getTorpedo();
                float dx = liveX - t.getX();
                float dy = liveY - t.getY();
                contactDist = (float) Math.sqrt(dx * dx + dy * dy);

                float ratio = Math.min(1f, contactDist / 2000f);
                int r = (int)(255 * ratio);
                int g = (int)(255 * (1 - ratio));
                StdDraw.setPenColor(new java.awt.Color(r, g, 0));
                StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 13));
                StdDraw.textRight(rightX, below - 16, String.format("DIST: %.0f m", contactDist));
            }
        }
    }
}