import java.io.*;
import java.util.*;

/**
 * GameEngine manages the sprite world, file I/O, and camera.
 * Deliberately free of any StdDraw / rendering calls —
 * each Sprite subclass renders itself via draw(engine).
 *
 * All terrain sprites are {@link Rock} instances; the old Polygon class
 * has been consolidated into Rock.
 */
public class GameEngine {

    private final List<Sprite> sprites;
    private final String       dataFile;

    private float cameraX;
    private float cameraY;

    // ── Construction ───────────────────────────────────────────────────────────

    public GameEngine(String dataFile) {
        this.sprites  = new ArrayList<>();
        this.dataFile = dataFile;
        this.cameraX  = 0;
        this.cameraY  = 0;
        loadSprites();
    }

    // ── Sprite I/O ─────────────────────────────────────────────────────────────

    public void loadSprites() {
        sprites.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                Sprite s = deserializeSprite(line);
                if (s != null) sprites.add(s);
            }
            System.out.println("Loaded " + sprites.size() + " sprites from " + dataFile);
        } catch (FileNotFoundException e) {
            System.out.println("No data file found — starting fresh: " + dataFile);
            saveSprites();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Sprite deserializeSprite(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0) return null;
        switch (parts[0]) {
            // "POLYGON" kept for backwards-compatibility with old save files
            case "POLYGON":
            case "ROCK": return Rock.deserialize(line);
            default:     return null;
        }
    }

    public void saveSprites() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(dataFile))) {
            writer.println("# Submarine Game — Sprite Data");
            writer.println("# Format: ROCK depth vertexCount x1 y1 ... r g b");
            for (Sprite s : sprites) writer.println(s.serialize());
            System.out.println("Saved " + sprites.size() + " sprites to " + dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── Sprite management ──────────────────────────────────────────────────────

    public Rock createRock(float x, float y, int depth) {
        return new Rock(x, y, depth);
    }

    public void addSprite(Sprite s) {
        sprites.add(s);
        saveSprites();
    }

    public void addRock(Rock rock) { addSprite(rock); }

    public void deleteSprite(float worldX, float worldY) {
        for (int i = sprites.size() - 1; i >= 0; i--) {
            if (sprites.get(i).contains(worldX, worldY)) {
                System.out.println("Deleted: " + sprites.remove(i));
                saveSprites();
                return;
            }
        }
    }

    public Sprite getSpriteAt(float worldX, float worldY) {
        for (int i = sprites.size() - 1; i >= 0; i--)
            if (sprites.get(i).contains(worldX, worldY)) return sprites.get(i);
        return null;
    }

    public void clearAll() {
        sprites.clear();
        saveSprites();
        System.out.println("Cleared all sprites.");
    }

    public List<Sprite> getSprites() { return sprites; }

    // ── Camera ─────────────────────────────────────────────────────────────────

    public void panCamera(float dx, float dy) {
        cameraX += dx;
        cameraY += dy;
    }

    public void setCamera(float x, float y) {
        cameraX = x;
        cameraY = y;
    }

    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }

    // ── Coordinate conversions ─────────────────────────────────────────────────

    public float  screenToWorldX(double sx) { return (float) sx + cameraX; }
    public float  screenToWorldY(double sy) { return (float) sy + cameraY; }
    public double worldToScreenX(float  wx) { return wx - cameraX; }
    public double worldToScreenY(float  wy) { return wy - cameraY; }
}