package fr.upem.net.udp.nonblocking;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Logger;

public class ServerEchoMultiPort {
    private static final Logger logger = Logger.getLogger(ServerEchoMultiPort.class.getName());
    private final int BUFFER_SIZE = 1024;
    private final Selector selector;

    private int portStart;
    private int portEnd;

    private class Context {
        private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        private InetSocketAddress sender;
    }

    public ServerEchoMultiPort(int portStart, int portEnd) throws IOException {
        this.portStart = portStart;
        this.portEnd = portEnd;
        selector = Selector.open();

        for (var i = portStart; i <= portEnd; i++) {
            var dc = DatagramChannel.open();
            dc.configureBlocking(false);
            dc.bind(new InetSocketAddress(i));
            dc.register(selector, SelectionKey.OP_READ, new Context());
        }
    }

    public void serve() throws IOException {
        logger.info("ServerEcho started on port " + portStart + " -> " + portEnd);
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
        var context = (Context) key.attachment();
        context.buffer.clear();

        var channel = (DatagramChannel) key.channel();
        context.sender = (InetSocketAddress) channel.receive(context.buffer);

        if (context.sender != null){
            System.out.println("Paquet reçu");
            context.buffer.flip();
            key.interestOps(SelectionKey.OP_WRITE);
        } else {
            System.out.println("Paquet non reçu");
            return;
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        // TODO
        var context = (Context) key.attachment();
        var channel = (DatagramChannel) key.channel();
        channel.send(context.buffer, context.sender);

        if(!context.buffer.hasRemaining()){
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
        if (args.length != 2) { // modif a 2 au lieu de 1
            usage();
            return;
        }
        new ServerEchoMultiPort(Integer.parseInt(args[0]), Integer.parseInt(args[1])).serve();
    }
}
