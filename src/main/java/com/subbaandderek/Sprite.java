package com.subbaandderek;

public abstract class Sprite {
    float x;
    float y;

    public Sprite(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    

    public void setX(float x)         { this.x = x; }
    public void setY(float y)         { this.y = y; }
    public void setPosition(float x, float y) { this.x = x; this.y = y; }

    public abstract void draw(GameEngine engine);
    public abstract boolean contains(float px, float py);
    public abstract String serialize();
    public abstract String getType();

    @Override
    public abstract String toString();
}