package com.subbaandderek;

import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LobbyServer — runs on a well-known public host.
 *
 * Responsibilities:
 *   - Accept client connections for lobby browsing
 *   - Let any client create a named lobby (they become host, max 4 players)
 *   - Serve the live lobby list to browsers
 *   - Route join requests and push updates to all lobby members
 *   - Tell all lobby members when the host starts the game
 *
 * The actual game traffic (Packets.*) runs peer-to-peer between the host's
 * NetworkServer and the joining clients — this server only orchestrates the
 * pre-game phase.
 */
public class LobbyServer {

    public static final int LOBBY_TCP_PORT = 54560;
    public static final int LOBBY_UDP_PORT = 54561;
    public static final int MAX_PLAYERS    = 4;

    private final Server server;

    // connectionId → player name (set on first meaningful packet)
    private final Map<Integer, String>  connNames   = new ConcurrentHashMap<>();
    // connectionId → lobbyId the player is currently in (null if browsing)
    private final Map<Integer, String>  connLobby   = new ConcurrentHashMap<>();
    // lobbyId → LobbyState
    private final Map<String, LobbyState> lobbies   = new ConcurrentHashMap<>();
    // lobbyId → host Connection (so we can tell the host to start their server)
    private final Map<String, Connection> hostConns = new ConcurrentHashMap<>();
    // connectionId → Connection object (for targeted sends)
    private final Map<Integer, Connection> connMap  = new ConcurrentHashMap<>();

    private final AtomicInteger lobbyCounter = new AtomicInteger(1);

    // ── Entry point ──────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        LobbyServer ls = new LobbyServer();
        ls.start();
        System.out.println("Lobby server running on TCP:" + LOBBY_TCP_PORT
                + "  UDP:" + LOBBY_UDP_PORT);
        System.out.println("Press Enter to stop.");
        System.in.read();
        ls.stop();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    public LobbyServer() {
        server = new Server(65536, 65536);
        registerClasses(server.getKryo());
    }

    public void start() throws IOException {
        server.start();
        server.bind(LOBBY_TCP_PORT, LOBBY_UDP_PORT);
        server.addListener(new LobbyListener());
        System.out.println("LobbyServer started.");
    }

    public void stop() {
        server.stop();
    }

    /** Must be called on both client and server Kryo instances. */
    public static void registerClasses(com.esotericsoftware.kryo.Kryo kryo) {
        kryo.register(LobbyPackets.CreateLobby.class);
        kryo.register(LobbyPackets.RequestLobbyList.class);
        kryo.register(LobbyPackets.JoinLobbyRequest.class);
        kryo.register(LobbyPackets.StartGame.class);
        kryo.register(LobbyPackets.LeaveLobby.class);
        kryo.register(LobbyPackets.LobbyInfo.class);
        kryo.register(LobbyPackets.LobbyList.class);
        kryo.register(LobbyPackets.CreateLobbyResponse.class);
        kryo.register(LobbyPackets.JoinLobbyResponse.class);
        kryo.register(LobbyPackets.LobbyUpdate.class);
        kryo.register(LobbyPackets.GameStarting.class);
        kryo.register(LobbyPackets.ErrorMessage.class);
        kryo.register(java.util.ArrayList.class);
    }

    // ── Internal lobby state ──────────────────────────────────────────────────────

    private static class LobbyState {
        final String id;
        final String name;
        String hostName;
        int hostConnectionId;
        final List<String>  playerNames = new ArrayList<>();
        final List<Integer> connectionIds = new ArrayList<>();
        boolean started = false;

        LobbyState(String id, String name, String hostName, int hostConnId) {
            this.id = id;
            this.name = name;
            this.hostName = hostName;
            this.hostConnectionId = hostConnId;
        }

