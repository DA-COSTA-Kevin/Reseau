package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPBurst {
    private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
    private static final Charset UTF8 = Charset.forName("UTF8");
    private static final int BUFFER_SIZE = 1024;

    private enum State {
        SENDING, RECEIVING, FINISHED
    };

    private final List<String> lines;
    private final long timeout;
    private final InetSocketAddress serverAddress;
    private final DatagramChannel dc;
    private final Selector selector;
    private final SelectionKey uniqueKey;

    // TODO add new fields
    private ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    private SocketAddress sender;
    private long lastSend;
    private int idLine = 0;
    private final AnswersLog answersLog;
    private final String[] upperCaseLines;


    private ClientIdUpperCaseUDPBurst.State state;

    private static void usage() {
        System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
    }

    private ClientIdUpperCaseUDPBurst(List<String> lines, long timeout, InetSocketAddress serverAddress,
                                         DatagramChannel dc, Selector selector, SelectionKey uniqueKey){
        this.lines = lines;
        this.timeout = timeout;
        this.serverAddress = serverAddress;
        this.dc = dc;
        this.selector = selector;
        this.uniqueKey = uniqueKey;
        this.state = ClientIdUpperCaseUDPBurst.State.SENDING;

        this.upperCaseLines = new String[lines.size()];
        this.answersLog = new AnswersLog(lines.size());
    }

    public static ClientIdUpperCaseUDPBurst create(String inFilename, long timeout,
                                                      InetSocketAddress serverAddress) throws IOException {
        Objects.requireNonNull(inFilename);
        Objects.requireNonNull(serverAddress);
        Objects.checkIndex(timeout, Long.MAX_VALUE);

        // Read all lines of inFilename opened in UTF-8
        var lines = Files.readAllLines(Path.of(inFilename), UTF8);
        var dc = DatagramChannel.open();
        dc.configureBlocking(false);
        dc.bind(null);
        var selector = Selector.open();
        var uniqueKey = dc.register(selector, SelectionKey.OP_WRITE);
        return new ClientIdUpperCaseUDPBurst(lines, timeout, serverAddress, dc, selector, uniqueKey);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        var inFilename = args[0];
        var outFilename = args[1];
        var timeout = Long.parseLong(args[2]);
        var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

        // Create client with the parameters and launch it
        var upperCaseLines = create(inFilename, timeout, server).launch();

        Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    private List<String> launch() throws IOException, InterruptedException {
        try {
            while (!isFinished()) {
                try {
                    selector.select(this::treatKey, updateInterestOps());
                } catch (UncheckedIOException tunneled) {
                    throw tunneled.getCause();
                }
            }
            return Arrays.asList(upperCaseLines); // modif
        } finally {
            dc.close();
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite();
            }
            if (key.isValid() && key.isReadable()) {
                doRead();
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    /**
     * Updates the interestOps on key based on state of the context
     *
     * @return the timeout for the next select (0 means no timeout)
     */

    private long updateInterestOps() {
        switch(state) {
            case RECEIVING:
                var time = lastSend + timeout - System.currentTimeMillis();
                if (time <= 0) {
                    state = State.SENDING;
                } else {
                    uniqueKey.interestOps(SelectionKey.OP_READ);
                    return time;
                }
            case SENDING :
                uniqueKey.interestOps(SelectionKey.OP_WRITE);
                return 0;
            default :
                return 0;
        }
    }

    private boolean isFinished() {
        return state == State.FINISHED;
    }

    /**
     * Performs the receptions of packets
     *
     * @throws IOException
     */

    private void doRead() throws IOException {
        // TODO
        buffer.clear();
        sender = dc.receive(buffer);

        if (sender == null){
            System.out.println("Paquet non reçu");
            return;
        }
        buffer.flip();

        var id = (int) buffer.getLong();
        if(!answersLog.get(id)){ // S'il on n'a pas deja l'id
            var message = UTF8.decode(buffer).toString(); // on recupere le message
            System.out.println("String : " + message);

            upperCaseLines[id] = message;
            answersLog.add(id); // on met a jour cet id dans notre bitSet
        }

        if(!answersLog.haveAll()){
            state = State.SENDING;
        } else {
            state = State.FINISHED;
        }
    }

    /**
     * Tries to send the packets
     *
     * @throws IOException
     */

    private void doWrite() throws IOException {
        // TODO
        buffer.clear();

        buffer.putLong(idLine);
        var message = UTF8.encode(lines.get(idLine));
        buffer.put(message);

        buffer.flip();
        dc.send(buffer, serverAddress);

        if(buffer.hasRemaining()){
            System.out.println("Aucun paquet envoyé");
            return;
        }

        idLine++;
        lastSend = System.currentTimeMillis();

        if(idLine == lines.size()){
            idLine = 0;
            state = State.RECEIVING;
        }
    }

    private static class AnswersLog {
        // TODO Thread-safe class handling the information about missing lines
        private final int nb_line;
        private final BitSet bitset;
        private int cpt = 0;

        AnswersLog(int nb_line){
            Objects.requireNonNull(nb_line);
            if(nb_line < 0){
                throw new IllegalArgumentException("nb_line must be > 0");
            }
            this.nb_line = nb_line;
            this.bitset = new BitSet(this.nb_line);
        }

        // Permet d'avoir la liste des numeros de ligne a envoyer
        public List<Integer> toListId() {
            var lst = new ArrayList<Integer>();
            for(var i = bitset.nextClearBit(0); i < nb_line; i = bitset.nextClearBit(i+1)){
                // System.out.println(i);
                lst.add(i);
                cpt++;
            }
            return lst;
        }

        // renvoie true si on a le meme nombre de lignes et le même nombre de données arrivées sinon false
        public boolean haveAll() {
            return nb_line == cpt;
        }

        // renvoie true si on le bitSet contient l'id sinon false
        public boolean get(int id) {
            return bitset.get(id);
        }

        // on met a jour l'id dans le bitSet
        public void add(int id) {
            if (!bitset.get(id)) { // on regarde si on a pas deja recu cette id
                bitset.set(id);
                cpt++;
            }
        }
    }
}
