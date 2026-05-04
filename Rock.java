import java.awt.Color;
import java.util.*;

/**
 * Rock — simple polygon-based terrain sprite.
 * Stores vertices and renders as a filled polygon.
 */
public class Rock extends Sprite {

    private final List<Float> vertices;
    private int depth;

    private static final Color BG_BASE   = Color.decode("#131313");
    private static final Color BG_SHADOW = Color.decode("#000000");
    private static final Color FG_BASE   = Color.decode("#848484");
    private static final Color FG_SHADOW = Color.decode("#595959");
    private static final float OUTLINE_WIDTH = 0.01f;
 

    public Rock(float x, float y, int depth) {
        super(x, y, Color.GRAY);
        this.vertices = new ArrayList<>();
        this.depth = Math.max(0, Math.min(1, depth));
        this.vertices.add(0f);
        this.vertices.add(0f);
    }



    public float[]getBounds() {
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        for (int i = 0; i < vertices.size(); i += 2) {
            float vx = vertices.get(i) + getX();
            float vy = vertices.get(i + 1) + getY();
            minX = Math.min(minX, vx);
            maxX = Math.max(maxX, vx);
            minY = Math.min(minY, vy);
            maxY = Math.max(maxY, vy);
        }
        return new float[]{minX, maxX, minY, maxY};
    }

    public void addVertex(float x, float y) {
        //adds verts as offsets, not abs pos
        this.vertices.add(x-getX());
        this.vertices.add(y-getY());
    }

    public void removeLastVertex() {
        if (this.vertices.size() >= 4) {
            this.vertices.remove(this.vertices.size() - 1);
            this.vertices.remove(this.vertices.size() - 1);
        }
    }

    public int getVertexCount() {
        //1/2ed bc verts are stored as x,y pairs in a single list
        return this.vertices.size() / 2;
    }
    public List<Float> getVertices() {
        return this.vertices;
        //gets verts list for the radar drawing
    }

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(int d) {
        this.depth = Math.max(0, Math.min(1, d));
    }

    @Override
    public boolean contains(float px, float py) {
        if (this.vertices.size() < 6) return false;
        int count = this.vertices.size() / 2;
        boolean inside = false;

        //I searched this one up, its just a basic way to see if a point (mouse click) is inside the list of pts
        //make sure it accounts for pos offest
        for (int i = 0, j = count - 1; i < count; j = i++) {
            float xi = this.vertices.get(i * 2)+this.getX(), yi = this.vertices.get(i * 2 + 1)+this.getY();
            float xj = this.vertices.get(j * 2)+this.getX(), yj = this.vertices.get(j * 2 + 1)+this.getY();
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi)
                inside = !inside;
        }
        return inside;
    }

    @Override
    public void draw(GameEngine engine) {
        
        
        //verts are stored as offsets from the rock's x,y pos
        double[] screenXs = new double[this.vertices.size() / 2];
        double[] screenYs = new double[this.vertices.size() / 2];
        for (int i = 0; i < this.vertices.size(); i += 2) {
            int idx = i / 2;
            // convert ot wrld coords
            screenXs[idx] = engine.worldToScreenX(this.vertices.get(i) + this.getX());
            screenYs[idx] = engine.worldToScreenY(this.vertices.get(i + 1) + this.getY());
        }



        // Fill
        Color baseColor = this.depth == 0 ? BG_BASE : FG_BASE;
        StdDraw.setPenColor(baseColor);
        StdDraw.filledPolygon(screenXs, screenYs);

        //draw shadow as individual lines connecting each outer point.
        Color shadowColor = this.depth == 0 ? BG_SHADOW : FG_SHADOW;
        StdDraw.setPenColor(shadowColor);
        StdDraw.setPenRadius(OUTLINE_WIDTH);
        for (int i = 0; i < screenXs.length; i++) {
            int next = (i + 1) % screenXs.length;
            StdDraw.line(screenXs[i], screenYs[i], screenXs[next], screenYs[next]);
        }
    
    }
    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        
        // Standardized save format: ROCK [x] [y] [depth] [vertexCount] [vertices...] [r] [g] [b]
        sb.append("ROCK ")
          .append(getX()).append(" ")
          .append(getY()).append(" ")
          .append(depth).append(" ")
          .append(this.vertices.size() / 2);
          
        for (Float v : this.vertices) {
            sb.append(" ").append(String.format("%.1f", v));
        }
        
        sb.append(" ").append(color.getRed())
          .append(" ").append(color.getGreen())
          .append(" ").append(color.getBlue());
          
        return sb.toString();
    }

    public static Rock deserialize(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            
            // Minimum parts: ROCK(0), x(1), y(2), depth(3), vCount(4), r, g, b = 8 items
            if (parts.length < 8) return null;

            int i = 0;
            
            // Handle the "ROCK" prefix
            if (parts[i].equalsIgnoreCase("ROCK")) {
                i++;
            }

            // Parse base attributes
            float x = Float.parseFloat(parts[i++]);
            float y = Float.parseFloat(parts[i++]);
            int depth = Integer.parseInt(parts[i++]);
            int vertexCount = Integer.parseInt(parts[i++]);
            
            // Validate remaining length for vertices (2 per count) + 3 color values
            if (parts.length < i + (vertexCount * 2) + 3) return null;

            Rock rock = new Rock(x, y, depth);
            rock.vertices.clear();

            // Parse vertices
            for (int v = 0; v < vertexCount * 2; v++) {
                rock.vertices.add(Float.parseFloat(parts[i++]));
            }

            // Parse RGB color
            int r = Integer.parseInt(parts[i++]);
            int g = Integer.parseInt(parts[i++]);
            int b = Integer.parseInt(parts[i++]);
            rock.setColor(r, g, b);

            return rock;
        } catch (Exception e) {
            return null;
        }
    } 

    @Override
    public String getType() {
        return "ROCK";
    }

    @Override
    public String toString() {
        return String.format("Rock(vertices=%d, depth=%d)", this.vertices.size() / 2, this.depth);
    }
}