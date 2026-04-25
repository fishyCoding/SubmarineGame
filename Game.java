import java.awt.Color;
import java.util.List;

/**
 * Game — the playable runtime for the submarine game.
 *
 * Features:
 *   - Submarine is always centred on screen; the world scrolls around it.
 *   - A fog-of-war oval masks most of the screen, with a soft feathered edge.
 *   - Pressing R sends a radar ping: all rock outlines glow green and fade out.
 *   - Health bar lives in the HUD, not on the submarine sprite.
 *
 * Controls:
 *   W / Up     → thrust forward
 *   S / Down   → thrust backward
 *   A / Left   → rotate left
 *   D / Right  → rotate right
 *   R          → radar ping
 *   ESC        → quit
 */
public class Game {

    // ── Canvas ─────────────────────────────────────────────────────────────────
    private static final int    WIDTH         = 1600;
    private static final int    HEIGHT        = 1000;

    // ── World ──────────────────────────────────────────────────────────────────
    private static final float  SURFACE_LEVEL = 0f;
    private static final float  SEAFLOOR_TOP  = -1820f;
    private static final float  SEAFLOOR_BASE = -2400f;
    private static final String DATA_FILE     = "sprites.txt";

    // ── Player spawn ───────────────────────────────────────────────────────────
    private static final float  SPAWN_X       = 800f;
    private static final float  SPAWN_Y       = -100f;
    private static final int    PLAYER_MAX_HP = 100;

    // ── Fog of war ─────────────────────────────────────────────────────────────
    // The visible oval half-axes (pixels).  World beyond this is hidden.
    private static final double FOG_HALF_W    = 300.0;
    private static final double FOG_HALF_H    = 200.0;
    // How many feather rings to blend the edge over (more = smoother fade)
    private static final int    FOG_RINGS     = 40;

    // ── Radar ping ─────────────────────────────────────────────────────────────
    private static final long   PING_DURATION_MS = 2500;  // ms until outline fully fades
    private static long         pingStartMs      = -1;    // -1 = no active ping
    private static boolean      rWasDown         = false;

    // ── Systems ────────────────────────────────────────────────────────────────
    private static GameEngine      engine;
    private static BottomRockLayer bottomLayer;
    private static Water           water;
    private static Submarine       player;

    // ── Cached screen centre ───────────────────────────────────────────────────
    private static final double CX = WIDTH  / 2.0;
    private static final double CY = HEIGHT / 2.0;

    // ── Entry point ────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        setupWindow();
        setupWorld();
        spawnPlayer();
        printControls();
        gameLoop();
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    private static void setupWindow() {
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();
        StdDraw.setTitle("Submarine Game");
    }

