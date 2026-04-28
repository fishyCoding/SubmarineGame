import java.awt.Color;

/**
 * Slider — an interactive editing handle for transforming image-based rocks.
 *
 * Acts as a visual indicator and input device for translating, rotating,
 * or scaling a rock. Tracks the current editing mode and renders accordingly.
 */
public class Slider extends Sprite {

    public enum Mode {
        TRANSLATE,  // Move the rock
        ROTATE,     // Rotate the rock
        SCALE       // Scale the rock
    }

    private Mode     mode;
    private boolean  selected;

    // ── Constructors ───────────────────────────────────────────────────────────

    public Slider(float x, float y) {
        super(x, y, Color.YELLOW);
        this.mode = Mode.TRANSLATE;
        this.selected = false;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public void setMode(Mode mode) { this.mode = mode; }
    public Mode getMode() { return mode; }

    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSelected() { return selected; }

    // ── Collision ──────────────────────────────────────────────────────────────

    @Override
    public boolean contains(float px, float py) {
        float radius = selected ? 20 : 12;
        return Math.pow(px - x, 2) + Math.pow(py - y, 2) <= radius * radius;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void draw(GameEngine engine) {
        double screenX = engine.worldToScreenX(x);
        double screenY = engine.worldToScreenY(y);

        if (selected) {
            // Draw outer glow when selected
            StdDraw.setPenColor(255, 255, 100);
            StdDraw.setPenRadius(0.015);
            StdDraw.point(screenX, screenY);
        }

        // Draw mode-specific icon
        switch (mode) {
            case TRANSLATE:
                drawTranslateIcon(screenX, screenY);
                break;
            case ROTATE:
                drawRotateIcon(screenX, screenY);
                break;
            case SCALE:
                drawScaleIcon(screenX, screenY);
                break;
        }
    }

    private void drawTranslateIcon(double screenX, double screenY) {
        StdDraw.setPenColor(100, 200, 255);  // Blue for translate
        StdDraw.setPenRadius(0.008);

        // Draw cross/move icon
        double size = 8;
        StdDraw.line(screenX - size, screenY, screenX + size, screenY);
        StdDraw.line(screenX, screenY - size, screenX, screenY + size);

        // Draw arrowheads
        StdDraw.point(screenX - size - 2, screenY);
        StdDraw.point(screenX + size + 2, screenY);
        StdDraw.point(screenX, screenY - size - 2);
        StdDraw.point(screenX, screenY + size + 2);
    }

    private void drawRotateIcon(double screenX, double screenY) {
        StdDraw.setPenColor(255, 150, 100);  // Orange for rotate
        StdDraw.setPenRadius(0.008);

        // Draw circular arc with arrow
        double radius = 10;
        for (int i = 0; i < 8; i++) {
            double angle1 = Math.PI / 2 + (i * Math.PI / 4);
            double angle2 = Math.PI / 2 + ((i + 1) * Math.PI / 4);
            double x1 = screenX + radius * Math.cos(angle1);
            double y1 = screenY + radius * Math.sin(angle1);
            double x2 = screenX + radius * Math.cos(angle2);
            double y2 = screenY + radius * Math.sin(angle2);
            StdDraw.line(x1, y1, x2, y2);
        }

        // Draw arrow tip
        double tipAngle = Math.PI / 2;
        double tipX = screenX + radius * Math.cos(tipAngle);
        double tipY = screenY + radius * Math.sin(tipAngle);
        StdDraw.point(tipX + 2, tipY + 2);
    }

    private void drawScaleIcon(double screenX, double screenY) {
        StdDraw.setPenColor(150, 255, 100);  // Green for scale
        StdDraw.setPenRadius(0.008);

        // Draw expanding square
        double size = 6;
        StdDraw.line(screenX - size, screenY - size, screenX + size, screenY - size);
        StdDraw.line(screenX + size, screenY - size, screenX + size, screenY + size);
        StdDraw.line(screenX + size, screenY + size, screenX - size, screenY + size);
        StdDraw.line(screenX - size, screenY + size, screenX - size, screenY - size);

        // Draw corner markers
        double cornerDist = 10;
        StdDraw.point(screenX - cornerDist, screenY - cornerDist);
        StdDraw.point(screenX + cornerDist, screenY + cornerDist);
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    public String serialize() {
        return String.format("SLIDER %.1f %.1f %s %d %d %d",
                x, y, mode, getR(), getG(), getB());
    }

    public static Slider deserialize(String line) {
        try {
            String[] p = line.trim().split("\\s+");
            if (p.length < 6) return null;

            float x = Float.parseFloat(p[1]);
            float y = Float.parseFloat(p[2]);
            Mode mode = Mode.valueOf(p[3]);
            int r = Integer.parseInt(p[4]);
            int g = Integer.parseInt(p[5]);
            int b = Integer.parseInt(p[6]);

            Slider slider = new Slider(x, y);
            slider.setMode(mode);
            slider.setColor(r, g, b);
            return slider;
        } catch (Exception e) {
            System.err.println("Error deserializing slider: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getType() { return "SLIDER"; }

    @Override
    public String toString() {
        return String.format("Slider(%s, x=%.0f, y=%.0f)", mode, x, y);
    }
}
