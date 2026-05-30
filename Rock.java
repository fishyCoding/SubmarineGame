import java.awt.Color;
import java.util.*;

// Polygon-based terrain sprite.
public class Rock extends Sprite {

    private final List<Float> vertices;
    private int depth;

    private static final Color BG_BASE = Color.decode("#131313");
    private static final Color BG_SHADOW = Color.decode("#000000");
    private static final Color FG_BASE = Color.decode("#848484");
    private static final Color FG_SHADOW = Color.decode("#595959");
    private static final float OUTLINE_WIDTH = 0.01f;

    public Rock(float x, float y, int depth) {
        super(x, y);
        this.vertices = new ArrayList<>();
        this.depth = Math.max(0, Math.min(1, depth));
        this.vertices.add(0f);
        this.vertices.add(0f);
    }

    public float[] getBounds() {
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        for(int i=0; i<vertices.size();i+=2) {
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
        // store as offset from rock origin
        this.vertices.add(x - getX());
        this.vertices.add(y - getY());
    }

    public void removeLastVertex() {
        if(this.vertices.size() >= 4) {
            this.vertices.remove(this.vertices.size() - 1);
            this.vertices.remove(this.vertices.size() - 1);
        }
    }

    public int getVertexCount() {
        return this.vertices.size() / 2;
    }

    // used by Radar.java for outline drawing
    public List<Float> getVertices() {
        return this.vertices;
    }

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(int d) {
        this.depth = Math.max(0, Math.min(1, d));
    }

    @Override
    public boolean contains(float px, float py) {
        if(this.vertices.size()<6){
             return false;
        }
        int count = this.vertices.size()/2;

        boolean inside =false;

        for(int i=0, j=count-1; i<count; j=i++) {
            float xi = this.vertices.get(i*2) + this.getX();
            float yi = this.vertices.get(i*2+1) + this.getY();
            float xj = this.vertices.get(j*2) + this.getX();
            float yj = this.vertices.get(j*2+1) + this.getY();
            float xint=(xj-xi)*(py-yi)/(yj-yi)+xi;
            if((yi>py) != (yj>py) && px < xint){
                inside = !inside;
            }
        }
        return inside;
    }

    @Override
    public void draw(GameEngine engine) {
        // vertices are stored as offsets from the rock's xy position
        double[] screenXs = new double[this.vertices.size() / 2];
        double[] screenYs = new double[this.vertices.size() / 2];
        for(int i=0; i<this.vertices.size(); i+=2) {
            int idx = i / 2;
            screenXs[idx] = engine.worldToScreenX(this.vertices.get(i) + this.getX());
            screenYs[idx] = engine.worldToScreenY(this.vertices.get(i + 1) + this.getY());
        }

        Color baseColor = this.depth == 0 ? BG_BASE : FG_BASE;
        StdDraw.setPenColor(baseColor);
        StdDraw.filledPolygon(screenXs, screenYs);

        Color shadowColor = this.depth == 0 ? BG_SHADOW : FG_SHADOW;
        StdDraw.setPenColor(shadowColor);
        StdDraw.setPenRadius(OUTLINE_WIDTH);
        for(int i=0; i<screenXs.length; i++) {
            int next = (i + 1) % screenXs.length;
            StdDraw.line(screenXs[i], screenYs[i], screenXs[next], screenYs[next]);
        }
        StdDraw.setPenRadius(0.002);
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROCK ")
          .append(getX()).append(" ")
          .append(getY()).append(" ")
          .append(depth).append(" ")
          .append(this.vertices.size() / 2);
        for(Float v : this.vertices)
            sb.append(" ").append(String.format("%.1f", v));
        return sb.toString();
    }

    public static Rock deserialize(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if(parts.length < 5) return null;

            int i = 0;
            if(parts[i].equalsIgnoreCase("ROCK")) i++;

            float x = Float.parseFloat(parts[i++]);
            float y = Float.parseFloat(parts[i++]);
            int depth = Integer.parseInt(parts[i++]);
            int vertexCount = Integer.parseInt(parts[i++]);

            if(parts.length < i + (vertexCount * 2)) return null;

            Rock rock = new Rock(x, y, depth);
            rock.vertices.clear();
            for(int v=0; v<vertexCount*2; v++)
                rock.vertices.add(Float.parseFloat(parts[i++]));

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