package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEcho {
    private static final Logger logger = Logger.getLogger(ServerEcho.class.getName());

    private final DatagramChannel dc;
    private final Selector selector;
    private final int BUFFER_SIZE = 1024;
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private SocketAddress sender;
    private int port;

    public ServerEcho(int port) throws IOException {
        this.port = port;
        selector = Selector.open();
        dc = DatagramChannel.open();
        dc.bind(new InetSocketAddress(port));

        // TODO set dc in non-blocking mode and register it to the selector
        dc.configureBlocking(false);
        dc.register(selector, SelectionKey.OP_READ);
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port " + port);
        while (!Thread.interrupted()) {
            // TODO modifier pour catch l'exception
            try{
                selector.select(this::treatKey);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }
    }

    private void treatKey(SelectionKey key) {
        try {
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
        } catch (IOException e) {
            // TODO
            throw new UncheckedIOException(e);
        }

    }

    private void doRead(SelectionKey key) throws IOException {
        // TODO
        buffer.clear();
        var channel = (DatagramChannel) key.channel();
        sender = channel.receive(buffer);

        if (sender != null){
            System.out.println("Paquet reçu");
            buffer.flip();
            key.interestOps(SelectionKey.OP_WRITE);
        } else {
            key.channel().keyFor(selector);
            System.out.println("Paquet non reçu");
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO
        var channel = (DatagramChannel) key.channel();
        channel.send(buffer, sender);

        if(!buffer.hasRemaining()){
            System.out.println("Paquet envoyé");
            key.interestOps(SelectionKey.OP_READ);
        } else {
            System.out.println("Aucun paquet envoyé");
            return;
        }
    }

    public static void usage() {
        System.out.println("Usage : ServerEcho port");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerEcho(Integer.parseInt(args[0])).serve();
    }
}
