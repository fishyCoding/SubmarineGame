import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NetworkClient — connects to NetworkServer and syncs game state.
 *
 * Thread safety: Kryonet callbacks run on a background thread.
 * All shared state (remoteSubs, pendingSounds, pendingPings) uses
 * ConcurrentHashMap / synchronized lists so the game loop can read
 * them safely from the main thread.
 *
 * Usage in Game.java:
 *
 *   // Setup
 *   NetworkClient net = new NetworkClient("localhost", "MyName");
 *   net.connect();
 *
 *   // Each tick (every 6 ticks for position)
 *   if (tick % 6 == 0) net.sendState(player);
 *
 *   // Send sound events when they happen
 *   net.sendSoundEvent(wx, wy, strength, "radar");
 *   net.sendRadarPing(player.getX(), player.getY());
 *
 *   // Each frame — drain pending events into game
 *   net.drainSounds(sounds);   // adds remote sounds to your local list
 *   net.drainPings(pingList);  // adds remote pings to your ping list
 *
 *   // Render remote submarines
 *   for (Submarine s : net.getRemoteSubs().values()) s.draw(engine);
 */
public class NetworkClient {

    private final Client client;

    public final String host;

    private final String requestedName;

    // Assigned by server on join
    private volatile String myId = null;

    // Remote submarines keyed by player ID — read by main thread, written by network thread
    private final Map<String, Submarine> remoteSubs = new ConcurrentHashMap<>();

