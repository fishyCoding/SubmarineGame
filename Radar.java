import java.awt.Color;
import java.util.List;

public class Radar {

    public static void drawRadarOutlines(float alpha, GameEngine engine) {
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock)) continue;
            Rock rock = (Rock) s;
            if (rock.getDepth() == 0) continue;  // Only foreground rocks

            int a = Math.min(255, (int)(alpha * 255));
            drawRockRadarOutline(rock, engine, a);
        }
    }


    private static void drawRockRadarOutline(Rock rock, GameEngine engine, int alpha) {
        List<Float> vertices = rock.getVertices();
        
        StdDraw.setPenColor(new Color(0, 255, 0, alpha));
        StdDraw.setPenRadius(0.002);

        // draw on the ones on the outside so we see the polygon
        for (int i = 0; i < vertices.size(); i += 2) {
            float x = vertices.get(i);
            float y = vertices.get(i + 1);
            double sx = engine.worldToScreenX(x);
            double sy = engine.worldToScreenY(y);
            StdDraw.point(sx, sy);
        }
    }
}