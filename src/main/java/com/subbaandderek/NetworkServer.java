package com.subbaandderek;

import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Game-play server (runs on the host's machine during an active session).
 *
 * Changes from original:
 *  - Accepts an optional lobbyName so the title bar and logs are descriptive.
 *  - Enforces MAX_PLAYERS (4) — rejects connections beyond that.
 *  - Sends a rejection packet before closing the connection so the client
 *    can show a meaningful error instead of a timeout.
 */
public class NetworkServer {

    public static final int TCP_PORT   = 54555;
    public static final int UDP_PORT   = 54556;
    public static final int MAX_PLAYERS = 4;

    private final Server server;
    private final String lobbyName;

    // maps Kryonet connection ID → player ID string
    private final Map<Integer, String> connectedPlayers = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    // ── Entry points ──────────────────────────────────────────────────────────────

    /** Standalone server (no lobby). */
    public static void main(String[] args) throws IOException {
        NetworkServer ns = new NetworkServer("Standalone Game");
        ns.start();
        System.out.println("Server running. Press Enter to stop.");
        System.in.read();
        ns.stop();
    }

    // ── Constructors ──────────────────────────────────────────────────────────────

    public NetworkServer() {
        this("Game");
    }

    public NetworkServer(String lobbyName) {
        this.lobbyName = lobbyName != null ? lobbyName : "Game";
        server = new Server(65536, 65536);
        registerClasses(server.getKryo());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    public void start() throws IOException {
        server.start();
        server.bind(TCP_PORT, UDP_PORT);
        server.addListener(new ServerListener());
        System.out.println("[" + lobbyName + "] Game server started on TCP:"
                + TCP_PORT + " UDP:" + UDP_PORT);
    }

    public void stop() {
        server.stop();
        System.out.println("[" + lobbyName + "] Server stopped.");
    }

    public int getPlayerCount() {
        return connectedPlayers.size();
    }

    // ── Packet registration (must match on client and server) ─────────────────────

    public static void registerClasses(com.esotericsoftware.kryo.Kryo kryo) {
        kryo.register(Packets.JoinRequest.class);
        kryo.register(Packets.JoinResponse.class);
        kryo.register(Packets.JoinRejected.class);   // new
        kryo.register(Packets.PlayerLeft.class);
        kryo.register(Packets.SubmarineState.class);
        kryo.register(Packets.SoundEvent.class);
        kryo.register(Packets.RadarPing.class);
        kryo.register(Packets.TorpedoState.class);
        kryo.register(Packets.TorpedoDetonate.class);
    }

    // ── Listener ──────────────────────────────────────────────────────────────────

    private class ServerListener extends Listener {

        @Override
        public void connected(Connection conn) {
            // If already at capacity, reject immediately.
            if (connectedPlayers.size() >= MAX_PLAYERS) {
                Packets.JoinRejected rej = new Packets.JoinRejected();
                rej.reason = "Server is full (" + MAX_PLAYERS + " players max).";
                conn.sendTCP(rej);
                conn.close();
                System.out.println("[" + lobbyName + "] Rejected connection (full): " + conn.getID());
                return;
            }
            System.out.println("[" + lobbyName + "] New connection: " + conn.getID());
        }

        @Override
        public void disconnected(Connection conn) {
            String playerId = connectedPlayers.remove(conn.getID());
            if (playerId == null) return;

            System.out.println("[" + lobbyName + "] Player disconnected: " + playerId
                    + " (" + connectedPlayers.size() + "/" + MAX_PLAYERS + " remaining)");

            Packets.PlayerLeft left = new Packets.PlayerLeft();
            left.playerId = playerId;
            server.sendToAllExceptTCP(conn.getID(), left);
        }

        @Override
        public void received(Connection conn, Object object) {

            if (object instanceof Packets.JoinRequest) {
                Packets.JoinRequest req = (Packets.JoinRequest) object;

                // Double-check capacity (race guard)
                if (connectedPlayers.size() >= MAX_PLAYERS) {
                    Packets.JoinRejected rej = new Packets.JoinRejected();
                    rej.reason = "Server is full.";
                    conn.sendTCP(rej);
                    conn.close();
                    return;
                }

                String assignedId = "player_" + nextId.getAndIncrement();
                connectedPlayers.put(conn.getID(), assignedId);
                System.out.println("[" + lobbyName + "] Player joined: " + assignedId
                        + " (requested: " + req.playerId + ")  "
                        + connectedPlayers.size() + "/" + MAX_PLAYERS);

                Packets.JoinResponse resp = new Packets.JoinResponse();
                resp.assignedId = assignedId;
                resp.spawnX = 800f;
                resp.spawnY = -100f;
                conn.sendTCP(resp);
                return;
            }

            if (object instanceof Packets.SubmarineState) {
                Packets.SubmarineState state = (Packets.SubmarineState) object;
                state.playerId = connectedPlayers.get(conn.getID());
                if (state.playerId == null) return;
                server.sendToAllExceptUDP(conn.getID(), state);
                return;
            }

            if (object instanceof Packets.SoundEvent) {
                Packets.SoundEvent ev = (Packets.SoundEvent) object;
                ev.playerId = connectedPlayers.get(conn.getID());
                if (ev.playerId == null) return;
                server.sendToAllExceptTCP(conn.getID(), ev);
                return;
            }

            if (object instanceof Packets.RadarPing) {
                Packets.RadarPing ping = (Packets.RadarPing) object;
                ping.playerId = connectedPlayers.get(conn.getID());
                if (ping.playerId == null) return;
                server.sendToAllExceptTCP(conn.getID(), ping);
            }

            if (object instanceof Packets.TorpedoState) {
                Packets.TorpedoState t = (Packets.TorpedoState) object;
                t.playerId = connectedPlayers.get(conn.getID());
                if (t.playerId == null) return;
                server.sendToAllExceptUDP(conn.getID(), t);
            }

            if (object instanceof Packets.TorpedoDetonate) {
                Packets.TorpedoDetonate d = (Packets.TorpedoDetonate) object;
                d.playerId = connectedPlayers.get(conn.getID());
                if (d.playerId == null) return;
                server.sendToAllExceptTCP(conn.getID(), d);
            }
        }
    }
}