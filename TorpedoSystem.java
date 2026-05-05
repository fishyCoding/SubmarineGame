import java.awt.Color;
import java.util.*;

/**
 * TorpedoSystem — manages the full torpedo workflow:
 *
 *   1. After a radar ping, call setContacts() with the detected remote subs.
 *      Numbers 1-N appear above the radar screen. Press 1/2/3... to select target.
 *
 *   2. First mouse click → launchTorpedo() fires a torpedo from the player.
 *
 *   3. Each tick → update(mouseWX, mouseWY) steers the torpedo toward mouse.
 *      While active, distance to selected target is shown on screen.
 *
 *   4. Second mouse click OR collision → detonates; damage applied to any sub
 *      within blast radius.
 *
 * Collision is checked externally by Game and reported via reportCollision().
 */
public class TorpedoSystem {

    // ── Contact list (from last ping) ──────────────────────────────────────────
    // Ordered list so numbers 1-N are stable within a ping session
    private final List<String>   contactIds  = new ArrayList<>();
    private final Map<String, float[]> contactPos = new LinkedHashMap<>(); // id → [x, y]

    private int selectedIndex = -1; 

    // ── Active torpedo ─────────────────────────────────────────────────────────
    public Torpedo torpedo = null;

    // ── Layout constants (screen coords) ──────────────────────────────────────
    private final int screenW;
    private final int contactListX; // right-edge X of the contact list text

    public TorpedoSystem(int screenWidth) {
        this.screenW      = screenWidth;
        this.contactListX = screenWidth - 10;
    }

    // ── Contact management ─────────────────────────────────────────────────────

    /**
     * Call this when the player fires a radar ping.
     * contactMap: playerId → [worldX, worldY]
     */
    public void setContacts(Map<String, float[]> contactMap) {
        contactIds.clear();
        contactPos.clear();
        contactIds.addAll(contactMap.keySet());
        contactPos.putAll(contactMap);
        //if torpedo is null, set target back to 0. if theres still a torpedo keep same target index
        if (torpedo == null || !torpedo.isAlive()) {
            selectedIndex = -1;
        }
    }

    /** Called each tick — check number keys 1-9 to select a target. */
    public void handleTargetInput() {
        for (int i = 0; i < contactIds.size() && i < 9; i++) {
            int keyCode = java.awt.event.KeyEvent.VK_1 + i;
            if (StdDraw.isKeyPressed(keyCode)) {
                selectedIndex = i;
                System.out.println("Target selected: " + contactIds.get(i));
            }
        }
    }

    // ── Torpedo lifecycle ──────────────────────────────────────────────────────

    public boolean hasTorpedo() { return torpedo != null && torpedo.isAlive(); }

    /**
     * Launch a torpedo from the player's position and heading.
     * Does nothing if a torpedo is already in flight.
     */
    public void launchTorpedo(float playerX, float playerY, float playerAngle) {
        if (hasTorpedo()) return;
        torpedo = new Torpedo("player", playerX, playerY, playerAngle);
        System.out.println("Torpedo launched!");
    }

    /**
     * Update torpedo steering and check for rock / seafloor collision.
     * @param mouseWX   world X of the mouse
     * @param mouseWY   world Y of the mouse
     * @param rocks     foreground rock list for collision
     * @param floor     seafloor for collision
     * @return list of player IDs that were hit (empty if none)
     */
    public List<String> update(double mouseScreenX, double mouseScreenY,
                               double screenCX, double screenCY,
                               List<Rock> rocks, BottomRockLayer floor,
                               Map<String, Submarine> remoteSubs,
                               Submarine localPlayer) {
        List<String> hits = new ArrayList<>();
        if (!hasTorpedo()) return hits;

        torpedo.update(mouseScreenX, mouseScreenY, screenCX, screenCY);

        // Rock collision
        for (Rock rock : rocks) {
            if (torpedo.collidesWithRock(rock)) {
                detonate(remoteSubs, localPlayer, hits);
                return hits;
            }
        }

        // Seafloor collision
        if (torpedo.getY() <= floor.getFloorYAt(torpedo.getX()) + torpedo.getCollisionRadius()) {
            detonate(remoteSubs, localPlayer, hits);
            return hits;
        }

        return hits;
    }

