import java.util.List;
import java.awt.Color;
public class Radar {
    private static void drawRadarOutlines(float alpha, GameEngine engine, Rock bottomLayer) {
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
}
