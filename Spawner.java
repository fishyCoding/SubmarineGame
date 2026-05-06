public class Spawner {
    static final int[][] SPAWNZONESX = {{-1083, 2000}, {3600, 5600}};
    static final int UPPERSPAWNY = -150;
    static final int LOWERSPAWNY = -400;

    public static int getSpawnX() {
        if (Math.random() > 0.5f) {
            return (int)(SPAWNZONESX[0][0] + (SPAWNZONESX[0][1] - SPAWNZONESX[0][0]) * Math.random());
        } else {
            return (int)(SPAWNZONESX[1][0] + (SPAWNZONESX[1][1] - SPAWNZONESX[1][0]) * Math.random());
        }
    }

    public static int getSpawnY() {
        return (int)(LOWERSPAWNY + (UPPERSPAWNY - LOWERSPAWNY) * Math.random());
    }
}