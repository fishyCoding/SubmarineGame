import java.util.*;

/**
 * Polygon sprite for creating rock structures and terrain in the submarine game.
 * Uses vertex-based polygons for flexible shape creation.
 * Each polygon has a depth layer for underwater effect (more water = darker/bluer).
 */
public class Polygon extends Sprite {
    private List<Float> vertices; // Alternating x, y coordinates
    private int depth; // 0 = background (far), 1 = foreground (close)
    private boolean closed;

    public Polygon(float startX, float startY, int r, int g, int b, int depth) {
        super(startX, startY, r, g, b);
        this.vertices = new ArrayList<>();
        this.depth = Math.max(0, Math.min(1, depth));
        this.closed = false;
        addVertex(startX, startY);
    }

    public void addVertex(float x, float y) {
        vertices.add(x);
        vertices.add(y);
    }

    public void closePath() {
        this.closed = true;
    }

    public void removeLastVertex() {
        if (vertices.size() >= 2) {
            vertices.remove(vertices.size() - 1);
            vertices.remove(vertices.size() - 1);
        }
    }

    public List<Float> getVertices() {
        return new ArrayList<>(vertices);
    }

    protected void clearVertices() {
        vertices.clear();
    }

    public int getDepth() {
        return depth;
    }

    public boolean isClosed() {
        return closed;
    }

    public int getVertexCount() {
        return vertices.size() / 2;
    }

    /**
     * Get bounds of this polygon for camera culling
     */
    public float[] getBounds() {
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        
        for (int i = 0; i < vertices.size(); i += 2) {
            minX = Math.min(minX, vertices.get(i));
            maxX = Math.max(maxX, vertices.get(i));
            minY = Math.min(minY, vertices.get(i + 1));
            maxY = Math.max(maxY, vertices.get(i + 1));
        }
        
        return new float[]{minX, maxX, minY, maxY};
    }

    /**
     * Point-in-polygon collision detection using ray casting algorithm
     */
    @Override
    public boolean contains(float px, float py) {
        if (vertices.size() < 6) return false; // Need at least 3 vertices
        
        int count = vertices.size() / 2;
        boolean inside = false;

        for (int i = 0, j = count - 1; i < count; j = i++) {
            float xi = vertices.get(i * 2);
            float yi = vertices.get(i * 2 + 1);
            float xj = vertices.get(j * 2);
            float yj = vertices.get(j * 2 + 1);

            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Serialize polygon to text format
     * Format: POLYGON depth vertexCount x1 y1 x2 y2 ... r g b
     */
    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("POLYGON ").append(depth).append(" ").append(getVertexCount());
        for (float v : vertices) {
            sb.append(" ").append(String.format("%.1f", v));
        }
        sb.append(" ").append(r).append(" ").append(g).append(" ").append(b);
        return sb.toString();
    }

    /**
     * Deserialize polygon from text
     */
    public static Polygon deserialize(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) return null;

            int depth = Integer.parseInt(parts[1]);
            int vertexCount = Integer.parseInt(parts[2]);

            if (parts.length < 3 + vertexCount * 2 + 3) return null;

            float x = Float.parseFloat(parts[3]);
            float y = Float.parseFloat(parts[4]);
            
            Polygon poly = new Polygon(x, y, 100, 150, 100, depth);
            poly.vertices.clear(); // Clear the initial vertex

            int idx = 3;
            for (int i = 0; i < vertexCount; i++) {
                float vx = Float.parseFloat(parts[idx++]);
                float vy = Float.parseFloat(parts[idx++]);
                poly.addVertex(vx, vy);
            }

            int r = Integer.parseInt(parts[idx++]);
            int g = Integer.parseInt(parts[idx++]);
            int b = Integer.parseInt(parts[idx++]);
            poly.setColor(r, g, b);
            poly.closePath();

            return poly;
        } catch (Exception e) {
            return null;
        }
    }
        public static void drawPolygon(Polygon poly) {
        List<Float> vertices = poly.getVertices();
        if (vertices.size() < 6) return; // Need at least 3 vertices

        float roughness = poly.getDepth() == 0 ? 4f : 8f;
        double[][] rockShape = buildRockShape(vertices, roughness);
        double[] screenX = rockShape[0];
        double[] screenY = rockShape[1];
        int vertexCount = screenX.length;

        int baseR, baseG, baseB;
        if (poly.getDepth() == 1) {
            // Foreground layer - brighter grey, collidable with sub
            baseR = 190;
            baseG = 190;
            baseB = 205;
        } else {
            // Background layer - darker grey, further back
            baseR = 75;
            baseG = 85;
            baseB = 100;
        }

        StdDraw.setPenColor(baseR, baseG, baseB);
        StdDraw.filledPolygon(screenX, screenY);
        drawRockTexture(screenX, screenY, baseR, baseG, baseB);

        StdDraw.setPenColor(Math.max(0, baseR - 45), Math.max(0, baseG - 45), Math.max(0, baseB - 45));
        StdDraw.setPenRadius(0.003);
        for (int i = 0; i < vertexCount; i++) {
            int next = (i + 1) % vertexCount;
            StdDraw.line(screenX[i], screenY[i], screenX[next], screenY[next]);
        }
        StdDraw.setPenRadius(0.002);
    }

    private static double[][] buildRockShape(List<Float> vertices, float roughness) {
        int count = vertices.size() / 2;
        int segments = 4;
        int totalPoints = count * segments + 1;
        double[] xs = new double[totalPoints];
        double[] ys = new double[totalPoints];
        int idx = 0;

        for (int i = 0; i < count; i++) {
            float x1 = vertices.get(i * 2);
            float y1 = vertices.get(i * 2 + 1);
            float x2 = vertices.get(((i + 1) % count) * 2);
            float y2 = vertices.get(((i + 1) % count) * 2 + 1);
            double dx = x2 - x1;
            double dy = y2 - y1;
            double length = Math.hypot(dx, dy);
            double nx = length > 0 ? -dy / length : 0;
            double ny = length > 0 ? dx / length : 0;

            for (int step = 0; step <= segments; step++) {
                if (i > 0 && step == 0) {
                    continue;
                }
                double t = step / (double) segments;
                double px = x1 + dx * t;
                double py = y1 + dy * t;
                double phase = (x1 + y1) * 0.03 + i * 1.4 + t * 3.8;
                double noise = Math.sin(phase) * roughness + Math.cos(phase * 1.8) * roughness * 0.35;
                px += nx * noise;
                py += ny * noise;
                xs[idx] = engine.worldToScreenX((float) px);
                ys[idx] = engine.worldToScreenY((float) py);
                idx++;
            }
        }

        if (idx < totalPoints) {
            xs[idx] = xs[0];
            ys[idx] = ys[0];
            idx++;
        }

        if (idx != totalPoints) {
            double[] finalXs = new double[idx];
            double[] finalYs = new double[idx];
            System.arraycopy(xs, 0, finalXs, 0, idx);
            System.arraycopy(ys, 0, finalYs, 0, idx);
            xs = finalXs;
            ys = finalYs;
        }

        return new double[][]{xs, ys};
    }

    @Override
    public String getType() {
        return "POLYGON";
    }

    @Override
    public String toString() {
        return String.format("Polygon(%d vertices, depth=%d, color=RGB(%d,%d,%d))",
                getVertexCount(), depth, r, g, b);
    }
}
