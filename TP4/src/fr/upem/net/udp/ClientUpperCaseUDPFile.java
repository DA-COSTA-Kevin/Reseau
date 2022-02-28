package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.*;

public class ClientUpperCaseUDPFile {
    private final static Charset UTF8 = StandardCharsets.UTF_8;
    private final static int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage : ClientUpperCaseUDPFile in-filename out-filename timeout host port ");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 5) {
            usage();
            return;
        }

        var inFilename = args[0];
        var outFilename = args[1];
        var timeout = Integer.parseInt(args[2]);
        var server = new InetSocketAddress(args[3], Integer.parseInt(args[4]));

        // Read all lines of inFilename opened in UTF-8
        var lines = Files.readAllLines(Path.of(inFilename), UTF8);
        var upperCaseLines = new ArrayList<String>();

        // Partie rajouté
        var queue = new ArrayBlockingQueue<String>(BUFFER_SIZE);
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try( DatagramChannel dc = DatagramChannel.open()) {
            dc.bind(null); //repris du cours

            var thread = new Thread(() -> {
                var cpt = 0;
                while (cpt < lines.size()) { //tant qu'il y a encore des lignes
                    try {
                        var sender = (InetSocketAddress) dc.receive(buffer); //repris du cours
                        cpt++;

                        buffer.flip();
                        System.out.println("Received " + buffer.remaining() + " bytes from " + sender);

                        var msg = UTF8.decode(buffer).toString();
                        queue.put("message -> " + msg);
                        buffer.clear();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            thread.start();

            for (var line : lines) {
                dc.send(UTF8.encode(line), server);

                var msg = queue.poll(timeout, TimeUnit.MILLISECONDS);
                while (msg == null) {
                    System.out.println("Le serveur n'a pas répondu...\n Tentative de renvoie...");
                    dc.send(UTF8.encode(line), server);
                    msg = queue.poll(timeout, TimeUnit.MILLISECONDS); // timeout donné
                }
                System.out.println("String : " + msg);
                upperCaseLines.add(msg); // ajout du message en majuscule a la liste
            }
        }

        // Write upperCaseLines to outFilename in UTF-8
        Files.write(Path.of(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
    }
}