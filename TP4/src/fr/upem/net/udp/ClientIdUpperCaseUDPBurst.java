package fr.upem.net.udp;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientIdUpperCaseUDPBurst {

        private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPBurst.class.getName());
        private static final Charset UTF8 = StandardCharsets.UTF_8;
        private static final int BUFFER_SIZE = 1024;
        private final List<String> lines;
        private final int nbLines;
        private final String[] upperCaseLines; //
        private final int timeout;
        private final String outFilename;
        private final InetSocketAddress serverAddress;
        private final DatagramChannel dc;
        private final AnswersLog answersLog;         // Thread-safe structure keeping track of missing responses

        public static void usage() {
            System.out.println("Usage : ClientIdUpperCaseUDPBurst in-filename out-filename timeout host port ");
        }

        public ClientIdUpperCaseUDPBurst(List<String> lines,int timeout,InetSocketAddress serverAddress,String outFilename) throws IOException {
            this.lines = lines;
            this.nbLines = lines.size();
            this.timeout = timeout;
            this.outFilename = outFilename;
            this.serverAddress = serverAddress;
            this.dc = DatagramChannel.open();
            dc.bind(null);
            this.upperCaseLines = new String[nbLines];
            this.answersLog = new AnswersLog(nbLines);
        }

        private void senderThreadRun() {
			// TODO : body of the sender thread
            var buffer = ByteBuffer.allocate(BUFFER_SIZE);
            var last_send = 0L;

            while(!Thread.currentThread().isInterrupted()) {
                var time = System.currentTimeMillis(); // time du debut
                var lst = answersLog.toListId(); // lst des ID

                // Si la liste est vide, on continue pas
                if (lst.isEmpty()){
                    System.out.println("lst is empty");
                    break;
                }

                // sinon on envoie
                else if (time - last_send > timeout) {
                    try {
                        System.out.println("lst -> " + lst);
                        for(var id: lst){
                            //System.out.println("id -> " + id);

                            // Remplit le buffer avec l'id et le message
                            buffer.putLong(id); // id
                            buffer.put(UTF8.encode(lines.get(id))); // message
                            buffer.flip();

                            dc.send(buffer, serverAddress);
                            buffer.flip();
                        }
                        last_send = time; // dernier envoie
                    } catch (AsynchronousCloseException e){
                        logger.log(Level.FINE, "", e);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "", e);
                    }
                }
            }
        }

        public void launch() throws IOException {
            Thread senderThread = new Thread(this::senderThreadRun);
            senderThread.start();
            
            // TODO : body of the receiver thread
            var buffer = ByteBuffer.allocate(BUFFER_SIZE);

            while (!answersLog.haveAll()){ // Tant qu'on a pas tout recupere on continue
                try {
                    dc.receive(buffer); // on recupere les responses
                    buffer.flip();

                    var id = (int) buffer.getLong(); // on recupere l'id

                    // S'il on n'a pas deja l'id
                    if(!answersLog.get(id)){
                        var message = UTF8.decode(buffer).toString(); // on recupere le message
                        System.out.println("String : " + message);

                        upperCaseLines[id] = message; // on stocke le message a l'id donné (pour mettre dans la bonne ligne)
                        answersLog.add(id); // on met a jour cet id dans notre bitSet
                    }

                    buffer.clear();
                } catch (AsynchronousCloseException e){
                    logger.log(Level.FINE, "", e);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "", e);
                }
            }
            senderThread.interrupt(); // on stop la thread

            Files.write(Paths.get(outFilename),Arrays.asList(upperCaseLines), UTF8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        }

        public static void main(String[] args) throws IOException, InterruptedException {
            if (args.length !=5) {
                usage();
                return;
            }

            String inFilename = args[0];
            String outFilename = args[1];
            int timeout = Integer.valueOf(args[2]);
            String host=args[3];
            int port = Integer.valueOf(args[4]);
            InetSocketAddress serverAddress = new InetSocketAddress(host,port);

            //Read all lines of inFilename opened in UTF-8
            List<String> lines= Files.readAllLines(Paths.get(inFilename),UTF8);
            //Create client with the parameters and launch it
            ClientIdUpperCaseUDPBurst client = new ClientIdUpperCaseUDPBurst(lines,timeout,serverAddress,outFilename);
            client.launch();

        }

        private static class AnswersLog {
            // TODO Thread-safe class handling the information about missing lines
            private final int nb_line;
            private final BitSet lock;

            AnswersLog(int nb_line){
                Objects.requireNonNull(nb_line);
                if(nb_line < 0){
                    throw new IllegalArgumentException("nb_line must be > 0");
                }
                this.nb_line = nb_line;
                this.lock = new BitSet(this.nb_line);
            }

            // Permet d'avoir la liste des numeros de ligne a envoyer
            public List<Integer> toListId() {
                synchronized (lock){
                    var lst = new ArrayList<Integer>();
                    for(var i = lock.nextClearBit(0); i < nb_line; i = lock.nextClearBit(i+1)){
                        // System.out.println(i);
                        lst.add(i);
                    }
                    return lst;
                }
            }

            // renvoie true si on a le meme nombre de lignes et le même nombre de données arrivées sinon false
            public boolean haveAll() {
                synchronized (lock){
                    return nb_line == lock.cardinality();
                }
            }

            // renvoie true si on le bitSet contient l'id sinon false
            public boolean get(int id) {
                synchronized (lock){
                    return lock.get(id);
                }
            }

            // on met a jour l'id dans le bitSet
            public void add(int id) {
                synchronized (lock) {
                    lock.set(id);
                }
            }
        }
    }


