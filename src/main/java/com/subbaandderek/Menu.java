package com.subbaandderek;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

/**
 * Menu — launch screen with lobby browser.
 *
 * States:
 *   NAME_ENTRY   → player types their name
 *   MAIN         → solo / host / browse
 *   CREATE       → type a lobby name, submit
 *   BROWSE       → scrollable list of open lobbies
 *   LOBBY_WAIT   → waiting room (host can start; others wait)
 *   CONNECTING   → brief "connecting…" splash
 *   ERROR        → error message with back button
 */
public class Menu {

    // ── Canvas ────────────────────────────────────────────────────────────────────
    private static final int W = 700;
    private static final int H = 480;

    // ── Palette (matches existing green-terminal style) ────────────────────────────
    private static final Color COL_BG       = Color.decode("#030E06");
    private static final Color COL_BORDER   = Color.decode("#145023");
    private static final Color COL_TEXT     = Color.decode("#00FF50");
    private static final Color COL_DIM      = Color.decode("#00A032");
    private static final Color COL_MUTED    = Color.decode("#005520");
    private static final Color COL_SELECTED = Color.decode("#00FF50");
    private static final Color COL_BTN      = Color.decode("#0A2D12");
    private static final Color COL_BTN_HOV  = Color.decode("#144D22");
    private static final Color COL_WARN     = Color.decode("#FF5040");
    private static final Color COL_WARN_DIM = Color.decode("#992010");
    private static final Color COL_FULL     = Color.decode("#404040");
    private static final Color COL_FULL_TXT = Color.decode("#606060");

    // Default lobby server — change to your public host
    private static final String LOBBY_SERVER = "localhost";

    // ── States ────────────────────────────────────────────────────────────────────
    private static final int STATE_NAME_ENTRY = 0;
    private static final int STATE_SERVER_SELECT = 7;
    private static final int STATE_MAIN       = 1;
    private static final int STATE_CREATE     = 2;
    private static final int STATE_BROWSE     = 3;
    private static final int STATE_LOBBY_WAIT = 4;
    private static final int STATE_CONNECTING = 5;
    private static final int STATE_ERROR      = 6;

    private static int state = STATE_NAME_ENTRY;

    // ── Input buffers ─────────────────────────────────────────────────────────────
    private static StringBuilder playerNameBuf = new StringBuilder("Player1");
    private static StringBuilder lobbyNameBuf  = new StringBuilder("");
    private static StringBuilder ipBuf         = new StringBuilder(LOBBY_SERVER);

    // ── Network ───────────────────────────────────────────────────────────────────
    private static LobbyClient lobbyClient = null;

    // ── Browse state ──────────────────────────────────────────────────────────────
    private static int selectedLobbyIdx = -1;
    private static int browseScrollOffset = 0;
    private static final int VISIBLE_LOBBIES = 6;

    // ── Lobby wait state ──────────────────────────────────────────────────────────
    // Whether this client is waiting for the host (true) or is the host (false)
    private static String errorMsg = "";

    // ── Click debounce ────────────────────────────────────────────────────────────
    private static boolean mouseWasDown = false;

    // ── Blink timer ───────────────────────────────────────────────────────────────
    private static long startMs = System.currentTimeMillis();

    // ── Entry point ───────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        StdDraw.setCanvasSize(W, H);
        StdDraw.setXscale(0, W);
        StdDraw.setYscale(0, H);
        StdDraw.enableDoubleBuffering();
        StdDraw.setTitle("Submarine");

        String[] result = null;
        while (result == null) {
            result = handleInput();
            render();
            StdDraw.show();
            StdDraw.pause(16);
        }

        // Tear down lobby connection before handing off to game
        if (lobbyClient != null && lobbyClient.isConnected()) {
            try { lobbyClient.disconnect(); } catch (Exception ignored) {}
        }

