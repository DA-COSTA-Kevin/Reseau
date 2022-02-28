package fr.upem.net.udp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;
import java.util.logging.Logger;

public class ServerLongSumUDP {
    private static final Logger logger = Logger.getLogger(ServerLongSumUDP.class.getName());
    private static final int BUFFER_SIZE = 1024;

    private final DatagramChannel dc;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

    // variable ajouté
    private final HashMap<Paquet, ClientData> hashmap = new HashMap<>();

    public ServerLongSumUDP(int port) throws IOException {
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));
        logger.info("ServerLongSumUDP started on port " + port);
    }

    private record Paquet(InetSocketAddress ip, long sessionId) {}

    public void serve() throws IOException {
        try {
            while (!Thread.interrupted()) {
                buffer.clear();

                // 1) receive request from client
                var sender = (InetSocketAddress) dc.receive(buffer);
                logger.info("Received " + buffer.position() + " bytes from " + sender.toString());

                // 2) read type
                buffer.flip();
                var type = buffer.get();

                // 3) read sessionID
                var sessionId = buffer.getLong();

                // 4) read id position operation
                var idPosOper = buffer.getLong();

                // 5) read total operation
                var totalOper = buffer.getLong();

                // 6) read operation value
                var opValue = buffer.getLong();

                // 7) save paquet
                var paquet = new Paquet(sender, sessionId);

                if (type == 1){ // si c'est bien un OP
                    /*var clientData = new ClientData(totalOper);
                    hashmap.putIfAbsent(paquet, clientData); // si données pas deja presente on ajoute

                    // Si la clé est deja présente on met a jour les données
                    clientData = hashmap.get(paquet);
                    clientData.add((int) idPosOper, opValue);
                    hashmap.put(paquet, clientData);*/

                    // A faire a la place de putIfAbsent
                    var clientData = hashmap.computeIfAbsent(paquet, key -> new ClientData(totalOper));
                    clientData.add((int) idPosOper, opValue);

                    // 8) send ACK
                    buffer.clear();
                    buffer.put((byte) 2); // type ACK
                    buffer.putLong(sessionId);
                    buffer.putLong(idPosOper);

                    buffer.flip();
                    dc.send(buffer, sender);

                    // 9) send RES
                    if(clientData.haveAll()){ // si true on a bien tout reçu
                        buffer.clear();
                        buffer.put((byte) 3); // type RES
                        buffer.putLong(sessionId);
                        buffer.putLong(clientData.sum);

                        buffer.flip();
                        dc.send(buffer, sender);
                    }
                }
            }
        } finally {
            dc.close();
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerIdUpperCaseUDP port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }

        var port = Integer.parseInt(args[0]);

        if (!(port >= 1024) & port <= 65535) {
            logger.severe("The port number must be between 1024 and 65535");
            return;
        }

        try {
            new ServerLongSumUDP(port).serve();
        } catch (BindException e) {
            logger.severe("Server could not bind on " + port + "\nAnother server is probably running on this port.");
            return;
        }
    }

    private static class ClientData {
        private final int nb_operations;
        private int cpt = 0;
        private long sum = 0L;
        private final BitSet bitset;

        public ClientData(long nb_operations) {
            Objects.requireNonNull(nb_operations);
            if (nb_operations < 0L) {
                throw new IllegalArgumentException("nb_operations must be > 0L");
            }
            this.nb_operations = (int) nb_operations;
            this.bitset = new BitSet(this.nb_operations);
        }

        public boolean haveAll() {
            return nb_operations == cpt;
        }

        public void add(int id, long val_operation) {
            if (!bitset.get(id)) { // on regarde si on a pas deja recu cette id
                sum += val_operation;
                bitset.set(id);
                cpt++;
            }
        }
    }
}
