package us.bringardner.net.pop3.server;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes message content as a POP3 multi-line response (RFC 1939 section 3):
 * a line starting with "." gets an extra "." (byte-stuffing), a bare LF becomes
 * CRLF, and {@link #close()} ends the last line if needed and writes the
 * terminating ".". Closing also closes the underlying stream.
 */
public class DotStuffingOutputStream extends FilterOutputStream {

	private int prev = '\n'; // at the start of a line

	public DotStuffingOutputStream(OutputStream out) {
		super(out);
	}

	@Override
	public void write(int b) throws IOException {
		b &= 0xff;
		if (prev == '\n' && b == '.') {
			out.write('.');
		}
		if (b == '\n' && prev != '\r') {
			out.write('\r');
		}
		out.write(b);
		prev = b;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		for (int i = off; i < off + len; i++) {
			write(b[i]);
		}
	}

	@Override
	public void close() throws IOException {
		if (prev != '\n') {
			out.write('\r');
			out.write('\n');
		}
		out.write('.');
		out.write('\r');
		out.write('\n');
		prev = '\n';
		super.close();
	}

	/**
	 * The number of octets content takes in a response before byte-stuffing: bare
	 * LFs become CRLF and an unterminated last line gets a CRLF. This is the size
	 * reported by LIST and STAT.
	 */
	public static final class Counter extends OutputStream {
		private long count;
		private int prev = '\n';
		private boolean nonAscii;

		@Override
		public void write(int b) {
			b &= 0xff;
			count += (b == '\n' && prev != '\r') ? 2 : 1;
			if (b >= 128) {
				nonAscii = true;
			}
			prev = b;
		}

		/** True if any byte written was over 127. */
		public boolean hasNonAscii() {
			return nonAscii;
		}

		@Override
		public void write(byte[] b, int off, int len) {
			for (int i = off; i < off + len; i++) {
				write(b[i]);
			}
		}

		public long getCount() {
			return prev == '\n' ? count : count + 2;
		}
	}
}