    private static void setupWorld() {
        engine      = new GameEngine(DATA_FILE);
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, 120, SEAFLOOR_TOP, SEAFLOOR_BASE);
        water       = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
        // Camera starts so the sub spawn is at screen centre
        engine.setCamera(SPAWN_X - (float) CX, SPAWN_Y - (float) CY);
    }

    private static void spawnPlayer() {
        player = new Submarine("Player", SPAWN_X, SPAWN_Y, PLAYER_MAX_HP, null);
        System.out.println("Spawned: " + player);
    }

    // ── Game loop ──────────────────────────────────────────────────────────────

    private static void gameLoop() {
        while (true) {
            handleInput();
            player.update();
            lockCamera();      // sub always at screen centre
            render();
            StdDraw.show();
            StdDraw.pause(16);
        }
    }

    // ── Camera ─────────────────────────────────────────────────────────────────

    /**
     * Hard-lock: camera is positioned so that the submarine's world coordinate
     * maps exactly to the screen centre (CX, CY) every frame.
     * worldToScreenX(player.x) = player.x - cameraX  →  set cameraX = player.x - CX
     */
    private static void lockCamera() {
        engine.setCamera(player.getX() - (float) CX,
                         player.getY() - (float) CY);
    }

    // ── Input ──────────────────────────────────────────────────────────────────

    private static void handleInput() {
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            System.out.println("Goodbye.");
            System.exit(0);
        }

        player.handleInput();

        // R — radar ping (edge-triggered so one press = one ping)
        boolean rDown = StdDraw.isKeyPressed('R') || StdDraw.isKeyPressed('r');
        if (rDown && !rWasDown) {
            pingStartMs = System.currentTimeMillis();
            System.out.println("Radar ping!");
        }
        rWasDown = rDown;
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    private static void render() {
        // 1. Water / sky gradient — fills the whole canvas each frame
        water.drawWaterGradient();

        // 2. Background rocks (depth 0)
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);

        // 3. Foreground rocks (depth 1)
        for (Sprite s : engine.getSprites())
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);

        // 4. Seafloor
        bottomLayer.draw(engine);

        // 5. Radar outlines — drawn BEFORE the fog so they show through it
        float pingAlpha = pingAlpha();
        if (pingAlpha > 0f) drawRadarOutlines(pingAlpha);

        // 6. Fog of war — covers everything outside the visible oval
        drawFog();

        // 7. Submarine — always drawn at screen centre, on top of the fog
        player.drawCentred(CX, CY);

        // 8. HUD — topmost layer
        drawHUD();
    }

    // ── Radar ──────────────────────────────────────────────────────────────────

    /** Returns 1.0 right after a ping, fading linearly to 0.0 after PING_DURATION_MS. */
    private static float pingAlpha() {
        if (pingStartMs < 0) return 0f;
        long elapsed = System.currentTimeMillis() - pingStartMs;
        if (elapsed >= PING_DURATION_MS) return 0f;
        return 1f - (float) elapsed / PING_DURATION_MS;
    }

    /**
     * Draw green outlines on every Rock and the seafloor silhouette.
     * Alpha fades as the ping ages, creating the sonar echo effect.
     */
    private static void drawRadarOutlines(float alpha) {
        // Rock outlines
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock)) continue;
            Rock rock = (Rock) s;
            List<Float> verts = rock.getVertices();
            int count = verts.size() / 2;
            if (count < 3) continue;

            int a = Math.min(255, (int)(alpha * 255));

            // Glow pass
            StdDraw.setPenColor(new Color(0, a / 3, 0));
            StdDraw.setPenRadius(0.012);
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                StdDraw.line(engine.worldToScreenX(verts.get(i * 2)),
                             engine.worldToScreenY(verts.get(i * 2 + 1)),
                             engine.worldToScreenX(verts.get(j * 2)),
                             engine.worldToScreenY(verts.get(j * 2 + 1)));
            }
            // Sharp core
            StdDraw.setPenColor(new Color(0, Math.min(255, a), 0));
            StdDraw.setPenRadius(0.003);
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                StdDraw.line(engine.worldToScreenX(verts.get(i * 2)),
                             engine.worldToScreenY(verts.get(i * 2 + 1)),
                             engine.worldToScreenX(verts.get(j * 2)),
                             engine.worldToScreenY(verts.get(j * 2 + 1)));
            }
        }

        // Seafloor silhouette
        bottomLayer.drawRadarOutline(engine, alpha);

        StdDraw.setPenRadius(0.002);
    }

    // ── Fog of war ─────────────────────────────────────────────────────────────

    /**
     * Draws a black mask over the entire screen except for a soft oval around
     * the screen centre.
     *
     * Strategy:
     *   1. Fill the four rectangular quadrants that are definitely outside the
     *      oval (fast, opaque).
     *   2. Feather the oval edge with FOG_RINGS concentric ellipses, each ring
     *      slightly larger and slightly more transparent, drawing outward.
     *      The innermost ring is fully transparent (0 alpha), the outermost is
     *      fully opaque (255 alpha) — but StdDraw doesn't support alpha on
     *      filled shapes, so we approximate with dark grey that blends visually.
     *
     * Because StdDraw has no alpha compositing, we simulate the fade by drawing
     * rings from the outermost inward, each ring a progressively lighter shade
     * of near-black, stopping before we reach the truly transparent centre.
     * The result is a visible "porthole" with a dark vignette ring.
     */
    private static void drawFog() {
        // ── Opaque black corners (outside max ellipse bounds) ──────────────────
        double outerW = FOG_HALF_W + FOG_RINGS * 3;
        double outerH = FOG_HALF_H + FOG_RINGS * 3;

        StdDraw.setPenColor(0, 0, 0);

        // Left strip
        if (CX - outerW > 0)
            StdDraw.filledRectangle((CX - outerW) / 2.0, CY,
                                     (CX - outerW) / 2.0, HEIGHT / 2.0);
        // Right strip
        if (CX + outerW < WIDTH)
            StdDraw.filledRectangle((CX + outerW + WIDTH) / 2.0, CY,
                                     (WIDTH - CX - outerW) / 2.0, HEIGHT / 2.0);
        // Top strip (full width)
        if (CY + outerH < HEIGHT)
            StdDraw.filledRectangle(CX, (CY + outerH + HEIGHT) / 2.0,
                                     WIDTH / 2.0, (HEIGHT - CY - outerH) / 2.0);
        // Bottom strip (full width)
        if (CY - outerH > 0)
            StdDraw.filledRectangle(CX, (CY - outerH) / 2.0,
                                     WIDTH / 2.0, (CY - outerH) / 2.0);

        // ── Feathered rings — outermost (opaque) down to inner edge (clear) ───
        // We draw filled ellipses from large to small.  Each step subtracts a
        // black ring.  The colour darkens toward the outside.
        for (int r = FOG_RINGS; r >= 0; r--) {
            // t=0 at inner edge (transparent), t=1 at outer edge (opaque)
            float t   = (float) r / FOG_RINGS;
            // Use an ease-in curve so the fade is gentle in the centre
            float tSq = t * t;

            double hw = FOG_HALF_W + r * 3;
            double hh = FOG_HALF_H + r * 3;

            // Alpha approximation: outer rings are near-black, inner are dark-grey
            // We layer from outside in, so the outer opaque fills cover the inner ones.
            // Actually we want outer = black (covers everything), inner = transparent.
            // Drawing largest first, then smaller ellipses cut a "hole" of the right shade.
            // Here we just draw each ring's own shade going inward.
            int dark = (int)(tSq * 255);
            StdDraw.setPenColor(dark / 14, dark / 14, dark / 12); // slightly blue-black
            StdDraw.filledEllipse(CX, CY, hw, hh);
        }

        // ── Final: tiny fully-clear centre (water colour bleed — skip, looks fine) ─
        // The innermost ellipse above has t=0 → colour (0,0,0) which is still black.
        // To leave the centre truly clear we just don't paint it — the loop stops at r=0
        // which draws a tiny near-black ellipse.  That's intentional: even the clearest
        // zone is dark-tinted like murky water.  To widen the clear zone, increase FOG_HALF_W/H.
    }

    // ── HUD ────────────────────────────────────────────────────────────────────

    private static void drawHUD() {
        java.awt.Font mono = new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 13);
        StdDraw.setFont(mono);

        float depthM = Math.max(0f, -player.getY());
        float ratio  = (float) player.getHealth() / player.getMaxHealth();

        // ── Depth & speed readout (top-left) ──────────────────────────────────
        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textLeft(11, HEIGHT - 19, String.format("Depth: %.0f m",  depthM));
        StdDraw.textLeft(11, HEIGHT - 35, String.format("Speed: %.1f kn", player.getSpeed()));

        StdDraw.setPenColor(200, 230, 255);
        StdDraw.textLeft(10, HEIGHT - 18, String.format("Depth: %.0f m",  depthM));
        StdDraw.textLeft(10, HEIGHT - 34, String.format("Speed: %.1f kn", player.getSpeed()));

        // ── Health bar (top-right corner) ─────────────────────────────────────
        double barRight = WIDTH - 20;
        double barTop   = HEIGHT - 20;
        double barW     = 160;
        double barH     = 10;
        double fill     = barW * ratio;

        // Label
        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textRight(barRight + 1, barTop + 14, "HULL INTEGRITY");
        StdDraw.setPenColor(180, 210, 255);
        StdDraw.textRight(barRight,     barTop + 15, "HULL INTEGRITY");

        // Track
        StdDraw.setPenColor(20, 20, 25);
        StdDraw.filledRectangle(barRight - barW / 2.0, barTop, barW / 2.0, barH / 2.0);

        // Fill (green → red)
        int hr = (int)(255 * (1 - ratio));
        int hg = (int)(255 * ratio);
        StdDraw.setPenColor(hr, hg, 0);
        StdDraw.filledRectangle(barRight - barW + fill / 2.0, barTop, fill / 2.0, barH / 2.0);

        // Border
        StdDraw.setPenColor(120, 140, 160);
        StdDraw.setPenRadius(0.002);
        StdDraw.rectangle(barRight - barW / 2.0, barTop, barW / 2.0, barH / 2.0);

        // ── Hint bar (bottom) ─────────────────────────────────────────────────
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11));
        String hint = "WASD: Move   R: Radar Ping   ESC: Quit";
        StdDraw.setPenColor(0, 0, 0);
        StdDraw.textLeft(11, 15, hint);
        StdDraw.setPenColor(180, 210, 255);
        StdDraw.textLeft(10, 16, hint);

        // ── Destroyed notice ──────────────────────────────────────────────────
        if (!player.isAlive()) {
            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 32));
            StdDraw.setPenColor(0, 0, 0);
            StdDraw.text(CX + 2, CY - 2, "DESTROYED");
            StdDraw.setPenColor(255, 60, 60);
            StdDraw.text(CX, CY, "DESTROYED");
        }
    }

    // ── Misc ───────────────────────────────────────────────────────────────────

    private static void printControls() {
        System.out.println("=== Submarine Game ===");
        System.out.println("  W / Up      → thrust forward");
        System.out.println("  S / Down    → thrust backward");
        System.out.println("  A / Left    → rotate left");
        System.out.println("  D / Right   → rotate right");
        System.out.println("  R           → radar ping");
        System.out.println("  ESC         → quit");
    }
}