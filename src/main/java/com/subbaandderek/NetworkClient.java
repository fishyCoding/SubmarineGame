package com.subbaandderek;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connects to NetworkServer and syncs game state.
 * Kryonet callbacks run on a background thread — shared state uses
 * ConcurrentHashMap / synchronized lists so the game loop can read safely.
 *
 * Changes:
 *  - Accepts optional playerName (used in JoinRequest so logs are readable).
 *  - Handles Packets.JoinRejected — sets rejectionReason; caller should check.
 */
public class NetworkClient {

    private final Client client;

    public final String host;
    private final String requestedName;

    // assigned by server on join
    private volatile String myId = null;

    // set if the server rejected our join (e.g. full lobby)
    private volatile String rejectionReason = null;

    // remote submarines keyed by player ID
    private final Map<String, Submarine> remoteSubs = new ConcurrentHashMap<>();

    // remote torpedo positions — null entry means torpedo is gone
    private final Map<String, Packets.TorpedoState> remoteTorpedoStates = new ConcurrentHashMap<>();

    private final List<Packets.TorpedoDetonate> pendingDetonations =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final List<Packets.SoundEvent> pendingSounds =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final List<Packets.RadarPing> pendingPings =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private volatile boolean connected = false;

    // ── Constructors ──────────────────────────────────────────────────────────────

    public NetworkClient(String host, String requestedName) {
        this.host = host;
        this.requestedName = requestedName != null ? requestedName : "Player";
        client = new Client(65536, 65536);
        NetworkServer.registerClasses(client.getKryo());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    public void connect() throws IOException {
        client.start();
        client.addListener(new ClientListener());
        client.connect(5000, host, NetworkServer.TCP_PORT, NetworkServer.UDP_PORT);

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

    /**
     * Non-null if the server sent a JoinRejected packet.
     * Check this after connect(); if non-null the connection was closed by the server.
     */
    public String getRejectionReason() { return rejectionReason; }

    // ── Sending ───────────────────────────────────────────────────────────────────

    /** Send local player state via UDP. Call every few ticks. */
    public void sendState(Submarine player) {
        if (!connected || myId == null) return;

        Packets.SubmarineState state = new Packets.SubmarineState();
        state.playerId   = myId;
        state.x          = player.getX();
        state.y          = player.getY();
        state.vx         = player.getVx();
        state.vy         = player.getVy();
        state.angle      = player.getAngle();
        state.rudderAngle = player.getRudderAngle();
        state.health     = player.getHealth();
        client.sendUDP(state);
    }

    /** type: "radar", "engine", "torpedo_explosion", etc. */
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

    public void sendRadarPing(float x, float y) {
        if (!connected || myId == null) return;

        Packets.RadarPing ping = new Packets.RadarPing();
        ping.playerId = myId;
        ping.x = x;
        ping.y = y;
        client.sendTCP(ping);
    }

    /** set alive=false on last send (after explode()) so others remove it */
    public void sendTorpedoState(float x, float y, float angle, boolean alive) {
        if (!connected || myId == null) return;

        Packets.TorpedoState t = new Packets.TorpedoState();
        t.playerId = myId;
        t.x        = x;
        t.y        = y;
        t.angle    = angle;
        t.alive    = alive;
        client.sendUDP(t);
    }

    public void sendTorpedoDetonate(float x, float y, float blastRadius, int damage) {
        if (!connected || myId == null) return;

        Packets.TorpedoDetonate d = new Packets.TorpedoDetonate();
        d.playerId    = myId;
        d.x           = x;
        d.y           = y;
        d.blastRadius = blastRadius;
        d.damage      = damage;
        client.sendTCP(d);
    }

    // ── Draining pending events ───────────────────────────────────────────────────

    /**
     * Call once per frame. Converts pending SoundEvents into Sound objects and
     * appends them to the provided list.
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

    /** Call once per frame. Returns pending radar pings from other players. */
    public List<Packets.RadarPing> drainPings() {
        synchronized (pendingPings) {
            List<Packets.RadarPing> copy = new java.util.ArrayList<>(pendingPings);
            pendingPings.clear();
            return copy;
        }
    }

    /** Call once per frame. Returns pending detonation packets. */
    public List<Packets.TorpedoDetonate> drainDetonations() {
        synchronized (pendingDetonations) {
            List<Packets.TorpedoDetonate> copy = new java.util.ArrayList<>(pendingDetonations);
            pendingDetonations.clear();
            return copy;
        }
    }

    /** Converts a SoundEvent packet into the appropriate Sound subclass. */
    private Sound buildSound(Packets.SoundEvent ev) {
        switch (ev.type) {
            case "radar":
                return new RadarSound(ev.x, ev.y, ev.strength, ev.playerId);
            case "torpedo_explosion":
                return new TorpedoSound(ev.x, ev.y, "remote_ping");
            default:
                return new Sound(ev.x, ev.y, ev.strength, ev.playerId) {};
        }
    }

    // ── Remote state access ───────────────────────────────────────────────────────

    public Map<String, Submarine>             getRemoteSubs()          { return remoteSubs; }
    public Map<String, Packets.TorpedoState>  getRemoteTorpedoStates() { return remoteTorpedoStates; }

    // ── Listener (runs on Kryonet background thread) ──────────────────────────────

    private class ClientListener extends Listener {

        @Override
        public void disconnected(Connection conn) {
            connected = false;
            System.out.println("Disconnected from server.");
        }

        @Override
        public void received(Connection conn, Object object) {

            if (object instanceof Packets.JoinRejected) {
                Packets.JoinRejected rej = (Packets.JoinRejected) object;
                rejectionReason = rej.reason;
                connected = false;
                System.err.println("Join rejected: " + rej.reason);
                return;
            }

            if (object instanceof Packets.JoinResponse) {
                Packets.JoinResponse resp = (Packets.JoinResponse) object;
                myId = resp.assignedId;
                System.out.println("Joined as: " + myId
                        + " spawn=(" + resp.spawnX + "," + resp.spawnY + ")");
                return;
            }

            if (object instanceof Packets.PlayerLeft) {
                Packets.PlayerLeft left = (Packets.PlayerLeft) object;
                remoteSubs.remove(left.playerId);
                System.out.println("Player left: " + left.playerId);
                return;
            }

            if (object instanceof Packets.SubmarineState) {
                Packets.SubmarineState state = (Packets.SubmarineState) object;
                if (state.playerId == null) return;

                Submarine sub = remoteSubs.computeIfAbsent(
                        state.playerId,
                        id -> new Submarine(id, state.x, state.y, state.health));

                sub.setPosition(state.x, state.y);
                sub.setVelocity(state.vx, state.vy);
                sub.setAngle(state.angle);
                sub.syncHealth(state.health);
                return;
            }

            if (object instanceof Packets.SoundEvent) {
                pendingSounds.add((Packets.SoundEvent) object);
                return;
            }

            if (object instanceof Packets.RadarPing) {
                pendingPings.add((Packets.RadarPing) object);
                return;
            }

            if (object instanceof Packets.TorpedoState) {
                Packets.TorpedoState t = (Packets.TorpedoState) object;
                if (t.playerId == null) return;
                if (t.alive) {
                    remoteTorpedoStates.put(t.playerId, t);
                } else {
                    remoteTorpedoStates.remove(t.playerId);
                }
                return;
            }

            if (object instanceof Packets.TorpedoDetonate) {
                pendingDetonations.add((Packets.TorpedoDetonate) object);
            }
        }
    }
}