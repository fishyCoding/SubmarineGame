package com.subbaandderek;

import java.util.ArrayList;
import java.util.List;

/**
 * Packets used exclusively for the lobby browser phase (before joining a game).
 * These are separate from Packets.java so the game network layer stays clean.
 */
public class LobbyPackets {

    // ── Client → Server ──────────────────────────────────────────────────────────

    /** Ask the server to create a new lobby and assign this client as host. */
    public static class CreateLobby {
        public String lobbyName;    // display name chosen by the host
        public String playerName;   // host's display name
    }

    /** Ask the server to list all open lobbies. */
    public static class RequestLobbyList {
        // no fields needed — it's just a ping
    }

    /** Ask to join a specific lobby by its ID. */
    public static class JoinLobbyRequest {
        public String lobbyId;
        public String playerName;
    }

    /** Host signals the lobby should start — transitions to gameplay. */
    public static class StartGame {
        public String lobbyId;
    }

    /** Player leaves the lobby browser (disconnects cleanly). */
    public static class LeaveLobby {
        public String lobbyId;
        public String playerName;
    }

    // ── Server → Client ──────────────────────────────────────────────────────────

    /** A snapshot of a single lobby for the browser list. */
    public static class LobbyInfo {
        public String lobbyId;
        public String lobbyName;
        public String hostName;
        public int playerCount;     // current number of players
        public int maxPlayers;      // always 4
        public boolean started;     // true once host clicks Start
        public List<String> playerNames = new ArrayList<>();
    }

    /** Server pushes the full list of lobbies in response to RequestLobbyList,
     *  or whenever the lobby state changes (push update). */
    public static class LobbyList {
        public List<LobbyInfo> lobbies = new ArrayList<>();
    }

    /** Server confirms a lobby was created; client now polls or waits. */
    public static class CreateLobbyResponse {
        public boolean success;
        public String lobbyId;      // assigned by server
        public String message;      // error description if !success
    }

    /** Server confirms this client joined a lobby. */
    public static class JoinLobbyResponse {
        public boolean success;
        public String lobbyId;
        public String hostIp;       // IP the client should connect the game client to
        public String message;
    }

    /** Server broadcasts to all players in a lobby when someone joins/leaves. */
    public static class LobbyUpdate {
        public LobbyInfo lobby;
    }

    /** Server tells all players in a lobby that the host started the game. */
    public static class GameStarting {
        public String lobbyId;
        public String hostIp;
        public int tcpPort;
        public int udpPort;
    }

    /** Server sends an error message to a specific client. */
    public static class ErrorMessage {
        public String message;
    }
}