    /** Called on second mouse click — detonates torpedo manually. */
    public void detonateManual(Map<String, Submarine> remoteSubs,
                               Submarine localPlayer) {
        if (!hasTorpedo()) return;
        List<String> dummy = new ArrayList<>();
        detonate(remoteSubs, localPlayer, dummy);
    }

    private void detonate(Map<String, Submarine> remoteSubs,
                          Submarine localPlayer, List<String> hitsOut) {
        if (torpedo == null) return;

        // Check remote subs
        for (Map.Entry<String, Submarine> e : remoteSubs.entrySet()) {
            if (torpedo.inBlastRadius(e.getValue().getX(), e.getValue().getY())) {
                e.getValue().takeDamage(torpedo.getDamage());
                hitsOut.add(e.getKey());
                System.out.println("Torpedo hit: " + e.getKey());
            }
        }

        // Check local player (self-destruct edge case)
        if (torpedo.inBlastRadius(localPlayer.getX(), localPlayer.getY())) {
            localPlayer.takeDamage(torpedo.getDamage() / 2);
            System.out.println("Torpedo hit yourself!");
        }

        torpedo.explode();
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    public void clearContacts(){
         


         if (torpedo == null || !torpedo.isAlive()) {
            contactIds.clear();
            contactPos.clear();
             selectedIndex = -1;   
         } else {
            //clear all items in list but the current selected one
            String selectedId = contactIds.get(selectedIndex);
            contactIds.clear();
            contactIds.add(selectedId);
            contactPos.clear();
            contactPos.put(selectedId, contactPos.get(selectedId)); 
            selectedIndex = 0;

         }

    }

    public void draw(GameEngine engine) {
        if (hasTorpedo()) torpedo.draw(engine);
        drawContactList();
        if (hasTorpedo()) drawDistanceReadout(engine);
    }

    /** Numbered contact list above the radar screen (top-right area). */
    private void drawContactList() {
        if (contactIds.isEmpty()) return;

        java.awt.Font font = new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 11);
        StdDraw.setFont(font);

        int startY = 340; // above RadarScreen which sits at ~220px
        int lineH  = 14;

        StdDraw.setPenColor(new Color(0, 180, 80));
        StdDraw.textRight(contactListX, startY + lineH, "CONTACTS");

        for (int i = 0; i < contactIds.size() && i < 9; i++) {
            //right now theres a bug its not changing color of the text when u select

            String id  = contactIds.get(i);
            float[] pos = contactPos.get(id);
            boolean sel = (i == selectedIndex);

            System.out.println(id);

            String label = String.format("[%d] %s", i + 1, id);
            if (sel) {
                StdDraw.setPenColor(new Color(255, 220, 80)); // highlighted
            } else {
                StdDraw.setPenColor(new Color(0, 160, 70));
            }
            StdDraw.textRight(contactListX, startY - i * lineH, label);
        }

        // Instruction line
        StdDraw.setPenColor(new Color(0, 120, 50));
        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 10));
        int below = startY - contactIds.size() * lineH - 4;
        if (!hasTorpedo()) {
            StdDraw.textRight(contactListX, below, "Click to launch torpedo");
        } else {
            StdDraw.textRight(contactListX, below, "Click to detonate");
        }
    }

    /**
     * While torpedo is in flight, show distance to the selected target
     * so the player can play hot/cold.
     */
    private void drawDistanceReadout(GameEngine engine) {
        if (selectedIndex < 0 || selectedIndex >= contactIds.size()) return;
        System.out.println("Selected index: " + selectedIndex);
        float[] pos = contactPos.get(contactIds.get(selectedIndex));
        if (pos == null) return;

        float dx   = pos[0] - torpedo.getX();
        float dy   = pos[1] - torpedo.getY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        float maxDist = 2000f;

        StdDraw.setPenColor(new Color(0, 255, 0));

        StdDraw.setFont(new java.awt.Font("Monospaced", java.awt.Font.BOLD, 13));
        StdDraw.textRight(contactListX, 310, String.format("DIST: %.0f m", dist));

        double sx = engine.worldToScreenX(torpedo.getX());
        double sy = engine.worldToScreenY(torpedo.getY());
        StdDraw.setPenColor(new Color(255, 220, 80, 200));
        StdDraw.setPenRadius(0.006);
        StdDraw.point(sx, sy);
        StdDraw.setPenRadius(0.002);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Torpedo getTorpedo() { return torpedo; }
}