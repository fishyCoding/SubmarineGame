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

        // Draws every single permutation (or is it combination i have no clue)
        //basic double loop to avoid redrawing, not sure if this is that necessary
        for (int i = 0; i < vertices.size(); i += 2) {
            double v1X = engine.worldToScreenX(rock.getX() + vertices.get(i));
            double v1Y = engine.worldToScreenY(rock.getY() + vertices.get(i + 1));

            for (int j = i + 2; j < vertices.size(); j += 2) {
                double v2X = engine.worldToScreenX(rock.getX() + vertices.get(j));
                double v2Y = engine.worldToScreenY(rock.getY() + vertices.get(j + 1));

                StdDraw.line(v1X, v1Y, v2X, v2Y);
            }
        }
    }

}