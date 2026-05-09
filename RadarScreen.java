import java.awt.Color;
import java.util.List;
import java.util.Map;

public class RadarScreen {

    private static final int MARGIN = 14;
    private static final int RADIUS = 90;
    private static final int DIAMETER = RADIUS * 2;
    private static final int SAMPLES = 36;

    private static final float WORLD_RADIUS = 3000f;
    private static final float BLAST_RADIUS = 150f;

    private static final Color COL_BG = Color.decode("#030E06");
    private static final Color COL_BORDER = Color.decode("#145023");
    private static final Color COL_GRID = Color.decode("#0A2D12");
    private static final Color COL_SWEEP = Color.decode("#00B446");
    private static final Color COL_CONTACT = Color.decode("#00FF50");
    private static final Color COL_EDGE = Color.decode("#00A032");
    private static final Color COL_LABEL = Color.decode("#00c83c");
    private static final Color COL_TITLE = Color.decode("#00B446");

    public static void draw(int screenW, int screenH, float playerX, float playerY, float pingAlpha, Map<String, float[]> contacts, List<Rock> rocks, float[] torpedoPos, BottomRockLayer bottomLayer, List<float[]> remoteTorpedoPositions, float[] selectedContact) {
        double cx = screenW - MARGIN - RADIUS;
        double cy = screenH - MARGIN - RADIUS;

        // background circle
        StdDraw.setPenColor(COL_BG);
        StdDraw.filledCircle(cx, cy, RADIUS);

        // grid: two concentric rings + crosshairs
        StdDraw.setPenColor(COL_GRID);
        StdDraw.setPenRadius(0.001);
        StdDraw.circle(cx, cy, RADIUS * 0.5);
        StdDraw.circle(cx, cy, RADIUS);
        StdDraw.line(cx - RADIUS, cy, cx + RADIUS, cy);
        StdDraw.line(cx, cy - RADIUS, cx, cy + RADIUS);

        // border
        StdDraw.setPenColor(COL_BORDER);
        StdDraw.setPenRadius(0.003);
        StdDraw.circle(cx, cy, RADIUS);
        StdDraw.setPenRadius(0.002);

        double radarScale = (double) RADIUS / WORLD_RADIUS;

        // blast radius reference ring
        StdDraw.setPenColor(COL_GRID);
        StdDraw.circle(cx, cy, radarScale * BLAST_RADIUS);

        drawRockMinimap(rocks, playerX, playerY, cx, cy, radarScale);
        if (bottomLayer != null)
            drawSeafloorMinimap(bottomLayer, playerX, playerY, cx, cy, radarScale);

        // sweep line — only visible when a ping is fresh
        if (pingAlpha > 0f) {
            double sweepAngle = pingAlpha * Math.PI * 2.0;
            double ex = cx + Math.cos(-sweepAngle) * RADIUS;
            double ey = cy + Math.sin(-sweepAngle) * RADIUS;

            int sweepA = Math.min(255, (int)(pingAlpha * 255));
            StdDraw.setPenColor(new Color(COL_SWEEP.getRed(), COL_SWEEP.getGreen(), COL_SWEEP.getBlue(), sweepA));
            StdDraw.setPenRadius(0.003);
            StdDraw.line(cx, cy, ex, ey);
            StdDraw.setPenRadius(0.002);

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

        // player dot
        StdDraw.setPenColor(new Color(0, 255, 0));
        StdDraw.filledCircle(cx, cy, 1.5);

        // contacts — only shown during a ping
        if (pingAlpha > 0f && contacts != null && !contacts.isEmpty()) {
            int contactA = Math.min(255, (int)(pingAlpha * 255));

            for (Map.Entry<String, float[]> entry : contacts.entrySet()) {
                float[] pos = entry.getValue();

                if (!hasLineOfSight(playerX, playerY, pos[0], pos[1], rocks)) continue;

                float dx = pos[0] - playerX;
                float dy = pos[1] - playerY;

                double scale = (double) RADIUS / WORLD_RADIUS;
                double sdx = dx * scale;
                double sdy = dy * scale;

                double dist = Math.sqrt(sdx * sdx + sdy * sdy);
                boolean clamped = dist > RADIUS - 4;

                if (clamped) {
                    double norm = (RADIUS - 4) / dist;
                    sdx *= norm;
                    sdy *= norm;
                }

                double bx = cx + sdx;
                double by = cy + sdy;

                StdDraw.setPenColor(new Color(0, contactA / 4, 0));
                StdDraw.filledCircle(bx, by, clamped ? 6 : 7);

                Color blipCol = clamped ? COL_EDGE : COL_CONTACT;
                StdDraw.setPenColor(new Color(blipCol.getRed(), blipCol.getGreen(), blipCol.getBlue(), contactA));
                StdDraw.filledCircle(bx, by, clamped ? 3 : 4);

                if (!clamped) {
                    String label = entry.getKey();
                    if (label.length() > 8) label = label.substring(0, 8);
                    StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 8));
                    StdDraw.setPenColor(new Color(COL_LABEL.getRed(), COL_LABEL.getGreen(), COL_LABEL.getBlue(), contactA));
                    StdDraw.textLeft(bx + 5, by + 5, label);
                }
            }
        }

