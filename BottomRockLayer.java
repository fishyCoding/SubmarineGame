import java.awt.Color;

/**
 * SeafloorPoint — a draggable handle for one control point on the BottomRockLayer.
 *
 * Behaves like any other Sprite so Main can select and drag it the same way
 * it selects and drags Rocks. It doesn't own the coordinate data — it just
 * reads/writes through to BottomRockLayer.
 */
public class SeafloorPoint extends Sprite {

    private final BottomRockLayer floor;
    private final int index;
    private static final float HIT_RADIUS = 12f; // screen pixels for click detection

    public SeafloorPoint(BottomRockLayer floor, int index) {
        super(floor.getPointWorldX(index), floor.getPointWorldY(index), Color.CYAN);
        this.floor = floor;
        this.index = index;
    }

    /** Sync this sprite's x/y from the floor array (call after any movePoint). */
    public void syncFromFloor() {
        this.x = floor.getPointWorldX(index);
        this.y = floor.getPointWorldY(index);
    }

    /** Push this sprite's y back to the floor array. */
    @Override
    public void setPosition(float x, float y) {
        floor.movePoint(index, y);
        syncFromFloor();
    }

    @Override
    public boolean contains(float px, float py) {
        // Hit test in world space — but we want a fixed screen-pixel radius.
        // We can't access the engine here, so we use a world-space approximation.
        float dx = px - this.x;
        float dy = py - this.y;
        return dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS;
    }

    @Override
    public void draw(GameEngine engine) {
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y);
        StdDraw.setPenColor(78, 92, 112); // same as HILIT in BottomRockLayer
        StdDraw.setPenRadius(0.002);
        StdDraw.circle(sx, sy, 6);
    }

    // SeafloorPoints are never saved to sprites.txt
    @Override public String serialize() { return ""; }
    @Override public String getType()   { return "SEAFLOORPOINT"; }
    @Override public String toString()  { return "SeafloorPoint[" + index + "]"; }
}