    // Pending sound/ping events — drained into the game's sound list each frame
    private final List<Packets.SoundEvent> pendingSounds =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final List<Packets.RadarPing> pendingPings =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private volatile boolean connected = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param host          IP or hostname of the server (e.g. "localhost" or "192.168.1.5")
     * @param requestedName display name to request (server may assign a different one)
     */
    public NetworkClient(String host, String requestedName) {
        this.host          = host;
        this.requestedName = requestedName;
        client = new Client(65536, 65536);
        NetworkServer.registerClasses(client.getKryo());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Connect to the server and send a JoinRequest.
     * Blocks until connected or throws IOException on failure.
     */
    public void connect() throws IOException {
        client.start();
        client.addListener(new ClientListener());
        // 5 second timeout for the TCP connection
        client.connect(5000, host, NetworkServer.TCP_PORT, NetworkServer.UDP_PORT);

        // Send join request
        Packets.JoinRequest req = new Packets.JoinRequest();
        req.playerId = requestedName;
        client.sendTCP(req);

        connected = true;
        System.out.println("Connected to server at " + host);
    }

    public void disconnect() {
        client.stop();
        connected = false;
    }

    public boolean isConnected() { return connected; }
    public String  getMyId()     { return myId; }

    // ── Sending ───────────────────────────────────────────────────────────────

    /**
     * Send local player's submarine state via UDP.
     * Call every 6 ticks — cheap, unreliable, that's fine for position.
     */
    public void sendState(Submarine player) {
        if (!connected || myId == null) return;

        Packets.SubmarineState state = new Packets.SubmarineState();
        state.playerId   = myId;
        state.x          = player.getX();
        state.y          = player.getY();
        state.vx         = player.getVx();
        state.vy         = player.getVy();
        state.angle      = player.getAngle();
        state.rudderAngle= player.getRudderAngle();
        state.health     = player.getHealth();
        client.sendUDP(state);
    }

    /**
     * Notify other players of a sound event via TCP (reliable).
     * @param type "radar", "engine", "test", etc.
     */
    public void sendSoundEvent(float x, float y, float strength, String type) {
        if (!connected || myId == null) return;

        Packets.SoundEvent ev = new Packets.SoundEvent();
        ev.playerId = myId;
        ev.x        = x;
        ev.y        = y;
        ev.strength = strength;
        ev.type     = type;
        client.sendTCP(ev);
    }

    /**
     * Notify other players of a radar ping via TCP.
     * Other clients will start their own visual ping animation.
     */
    public void sendRadarPing(float x, float y) {
        if (!connected || myId == null) return;

        Packets.RadarPing ping = new Packets.RadarPing();
        ping.playerId = myId;
        ping.x        = x;
        ping.y        = y;
        client.sendTCP(ping);
    }

    // ── Draining pending events into the game ─────────────────────────────────

    /**
     * Call once per frame from the game loop.
     * Converts pending SoundEvents into real Sound objects and adds them to
     * the game's sound list so the passive sonar reacts to remote players.
     */
    public void drainSounds(List<Sound> sounds) {
        synchronized (pendingSounds) {
            for (Packets.SoundEvent ev : pendingSounds) {
                Sound s = buildSound(ev);
                if (s != null) sounds.add(s);
            }
            pendingSounds.clear();
        }
    }

    /**
     * Call once per frame. Returns all pending radar pings from other players
     * and clears the queue. The game loop should start ping animations for these.
     */
    public List<Packets.RadarPing> drainPings() {
        synchronized (pendingPings) {
            List<Packets.RadarPing> copy = new java.util.ArrayList<>(pendingPings);
            pendingPings.clear();
            return copy;
        }
    }

    /** Build the right Sound subclass from a SoundEvent packet. */
    private Sound buildSound(Packets.SoundEvent ev) {
        switch (ev.type) {
            case "radar":
                return new RadarSound(ev.x, ev.y, ev.strength, ev.playerId);
            default:
                // Generic one-shot sound for test/unknown types
                return new Sound(ev.x, ev.y, ev.strength, ev.playerId) {};
        }
    }

    // ── Remote submarine access ───────────────────────────────────────────────

    /** Get a live view of all remote submarines (read-only from game loop). */
    public Map<String, Submarine> getRemoteSubs() {
        return remoteSubs;
    }

    // ── Listener (runs on Kryonet background thread) ──────────────────────────

    private class ClientListener extends Listener {

        @Override
        public void disconnected(Connection conn) {
            connected = false;
            System.out.println("Disconnected from server.");
        }

        @Override
        public void received(Connection conn, Object object) {

            // ── Server confirmed our join ─────────────────────────────────────
            if (object instanceof Packets.JoinResponse) {
                Packets.JoinResponse resp = (Packets.JoinResponse) object;
                myId = resp.assignedId;
                System.out.println("Joined as: " + myId
                        + " spawn=(" + resp.spawnX + "," + resp.spawnY + ")");
                return;
            }

            // ── Another player left ───────────────────────────────────────────
            if (object instanceof Packets.PlayerLeft) {
                Packets.PlayerLeft left = (Packets.PlayerLeft) object;
                remoteSubs.remove(left.playerId);
                System.out.println("Player left: " + left.playerId);
                return;
            }

            // ── Remote submarine position update ──────────────────────────────
            if (object instanceof Packets.SubmarineState) {
                Packets.SubmarineState state = (Packets.SubmarineState) object;
                if (state.playerId == null) return;

                // Get or create a Submarine shell for this remote player.
                // Remote subs never call handleInput() or update() physics —
                // we just slam their position/angle from the packet.
                Submarine sub = remoteSubs.computeIfAbsent(
                        state.playerId,
                        id -> new Submarine(id, state.x, state.y, state.health, null));

                // Overwrite position/velocity directly — no physics simulation
                sub.setPosition(state.x, state.y);
                sub.setVelocity(state.vx, state.vy);
                sub.setAngle(state.angle);
                return;
            }

            // ── Remote sound event ────────────────────────────────────────────
            if (object instanceof Packets.SoundEvent) {
                pendingSounds.add((Packets.SoundEvent) object);
                return;
            }

            // ── Remote radar ping ─────────────────────────────────────────────
            if (object instanceof Packets.RadarPing) {
                pendingPings.add((Packets.RadarPing) object);
            }
        }
    }
}