        Game.main(result);
    }

    // ── Input dispatch ────────────────────────────────────────────────────────────

    private static String[] handleInput() {
        double mx = StdDraw.mouseX();
        double my = StdDraw.mouseY();
        boolean mouseDown = StdDraw.isMousePressed();
        boolean clicked = mouseDown && !mouseWasDown;
        mouseWasDown = mouseDown;

        switch (state) {
            case STATE_NAME_ENTRY: return handleNameEntry(mx, my, clicked);
            case STATE_SERVER_SELECT: return handleServerSelect(mx, my, clicked);
            case STATE_MAIN:       return handleMain(mx, my, clicked);
            case STATE_CREATE:     return handleCreate(mx, my, clicked);
            case STATE_BROWSE:     return handleBrowse(mx, my, clicked);
            case STATE_LOBBY_WAIT: return handleLobbyWait(mx, my, clicked);
            case STATE_CONNECTING: return handleConnecting();
            case STATE_ERROR:      return handleError(mx, my, clicked);
        }
        return null;
    }

    // ── State: NAME_ENTRY ─────────────────────────────────────────────────────────

    private static String[] handleNameEntry(double mx, double my, boolean clicked) {
        drainTyping(playerNameBuf, 16, true);

        if (clicked && hitBtn(mx, my, W / 2.0, 155, 150, 28)) {
            if (playerNameBuf.length() == 0) playerNameBuf.append("Player");
            waitRelease();
            state = STATE_SERVER_SELECT;
        }
        // Also accept Enter
        return null;
    }

    // ── State: SERVER_SELECT ──────────────────────────────────────────────────────

    private static String[] handleServerSelect(double mx, double my, boolean clicked) {
        drainTyping(ipBuf, 64, false);

        if (clicked && hitBtn(mx, my, W / 2.0, 120, 150, 28)) {
            if (ipBuf.length() == 0) ipBuf.append(LOBBY_SERVER);
            waitRelease();
            state = STATE_MAIN;
        }
        // Also accept Enter
        return null;
    }

    // ── State: MAIN ───────────────────────────────────────────────────────────────

    private static String[] handleMain(double mx, double my, boolean clicked) {
        // Solo
        if (clicked && hitBtn(mx, my, W / 2.0, 270, 150, 28)) {
            waitRelease();
            return new String[]{"--solo"};
        }
        // Host
        if (clicked && hitBtn(mx, my, W / 2.0, 210, 150, 28)) {
            waitRelease();
            lobbyNameBuf = new StringBuilder(playerNameBuf + "'s Game");
            state = STATE_CREATE;
        }
        // Browse lobbies
        if (clicked && hitBtn(mx, my, W / 2.0, 150, 150, 28)) {
            waitRelease();
            connectToLobbyServer();
            if (state != STATE_ERROR) {
                selectedLobbyIdx = -1;
                browseScrollOffset = 0;
                if (lobbyClient != null) lobbyClient.requestList();
                state = STATE_BROWSE;
            }
        }
        return null;
    }

    // ── State: CREATE ─────────────────────────────────────────────────────────────

    private static String[] handleCreate(double mx, double my, boolean clicked) {
        drainTyping(lobbyNameBuf, 28, false);

        // Back
        if (clicked && hitBtn(mx, my, W / 2.0 - 90, 120, 100, 26)) {
            waitRelease();
            state = STATE_MAIN;
        }
        // Create & Host
        if (clicked && hitBtn(mx, my, W / 2.0 + 90, 120, 120, 26)) {
            waitRelease();
            if (lobbyNameBuf.length() == 0) lobbyNameBuf.append("My Lobby");
            connectToLobbyServer();
            if (state == STATE_ERROR) return null;

            lobbyClient.createLobby(lobbyNameBuf.toString());

            // Wait briefly for server to confirm before entering lobby wait
            for (int i = 0; i < 60; i++) {
                if (lobbyClient.getMyLobbyId() != null) break;
                StdDraw.pause(50);
            }

            if (lobbyClient.getMyLobbyId() == null) {
                errorMsg = lobbyClient.getLastError() != null
                        ? lobbyClient.getLastError() : "Server did not respond.";
                state = STATE_ERROR;
                return null;
            }
            // Host also starts their NetworkServer immediately
            state = STATE_LOBBY_WAIT;
        }
        return null;
    }

    // ── State: BROWSE ─────────────────────────────────────────────────────────────

    private static String[] handleBrowse(double mx, double my, boolean clicked) {
        // Refresh button
        if (clicked && hitBtn(mx, my, W - 70, H - 28, 60, 18)) {
            if (lobbyClient != null) lobbyClient.requestList();
        }
        // Back
        if (clicked && hitBtn(mx, my, 55, H - 28, 50, 18)) {
            waitRelease();
            if (lobbyClient != null) {
                try { lobbyClient.disconnect(); } catch (Exception ignored) {}
                lobbyClient = null;
            }
            state = STATE_MAIN;
        }

        // Scroll
        List<LobbyPackets.LobbyInfo> list = lobbyClient != null ? lobbyClient.getLobbyList() : List.of();
        int listSize = list.size();
        if (clicked && hitBtn(mx, my, W - 24, H / 2.0, 14, 60)) {
            browseScrollOffset = Math.max(0, browseScrollOffset - 1);
        }
        if (clicked && hitBtn(mx, my, W - 24, H / 2.0 - 90, 14, 60)) {
            browseScrollOffset = Math.min(Math.max(0, listSize - VISIBLE_LOBBIES), browseScrollOffset + 1);
        }

        // Row clicks
        double rowH = 52;
        double listTop = H - 80;
        for (int i = 0; i < VISIBLE_LOBBIES; i++) {
            int dataIdx = i + browseScrollOffset;
            if (dataIdx >= listSize) break;
            double rowCY = listTop - i * rowH - rowH / 2.0;
            if (clicked && my >= rowCY - rowH / 2.0 && my < rowCY + rowH / 2.0
                    && mx >= 20 && mx < W - 40) {
                LobbyPackets.LobbyInfo info = list.get(dataIdx);
                if (!info.started && info.playerCount < info.maxPlayers) {
                    selectedLobbyIdx = dataIdx;
                }
            }
        }

        // Join button
        if (clicked && hitBtn(mx, my, W / 2.0, 36, 100, 22) && selectedLobbyIdx >= 0) {
            LobbyPackets.LobbyInfo info = selectedLobbyIdx < listSize ? list.get(selectedLobbyIdx) : null;
            if (info != null && !info.started && info.playerCount < info.maxPlayers) {
                waitRelease();
                lobbyClient.joinLobby(info.lobbyId);
                for (int i = 0; i < 60; i++) {
                    if (lobbyClient.getMyLobbyId() != null || lobbyClient.getLastError() != null) break;
                    StdDraw.pause(50);
                }
                if (lobbyClient.getMyLobbyId() != null) {
                    state = STATE_LOBBY_WAIT;
                } else {
                    errorMsg = lobbyClient.getLastError() != null
                            ? lobbyClient.getLastError() : "Failed to join lobby.";
                    state = STATE_ERROR;
                }
            }
        }

        return null;
    }

    // ── State: LOBBY_WAIT ─────────────────────────────────────────────────────────

    private static String[] handleLobbyWait(double mx, double my, boolean clicked) {
        // Check if game has started
        if (lobbyClient != null && lobbyClient.getPendingStart() != null) {
            LobbyPackets.GameStarting gs = lobbyClient.getPendingStart();
            if (lobbyClient.isHost()) {
                // Host: start local server, then connect as client
                return new String[]{"--host"};
            } else {
                // Joiner: connect to host IP
                return new String[]{"--join", gs.hostIp};
            }
        }

        // Leave lobby
        if (clicked && hitBtn(mx, my, W / 2.0 - 90, 55, 110, 26)) {
            waitRelease();
            if (lobbyClient != null) lobbyClient.leaveLobby();
            state = STATE_BROWSE;
            if (lobbyClient != null) lobbyClient.requestList();
        }

        // [Host only] Start game
        if (lobbyClient != null && lobbyClient.isHost()) {
            LobbyPackets.LobbyInfo lobby = lobbyClient.getCurrentLobby();
            int count = lobby != null ? lobby.playerCount : 1;
            if (clicked && hitBtn(mx, my, W / 2.0 + 90, 55, 110, 26)) {
                waitRelease();
                // Host will start the server in Game.setupNetwork()
                lobbyClient.startGame();
                // The GameStarting packet will come back and trigger the return above
            }
        }

        return null;
    }

    // ── State: CONNECTING ─────────────────────────────────────────────────────────

    private static String[] handleConnecting() {
        // Nothing — connecting happens synchronously in connectToLobbyServer()
        return null;
    }

    // ── State: ERROR ──────────────────────────────────────────────────────────────

    private static String[] handleError(double mx, double my, boolean clicked) {
        if (clicked && hitBtn(mx, my, W / 2.0, 140, 120, 26)) {
            waitRelease();
            state = STATE_MAIN;
        }
        return null;
    }

    // ── Render dispatch ───────────────────────────────────────────────────────────

    private static void render() {
        // Background
        StdDraw.setPenColor(COL_BG);
        StdDraw.filledRectangle(W / 2.0, H / 2.0, W / 2.0, H / 2.0);

        // Scanline texture — very subtle horizontal lines
        StdDraw.setPenColor(new Color(0, 255, 80, 6));
        for (int y = 0; y < H; y += 3) StdDraw.line(0, y, W, y);

        switch (state) {
            case STATE_NAME_ENTRY: renderNameEntry(); break;            case STATE_SERVER_SELECT: renderServerSelect(); break;            case STATE_MAIN:       renderMain();      break;
            case STATE_CREATE:     renderCreate();    break;
            case STATE_BROWSE:     renderBrowse();    break;
            case STATE_LOBBY_WAIT: renderLobbyWait(); break;
            case STATE_CONNECTING: renderConnecting(); break;
            case STATE_ERROR:      renderError();     break;
        }
    }

    // ── Render: NAME ENTRY ────────────────────────────────────────────────────────

    private static void renderNameEntry() {
        double mx = StdDraw.mouseX(), my = StdDraw.mouseY();

        header("SUBMARINE", 340);
        small("Who are you?", W / 2.0, 290, COL_DIM);

        inputBox(playerNameBuf.toString(), W / 2.0, 230, 220, true);

        drawBtn("ENTER GAME", W / 2.0, 155, 150, 28, mx, my);
        small("v1.0 — up to 4 players per lobby", W / 2.0, 55, COL_MUTED);
    }

    // ── Render: SERVER_SELECT ─────────────────────────────────────────────────────

    private static void renderServerSelect() {
        double mx = StdDraw.mouseX(), my = StdDraw.mouseY();

        header("LOBBY SERVER", 350);
        small("Enter lobby server hostname or IP", W / 2.0, 300, COL_DIM);
        small("(leave blank for localhost)", W / 2.0, 270, COL_DIM);

        inputBox(ipBuf.toString(), W / 2.0, 220, 300, false);

        drawBtn("CONTINUE", W / 2.0, 120, 150, 28, mx, my);
    }

    // ── Render: MAIN ──────────────────────────────────────────────────────────────

    private static void renderMain() {
        double mx = StdDraw.mouseX(), my = StdDraw.mouseY();

        header("SUBMARINE", 360);
        small("Playing as: " + playerNameBuf, W / 2.0, 315, COL_DIM);

        drawBtn("SOLO",           W / 2.0, 270, 150, 28, mx, my);
        drawBtn("HOST LOBBY",     W / 2.0, 210, 150, 28, mx, my);
        drawBtn("BROWSE LOBBIES", W / 2.0, 150, 150, 28, mx, my);

        String serverDisplay = ipBuf.length() > 0 ? ipBuf.toString() : LOBBY_SERVER;
        small("Server: " + serverDisplay, W / 2.0, 50, COL_MUTED);
    }

    // ── Render: CREATE ────────────────────────────────────────────────────────────

    private static void renderCreate() {
        double mx = StdDraw.mouseX(), my = StdDraw.mouseY();

        header("HOST A LOBBY", 400);
        small("Choose a name for your lobby", W / 2.0, 355, COL_DIM);

        inputBox(lobbyNameBuf.toString(), W / 2.0, 290, 300, true);

        drawBtn("← BACK",         W / 2.0 - 90, 120, 100, 26, mx, my);
        drawBtn("CREATE & HOST →", W / 2.0 + 90, 120, 120, 26, mx, my);
    }

    // ── Render: BROWSE ────────────────────────────────────────────────────────────

    private static void renderBrowse() {
        double mx = StdDraw.mouseX(), my = StdDraw.mouseY();

        List<LobbyPackets.LobbyInfo> list = lobbyClient != null ? lobbyClient.getLobbyList() : List.of();
        int listSize = list.size();

        // Header bar
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.filledRectangle(W / 2.0, H - 18, W / 2.0, 18);
        StdDraw.setPenColor(COL_TEXT);
        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 13));
        StdDraw.textLeft(16, H - 18, "LOBBY BROWSER");
        small("REFRESH", W - 70, H - 18, hitBtn(mx, my, W - 70, H - 28, 60, 18) ? COL_TEXT : COL_DIM);

        // Column headers
        StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 11));
        StdDraw.setPenColor(COL_DIM);
        StdDraw.textLeft(20, H - 50, "LOBBY NAME");
        StdDraw.textLeft(280, H - 50, "HOST");
        StdDraw.text(490, H - 50, "PLAYERS");
        StdDraw.textRight(W - 45, H - 50, "STATUS");

        // Separator
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(0.001);
        StdDraw.line(20, H - 58, W - 40, H - 58);

        // Rows
        double rowH = 52;
        double listTop = H - 80;
        for (int i = 0; i < VISIBLE_LOBBIES; i++) {
            int dataIdx = i + browseScrollOffset;
            if (dataIdx >= listSize) break;
            LobbyPackets.LobbyInfo info = list.get(dataIdx);
            double rowCY = listTop - i * rowH - rowH / 2.0;

            boolean isSelected = dataIdx == selectedLobbyIdx;
            boolean isFull     = info.playerCount >= info.maxPlayers || info.started;

            // Row background
            if (isSelected && !isFull) {
                StdDraw.setPenColor(new Color(0, 60, 20));
                StdDraw.filledRectangle(W / 2.0 - 20, rowCY, W / 2.0 - 20, rowH / 2.0 - 1);
            }
            // Border
            StdDraw.setPenColor(isSelected && !isFull ? COL_BORDER : COL_MUTED);
            StdDraw.setPenRadius(0.001);
            StdDraw.rectangle(W / 2.0 - 20, rowCY, W / 2.0 - 20, rowH / 2.0 - 1);

            // Text colors
            Color nameCol   = isFull ? COL_FULL_TXT : (isSelected ? COL_TEXT : COL_DIM);
            Color statusCol = info.started ? COL_WARN_DIM : (isFull ? COL_FULL_TXT : COL_DIM);

            StdDraw.setFont(new Font("Monospaced", Font.BOLD, 13));
            StdDraw.setPenColor(nameCol);
            StdDraw.textLeft(26, rowCY + 8, clip(info.lobbyName, 22));

            StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 11));
            StdDraw.setPenColor(nameCol);
            StdDraw.textLeft(280, rowCY + 8, clip(info.hostName, 16));

            // Player count pip bar
            for (int p = 0; p < info.maxPlayers; p++) {
                boolean filled = p < info.playerCount;
                StdDraw.setPenColor(filled ? COL_TEXT : COL_MUTED);
                StdDraw.filledRectangle(462 + p * 14, rowCY + 8, 5, 5);
            }
            StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 10));
            StdDraw.setPenColor(nameCol);
            StdDraw.text(490, rowCY - 4, info.playerCount + "/" + info.maxPlayers);

            // Status badge
            String badge = info.started ? "IN GAME" : (isFull ? "FULL" : "OPEN");
            StdDraw.setPenColor(statusCol);
            StdDraw.setFont(new Font("Monospaced", Font.BOLD, 10));
            StdDraw.textRight(W - 46, rowCY + 3, badge);
        }

        // Empty state
        if (listSize == 0) {
            StdDraw.setPenColor(COL_MUTED);
            StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 12));
            StdDraw.text(W / 2.0, H / 2.0, "No lobbies found — host one!");
        }

        // Scroll arrows
        if (browseScrollOffset > 0) {
            StdDraw.setPenColor(COL_DIM);
            StdDraw.setFont(new Font("Monospaced", Font.BOLD, 14));
            StdDraw.text(W - 24, H / 2.0, "▲");
        }
        if (browseScrollOffset + VISIBLE_LOBBIES < listSize) {
            StdDraw.setPenColor(COL_DIM);
            StdDraw.setFont(new Font("Monospaced", Font.BOLD, 14));
            StdDraw.text(W - 24, H / 2.0 - 90, "▼");
        }

        // Footer
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.line(0, 65, W, 65);

        boolean canJoin = selectedLobbyIdx >= 0 && selectedLobbyIdx < listSize
                && !list.get(selectedLobbyIdx).started
                && list.get(selectedLobbyIdx).playerCount < list.get(selectedLobbyIdx).maxPlayers;
        drawBtn("JOIN →", W / 2.0, 36, 100, 22, mx, my,
                canJoin ? COL_TEXT : COL_MUTED, canJoin ? COL_BTN_HOV : COL_BTN);
        small("← BACK", 55, H - 18, hitBtn(mx, my, 55, H - 28, 50, 18) ? COL_TEXT : COL_DIM);
    }

    // ── Render: LOBBY_WAIT ────────────────────────────────────────────────────────

    private static void renderLobbyWait() {
        double mx = StdDraw.mouseX(), my = StdDraw.mouseY();

        boolean isHost = lobbyClient != null && lobbyClient.isHost();
        LobbyPackets.LobbyInfo lobby = lobbyClient != null ? lobbyClient.getCurrentLobby() : null;
        String lobbyName = lobby != null ? lobby.lobbyName : "…";
        int count = lobby != null ? lobby.playerCount : 1;

        header(lobbyName, 410);
        small(isHost ? "You are the host" : "Waiting for host to start…", W / 2.0, 365, COL_DIM);

        // Player slots
        double slotStartX = W / 2.0 - (LobbyServer.MAX_PLAYERS - 1) * 75;
        for (int i = 0; i < LobbyServer.MAX_PLAYERS; i++) {
            double sx = slotStartX + i * 150;
            double sy = 270;

            boolean occupied = lobby != null && i < lobby.playerNames.size();
            String name = occupied ? lobby.playerNames.get(i) : "";
            boolean isYou = occupied && name.equals(lobbyClient != null ? lobbyClient.getPlayerName() : "");

            // Slot card
            StdDraw.setPenColor(occupied ? new Color(0, 35, 15) : new Color(5, 12, 7));
            StdDraw.filledRectangle(sx, sy, 60, 38);
            StdDraw.setPenColor(occupied ? COL_BORDER : COL_MUTED);
            StdDraw.setPenRadius(0.001);
            StdDraw.rectangle(sx, sy, 60, 38);

            // Submarine icon placeholder
            if (occupied) {
                StdDraw.setPenColor(isYou ? COL_TEXT : COL_DIM);
                StdDraw.filledEllipse(sx, sy + 10, 18, 7);
                StdDraw.setPenColor(isYou ? new Color(0, 80, 30) : new Color(0, 50, 20));
                StdDraw.filledRectangle(sx + 16, sy + 10, 4, 4);
            } else {
                StdDraw.setPenColor(COL_MUTED);
                StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 18));
                StdDraw.text(sx, sy + 10, "+");
            }

            StdDraw.setFont(new Font("Monospaced", Font.BOLD, 10));
            StdDraw.setPenColor(occupied ? (isYou ? COL_TEXT : COL_DIM) : COL_MUTED);
            String label = occupied ? clip(name, 9) : "OPEN";
            StdDraw.text(sx, sy - 20, label);

            // Host crown
            if (lobby != null && occupied && name.equals(lobby.hostName)) {
                StdDraw.setPenColor(new Color(200, 160, 40));
                StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 12));
                StdDraw.text(sx, sy + 32, "★");
            }
        }

        // Player count
        small(count + " / " + LobbyServer.MAX_PLAYERS + " players", W / 2.0, 210, COL_DIM);

        // Animated waiting dots
        long elapsed = System.currentTimeMillis() - startMs;
        int dots = (int)((elapsed / 500) % 4);
        String dotStr = ".".repeat(dots) + " ".repeat(3 - dots);

        if (isHost) {
            small("Waiting for players" + dotStr, W / 2.0, 180, COL_MUTED);
        } else {
            small("Waiting for host" + dotStr, W / 2.0, 180, COL_MUTED);
        }

        // Tip
        if (isHost) {
            small("Share your IP with friends so they can join", W / 2.0, 105, COL_MUTED);
            try {
                String ip = java.net.InetAddress.getLocalHost().getHostAddress();
                small("Your IP: " + ip, W / 2.0, 88, COL_DIM);
            } catch (Exception ignored) {}
        }

        // Buttons
        drawBtn("← LEAVE", W / 2.0 - 90, 55, 110, 26, mx, my);

        if (isHost) {
            // Host can start with at least 1 player (themselves)
            drawBtn("START GAME →", W / 2.0 + 90, 55, 110, 26, mx, my);
        }
    }

    // ── Render: CONNECTING ────────────────────────────────────────────────────────

    private static void renderConnecting() {
        long elapsed = System.currentTimeMillis() - startMs;
        int dots = (int)((elapsed / 400) % 4);
        String dotStr = ".".repeat(dots);

        StdDraw.setPenColor(COL_TEXT);
        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 18));
        StdDraw.text(W / 2.0, H / 2.0, "Connecting" + dotStr);
    }

    // ── Render: ERROR ─────────────────────────────────────────────────────────────

    private static void renderError() {
        double mx = StdDraw.mouseX(), my = StdDraw.mouseY();

        StdDraw.setPenColor(COL_WARN);
        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 18));
        StdDraw.text(W / 2.0, H / 2.0 + 60, "CONNECTION ERROR");

        StdDraw.setPenColor(COL_DIM);
        StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 12));
        StdDraw.text(W / 2.0, H / 2.0 + 20, errorMsg);

        drawBtn("← BACK", W / 2.0, 140, 120, 26, mx, my);
    }

    // ── Network helpers ───────────────────────────────────────────────────────────

    private static void connectToLobbyServer() {
        if (lobbyClient != null && lobbyClient.isConnected()) return;

        String host = ipBuf.length() > 0 ? ipBuf.toString() : LOBBY_SERVER;
        startMs = System.currentTimeMillis();

        try {
            lobbyClient = new LobbyClient(host, playerNameBuf.toString());
            lobbyClient.connect();
        } catch (Exception e) {
            errorMsg = "Could not reach lobby server at " + host;
            lobbyClient = null;
            state = STATE_ERROR;
        }
    }

    // ── UI primitives ─────────────────────────────────────────────────────────────

    private static void header(String text, double cy) {
        // Shadow
        StdDraw.setPenColor(new Color(0, 80, 30, 120));
        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 28));
        StdDraw.text(W / 2.0 + 2, cy - 2, text);
        // Main
        StdDraw.setPenColor(COL_TEXT);
        StdDraw.text(W / 2.0, cy, text);

        // Underline
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(0.001);
        double hw = text.length() * 8.5;
        StdDraw.line(W / 2.0 - hw, cy - 18, W / 2.0 + hw, cy - 18);
    }

    private static void small(String text, double cx, double cy, Color color) {
        StdDraw.setPenColor(color);
        StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 11));
        StdDraw.text(cx, cy, text);
    }

    private static void inputBox(String content, double cx, double cy, double bw, boolean active) {
        double bh = 28;
        StdDraw.setPenColor(active ? new Color(0, 20, 8) : COL_BTN);
        StdDraw.filledRectangle(cx, cy, bw / 2, bh / 2);
        StdDraw.setPenColor(active ? COL_BORDER : COL_MUTED);
        StdDraw.setPenRadius(0.002);
        StdDraw.rectangle(cx, cy, bw / 2, bh / 2);

        long blink = (System.currentTimeMillis() / 500) % 2;
        String display = active ? (content + (blink == 0 ? "|" : " ")) : content;
        StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 13));
        StdDraw.setPenColor(COL_TEXT);
        StdDraw.text(cx, cy, display);
    }

    private static void drawBtn(String label, double cx, double cy, double w, double h,
                                double mx, double my) {
        drawBtn(label, cx, cy, w, h, mx, my, COL_SELECTED, COL_BTN_HOV);
    }

    private static void drawBtn(String label, double cx, double cy, double w, double h,
                                double mx, double my, Color textColor, Color hoverBg) {
        boolean hover = hitBtn(mx, my, cx, cy, w, h);
        StdDraw.setPenColor(hover ? hoverBg : COL_BTN);
        StdDraw.filledRectangle(cx, cy, w / 2, h / 2);
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(0.002);
        StdDraw.rectangle(cx, cy, w / 2, h / 2);
        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 12));
        StdDraw.setPenColor(hover ? textColor : COL_DIM);
        StdDraw.text(cx, cy, label);
    }

    // ── Input helpers ─────────────────────────────────────────────────────────────

    private static void drainTyping(StringBuilder buf, int maxLen, boolean alphaNumOnly) {
        while (StdDraw.hasNextKeyTyped()) {
            char c = StdDraw.nextKeyTyped();
            if (c == '\b' || c == 127) {
                if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
            } else if (buf.length() < maxLen && isValidChar(c, alphaNumOnly)) {
                buf.append(c);
            }
        }
    }

    private static boolean isValidChar(char c, boolean alphaNumOnly) {
        if (alphaNumOnly) return java.lang.Character.isLetterOrDigit(c) || c == '_' || c == '-';
        return c >= 0x20 && c < 0x7F;
    }

    private static boolean hitBtn(double mx, double my, double cx, double cy, double w, double h) {
        return Math.abs(mx - cx) < w / 2.0 && Math.abs(my - cy) < h / 2.0;
    }

    private static void waitRelease() {
        while (StdDraw.isMousePressed()) StdDraw.pause(10);
        mouseWasDown = false;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}