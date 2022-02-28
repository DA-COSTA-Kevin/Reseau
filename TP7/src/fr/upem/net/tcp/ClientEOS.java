package fr.upem.net.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.logging.Logger;

public class ClientEOS {

    public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
    public static final int BUFFER_SIZE = 1024;
    public static final Logger logger = Logger.getLogger(ClientEOS.class.getName());

    /**
     * This method: 
     * - connect to server 
     * - writes the bytes corresponding to request in UTF8 
     * - closes the write-channel to the server 
     * - stores the bufferSize first bytes of server response 
     * - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @param bufferSize
     * @return the UTF8 string corresponding to bufferSize first bytes of server
     *         response
     * @throws IOException
     */

    public static String getFixedSizeResponse(String request, SocketAddress server, int bufferSize) throws IOException {
        // TODO

        // connect to server
        var sc = SocketChannel.open();
        sc.connect(server);

        // writes the bytes corresponding to request in UTF8
        var buffer = ByteBuffer.allocate(bufferSize);
        buffer.put(UTF8_CHARSET.encode(request));
        buffer.flip();

        sc.write(buffer);
        // closes the write-channel to the server
        sc.shutdownOutput();
        buffer.clear();

        // stores the bufferSize first bytes of server response
        var read = sc.read(buffer);
        while(read != -1 && buffer.hasRemaining()){
            read = sc.read(buffer);
        }
        buffer.flip();

        sc.close();
        return UTF8_CHARSET.decode(buffer).toString();
    }

    /**
     * This method: 
     * - connect to server 
     * - writes the bytes corresponding to request in UTF8 
     * - closes the write-channel to the server 
     * - reads and stores all bytes from server until read-channel is closed 
     * - return the corresponding string in UTF8
     *
     * @param request
     * @param server
     * @return the UTF8 string corresponding the full response of the server
     * @throws IOException
     */

    public static String getUnboundedResponse(String request, SocketAddress server) throws IOException {
        // TODO
        // connect to server
        var sc = SocketChannel.open();
        sc.connect(server);

        // writes the bytes corresponding to request in UTF8
        var buffer = ByteBuffer.allocate(BUFFER_SIZE);
        buffer.put(UTF8_CHARSET.encode(request));
        buffer.flip();

        sc.write(buffer);
        // closes the write-channel to the server
        sc.shutdownOutput();
        buffer.clear();

        // reads and stores all bytes from server until read-channel is closed
        /*var read = sc.read(buffer);
        while(read != -1){
            if(!buffer.hasRemaining()){*/
                /*var size = buffer.capacity()*2;
                var tmp = ByteBuffer.allocate(size);

                buffer.flip();
                tmp.put(buffer);

                buffer = tmp;*/
                /*buffer = ByteBuffer.allocate(buffer.capacity()*2);
            }
            read = sc.read(buffer);
        }*/
        while(readFully(sc,buffer)){
            readFully(sc,buffer);
        }

        buffer.flip();
        sc.close();
        return UTF8_CHARSET.decode(buffer).toString();
    }

    /**
     * Fill the workspace of the Bytebuffer with bytes read from sc.
     *
     * @param sc
     * @param buffer
     * @return false if read returned -1 at some point and true otherwise
     * @throws IOException
     */
    static boolean readFully(SocketChannel sc, ByteBuffer buffer) throws IOException {
        var read = sc.read(buffer);
        while(read != -1){
            if(!buffer.hasRemaining()){
                buffer = ByteBuffer.allocate(buffer.capacity()*2);
            }
            read = sc.read(buffer);
        }
        return false;
    }

    public static void main(String[] args) throws IOException {
        var google = new InetSocketAddress("www.google.fr", 80);
        //System.out.println(getFixedSizeResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google, 512));
         System.out.println(getUnboundedResponse("GET / HTTP/1.1\r\nHost: www.google.fr\r\n\r\n", google));
    }
}
