package com.subbaandderek;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LobbyClient — connects to LobbyServer for the pre-game lobby browser phase.
 *
 * All Kryonet callbacks run on a background thread.
 * All state exposed to the Menu render loop is volatile or thread-safe.
 *
 * Typical flow:
 *   1. connect()
 *   2. requestList()               → onLobbyList fires with available lobbies
 *   3a. createLobby(name, player)  → onCreateResponse fires; wait in lobby
 *   3b. joinLobby(id, player)      → onJoinResponse fires; wait in lobby
 *   4. [host only] startGame()     → all members receive onGameStarting
 *   5. disconnect() and hand off to Game.main(args)
 */
public class LobbyClient {

    private final Client client;
    private final String serverHost;
    private final String playerName;

    // ── Volatile state readable from the game/UI thread ─────────────────────────

    private volatile boolean connected = false;

    /** The most recent lobby list pushed by the server. */
    private final List<LobbyPackets.LobbyInfo> lobbyList = new CopyOnWriteArrayList<>();

    /** Our current lobby (null if just browsing). */
    private volatile LobbyPackets.LobbyInfo currentLobby = null;

    /** Set after createLobby() succeeds. */
    private volatile String myLobbyId = null;

    /** True if we are the host of myLobbyId. */
    private volatile boolean isHost = false;

    /** Filled in when the server fires GameStarting. */
    private volatile LobbyPackets.GameStarting pendingStart = null;

    /** Last error from a Create/Join response. */
    private volatile String lastError = null;

    // ── Callbacks (set by Menu before connecting) ───────────────────────────────

    /** Called on the Kryonet thread whenever the lobby list changes. */
    public Runnable onLobbyListUpdated = () -> {};

    /** Called when our lobby's member list changes. */
    public Runnable onLobbyUpdated = () -> {};

    /** Called when the host triggers game start. */
    public Runnable onGameStarting = () -> {};

    // ── Constructor ──────────────────────────────────────────────────────────────

    public LobbyClient(String serverHost, String playerName) {
        this.serverHost = serverHost;
        this.playerName = playerName;
        client = new Client(65536, 65536);
        LobbyServer.registerClasses(client.getKryo());
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    public void connect() throws IOException {
        client.start();
        client.addListener(new InternalListener());
        client.connect(5000, serverHost, LobbyServer.LOBBY_TCP_PORT, LobbyServer.LOBBY_UDP_PORT);
        connected = true;
        System.out.println("LobbyClient connected to " + serverHost);
    }

    public void disconnect() {
        connected = false;
        client.stop();
    }

    public boolean isConnected() { return connected; }

    // ── Actions ──────────────────────────────────────────────────────────────────

    /** Ask the server for the current lobby list. */
    public void requestList() {
        if (!connected) return;
        client.sendTCP(new LobbyPackets.RequestLobbyList());
    }

    /**
     * Create a new lobby with the given name.
     * On success, onLobbyUpdated fires and isHost becomes true.
     */
    public void createLobby(String lobbyName) {
        if (!connected) return;
        lastError = null;
        LobbyPackets.CreateLobby pkt = new LobbyPackets.CreateLobby();
        pkt.lobbyName  = lobbyName;
        pkt.playerName = playerName;
        client.sendTCP(pkt);
    }

    /**
     * Join an existing lobby by ID.
     * On success, onLobbyUpdated fires.
     */
    public void joinLobby(String lobbyId) {
        if (!connected) return;
        lastError = null;
        LobbyPackets.JoinLobbyRequest pkt = new LobbyPackets.JoinLobbyRequest();
        pkt.lobbyId    = lobbyId;
        pkt.playerName = playerName;
        client.sendTCP(pkt);
    }

    /**
     * [Host only] Tell the server to start the game.
     * This causes GameStarting to be sent to all lobby members.
     */
    public void startGame() {
        if (!connected || myLobbyId == null || !isHost) return;
        LobbyPackets.StartGame pkt = new LobbyPackets.StartGame();
        pkt.lobbyId = myLobbyId;
        client.sendTCP(pkt);
    }

    /** Leave the current lobby and return to the browser. */
    public void leaveLobby() {
        if (!connected || myLobbyId == null) return;
        LobbyPackets.LeaveLobby pkt = new LobbyPackets.LeaveLobby();
        pkt.lobbyId    = myLobbyId;
        pkt.playerName = playerName;
        client.sendTCP(pkt);
        myLobbyId    = null;
        currentLobby = null;
        isHost       = false;
    }

    // ── State accessors ──────────────────────────────────────────────────────────

    public List<LobbyPackets.LobbyInfo> getLobbyList() { return lobbyList; }
    public LobbyPackets.LobbyInfo getCurrentLobby()    { return currentLobby; }
    public String getMyLobbyId()                       { return myLobbyId; }
    public boolean isHost()                            { return isHost; }
    public LobbyPackets.GameStarting getPendingStart() { return pendingStart; }
    public String getLastError()                       { return lastError; }
    public String getPlayerName()                      { return playerName; }

    // ── Kryonet listener ─────────────────────────────────────────────────────────

    private class InternalListener extends Listener {

        @Override
        public void disconnected(Connection conn) {
            connected = false;
            System.out.println("LobbyClient disconnected.");
        }

        @Override
        public void received(Connection conn, Object object) {

            if (object instanceof LobbyPackets.LobbyList) {
                LobbyPackets.LobbyList list = (LobbyPackets.LobbyList) object;
                lobbyList.clear();
                lobbyList.addAll(list.lobbies);
                onLobbyListUpdated.run();
                return;
            }

            if (object instanceof LobbyPackets.CreateLobbyResponse) {
                LobbyPackets.CreateLobbyResponse resp = (LobbyPackets.CreateLobbyResponse) object;
                if (resp.success) {
                    myLobbyId = resp.lobbyId;
                    isHost    = true;
                    System.out.println("Lobby created: " + myLobbyId);
                } else {
                    lastError = resp.message;
                    System.err.println("Create lobby failed: " + resp.message);
                }
                return;
            }

            if (object instanceof LobbyPackets.JoinLobbyResponse) {
                LobbyPackets.JoinLobbyResponse resp = (LobbyPackets.JoinLobbyResponse) object;
                if (resp.success) {
                    myLobbyId = resp.lobbyId;
                    isHost    = false;
                    System.out.println("Joined lobby: " + myLobbyId);
                } else {
                    lastError = resp.message;
                    System.err.println("Join lobby failed: " + resp.message);
                }
                return;
            }

            if (object instanceof LobbyPackets.LobbyUpdate) {
                LobbyPackets.LobbyUpdate update = (LobbyPackets.LobbyUpdate) object;
                currentLobby = update.lobby;
                // Keep isHost accurate if host transferred
                if (myLobbyId != null && currentLobby != null) {
                    isHost = playerName.equals(currentLobby.hostName);
                }
                onLobbyUpdated.run();
                return;
            }

            if (object instanceof LobbyPackets.GameStarting) {
                pendingStart = (LobbyPackets.GameStarting) object;
                onGameStarting.run();
            }
        }
    }
}