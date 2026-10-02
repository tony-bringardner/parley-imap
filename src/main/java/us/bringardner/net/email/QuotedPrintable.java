package us.bringardner.net.email;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Quoted-printable content transfer encoding (RFC 2045 section 6.7).
 */
public final class QuotedPrintable {

	/** Encoded lines are at most 76 characters, including a trailing '=' soft break. */
	private static final int MAX_LINE = 76;

	private QuotedPrintable() {
	}

	/**
	 * Encode bytes.
	 *
	 * @param text true for text: CRLF (or a bare LF) is kept as a line break
	 *             and written as CRLF. False for binary data: every CR and LF is encoded.
	 */
	public static byte[] encode(byte[] data, boolean text) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + data.length / 8 + 16);
		int col = 0;
		for (int i = 0; i < data.length; i++) {
			int b = data[i] & 0xff;
			if (text) {
				if (b == '\r' && i + 1 < data.length && data[i + 1] == '\n') {
					crlf(out);
					col = 0;
					i++;
					continue;
				}
				if (b == '\n') {
					crlf(out);
					col = 0;
					continue;
				}
			}
			boolean literal;
			if (b == ' ' || b == '\t') {
				// whitespace at the end of a line must be encoded
				literal = !atLineEnd(data, i + 1, text);
			} else {
				literal = b >= 33 && b <= 126 && b != '=';
			}
			int len = literal ? 1 : 3;
			if (col + len > MAX_LINE - 1) {
				out.write('=');
				crlf(out);
				col = 0;
			}
			if (literal) {
				out.write(b);
			} else {
				out.write('=');
				out.write(Character.toUpperCase(Character.forDigit(b >> 4, 16)));
				out.write(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
			}
			col += len;
		}
		return out.toByteArray();
	}

	private static boolean atLineEnd(byte[] data, int j, boolean text) {
		if (j >= data.length) {
			return true;
		}
		return text && (data[j] == '\n' || (data[j] == '\r' && j + 1 < data.length && data[j + 1] == '\n'));
	}

	private static void crlf(ByteArrayOutputStream out) {
		out.write('\r');
		out.write('\n');
	}

	/**
	 * Decode bytes. Lenient: an '=' not followed by two hex digits is kept as is,
	 * and trailing whitespace on encoded lines is removed as RFC 2045 requires.
	 */
	public static byte[] decode(byte[] data) {
		byte[] out = new byte[data.length];
		int n = 0;
		int wsStart = -1; // start of a run of literal trailing whitespace in out
		int i = 0;
		while (i < data.length) {
			int b = data[i] & 0xff;
			if (b == '=') {
				int j = i + 1;
				while (j < data.length && (data[j] == ' ' || data[j] == '\t')) {
					j++;
				}
				if (j >= data.length) { // soft break at end of data
					i = data.length;
					wsStart = -1;
					continue;
				}
				if (data[j] == '\r' && j + 1 < data.length && data[j + 1] == '\n') {
					i = j + 2;
					wsStart = -1;
					continue;
				}
				if (data[j] == '\n') {
					i = j + 1;
					wsStart = -1;
					continue;
				}
				if (i + 2 < data.length && MimeHeaderValue.hex((char) data[i + 1]) >= 0
						&& MimeHeaderValue.hex((char) data[i + 2]) >= 0) {
					out[n++] = (byte) (MimeHeaderValue.hex((char) data[i + 1]) * 16 + MimeHeaderValue.hex((char) data[i + 2]));
					i += 3;
				} else {
					out[n++] = '=';
					i++;
				}
				wsStart = -1;
				continue;
			}
			if (b == '\n' || (b == '\r' && i + 1 < data.length && data[i + 1] == '\n')) {
				if (wsStart >= 0) {
					n = wsStart;
				}
				wsStart = -1;
				if (b == '\r') {
					out[n++] = '\r';
					i++;
				}
				out[n++] = '\n';
				i++;
				continue;
			}
			if (b == ' ' || b == '\t') {
				if (wsStart < 0) {
					wsStart = n;
				}
			} else {
				wsStart = -1;
			}
			out[n++] = (byte) b;
			i++;
		}
		if (wsStart >= 0) {
			n = wsStart;
		}
		return Arrays.copyOf(out, n);
	}
}
