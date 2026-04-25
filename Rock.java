/**
 * Rock sprite — an underwater polygon with default grey coloring.
 * Inherits all drawing logic from Polygon.
 */
import java.awt.Color;

public class Rock extends Polygon {
    private static Color rgbColor(int r, int g, int b) {
    return new Color(r, g, b);
    }

    public Rock(float startX, float startY, int depth) {

        super(startX, startY, rgbColor(170,170,170), depth);
    }

    public Rock(float startX, float startY, int r, int g, int b, int depth) {
        
        super(startX, startY, rgbColor(r,g,b), depth);
        

    }

    @Override
    public String getType() { return "ROCK"; }

    /**
     * Deserialize a rock from a text line.
     * Format: ROCK depth vertexCount x1 y1 x2 y2 ... r g b
     */
    public static Rock deserialize(String line) {
        try {
            String[] p = line.trim().split("\\s+");
            if (p.length < 6) return null;

            int depth       = Integer.parseInt(p[1]);
            int vertexCount = Integer.parseInt(p[2]);
            if (p.length < 3 + vertexCount * 2 + 3) return null;

            Rock rock = new Rock(Float.parseFloat(p[3]), Float.parseFloat(p[4]), depth);
            rock.clearVertices();

            int idx = 3;
            for (int i = 0; i < vertexCount; i++) {
                rock.addVertex(Float.parseFloat(p[idx++]), Float.parseFloat(p[idx++]));
            }
            rock.setColor(Integer.parseInt(p[idx]),
                          Integer.parseInt(p[idx + 1]),
                          Integer.parseInt(p[idx + 2]));
            rock.closePath();
            return rock;
        } catch (Exception e) { return null; }
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROCK ").append(getDepth()).append(" ").append(getVertexCount());
        for (float v : getVertices()) sb.append(" ").append(String.format("%.1f", v));
        sb.append(" ").append(getR()).append(" ").append(getG()).append(" ").append(getB());
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("Rock(%d vertices, depth=%d, color=RGB(%d,%d,%d))",
                getVertexCount(), getDepth(), getR(), getG(), getB());
    }
}