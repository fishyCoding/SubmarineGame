import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;

// Launch menu. Returns args to pass to Game.main().
// Modes: solo, host server, join server (with IP entry).
public class Menu {

    private static final int W = 600;
    private static final int H = 400;

    private static final Color COL_BG       = Color.decode("#030E06");
    private static final Color COL_BORDER   = Color.decode("#145023");
    private static final Color COL_TEXT     = Color.decode("#00FF50");
    private static final Color COL_DIM      = Color.decode("#00A032");
    private static final Color COL_SELECTED = Color.decode("#00FF50");
    private static final Color COL_BTN      = Color.decode("#0A2D12");
    private static final Color COL_BTN_HOV  = Color.decode("#144D22");

    // menu states
    private static final int STATE_MAIN  = 0;
    private static final int STATE_JOIN  = 1;

    private static int state = STATE_MAIN;

    // IP input for join screen
    private static StringBuilder ipInput = new StringBuilder("localhost");
    private static long lastKeyTime = 0;
    private static final long KEY_REPEAT_MS = 80;

    public static void main(String[] args) {
        StdDraw.setCanvasSize(W, H);
        StdDraw.setXscale(0, W);
        StdDraw.setYscale(0, H);
        StdDraw.enableDoubleBuffering();
        StdDraw.setTitle("Submarine Game");

        String[] result = null;
        while (result == null) {
            result = handleInput();
            render();
            StdDraw.show();
            StdDraw.pause(16);
        }

        // launch game with chosen args
        Game.main(result);
    }

    private static String[] handleInput() {
        double mx = StdDraw.mouseX();
        double my = StdDraw.mouseY();
        boolean clicked = StdDraw.isMousePressed();

        if (state == STATE_MAIN) {
            // Solo button: centered at (W/2, 230)
            if (clicked && hitButton(mx, my, W / 2.0, 230, 140, 28)) {
                waitRelease();
                return new String[]{"--solo"};
            }
            // Host button: centered at (W/2, 175)
            if (clicked && hitButton(mx, my, W / 2.0, 175, 140, 28)) {
                waitRelease();
                return new String[]{"--host"};
            }
            // Join button: centered at (W/2, 120)
            if (clicked && hitButton(mx, my, W / 2.0, 120, 140, 28)) {
                waitRelease();
                state = STATE_JOIN;
            }
        } else if (state == STATE_JOIN) {
            // handle typing for IP field
            handleTyping();

            // Connect button: centered at (W/2, 130)
            if (clicked && hitButton(mx, my, W / 2.0, 130, 140, 28)) {
                waitRelease();
                return new String[]{"--join", ipInput.toString()};
            }
            // Back button: centered at (W/2, 80)
            if (clicked && hitButton(mx, my, W / 2.0, 80, 140, 28)) {
                waitRelease();
                state = STATE_MAIN;
            }
        }
        return null;
    }

    private static void handleTyping() {
        long now = System.currentTimeMillis();
        if (now - lastKeyTime < KEY_REPEAT_MS) return;

        // backspace
        if (StdDraw.isKeyPressed(KeyEvent.VK_BACK_SPACE)) {
            if (ipInput.length() > 0)
                ipInput.deleteCharAt(ipInput.length() - 1);
            lastKeyTime = now;
            return;
        }

        // allowed chars: 0-9, a-z, A-Z, dot, hyphen
        int[] keyCodes = {
            '0','1','2','3','4','5','6','7','8','9',
            'A','B','C','D','E','F','G','H','I','J','K','L','M',
            'N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
            KeyEvent.VK_PERIOD, KeyEvent.VK_MINUS
        };
        char[] chars = {
            '0','1','2','3','4','5','6','7','8','9',
            'a','b','c','d','e','f','g','h','i','j','k','l','m',
            'n','o','p','q','r','s','t','u','v','w','x','y','z',
            '.', '-'
        };

        for (int i = 0; i < keyCodes.length; i++) {
            if (StdDraw.isKeyPressed(keyCodes[i]) && ipInput.length() < 40) {
                ipInput.append(chars[i]);
                lastKeyTime = now;
                return;
            }
        }
    }

    private static void render() {
        // background
        StdDraw.setPenColor(COL_BG);
        StdDraw.filledRectangle(W / 2.0, H / 2.0, W / 2.0, H / 2.0);

        // border
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(0.003);
        StdDraw.rectangle(W / 2.0, H / 2.0, W / 2.0 - 10, H / 2.0 - 10);
        StdDraw.setPenRadius(0.002);

        if (state == STATE_MAIN) renderMain();
        else                     renderJoin();
    }

    private static void renderMain() {
        double mx = StdDraw.mouseX();
        double my = StdDraw.mouseY();

        // title
        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 32));
        StdDraw.setPenColor(COL_TEXT);
        StdDraw.text(W / 2.0, 320, "SUBMARINE");
        StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 13));
        StdDraw.setPenColor(COL_DIM);
        StdDraw.text(W / 2.0, 290, "select game mode");

        drawButton("SOLO",          W / 2.0, 230, 140, 28, mx, my);
        drawButton("HOST SERVER",   W / 2.0, 175, 140, 28, mx, my);
        drawButton("JOIN SERVER",   W / 2.0, 120, 140, 28, mx, my);
    }

    private static void renderJoin() {
        double mx = StdDraw.mouseX();
        double my = StdDraw.mouseY();

        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 20));
        StdDraw.setPenColor(COL_TEXT);
        StdDraw.text(W / 2.0, 310, "JOIN SERVER");

        StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 12));
        StdDraw.setPenColor(COL_DIM);
        StdDraw.text(W / 2.0, 260, "enter server IP");

        // IP input box
        double boxW = 200, boxH = 20;
        double boxX = W / 2.0, boxY = 210;
        StdDraw.setPenColor(COL_BTN);
        StdDraw.filledRectangle(boxX, boxY, boxW / 2, boxH / 2);
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(0.002);
        StdDraw.rectangle(boxX, boxY, boxW / 2, boxH / 2);

        StdDraw.setFont(new Font("Monospaced", Font.PLAIN, 12));
        StdDraw.setPenColor(COL_TEXT);
        // blinking cursor
        long blink = (System.currentTimeMillis() / 500) % 2;
        String display = ipInput.toString() + (blink == 0 ? "|" : " ");
        StdDraw.text(boxX, boxY, display);

        drawButton("CONNECT", W / 2.0, 130, 140, 28, mx, my);
        drawButton("BACK",    W / 2.0, 80,  140, 28, mx, my);
    }

    private static void drawButton(String label, double cx, double cy, double w, double h, double mx, double my) {
        boolean hover = hitButton(mx, my, cx, cy, w, h);
        StdDraw.setPenColor(hover ? COL_BTN_HOV : COL_BTN);
        StdDraw.filledRectangle(cx, cy, w / 2, h / 2);
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(hover ? 0.003 : 0.002);
        StdDraw.rectangle(cx, cy, w / 2, h / 2);
        StdDraw.setPenRadius(0.002);
        StdDraw.setFont(new Font("Monospaced", Font.BOLD, 13));
        StdDraw.setPenColor(hover ? COL_SELECTED : COL_DIM);
        StdDraw.text(cx, cy, label);
    }

    private static boolean hitButton(double mx, double my, double cx, double cy, double w, double h) {
        return Math.abs(mx - cx) < w / 2 && Math.abs(my - cy) < h / 2;
    }

    // wait for mouse release so a click doesn't carry over to the next screen
    private static void waitRelease() {
        while (StdDraw.isMousePressed()) {
            StdDraw.pause(16);
        }
    }
}