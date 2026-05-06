import java.util.*;

/**
 * TorpedoSystem — owns exactly one torpedo.
 *
 * Things it controls:
 * Launching a torpedo
 * Steering and moving the torpedo (calls torpedo class do this)
 * Check collisions
 * Detonation
 * Drawing the torpedo (uses torpedo's draw method)
 */
public class TorpedoSystem {

    private Torpedo torpedo = null;

    public boolean hasTorpedo() { 
        return torpedo != null && torpedo.isAlive(); 
    }
    public Torpedo getTorpedo() { 
        return torpedo;
    }

    public void launchTorpedo(float playerX, float playerY, float playerAngle) {
        if (hasTorpedo()) return;
        torpedo = new Torpedo("player", playerX, playerY, playerAngle);
    }

    /**
     * Steer + move the torpedo, then check collisions.
     * Returns true if the torpedo just detonated this tick.
     */
    public boolean update(double mouseScreenX, double mouseScreenY, double screenCX, double screenCY, List<Rock> rocks, BottomRockLayer floor, Map<String, Submarine> remoteSubs, Submarine localPlayer) {
        
        if (!hasTorpedo()){
             return false;
        }

        //handle torpedo mvment on torpedo's class bc torpedo has all the vars for mvment mechs
        torpedo.update(mouseScreenX, mouseScreenY, screenCX, screenCY);
        

        // Rock collision
        for (Rock rock : rocks) {
            if (torpedo.collidesWithRock(rock)) {
                detonate(remoteSubs, localPlayer);
                return true;
            }
        }

        // Seafloor collision
        if (torpedo.getY() <= floor.getFloorYAt(torpedo.getX()) + torpedo.getCollisionRadius()) {
            detonate(remoteSubs, localPlayer);
            return true;
        }

        return false;
    }

    /** Second mouse click — manual detonate. */
    public void detonateManual(Map<String, Submarine> remoteSubs, Submarine localPlayer) {
        if (!hasTorpedo()) return;
        detonate(remoteSubs, localPlayer);
    }

    private void detonate(Map<String, Submarine> remoteSubs, Submarine localPlayer) {
        if (torpedo == null){
            return;
        }
        for (Submarine s : remoteSubs.values()) {
            if (torpedo.inBlastRadius(s.getX(), s.getY())) {
                s.takeDamage(torpedo.getDamage());
                System.out.println("Torpedo hit remote sub!");
            }
        }
        if (torpedo.inBlastRadius(localPlayer.getX(), localPlayer.getY())) {
            localPlayer.takeDamage(torpedo.getDamage() / 2);
            System.out.println("Torpedo hit yourself!");
        }
        torpedo.explode();
    }

    public void draw(GameEngine engine) {
        if (hasTorpedo()) torpedo.draw(engine);
    }

    /** Null out the torpedo reference after it has exploded and been handled. */
    public void resetTorpedo() {
        torpedo = null;
    }
}