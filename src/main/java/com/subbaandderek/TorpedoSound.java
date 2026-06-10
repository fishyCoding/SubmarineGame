package com.subbaandderek;

public class TorpedoSound extends Sound {
    
    public TorpedoSound(float x, float y, String owner) {
        super(x, y, 20000f, owner);
        DECAY_RATE = 0.03f; 
    }
}