        // torpedo blip — yellow triangle, always shown while torpedo is alive
        if (torpedoPos != null) {
            float tdx = torpedoPos[0] - playerX;
            float tdy = torpedoPos[1] - playerY;
            double scale = (double) RADIUS / WORLD_RADIUS;
            double sdx = tdx * scale;
            double sdy = tdy * scale;
            double dist = Math.sqrt(sdx * sdx + sdy * sdy);
            if (dist > RADIUS - 4) {
                double norm = (RADIUS - 4) / dist;
                sdx *= norm;
                sdy *= norm;
            }
            double tx = cx + sdx;
            double ty = cy + sdy;

            double ang = Math.atan2(sdy, sdx);
            double size = 1.0;
            double[] trx = {
                tx + Math.cos(ang)       * size * 2,
                tx + Math.cos(ang + 2.4) * size,
                tx + Math.cos(ang - 2.4) * size
            };
            double[] try_ = {
                ty + Math.sin(ang)       * size * 2,
                ty + Math.sin(ang + 2.4) * size,
                ty + Math.sin(ang - 2.4) * size
            };
            StdDraw.setPenColor(new Color(255, 220, 0));
            StdDraw.filledPolygon(trx, try_);
            StdDraw.setPenColor(new Color(200, 160, 0));
            StdDraw.setPenRadius(0.001);
            StdDraw.polygon(trx, try_);
            StdDraw.setPenRadius(0.002);

            // blast radius ring centered on torpedo, only when a contact is selected
if (selectedContact != null) {
    // distance from torpedo to selected contact in world units
    float distToContact = (float) Math.sqrt(
        Math.pow(selectedContact[0] - torpedoPos[0], 2) +
        Math.pow(selectedContact[1] - torpedoPos[1], 2)
    );

    if (distToContact <=1250f) {
        double radarDist = distToContact * scale;
        StdDraw.setPenColor(new Color(255, 220, 0, 40));
        StdDraw.filledCircle(tx, ty, radarDist);
        StdDraw.setPenColor(new Color(255, 220, 0, 140));
        StdDraw.setPenRadius(0.001);
        StdDraw.circle(tx, ty, radarDist);
        StdDraw.setPenRadius(0.002);
    }
}
        }

        // remote torpedo blips — red triangles, only shown during a ping
        if (pingAlpha > 0f && remoteTorpedoPositions != null) {
            for (float[] rtp : remoteTorpedoPositions) {
                float tdx = rtp[0] - playerX;
                float tdy = rtp[1] - playerY;
                double scale = (double) RADIUS / WORLD_RADIUS;
                double sdx = tdx * scale;
                double sdy = tdy * scale;
                double dist = Math.sqrt(sdx * sdx + sdy * sdy);
                if (dist > RADIUS - 4) {
                    double norm = (RADIUS - 4) / dist;
                    sdx *= norm;
                    sdy *= norm;
                }
                double tx = cx + sdx;
                double ty = cy + sdy;

                double ang = Math.atan2(sdy, sdx);
                double size = 1.0;
                double[] trx = {
                    tx + Math.cos(ang)       * size * 2,
                    tx + Math.cos(ang + 2.4) * size,
                    tx + Math.cos(ang - 2.4) * size
                };
                double[] try_ = {
                    ty + Math.sin(ang)       * size * 2,
                    ty + Math.sin(ang + 2.4) * size,
                    ty + Math.sin(ang - 2.4) * size
                };
                int torpA = Math.min(255, (int)(pingAlpha * 255));
                StdDraw.setPenColor(new Color(255, 40, 40, torpA));
                StdDraw.filledPolygon(trx, try_);
                StdDraw.setPenColor(new Color(180, 0, 0, torpA));
                StdDraw.setPenRadius(0.001);
                StdDraw.polygon(trx, try_);
                StdDraw.setPenRadius(0.002);
            }
        }

