import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

//runs on whoever is hosting
public class NetworkServer {

    public static final int TCP_PORT = 54555;
    public static final int UDP_PORT = 54556;

    private final Server server;

    // maps Kryonet connection ID → player ID string
    private final Map<Integer, String> connectedPlayers = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        NetworkServer ns = new NetworkServer();
        ns.start();
        System.out.println("Server running. Press Enter to stop.");
        System.in.read();
        ns.stop();
    }

    public NetworkServer() {
        server = new Server(65536, 65536);
        registerClasses(server.getKryo());
    }


    public void start() throws IOException {
        server.start();
        server.bind(TCP_PORT, UDP_PORT);
        server.addListener(new ServerListener());
        System.out.println("Submarine server started on TCP:" + TCP_PORT + " UDP:" + UDP_PORT);
    }

    public void stop() {
        server.stop();
        System.out.println("Server stopped.");
    }

    // Every packet class will be registered here
    //absolute must
    public static void registerClasses(com.esotericsoftware.kryo.Kryo kryo) {
        kryo.register(Packets.JoinRequest.class);
        kryo.register(Packets.JoinResponse.class);
        kryo.register(Packets.PlayerLeft.class);
        kryo.register(Packets.SubmarineState.class);
        kryo.register(Packets.SoundEvent.class);
        kryo.register(Packets.RadarPing.class);
        kryo.register(Packets.TorpedoState.class);
        kryo.register(Packets.TorpedoDetonate.class);
    }

    //listen for packets
    private class ServerListener extends Listener {

        @Override
        public void connected(Connection conn) {
            System.out.println("New connection: " + conn.getID());
        }

        @Override
        public void disconnected(Connection conn) {
            String playerId = connectedPlayers.remove(conn.getID());
            if (playerId == null) return;

            System.out.println("Player disconnected: " + playerId);

            Packets.PlayerLeft left = new Packets.PlayerLeft();
            left.playerId = playerId;
            server.sendToAllExceptTCP(conn.getID(), left);
        }

        @Override
        public void received(Connection conn, Object object) {

            if (object instanceof Packets.JoinRequest) {
                Packets.JoinRequest req = (Packets.JoinRequest) object;

                String assignedId = "player_" + nextId.getAndIncrement();
                connectedPlayers.put(conn.getID(), assignedId);
                System.out.println("Player joined: " + assignedId + " (requested: " + req.playerId + ")");

                Packets.JoinResponse resp = new Packets.JoinResponse();
                resp.assignedId = assignedId;
                resp.spawnX = 800f;
                resp.spawnY = -100f;
                conn.sendTCP(resp);
                return;
            }

            if (object instanceof Packets.SubmarineState) {
                Packets.SubmarineState state = (Packets.SubmarineState) object;
                state.playerId = connectedPlayers.get(conn.getID());
                if (state.playerId == null) return;
                server.sendToAllExceptUDP(conn.getID(), state);
                return;
            }

            if (object instanceof Packets.SoundEvent) {
                Packets.SoundEvent ev = (Packets.SoundEvent) object;
                ev.playerId = connectedPlayers.get(conn.getID());
                if (ev.playerId == null) return;
                server.sendToAllExceptTCP(conn.getID(), ev);
                return;
            }

            if (object instanceof Packets.RadarPing) {
                Packets.RadarPing ping = (Packets.RadarPing) object;
                ping.playerId = connectedPlayers.get(conn.getID());
                if (ping.playerId == null) return;
                server.sendToAllExceptTCP(conn.getID(), ping);
            }

            if (object instanceof Packets.TorpedoState) {
                Packets.TorpedoState t = (Packets.TorpedoState) object;
                t.playerId = connectedPlayers.get(conn.getID());
                if (t.playerId == null) return;
                server.sendToAllExceptUDP(conn.getID(), t);
            }

            if (object instanceof Packets.TorpedoDetonate) {
                Packets.TorpedoDetonate d = (Packets.TorpedoDetonate) object;
                d.playerId = connectedPlayers.get(conn.getID());
                if (d.playerId == null) return;
                server.sendToAllExceptTCP(conn.getID(), d);
            }
        }
    }
}