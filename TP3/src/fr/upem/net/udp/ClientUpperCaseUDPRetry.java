package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ClientUpperCaseUDPRetry {
    public static final int BUFFER_SIZE = 1024;

    private static void usage() {
        System.out.println("Usage : NetcatUDP host port charset");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            usage();
            return;
        }

        var queue = new ArrayBlockingQueue<String>(BUFFER_SIZE);
        var server = new InetSocketAddress(args[0], Integer.parseInt(args[1]));
        var cs = Charset.forName(args[2]);
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try( DatagramChannel dc = DatagramChannel.open()){
            dc.bind(null); //repris du cours
            try (var scanner = new Scanner(System.in)) {
                var thread = new Thread(() -> {
                    while(!Thread.currentThread().isInterrupted()) { //tant que la thread n'est pas bloqué
                        try {
                            var sender = (InetSocketAddress) dc.receive(buffer); //repris du cours

                            buffer.flip();
                            System.out.println("Received " + buffer.remaining() + " bytes from " + sender);

                            var msg = cs.decode(buffer).toString();
                            queue.put("message -> " + msg);
                            buffer.clear();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                thread.start();

                while (scanner.hasNextLine()) {
                    var line = scanner.nextLine();
                    dc.send(cs.encode(line), server);

                    var msg = queue.poll(1, TimeUnit.SECONDS);
                    while(msg == null) {
                        System.out.println("Le serveur n'a pas répondu...\n Tentative de renvoie...");
                        dc.send(cs.encode(line), server);
                        msg = queue.poll(1, TimeUnit.SECONDS);
                    }
                    System.out.println("String : " + msg);
                }
            }
        }

    }
}