        LobbyPackets.LobbyInfo toInfo() {
            LobbyPackets.LobbyInfo info = new LobbyPackets.LobbyInfo();
            info.lobbyId     = id;
            info.lobbyName   = name;
            info.hostName    = hostName;
            info.playerCount = playerNames.size();
            info.maxPlayers  = MAX_PLAYERS;
            info.started     = started;
            info.playerNames = new ArrayList<>(playerNames);
            return info;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private LobbyPackets.LobbyList buildLobbyList() {
        LobbyPackets.LobbyList list = new LobbyPackets.LobbyList();
        for (LobbyState ls : lobbies.values()) {
            if (!ls.started) list.lobbies.add(ls.toInfo());
        }
        return list;
    }

    /** Broadcast the fresh lobby list to every browsing client (not in a lobby). */
    private void broadcastListToBrowsers() {
        LobbyPackets.LobbyList list = buildLobbyList();
        for (Map.Entry<Integer, Connection> e : connMap.entrySet()) {
            if (!connLobby.containsKey(e.getKey())) {
                e.getValue().sendTCP(list);
            }
        }
    }

    /** Broadcast a LobbyUpdate to every member of a lobby. */
    private void broadcastLobbyUpdate(LobbyState ls) {
        LobbyPackets.LobbyUpdate update = new LobbyPackets.LobbyUpdate();
        update.lobby = ls.toInfo();
        for (int cid : ls.connectionIds) {
            Connection c = connMap.get(cid);
            if (c != null) c.sendTCP(update);
        }
    }

    private void removeFromLobby(int connId) {
        String lobbyId = connLobby.remove(connId);
        if (lobbyId == null) return;
        LobbyState ls = lobbies.get(lobbyId);
        if (ls == null) return;

        int idx = ls.connectionIds.indexOf(connId);
        if (idx >= 0) {
            ls.connectionIds.remove(idx);
            ls.playerNames.remove(idx);
        }

        if (ls.connectionIds.isEmpty()) {
            // Last person left — dissolve the lobby
            lobbies.remove(lobbyId);
            hostConns.remove(lobbyId);
            System.out.println("Lobby dissolved (empty): " + lobbyId);
            broadcastListToBrowsers();
        } else if (connId == ls.hostConnectionId && !ls.connectionIds.isEmpty()) {
            // Host left — transfer to next player
            int newHostConnId = ls.connectionIds.get(0);
            ls.hostConnectionId = newHostConnId;
            ls.hostName = ls.playerNames.get(0);
            Connection newHostConn = connMap.get(newHostConnId);
            if (newHostConn != null) hostConns.put(lobbyId, newHostConn);
            System.out.println("Host transferred to: " + ls.hostName + " in lobby " + lobbyId);
            broadcastLobbyUpdate(ls);
            broadcastListToBrowsers();
        } else {
            broadcastLobbyUpdate(ls);
            broadcastListToBrowsers();
        }
    }

    // ── Listener ──────────────────────────────────────────────────────────────────

    private class LobbyListener extends Listener {

        @Override
        public void connected(Connection conn) {
            connMap.put(conn.getID(), conn);
            System.out.println("Browser connected: " + conn.getID());
        }

        @Override
        public void disconnected(Connection conn) {
            int cid = conn.getID();
            removeFromLobby(cid);
            connMap.remove(cid);
            connNames.remove(cid);
            System.out.println("Browser disconnected: " + cid);
            broadcastListToBrowsers();
        }

        @Override
        public void received(Connection conn, Object object) {
            int cid = conn.getID();

            // ── Create lobby ─────────────────────────────────────────────────────
            if (object instanceof LobbyPackets.CreateLobby) {
                LobbyPackets.CreateLobby req = (LobbyPackets.CreateLobby) object;
                String playerName = sanitize(req.playerName, "Host");
                String lobbyName  = sanitize(req.lobbyName,  "Unnamed Lobby");
                connNames.put(cid, playerName);

                // Leave any existing lobby first
                removeFromLobby(cid);

                String lobbyId = "lobby_" + lobbyCounter.getAndIncrement();
                LobbyState ls = new LobbyState(lobbyId, lobbyName, playerName, cid);
                ls.playerNames.add(playerName);
                ls.connectionIds.add(cid);
                lobbies.put(lobbyId, ls);
                hostConns.put(lobbyId, conn);
                connLobby.put(cid, lobbyId);

                LobbyPackets.CreateLobbyResponse resp = new LobbyPackets.CreateLobbyResponse();
                resp.success = true;
                resp.lobbyId = lobbyId;
                conn.sendTCP(resp);

                broadcastLobbyUpdate(ls);
                broadcastListToBrowsers();
                System.out.println("Lobby created: " + lobbyName + " (" + lobbyId + ") by " + playerName);
                return;
            }

            // ── List request ─────────────────────────────────────────────────────
            if (object instanceof LobbyPackets.RequestLobbyList) {
                conn.sendTCP(buildLobbyList());
                return;
            }

            // ── Join lobby ───────────────────────────────────────────────────────
            if (object instanceof LobbyPackets.JoinLobbyRequest) {
                LobbyPackets.JoinLobbyRequest req = (LobbyPackets.JoinLobbyRequest) object;
                String playerName = sanitize(req.playerName, "Player");
                connNames.put(cid, playerName);

                LobbyState ls = lobbies.get(req.lobbyId);
                LobbyPackets.JoinLobbyResponse resp = new LobbyPackets.JoinLobbyResponse();

                if (ls == null) {
                    resp.success = false;
                    resp.message = "Lobby no longer exists.";
                } else if (ls.started) {
                    resp.success = false;
                    resp.message = "That game has already started.";
                } else if (ls.playerNames.size() >= MAX_PLAYERS) {
                    resp.success = false;
                    resp.message = "Lobby is full (max " + MAX_PLAYERS + " players).";
                } else {
                    // Leave previous lobby if any
                    removeFromLobby(cid);

                    ls.playerNames.add(playerName);
                    ls.connectionIds.add(cid);
                    connLobby.put(cid, ls.id);

                    resp.success = true;
                    resp.lobbyId = ls.id;
                    // hostIp is the lobby server's address — the game server runs there
                    try {
                        resp.hostIp = conn.getRemoteAddressTCP().getAddress().getHostAddress();
                    } catch (Exception e) {
                        resp.hostIp = "localhost";
                    }

                    broadcastLobbyUpdate(ls);
                    broadcastListToBrowsers();
                    System.out.println(playerName + " joined lobby " + ls.id);
                }
                conn.sendTCP(resp);
                return;
            }

            // ── Start game ───────────────────────────────────────────────────────
            if (object instanceof LobbyPackets.StartGame) {
                LobbyPackets.StartGame req = (LobbyPackets.StartGame) object;
                LobbyState ls = lobbies.get(req.lobbyId);
                if (ls == null || ls.hostConnectionId != cid) return; // ignore bad requests
                if (ls.playerNames.size() < 1) return;

                ls.started = true;

                // Determine the host's IP for joinees to connect to
                String hostIp;
                try {
                    hostIp = conn.getRemoteAddressTCP().getAddress().getHostAddress();
                } catch (Exception e) {
                    hostIp = "localhost";
                }

                LobbyPackets.GameStarting starting = new LobbyPackets.GameStarting();
                starting.lobbyId  = ls.id;
                starting.hostIp   = hostIp;
                starting.tcpPort  = NetworkServer.TCP_PORT;
                starting.udpPort  = NetworkServer.UDP_PORT;

                for (int memberId : ls.connectionIds) {
                    Connection mc = connMap.get(memberId);
                    if (mc != null) mc.sendTCP(starting);
                }
                broadcastListToBrowsers();
                System.out.println("Game starting in lobby " + ls.id + " hosted at " + hostIp);
                return;
            }

            // ── Leave lobby ──────────────────────────────────────────────────────
            if (object instanceof LobbyPackets.LeaveLobby) {
                removeFromLobby(cid);
                conn.sendTCP(buildLobbyList()); // refresh their browser view
            }
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────────

    private static String sanitize(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        // Strip non-printable chars, limit length
        String cleaned = s.replaceAll("[^\\x20-\\x7E]", "").trim();
        if (cleaned.isEmpty()) return fallback;
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }
}