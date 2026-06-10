package com.subbaandderek;

import java.util.*;

// Owns exactly one torpedo at a time.
// Handles launching, steering, collision, detonation, and drawing.
public class TorpedoSystem {

    private Torpedo torpedo = null;

    public boolean hasTorpedo() {
        return torpedo != null && torpedo.isAlive();
    }

    public Torpedo getTorpedo() {
        return torpedo;
    }

    public void launchTorpedo(float playerX, float playerY, float playerAngle) {
        if(hasTorpedo()) return;
        torpedo = new Torpedo("player", playerX, playerY, playerAngle);
    }

    // Steer + move the torpedo, then check collisions.
    // Returns true if the torpedo detonated this tick.
    public boolean update(double mouseScreenX, double mouseScreenY, double screenCX, double screenCY,
                          List<Rock> rocks, BottomRockLayer floor, Map<String, Submarine> remoteSubs, Submarine localPlayer) {
        if(!hasTorpedo()) return false;

        torpedo.update(mouseScreenX, mouseScreenY, screenCX, screenCY);

        for(Rock rock : rocks) {
            if(torpedo.collidesWithRock(rock)) {
                detonate(remoteSubs, localPlayer);
                return true;
            }
        }

        if(torpedo.getY() <= floor.getFloorYAt(torpedo.getX()) + torpedo.getCollisionRadius()) {
            detonate(remoteSubs, localPlayer);
            return true;
        }

        return false;
    }

    public void detonateManual(Map<String, Submarine> remoteSubs, Submarine localPlayer) {
        if(!hasTorpedo()) return;
        detonate(remoteSubs, localPlayer);
    }

    private void detonate(Map<String, Submarine> remoteSubs, Submarine localPlayer) {
        if(torpedo == null) return;
        for(Submarine s : remoteSubs.values()) {
            if(torpedo.inBlastRadius(s.getX(), s.getY())) {
                s.takeDamage(torpedo.getDamage());
                System.out.println("Torpedo hit remote sub!");
            }
        }
        if(torpedo.inBlastRadius(localPlayer.getX(), localPlayer.getY())) {
            localPlayer.takeDamage(torpedo.getDamage() / 2);
            System.out.println("Torpedo hit yourself!");
        }
        torpedo.explode();
    }

    public void draw(GameEngine engine) {
        if(hasTorpedo()) torpedo.draw(engine);
    }

    public void resetTorpedo() {
        torpedo = null;
    }
}