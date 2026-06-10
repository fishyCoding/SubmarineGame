package com.subbaandderek;

import java.util.List;

public class Sound {

    //global sound consts  
    public static float DECAY_RATE     = 0.015f;
    public static final float DEAD_THRESHOLD = 0.5f;
    public static final float FALLOFF        = 0.0000015f;

    float x;
    float y;
    float strength;   // current intensity
    String owner; // "player" or "environment", etc.


    public Sound(float x, float y, float strength, String owner) {
        this.x = x;
        this.y = y;
        this.strength = strength;
        this.owner = owner;
    }

    public void tick() {
        strength *= (1f - DECAY_RATE);
    }

    // Considered dead once the sound gets bellow 0.5
    public boolean isDead() {
        return strength < DEAD_THRESHOLD;
    }

    // Helper function to get sound strength from a world pos coor
    public float perceivedAt(float lx, float ly) {
        float dx = x -lx;
        float dy = y -ly;
        float dist2 = dx*dx+dy*dy;
        return strength / (1f+dist2*FALLOFF);
    }


    public float  getX()        { return x; }
    public float  getY()        { return y; }
    public float  getStrength() { return strength; }
    public String getOwner()    { return owner; }

    //Helper for getting total sound
    public static float totalPerceivedAt(List<Sound> sounds, float lx, float ly) {
        float total = 0f;
        for (Sound s : sounds) total += s.perceivedAt(lx, ly);
        return total;
    }

    //Yoink all dead sounds
    public static void pruneDead(List<Sound> sounds) {
        sounds.removeIf(Sound::isDead);
    }

}