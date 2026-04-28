import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Radar — sonar visualization for image-based rocks.
 *
 * Renders rock outlines on sonar display with green tint and transparency.
 * Uses rock1outline.png for the radar image representation.
 */
public class Radar {
    private static BufferedImage radarRockImage;
    private static final String RADAR_IMAGE_PATH = "rock1outline.png";

    static {
        try {
            File imageFile = new File(RADAR_IMAGE_PATH);
            if (imageFile.exists()) {
                radarRockImage = ImageIO.read(imageFile);
            } else {
                System.err.println("Warning: " + RADAR_IMAGE_PATH + " not found");
                radarRockImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
            }
        } catch (Exception e) {
            System.err.println("Error loading radar rock image: " + e.getMessage());
            radarRockImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        }
    }

    /**
     * Draw radar outlines for all foreground rocks.
     */
    public static void drawRadarOutlines(float alpha, GameEngine engine) {
        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock)) continue;
            Rock rock = (Rock) s;
            if (rock.getDepth() == 0) continue;  // Only foreground rocks

            int a = Math.min(255, (int)(alpha * 255));
            drawRockRadarOutline(rock, engine, a);
        }
    }

    /**
     * Draw a single rock's radar outline.
     */
    private static void drawRockRadarOutline(Rock rock, GameEngine engine, int alpha) {
        double screenX = engine.worldToScreenX(rock.getX());
        double screenY = engine.worldToScreenY(rock.getY());

        // Draw outer glow (very dim green)
        StdDraw.setPenColor(new Color(0, alpha / 3, 0));
        StdDraw.setPenRadius(0.012);
        StdDraw.point(screenX, screenY);

        // Draw bright green outline
        StdDraw.setPenColor(new Color(0, Math.min(255, alpha), 0));
        StdDraw.setPenRadius(0.003);
        StdDraw.point(screenX, screenY);

        // Draw rotation indicator line (shows orientation)
        double rotRad = Math.toRadians(rock.getRotation());
        double indicatorLength = 20;
        StdDraw.line(screenX, screenY,
                    screenX + indicatorLength * Math.cos(rotRad),
                    screenY - indicatorLength * Math.sin(rotRad));
    }

    /**
     * Draw a single rock's radar outline with image (for future enhancement).
     */
    private static void drawRockRadarImage(Rock rock, GameEngine engine, int alpha) {
        if (radarRockImage == null) return;

        double screenX = engine.worldToScreenX(rock.getX());
        double screenY = engine.worldToScreenY(rock.getY());

        // Draw simple circle as placeholder
        StdDraw.setPenColor(new Color(0, Math.min(255, alpha), 0));
        StdDraw.setPenRadius(0.003);
        double radius = Math.max(radarRockImage.getWidth() * rock.getScaleX(),
                                 radarRockImage.getHeight() * rock.getScaleY()) / 2;
        StdDraw.circle(screenX, screenY, radius);
    }
}
