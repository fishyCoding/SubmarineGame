import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * RadarScreen — a top-right HUD panel that displays detected submarine contacts.
 *
 * When the player fires a radar ping (R key), any remote submarines whose
 * positions were captured at ping time appear as blips on this screen.
 * The blips fade out in sync with the rock radar outlines.
 * * Only contacts that have a clear line of sight to the player are displayed.
 *
 * Layout (screen-space, top-right corner):
 * - Dark green circular screen with grid lines
 * - Player's sub always at centre
 * - Each contact shown as a bright green dot + small label
 * - Sweep line that fades with pingAlpha
 */
public class RadarScreen {

    private static final int MARGIN   = 14;   // pixels from right / top edge
    private static final int RADIUS   = 90;   // screen-radius of the circular display
    private static final int DIAMETER = RADIUS * 2;
    private static final int SAMPLES = 36; // Number of raymarch samples for line-of-sight checks


    // World range represented by the full radar radius
    private static final float WORLD_RADIUS = 2000f;

    private static final Color COL_BG        = new Color(  3, 14,  6);
    private static final Color COL_BORDER    = new Color( 20, 80, 35);
    private static final Color COL_GRID      = new Color( 10, 45, 18);
    private static final Color COL_SWEEP     = new Color(  0,180, 70);
    private static final Color COL_PLAYER    = new Color( 80,255,120);
    private static final Color COL_CONTACT   = new Color(  0,255, 80);
    private static final Color COL_EDGE      = new Color(  0,160, 50);
    private static final Color COL_LABEL     = new Color(  0,200, 60);
    private static final Color COL_TITLE     = new Color(  0,180, 70);

