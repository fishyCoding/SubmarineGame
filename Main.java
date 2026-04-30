/*

Main engine, allows for rocks to be moved and map to be edited

saved to a txt file


 */
public class Main {

    //set up variables
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 800;

    //seafloor depths (in meters)
    private static final float SURFACE_LEVEL = 0f;
    private static final float SEAFLOOR_TOP = -1820f;
    private static final float  SEAFLOOR_BASE = -2400f;

    //save file
    private static final String DATA_FILE = "sprites.txt";

    //engine state variables, most get set during the start of runtime
    private static GameEngine engine;
    private static BottomRockLayer bottomLayer;
    private static Rock selectedRock;
    private static int currentDepth = 0;

    //mouse click for engine inputs: 
    private static boolean mouseWasDown = false;
    private static float lastMouseX, lastMouseY;

    //UI and background
    static Water watergradient;
    static EngineUI UI;

    public static void main(String[] args) {
        
        //canvas setup
        StdDraw.setCanvasSize(WIDTH, HEIGHT);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.enableDoubleBuffering();

        //Start up diff classes and load data from txt file that stores the rocks
        engine = new GameEngine(DATA_FILE);
        engine.panCamera(0, -200);
        bottomLayer = new BottomRockLayer(-WIDTH, WIDTH * 4, 120, SEAFLOOR_TOP, SEAFLOOR_BASE);
        watergradient = new Water(HEIGHT, WIDTH, SURFACE_LEVEL, engine);
        UI = new EngineUI(engine, WIDTH, HEIGHT);

        //w documentation
        printHelp();
        //enigine loop
        while (true) {
            handleInput();
            render();
            StdDraw.show();
            StdDraw.pause(16);
        }
    }

    private static void handleCam(){
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_LEFT))  engine.panCamera(-10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_RIGHT)) engine.panCamera( 10,  0);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_UP))    engine.panCamera(  0, 10);
        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_DOWN))  engine.panCamera(  0,-10);
    }
    

    private static void handleInput() {
        handleCam();

        //toggle depth
        if (StdDraw.isKeyPressed(' ')) {
            currentDepth = 1 - currentDepth;
            if (selectedRock != null){
                 selectedRock.setDepth(currentDepth);
            }
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('U') || StdDraw.isKeyPressed('u')) {
            if (selectedRock != null) {
                selectedRock.removeLastVertex();
            }
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('D') || StdDraw.isKeyPressed('d')) {
            engine.deleteSprite(worldMouseX(), worldMouseY());
            selectedRock = null;
            StdDraw.pause(200);
        }


        if (StdDraw.isKeyPressed('N') || StdDraw.isKeyPressed('n')) {
            Rock newRock = new Rock(worldMouseX(), worldMouseY(), currentDepth);
            engine.addRock(newRock);
            selectedRock = newRock;
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed('S') || StdDraw.isKeyPressed('s')) {
            engine.saveSprites();
            StdDraw.pause(200);
        }

        if (StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_ESCAPE)) {
            engine.saveSprites();
            System.out.println("Goodbye.");
            System.exit(0);
        }

        boolean mouseDown = StdDraw.isMousePressed();
        boolean shiftDown = StdDraw.isKeyPressed(java.awt.event.KeyEvent.VK_SHIFT);
        float mouseX = worldMouseX();
        float mouseY = worldMouseY();

        //on mouse click
        if (mouseDown && !mouseWasDown) {
            //if ur holding shift and uv selected a rock
            if (shiftDown && selectedRock !=null) {
                selectedRock.addVertex(mouseX,mouseY);
                System.out.println("Added vertex. Now "+selectedRock.getVertexCount()+" vertices.");
            } else {
                //check if u clicked on a rock and select it
                Sprite sprite = engine.getSpriteAt(mouseX,mouseY);
                //check if its a rock to move it
                //im not sure if it is needed lowk theres only rocks
                if (sprite instanceof Rock) {
                    selectedRock = (Rock) sprite;
                    currentDepth = selectedRock.getDepth();
                    System.out.println("Selected: " + selectedRock);
                } else {
                    selectedRock = null;
                }
            }
            //update last mouse pos for dragging
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        } 
        //on mouse drag, move the rock uv selected
        else if (mouseDown && mouseWasDown && selectedRock != null && !shiftDown) {
            float deltaX = mouseX-lastMouseX;
            float deltaY = mouseY-lastMouseY;
            selectedRock.setPosition(selectedRock.getX()+deltaX, selectedRock.getY()+deltaY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        
        mouseWasDown=mouseDown;
    }

    private static void render() {
        watergradient.drawWaterGradient();

        for (Sprite s : engine.getSprites()) {
            if (s instanceof Rock && ((Rock) s).getDepth() == 0)
                s.draw(engine);
        }

        for (Sprite s : engine.getSprites()) {
            if (s instanceof Rock && ((Rock) s).getDepth() == 1)
                s.draw(engine);
        }

        for (Sprite s : engine.getSprites()) {
            if (!(s instanceof Rock))
                s.draw(engine);
        }

        bottomLayer.draw(engine);

        //highlight selected rock with a circle to show its been clicked
        if (selectedRock != null) {
            double sx = engine.worldToScreenX(selectedRock.getX());
            double sy = engine.worldToScreenY(selectedRock.getY());
            StdDraw.setPenColor(255, 200, 100);
            StdDraw.setPenRadius(0.003);
            StdDraw.circle(sx, sy, 15);
        }

        UI.drawUI(null, currentDepth, 1.0f);
    }


    public static float worldMouseX() { return engine.screenToWorldX(StdDraw.mouseX()); }
    private static float worldMouseY() { return engine.screenToWorldY(StdDraw.mouseY()); }

    private static void printHelp() {
        System.out.println("=== Submarine Game Editor ===");
        System.out.println("  Click              → select rock");
        System.out.println("  Shift+Click        → add vertex to selected rock");
        System.out.println("  Drag               → move selected rock");
        System.out.println("  Arrow Keys         → scroll camera");
        System.out.println("  Space              → toggle layer (bg / fg)");
        System.out.println("  U                  → undo last vertex");
        System.out.println("  N                  → create new rock");
        System.out.println("  D                  → delete sprite under cursor");
        System.out.println("  C                  → clear all sprites");
        System.out.println("  S                  → save");
        System.out.println("  ESC                → save & exit");
    }
}