        // title label
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 10));
        StdDraw.setPenColor(COL_TITLE);
        StdDraw.text(cx, cy + RADIUS + 10, "ACTIVE SONAR");
        StdDraw.setPenRadius(0.002);
    }

    // ── Minimap helpers ──────────────────────────────────────────────────────────

    private static void drawRockMinimap(List<Rock> rocks, float playerX, float playerY, double cx, double cy, double scale) {
        if (rocks == null) return;
        StdDraw.setPenRadius(0.0012);
        for (Rock rock : rocks) {
            java.util.List<Float> verts = rock.getVertices();
            int count = verts.size() / 2;
            if (count < 2) continue;
            if (rock.getDepth() == 1) {
                StdDraw.setPenColor(Color.decode("#8b8b8b"));
                float rockWX = rock.getX();
                float rockWY = rock.getY();
                for (int i = 0; i < count; i++) {
                    int ni = (i + 1) % count;
                    float ax = rockWX + verts.get(i * 2);
                    float ay = rockWY + verts.get(i * 2 + 1);
                    float bx = rockWX + verts.get(ni * 2);
                    float by = rockWY + verts.get(ni * 2 + 1);
                    double rax = (ax - playerX) * scale;
                    double ray = (ay - playerY) * scale;
                    double rbx = (bx - playerX) * scale;
                    double rby = (by - playerY) * scale;
                    drawClippedSegment(cx, cy, rax, ray, rbx, rby, RADIUS - 1.0);
                }
            }
        }
        StdDraw.setPenRadius(0.002);
    }

    private static void drawSeafloorMinimap(BottomRockLayer floor, float playerX, float playerY, double cx, double cy, double scale) {
        StdDraw.setPenColor(Color.decode("#8b8b8b"));
        StdDraw.setPenRadius(0.0015);
        int n = BottomRockLayer.NUM_POINTS;
        for (int i = 0; i < n - 1; i++) {
            float ax = floor.getPointWorldX(i);
            float ay = floor.getPointWorldY(i);
            float bx = floor.getPointWorldX(i + 1);
            float by = floor.getPointWorldY(i + 1);
            double rax = (ax - playerX) * scale;
            double ray = (ay - playerY) * scale;
            double rbx = (bx - playerX) * scale;
            double rby = (by - playerY) * scale;
            drawClippedSegment(cx, cy, rax, ray, rbx, rby, RADIUS - 1.0);
        }
        StdDraw.setPenRadius(0.002);
    }

    private static void drawClippedSegment(double cx, double cy,
                                           double rax, double ray,
                                           double rbx, double rby,
                                           double r) {
        double r2 = r * r;
        boolean aIn = rax * rax + ray * ray <= r2;
        boolean bIn = rbx * rbx + rby * rby <= r2;

        if (!aIn && !bIn) {
            double dx = rbx - rax, dy = rby - ray;
            double fDot = rax * dx + ray * dy;
            double dLen2 = dx * dx + dy * dy;
            if (dLen2 == 0) return;
            double tClosest = -fDot / dLen2;
            if (tClosest < 0 || tClosest > 1) return;
            double closestDist2 = (rax + tClosest * dx) * (rax + tClosest * dx)
                                + (ray + tClosest * dy) * (ray + tClosest * dy);
            if (closestDist2 > r2) return;
        }

        if (aIn && bIn) {
            StdDraw.line(cx + rax, cy + ray, cx + rbx, cy + rby);
            return;
        }

        double dx = rbx - rax, dy = rby - ray;
        double a = dx * dx + dy * dy;
        double b = 2 * (rax * dx + ray * dy);
        double c = rax * rax + ray * ray - r2;
        double disc = b * b - 4 * a * c;
        if (disc < 0) return;
        double sqrtDisc = Math.sqrt(disc);
        double t0 = (-b - sqrtDisc) / (2 * a);
        double t1 = (-b + sqrtDisc) / (2 * a);
        double tStart = Math.max(0.0, Math.min(t0, t1));
        double tEnd = Math.min(1.0, Math.max(t0, t1));
        if (tStart > tEnd) return;

        StdDraw.line(cx + rax + tStart * dx, cy + ray + tStart * dy,
                     cx + rax + tEnd   * dx, cy + ray + tEnd   * dy);
    }

    private static boolean hasLineOfSight(float x1, float y1, float x2, float y2, List<Rock> rocks) {
        for (int i = 1; i < SAMPLES; i++) {
            float t = (float) i / SAMPLES;
            float px = x1 + t * (x2 - x1);
            float py = y1 + t * (y2 - y1);
            for (Rock r : rocks) {
                if (r.contains(px, py)) return false;
            }
        }
        return true;
    }
}