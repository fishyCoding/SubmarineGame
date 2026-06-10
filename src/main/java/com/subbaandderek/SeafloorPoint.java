package com.subbaandderek;

public class SeafloorPoint extends Sprite {

    private final BottomRockLayer floor;
    private final int index;
    private static final float HIT_RADIUS = 12f;

    public SeafloorPoint(BottomRockLayer floor, int index) {
        super(floor.getPointWorldX(index), floor.getPointWorldY(index));
        this.floor = floor;
        this.index = index;
    }

    public void syncFromFloor() {
        this.x = floor.getPointWorldX(index);
        this.y = floor.getPointWorldY(index);
    }

    @Override
    public void setPosition(float x, float y) {
        floor.movePoint(index, y);
        syncFromFloor();
    }

    @Override
    public boolean contains(float px, float py) {

        float dx = px - this.x;
        float dy = py - this.y;
        return dx * dx + dy * dy <= HIT_RADIUS * HIT_RADIUS;
    }

    @Override
    public void draw(GameEngine engine) {
        double sx = engine.worldToScreenX(x);
        double sy = engine.worldToScreenY(y);
        StdDraw.setPenColor(78, 92, 112);
        StdDraw.setPenRadius(0.002);
        StdDraw.circle(sx, sy, 6);
    }

    @Override public String serialize() { return ""; }
    @Override public String getType()   { return "SEAFLOORPOINT"; }
    @Override public String toString()  { return "SeafloorPoint[" + index + "]"; }
}