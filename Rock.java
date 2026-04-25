
/**
 * Rock sprite represents underwater rock structures.
 * Rocks are built from polygon vertex lists and support depth layers.
 */
public class Rock extends Polygon {
    public Rock(float startX, float startY, int depth) {
        super(startX, startY, 170, 170, 170, depth);
    }

    public Rock(float startX, float startY, int r, int g, int b, int depth) {
        super(startX, startY, r, g, b, depth);
    }

    @Override
    public String getType() {
        return "ROCK";
    }

    /**
     * Deserialize a rock from text
     * Expected format: ROCK depth vertexCount x1 y1 x2 y2 ... r g b
     */
    public static Rock deserialize(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) return null;

            int depth = Integer.parseInt(parts[1]);
            int vertexCount = Integer.parseInt(parts[2]);
            if (parts.length < 3 + vertexCount * 2 + 3) return null;

            float x = Float.parseFloat(parts[3]);
            float y = Float.parseFloat(parts[4]);
            Rock rock = new Rock(x, y, depth);
            rock.clearVertices();

            int idx = 3;
            for (int i = 0; i < vertexCount; i++) {
                float vx = Float.parseFloat(parts[idx++]);
                float vy = Float.parseFloat(parts[idx++]);
                rock.addVertex(vx, vy);
            }

            int r = Integer.parseInt(parts[idx++]);
            int g = Integer.parseInt(parts[idx++]);
            int b = Integer.parseInt(parts[idx++]);
            rock.setColor(r, g, b);
            rock.closePath();
            return rock;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROCK ").append(getDepth()).append(" ").append(getVertexCount());
        for (float v : getVertices()) {
            sb.append(" ").append(String.format("%.1f", v));
        }
        sb.append(" ").append(getR()).append(" ").append(getG()).append(" ").append(getB());
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("Rock(%d vertices, depth=%d, color=RGB(%d,%d,%d))",
                getVertexCount(), getDepth(), getR(), getG(), getB());
    }
}
