package com.subbaandderek;

public class Spawner {
    // Each row: {minX, maxX, weight}
    // Weights are relative: a weight of 2 is twice as likely as a weight of 1.
    static final double[][] ZONES = {
        {-3600, -900, 1.0}, // Zone 1
        {50,    2100, 2.0}, // Zone 2 (Double probability)
        {3230,  5804, 1.5}, // Zone 3
        {6300,  8200, 1.0}  // Zone 4
    };

    static final int UPPERSPAWNY = -150;
    static final int LOWERSPAWNY = -400;

    public static int getSpawnX() {
        double totalWeight = 0;
        for (double[] zone : ZONES) {
            totalWeight += zone[2];
        }

        // Pick a random threshold between 0 and total weight
        double randomValue = Math.random() * totalWeight;
        double cumulativeWeight = 0;

        for (double[] zone : ZONES) {
            cumulativeWeight += zone[2];
            if (randomValue <= cumulativeWeight) {
                // Return a random X within this specific zone
                double minX = zone[0];
                double maxX = zone[1];
                return (int)(minX + (maxX - minX) * Math.random());
            }
        }

        // Fallback to the last zone's center if something goes wrong
        return (int)ZONES[0][0];
    }

    public static int getSpawnY() {
        return (int)(LOWERSPAWNY + (UPPERSPAWNY - LOWERSPAWNY) * Math.random());
    }
}