    /**
     * Draw the radar screen.
     *
     * @param screenW    canvas width  (pixels)
     * @param screenH    canvas height (pixels)
     * @param playerX    player world X
     * @param playerY    player world Y
     * @param pingAlpha  0 = no ping / fully faded, 1 = fresh ping
     * @param contacts   map of playerId → float[]{worldX, worldY} captured at ping time
     * @param rocks      list of rocks currently in the world for line-of-sight occlusion
     */
    public static void draw(int screenW, int screenH,
                            float playerX, float playerY,
                            float pingAlpha,
                            Map<String, float[]> contacts,
                            List<Rock> rocks) {

        // Panel centre in screen coords (top-right corner)
        double cx = screenW - MARGIN - RADIUS;
        double cy = screenH - MARGIN - RADIUS;

        // ── Background circle ──────────────────────────────────────────────────
        StdDraw.setPenColor(COL_BG);
        StdDraw.filledCircle(cx, cy, RADIUS);

        // ── Grid: two concentric rings + crosshairs ────────────────────────────
        StdDraw.setPenColor(COL_GRID);
        StdDraw.setPenRadius(0.001);
        StdDraw.circle(cx, cy, RADIUS * 0.5);
        StdDraw.circle(cx, cy, RADIUS);
        // Crosshair
        StdDraw.line(cx - RADIUS, cy, cx + RADIUS, cy);
        StdDraw.line(cx, cy - RADIUS, cx, cy + RADIUS);

        // ── Border ─────────────────────────────────────────────────────────────
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(0.003);
        StdDraw.circle(cx, cy, RADIUS);
        StdDraw.setPenRadius(0.002);

        // ── Sweep line — only visible when a ping is fresh ─────────────────────
        if (pingAlpha > 0f) {
            double sweepAngle = pingAlpha * Math.PI * 2.0;   // clockwise
            double ex = cx + Math.cos(-sweepAngle) * RADIUS;
            double ey = cy + Math.sin(-sweepAngle) * RADIUS;

            int sweepA = Math.min(255, (int)(pingAlpha * 255));
            StdDraw.setPenColor(new Color(COL_SWEEP.getRed(),
                                          COL_SWEEP.getGreen(),
                                          COL_SWEEP.getBlue(), sweepA));
            StdDraw.setPenRadius(0.003);
            StdDraw.line(cx, cy, ex, ey);
            StdDraw.setPenRadius(0.002);

            // Faint trailing arc
            for (int i = 1; i <= 8; i++) {
                double trailAngle = sweepAngle + i * 0.08;
                double tex = cx + Math.cos(-trailAngle) * RADIUS;
                double tey = cy + Math.sin(-trailAngle) * RADIUS;
                int trailA = Math.max(0, sweepA - i * 28);
                StdDraw.setPenColor(new Color(0, 150, 50, trailA));
                StdDraw.setPenRadius(0.0015);
                StdDraw.line(cx, cy, tex, tey);
            }
            StdDraw.setPenRadius(0.002);
        }

        // ── Player dot (always centred) ────────────────────────────────────────
        StdDraw.setPenColor(COL_PLAYER);
        StdDraw.filledCircle(cx, cy, 3.5);

        // ── Contacts — only shown/faded when a ping is active ─────────────────
        if (pingAlpha > 0f && contacts != null && !contacts.isEmpty()) {
            int contactA = Math.min(255, (int)(pingAlpha * 255));

            for (Map.Entry<String, float[]> entry : contacts.entrySet()) {
                float[] pos = entry.getValue();
                
                // --- LINE OF SIGHT CHECK ---
                // If a rock blocks the path between the player and the contact, do not render this blip
                if (!hasLineOfSight(playerX, playerY, pos[0], pos[1], rocks)) {
                    continue;
                }

                float dx = pos[0] - playerX;
                float dy = pos[1] - playerY;

                // Scale world offset → radar screen offset
                double scale  = (double) RADIUS / WORLD_RADIUS;
                double sdx    = dx * scale;
                double sdy    = dy * scale;   // world Y up = screen Y up in StdDraw

                double dist = Math.sqrt(sdx * sdx + sdy * sdy);
                boolean clamped = dist > RADIUS - 4;

                if (clamped) {
                    // Clamp to edge ring
                    double norm = (RADIUS - 4) / dist;
                    sdx *= norm;
                    sdy *= norm;
                }

                double bx = cx + sdx;
                double by = cy + sdy;

                // Outer glow
                StdDraw.setPenColor(new Color(0, contactA / 4, 0));
                StdDraw.filledCircle(bx, by, clamped ? 6 : 7);

                // Bright blip
                Color blipCol = clamped ? COL_EDGE : COL_CONTACT;
                StdDraw.setPenColor(new Color(blipCol.getRed(),
                                              blipCol.getGreen(),
                                              blipCol.getBlue(), contactA));
                StdDraw.filledCircle(bx, by, clamped ? 3 : 4);

                // Label (short id, truncated)
                if (!clamped) {
                    String label = entry.getKey();
                    if (label.length() > 8) label = label.substring(0, 8);
                    StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 8));
                    StdDraw.setPenColor(new Color(COL_LABEL.getRed(),
                                                  COL_LABEL.getGreen(),
                                                  COL_LABEL.getBlue(), contactA));
                    StdDraw.textLeft(bx + 5, by + 5, label);
                }
            }
        }

        // ── "No contacts" message when panel is idle ───────────────────────────
        if (pingAlpha <= 0f) {
            StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 9));
            StdDraw.setPenColor(new Color(0, 60, 25));
            StdDraw.text(cx, cy, "NO SIGNAL");
        }

        // ── Title label above panel ────────────────────────────────────────────
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 10));
        StdDraw.setPenColor(COL_TITLE);
        StdDraw.text(cx, cy + RADIUS + 10, "ACTIVE SONAR");

        StdDraw.setPenRadius(0.002);
    }

    /**
     * Checks if there is a clear line of sight between two world points using raymarching.
     */
    private static boolean hasLineOfSight(float x1, float y1, float x2, float y2, List<Rock> rocks) {
        for (int i = 1; i < SAMPLES; i++) {
            float t  = (float) i / SAMPLES;
            float px = x1 + t * (x2 - x1);
            float py = y1 + t * (y2 - y1);
            for (Rock r : rocks) {
                if (r.contains(px, py)) {
                    return false;
                }
            }
        }
        return true;
    }
}