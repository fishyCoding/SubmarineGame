public class Spawner {
    final static int[][] SPAWNZONESX = {{-1083,2000},{3600,5600}};
    final static int UPPERSPAWNY = -150;
    final static int LOWERSPAWNY=-400;
    
    public static int getSpawnX(){
        double output;
        if (Math.random()>0.5f){
            output= (int) SPAWNZONESX[0][0]+(SPAWNZONESX[0][1]-SPAWNZONESX[0][0])*Math.random();
        }
        else{
            output= (int) SPAWNZONESX[1][0]+(SPAWNZONESX[1][1]-SPAWNZONESX[1][0])*Math.random();
        }
        return (int) output;
    }
    public static int getSpawnY(){
        double output= LOWERSPAWNY+(UPPERSPAWNY-UPPERSPAWNY)*Math.random();
        return (int) output;
    }
}
