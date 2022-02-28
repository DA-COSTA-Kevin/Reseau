package fr.upem.net.udp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardOpenOption.*;

public class ClientIdUpperCaseUDPOneByOne {

	private static Logger logger = Logger.getLogger(ClientIdUpperCaseUDPOneByOne.class.getName());
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final int BUFFER_SIZE = 1024;

	private record Response(long id, String message) {
	};

	private final String inFilename;
	private final String outFilename;
	private final long timeout;
	private final InetSocketAddress server;
	private final DatagramChannel dc;
	private final SynchronousQueue<Response> queue = new SynchronousQueue<>();

	public static void usage() {
		System.out.println("Usage : ClientIdUpperCaseUDPOneByOne in-filename out-filename timeout host port ");
	}

	public ClientIdUpperCaseUDPOneByOne(String inFilename, String outFilename, long timeout, InetSocketAddress server)
			throws IOException {
		this.inFilename = Objects.requireNonNull(inFilename);
		this.outFilename = Objects.requireNonNull(outFilename);
		this.timeout = timeout;
		this.server = server;
		this.dc = DatagramChannel.open();
		dc.bind(null);
	}

	private void listenerThreadRun() {
		var buffer = ByteBuffer.allocate(BUFFER_SIZE);
		while (true) {
			try {
				var sender = (InetSocketAddress) dc.receive(buffer); //repris du cours

				buffer.flip();
				//System.out.println("Received " + buffer.remaining() + " bytes from " + sender);

				var id = buffer.getLong();
				var msg = UTF8.decode(buffer).toString();

				queue.put(new Response(id, msg));

				buffer.clear();
			} catch (InterruptedException | AsynchronousCloseException e) {
				logger.log(Level.FINE, "", e);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "", e);
			}
		}
	}

	public void launch() throws IOException, InterruptedException {
		try {
			var listenerThread = new Thread(this::listenerThreadRun);
			listenerThread.start();

			// Read all lines of inFilename opened in UTF-8
			var lines = Files.readAllLines(Path.of(inFilename), UTF8);

			var upperCaseLines = new ArrayList<String>();

			// Partie rajouté
			long id_line = 0L;
			var buffer = ByteBuffer.allocate(BUFFER_SIZE);
			
			for (var line : lines) {

				buffer.putLong(id_line);
				buffer.put(UTF8.encode(line));
				var last_send = 0L;
				
				//dc.send(buffer, server);
				//buffer.flip();
				
				while(true) {
					var time = System.currentTimeMillis(); // time du debut
					if(time - last_send > timeout) {
						try {
							dc.send(buffer, server);
							buffer.flip();
							last_send = time; // dernier envoie
						} catch (AsynchronousCloseException e){
							logger.log(Level.FINE, "", e);
						} catch (IOException e) {
							logger.log(Level.SEVERE, "", e);
						}
					}
					var time_pass = (last_send + timeout) - time; // temps passer
					var response = queue.poll(time_pass, TimeUnit.MILLISECONDS);
					// Analyse de la response 
					if(response == null) { // Echec on retente
						last_send = 0;
					}
					else if(response.id == id_line) { // Reussite on ajoute a la liste
						System.out.println("Time since last receive: " + time_pass + " ms.");
						System.out.println("String : " + response.message);
						upperCaseLines.add(response.message); // ajout du message en majuscule a la liste
						break;
					}
				}
				id_line += 1; // on augmente l'id de la line
			}
			listenerThread.interrupt();
			Files.write(Paths.get(outFilename), upperCaseLines, UTF8, CREATE, WRITE, TRUNCATE_EXISTING);
		} finally {
			dc.close();
		}
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
		new ClientIdUpperCaseUDPOneByOne(inFilename, outFilename, timeout, server).launch();
	}
}
