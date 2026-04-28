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
     * Draw a single rock's radar outline using rock1outline.png.
     */
    private static void drawRockRadarOutline(Rock rock, GameEngine engine, int alpha) {
        double screenX = engine.worldToScreenX(rock.getX());
        double screenY = engine.worldToScreenY(rock.getY());

        if (radarRockImage != null && radarRockImage.getWidth() > 1) {
            double screenW = Math.abs(radarRockImage.getWidth()  * rock.getScaleX());
            double screenH = Math.abs(radarRockImage.getHeight() * rock.getScaleY());
            screenW = Math.max(screenW, 1.0);
            screenH = Math.max(screenH, 1.0);

            // Tint the pen green for any supplemental lines, then draw the outline image.
            // StdDraw.picture does not support per-pixel alpha blending with a tint, so
            // we draw the image at full opacity and overlay a transparent green circle
            // whose opacity encodes the ping strength.
            StdDraw.picture(screenX, screenY, RADAR_IMAGE_PATH,
                            screenW, screenH, rock.getRotation());

            // Green glow overlay scaled by alpha
            int glowAlpha = Math.min(200, alpha);
            StdDraw.setPenColor(new java.awt.Color(0, glowAlpha, 0, glowAlpha));
            StdDraw.setPenRadius(0.006);
            StdDraw.point(screenX, screenY);
        } else {
            // Fallback: bright green dot if image is missing
            StdDraw.setPenColor(new java.awt.Color(0, Math.min(255, alpha), 0));
            StdDraw.setPenRadius(0.008);
            StdDraw.point(screenX, screenY);
        }
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