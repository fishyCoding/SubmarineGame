import java.io.*;
import java.util.*;

/**
 * GameEngine class manages the game world, sprites, file I/O, and rendering.
 * Handles editing mode, camera scrolling, and sprite management.
 */
public class GameEngine {
    private List<Sprite> sprites;
    private String dataFile;
    private float cameraX; // Camera scroll position X
    private float cameraY; // Camera scroll position Y
    private static final float CAMERA_SPEED = 2.0f;
    private static final float GRID_SIZE = 20.0f;

    public GameEngine(String dataFile) {
        this.sprites = new ArrayList<>();
        this.dataFile = dataFile;
        this.cameraX = 0;
        this.cameraY = 0;
        loadSprites();
    }

    /**
     * Load sprites from text file
     * Format: Each line is a serialized sprite (TYPE x y ...)
     */
    public void loadSprites() {
        sprites.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                Sprite sprite = deserializeSprite(line);
                if (sprite != null) {
                    sprites.add(sprite);
                }
            }
            System.out.println("Loaded " + sprites.size() + " sprites from " + dataFile);
        } catch (FileNotFoundException e) {
            System.out.println("Data file not found. Creating new file: " + dataFile);
            saveSprites();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deserialize a sprite based on its type
     */
    private Sprite deserializeSprite(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0) return null;
        
        String type = parts[0];
        switch (type) {
            case "SQUARE":
                return Square.deserialize(line);
            case "POLYGON":
                return Polygon.deserialize(line);
            case "ROCK":
                return Rock.deserialize(line);
            default:
                return null;
        }
    }

    /**
     * Save all sprites to text file
     */
    public void saveSprites() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(dataFile))) {
            writer.println("# Game Engine Sprite Data");
            writer.println("# Format: TYPE depth vertexCount x1 y1 x2 y2 ... r g b");
            for (Sprite sprite : sprites) {
                writer.println(sprite.serialize());
            }
            System.out.println("Saved " + sprites.size() + " sprites to " + dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create a new rock at starting position
     */
    public Rock createRock(float startX, float startY, int depth) {
        return new Rock(startX, startY, depth);
    }

    public Polygon createPolygon(float startX, float startY, int depth) {
        return createRock(startX, startY, depth);
    }

    /**
     * Finalize and add rock to world
     */
    public void addRock(Rock rock) {
        sprites.add(rock);
        saveSprites();
        System.out.println("Added rock: " + rock);
    }

    public void addPolygon(Polygon poly) {
        sprites.add(poly);
        saveSprites();
        System.out.println("Added polygon: " + poly);
    }

    /**
     * Delete sprite at world coordinates
     */
    public void deleteSprite(float worldX, float worldY) {
        for (int i = sprites.size() - 1; i >= 0; i--) {
            if (sprites.get(i).contains(worldX, worldY)) {
                Sprite deleted = sprites.remove(i);
                System.out.println("Deleted sprite: " + deleted);
                saveSprites();
                return;
            }
        }
    }

    /**
     * Get sprite at world coordinates (for selection/editing)
     */
    public Sprite getSpriteAt(float worldX, float worldY) {
        for (int i = sprites.size() - 1; i >= 0; i--) {
            if (sprites.get(i).contains(worldX, worldY)) {
                return sprites.get(i);
            }
        }
        return null;
    }

    /**
     * Move camera (scrolling/panning)
     */
    public void panCamera(float dx, float dy) {
        cameraX += dx;
        cameraY += dy;
    }

    /**
     * Convert screen coordinates to world coordinates based on camera position
     */
    public float screenToWorldX(double screenX) {
        return (float) screenX + cameraX;
    }

    public float screenToWorldY(double screenY) {
        return (float) screenY + cameraY;
    }

    /**
     * Convert world coordinates to screen coordinates for rendering
     */
    public double worldToScreenX(float worldX) {
        return worldX - cameraX;
    }

    public double worldToScreenY(float worldY) {
        return worldY - cameraY;
    }

    public List<Sprite> getSprites() {
        return sprites;
    }

    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }

    public void clearAll() {
        sprites.clear();
        saveSprites();
        System.out.println("Cleared all sprites");
    }
}
