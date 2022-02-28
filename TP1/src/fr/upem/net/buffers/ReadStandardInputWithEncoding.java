package fr.upem.net.buffers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

public class ReadStandardInputWithEncoding {

	private static final int BUFFER_SIZE = 1024;

	private static void usage() {
		System.out.println("Usage: ReadStandardInputWithEncoding charset");
	}

	private static String stringFromStandardInput(Charset cs) throws IOException {
		try(ReadableByteChannel in = Channels.newChannel(System.in)) {
			var bb = ByteBuffer.allocate(BUFFER_SIZE);
			while(in.read(bb) != -1) { // -1 si on a atteint la fin
				if(!bb.hasRemaining()) { // si le buffer est plein alors
					var newBuffer = ByteBuffer.allocate(bb.capacity() * 2);
					bb.flip(); // pour reinitialisé la position au debut
					newBuffer.put(bb);
					bb = newBuffer;
				}
			}
			bb.clear();
			return cs.decode(bb).toString();
		}
		//return null;
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		Charset cs = Charset.forName(args[0]);
		System.out.print(stringFromStandardInput(cs));
	}
}
