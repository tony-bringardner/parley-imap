package us.bringardner.net.email;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An Internet message (RFC 5322) with MIME structure (RFC 2045, 2046), whose
 * header parameters and encoded words are read and written according to
 * RFC 2231 and RFC 2047.
 * <p>
 * A Message holds an ordered list of headers and either a body or, for a
 * multipart message, a list of parts. Each part is itself a Message (a MIME
 * "entity"), so parts can be nested.
 * <ul>
 * <li>{@link #read(InputStream)} / {@link #parse(byte[])} read a message.
 *     Headers are unfolded, CRLF or bare LF line endings are accepted, and
 *     multipart bodies are split into parts.</li>
 * <li>{@link #writeTo(OutputStream)} / {@link #toByteArray()} write it with
 *     CRLF line endings and headers folded at 78 characters where possible.
 *     A message read with CRLF line endings and not changed is written back
 *     byte for byte.</li>
 * <li>{@link #setText(String)}, {@link #setContent(byte[], String)} and
 *     {@link #addAttachment(String, String, byte[])} build content, choosing
 *     the transfer encoding (7bit, quoted-printable or base64) and encoding
 *     non-ASCII file names per RFC 2231.</li>
 * <li>{@link #getText()}, {@link #getContent()}, {@link #getFilename()} and
 *     {@link #getContentType()} decode it again.</li>
 * </ul>
 * <h2>Internationalized headers (RFC 6532)</h2>
 * Header values are kept as Unicode. Raw UTF-8 headers are read as such, and how
 * headers are written depends on {@link #setUtf8Headers(boolean)}:
 * <ul>
 * <li>false (the default for new messages): non-ASCII text is written in the
 *     ASCII-only forms every server accepts: RFC 2047 encoded words in Subject and
 *     other unstructured headers and in display names, RFC 2231 in MIME parameters.
 *     Only a non-ASCII mailbox (e.g. {@code josé@exämple.com}) stays UTF-8, because
 *     it has no ASCII form.</li>
 * <li>true: headers are written as raw UTF-8 (RFC 6532). Use this only when the
 *     receiving server supports SMTPUTF8. A message parsed from raw UTF-8 headers
 *     starts in this mode.</li>
 * </ul>
 * In both modes header values are normalized to NFC, lines are folded and limited
 * in octets (78 recommended, 998 maximum), and {@link #needsSmtpUtf8()} says whether
 * the result contains raw UTF-8 headers. Unchanged headers of a parsed message are
 * always written exactly as they were read.
 * <p>
 * Attached messages (message/rfc822 and message/global parts) are parsed into a
 * Message available from {@link #getAttachedMessage()}; {@link #attachMessage(Message)}
 * adds one.
 * <p>
 * The whole message is kept in memory.
 */
public class Message implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String CRLF = "\r\n";
	private static final byte[] CRLF_BYTES = {'\r', '\n'};

	/** Fold header lines longer than this (RFC 5322 section 2.1.1). */
	private static final int FOLD_AT = 78;
	/** Lines in a 7bit or 8bit body must not be longer than this (without CRLF). */
	private static final int MAX_LINE = 998;

	private static final SecureRandom RANDOM = new SecureRandom();

	protected final ArrayList<Header> headers = new ArrayList<>();
	/** The body as transferred (still transfer-encoded); null when the message has parts. */
	protected byte[] body = new byte[0];
	protected final ArrayList<Message> parts = new ArrayList<>();
	/** Multipart text before the first boundary; null if there was none. */
	protected byte[] preamble;
	/** Multipart text after the closing boundary line; null if the closing line ended the data. */
	protected byte[] epilogue = new byte[0];
	/** True for a body part (it doesn't get a MIME-Version header). */
	protected boolean part;
	/** Write headers as raw UTF-8 (RFC 6532) instead of RFC 2047/2231 encodings. */
	protected boolean utf8Headers;
	/** The parsed content of a message/rfc822 or message/global part, or null. */
	protected Message attached;

	/** Deeper nesting than this is kept as an unparsed body (protects against stack overflow). */
	private static final int MAX_DEPTH = 50;

	private static final Set<String> ADDRESS_HEADERS = Set.of("from", "to", "cc", "bcc", "reply-to", "sender",
			"resent-from", "resent-to", "resent-cc", "resent-bcc", "resent-sender");
	private static final Set<String> UNSTRUCTURED_HEADERS = Set.of("subject", "comments", "content-description",
			"thread-topic");

	public Message() {
	}

	// ------------------------------------------------------------------ reading

	/** Read a whole message from the stream. The stream is read to its end but not closed. */
	public static Message read(InputStream in) throws IOException {
		return parse(in.readAllBytes());
	}

	/** Parse a message from bytes. */
	public static Message parse(byte[] data) {
		Message m = new Message();
		m.load(data, 0, data.length, true, 0);
		return m;
	}

	private void load(byte[] data, int start, int end, boolean topLevel, int depth) {
		headers.clear();
		parts.clear();
		attached = null;
		int pos = start;
		StringBuilder current = null;
		StringBuilder raw = null;
		boolean firstLine = true;
		while (pos < end) {
			int lineEnd = indexOf(data, (byte) '\n', pos, end);
			int next = lineEnd < 0 ? end : lineEnd + 1;
			int contentEnd = lineEnd < 0 ? end : lineEnd;
			if (contentEnd > pos && data[contentEnd - 1] == '\r') {
				contentEnd--;
			}
			if (contentEnd == pos) { // blank line: end of headers
				pos = next;
				break;
			}
			String line = decodeHeaderLine(data, pos, contentEnd);
			if (!utf8Headers && isUtf8(data, pos, contentEnd)) {
				utf8Headers = true; // keep the message's RFC 6532 style when it is written
			}
			if ((line.charAt(0) == ' ' || line.charAt(0) == '\t') && current != null) {
				current.append(line); // unfold: the line break is removed, the whitespace kept
				raw.append(CRLF).append(line);
			} else if (line.indexOf(':') > 0 && isFieldName(line.substring(0, line.indexOf(':')).trim())) {
				addParsedHeader(current, raw);
				current = new StringBuilder(line);
				raw = new StringBuilder(line);
			} else if (firstLine && topLevel && line.startsWith("From ")) {
				// mbox separator line: not part of the message
			} else {
				// not a header: the headers are missing their blank line, the body starts here
				break;
			}
			firstLine = false;
			pos = next;
		}
		addParsedHeader(current, raw);

		body = copy(data, pos, end);
		preamble = null;
		epilogue = new byte[0];
		if (depth >= MAX_DEPTH) {
			return;
		}
		if (isMultipart()) {
			String boundary = getContentType().getParameter("boundary");
			if (boundary != null && !boundary.isEmpty()) {
				splitMultipart(boundary, depth);
			}
		} else if (isAttachedMessageType()) {
			try {
				Message m = new Message();
				byte[] content = getContent();
				m.load(content, 0, content.length, true, depth + 1);
				attached = m;
			} catch (RuntimeException e) {
				attached = null; // e.g. corrupt base64: keep the body as it is
			}
		}
	}

	private boolean isAttachedMessageType() {
		String t = getMimeType();
		return t.equals("message/rfc822") || t.equals("message/global");
	}

	/** True if the bytes contain non-ASCII and are valid UTF-8. */
	private static boolean isUtf8(byte[] data, int start, int end) {
		boolean nonAscii = false;
		for (int i = start; i < end && !nonAscii; i++) {
			nonAscii = data[i] < 0;
		}
		if (!nonAscii) {
			return false;
		}
		try {
			StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, start, end - start));
			return true;
		} catch (CharacterCodingException e) {
			return false;
		}
	}

	private void addParsedHeader(StringBuilder line, StringBuilder raw) {
		if (line != null) {
			Header h = Header.parseHeader(line.toString());
			headers.add(new ParsedHeader(h.getName(), h.getValue(), raw.toString()));
		}
	}

	/**
	 * A header as it was read, with its original (folded) text. While its name and
	 * value are unchanged it is written back exactly as it was read.
	 */
	private static class ParsedHeader extends Header {
		private static final long serialVersionUID = 1L;
		private final String originalName;
		private final String originalValue;
		private final String rawText;

		ParsedHeader(String name, String value, String rawText) {
			super(name, value);
			this.originalName = name;
			this.originalValue = value;
			this.rawText = rawText;
		}

		String unchangedText() {
			return originalName.equals(name) && java.util.Objects.equals(originalValue, value) ? rawText : null;
		}
	}

	/** Header bytes are UTF-8 (RFC 6532); fall back to ISO-8859-1 for legacy 8-bit headers. */
	private static String decodeHeaderLine(byte[] data, int start, int end) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(data, start, end - start)).toString();
		} catch (CharacterCodingException e) {
			return new String(data, start, end - start, StandardCharsets.ISO_8859_1);
		}
	}

	private static boolean isFieldName(String name) {
		if (name.isEmpty()) {
			return false;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c < 33 || c > 126) {
				return false;
			}
		}
		return true;
	}

	private void splitMultipart(String boundary, int depth) {
		byte[] data = body;
		byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

		List<int[]> found = new ArrayList<>(); // {lineStart, lineEnd, isClose}
		int ls = 0;
		while (ls < data.length) {
			if (startsWith(data, ls, delim)) {
				int p = ls + delim.length;
				boolean close = p + 1 < data.length && data[p] == '-' && data[p + 1] == '-';
				if (close) {
					p += 2;
				}
				// the rest of the line may only be whitespace
				int q = p;
				while (q < data.length && (data[q] == ' ' || data[q] == '\t' || data[q] == '\r')) {
					q++;
				}
				if (q >= data.length || data[q] == '\n') {
					boolean newline = q < data.length;
					found.add(new int[] {ls, newline ? q + 1 : data.length, close ? 1 : 0, newline ? 1 : 0});
					if (close) {
						break;
					}
				}
			}
			int nl = indexOf(data, (byte) '\n', ls, data.length);
			if (nl < 0) {
				break;
			}
			ls = nl + 1;
		}
		if (found.isEmpty()) {
			return; // not really multipart: keep the body as it is
		}

		int first = found.get(0)[0];
		preamble = first == 0 ? null : copy(data, 0, endBeforeDelimiter(data, first));
		for (int i = 0; i < found.size(); i++) {
			int[] d = found.get(i);
			if (d[2] == 1) {
				break;
			}
			int start = d[1];
			int end = i + 1 < found.size() ? endBeforeDelimiter(data, found.get(i + 1)[0]) : data.length;
			Message p = new Message();
			p.part = true;
			p.load(data, start, Math.max(start, end), false, depth + 1);
			parts.add(p);
		}
		int[] last = found.get(found.size() - 1);
		if (last[2] == 1) {
			// null epilogue = the closing delimiter line had no line break
			epilogue = last[3] == 1 ? copy(data, last[1], data.length) : null;
		} else {
			epilogue = null; // no closing delimiter; one is written on output
		}
		body = null;
	}

	/** The CRLF (or LF) before a delimiter line belongs to the delimiter. */
	private static int endBeforeDelimiter(byte[] data, int lineStart) {
		int e = lineStart;
		if (e > 0 && data[e - 1] == '\n') {
			e--;
			if (e > 0 && data[e - 1] == '\r') {
				e--;
			}
		}
		return e;
	}

	// ------------------------------------------------------------------ writing

	/**
	 * Write the message with CRLF line endings, in the header mode set by
	 * {@link #setUtf8Headers(boolean)}. The stream is not closed.
	 */
	public void writeTo(OutputStream out) throws IOException {
		write(out, utf8Headers);
	}

	private void write(OutputStream out, boolean utf8) throws IOException {
		writeHeaders(out, utf8);
		out.write(CRLF_BYTES);
		writeBody(out, utf8);
	}

	private void writeHeaders(OutputStream out, boolean utf8) throws IOException {
		for (Header h : headers) {
			String text = h instanceof ParsedHeader ? ((ParsedHeader) h).unchangedText() : null;
			if (text == null) {
				text = formatHeader(h.getName(), h.getValue(), utf8);
			}
			out.write(text.getBytes(StandardCharsets.UTF_8));
			out.write(CRLF_BYTES);
		}
	}

	/** One header, NFC-normalized, encoded for the mode and folded. */
	static String formatHeader(String name, String rawValue, boolean utf8) {
		String value = rawValue == null ? "" : rawValue.replaceAll("[\\r\\n]+", " ");
		value = Normalizer.normalize(value, Normalizer.Form.NFC);
		if (!utf8) {
			value = toAscii(name, value);
		}
		String line = fold(name + ": " + value);
		if (maxLineOctets(line) > MAX_LINE && isUnstructured(name)) {
			// a run with no whitespace longer than 998 octets: encoded words can be folded
			line = fold(name + ": " + EncodedWord.encode(value, null, name.length() + 2, true));
		}
		return line;
	}

	/** The ASCII-only form of a header value: RFC 2047 for text, RFC 2231 for MIME parameters. */
	private static String toAscii(String name, String value) {
		if (isAscii(value)) {
			return value;
		}
		String n = name.toLowerCase(Locale.ROOT);
		if (ADDRESS_HEADERS.contains(n)) {
			List<Address> list = Address.parseAddressList(value);
			if (list.isEmpty()) {
				return value;
			}
			StringBuilder sb = new StringBuilder();
			for (Address a : list) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(a.toString());
			}
			return sb.toString();
		}
		if (n.equals("content-type") || n.equals("content-disposition")) {
			return MimeHeaderValue.parse(value).toString();
		}
		if (isUnstructured(name)) {
			return EncodedWord.encode(value, null, name.length() + 2);
		}
		return value; // structured header with no ASCII form: left as UTF-8
	}

	private static boolean isUnstructured(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return UNSTRUCTURED_HEADERS.contains(n) || n.startsWith("x-");
	}

	private static boolean isAscii(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) > 127) {
				return false;
			}
		}
		return true;
	}

	private static int maxLineOctets(String folded) {
		int max = 0;
		for (String l : folded.split(CRLF)) {
			max = Math.max(max, l.getBytes(StandardCharsets.UTF_8).length);
		}
		return max;
	}

	public byte[] toByteArray() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			writeTo(out);
		} catch (IOException e) {
			throw new IllegalStateException(e); // can't happen with a ByteArrayOutputStream
		}
		return out.toByteArray();
	}

	private void writeBody(OutputStream out, boolean utf8) throws IOException {
		if (parts.isEmpty()) {
			byte[] b = body;
			if (attached != null && b != null) {
				b = attachedBody();
			}
			if (b != null) {
				if ("binary".equals(getTransferEncoding())) {
					out.write(b);
				} else {
					writeWithCrlf(out, b);
				}
			}
			return;
		}
		String boundary = ensureBoundary();
		byte[] delim = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
		if (preamble != null) {
			writeWithCrlf(out, preamble);
			out.write(CRLF_BYTES);
		}
		for (Message p : parts) {
			out.write(delim);
			out.write(CRLF_BYTES);
			p.write(out, utf8);
			out.write(CRLF_BYTES);
		}
		out.write(delim);
		out.write('-');
		out.write('-');
		if (epilogue != null) {
			out.write(CRLF_BYTES);
			writeWithCrlf(out, epilogue);
		}
	}

	/** Write bytes, turning bare LF into CRLF. */
	private static void writeWithCrlf(OutputStream out, byte[] data) throws IOException {
		int from = 0;
		for (int i = 0; i < data.length; i++) {
			if (data[i] == '\n' && (i == 0 || data[i - 1] != '\r')) {
				out.write(data, from, i - from);
				out.write('\r');
				from = i; // the '\n' is written with the next chunk
			}
		}
		out.write(data, from, data.length - from);
	}

	/**
	 * Fold a header line at whitespace so lines are at most 78 octets (UTF-8 bytes)
	 * where possible (RFC 5322 section 2.2.3, RFC 6532). A run of text without
	 * whitespace is never split.
	 */
	static String fold(String line) {
		if (line.getBytes(StandardCharsets.UTF_8).length <= FOLD_AT) {
			return line;
		}
		StringBuilder out = new StringBuilder();
		int noBreakBefore = line.indexOf(':') + 2; // keep "Name: x" together
		int lineStart = 0;
		int col = 0;            // octets since lineStart
		int lastWs = -1;        // last place a break is allowed
		int colAtLastWs = 0;
		boolean content = false; // non-whitespace seen on the current line
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (isWsp(c) && i > lineStart && i >= noBreakBefore && content) {
				lastWs = i;
				colAtLastWs = col;
			} else if (!isWsp(c)) {
				content = true;
			}
			col += octets(c);
			if (col > FOLD_AT && lastWs > lineStart) {
				out.append(line, lineStart, lastWs).append(CRLF);
				lineStart = lastWs;
				col -= colAtLastWs;
				content = !line.substring(lastWs, i + 1).trim().isEmpty();
				lastWs = -1;
			}
		}
		return out.append(line, lineStart, line.length()).toString();
	}

	private static int octets(char c) {
		if (c < 0x80) {
			return 1;
		}
		if (c < 0x800) {
			return 2;
		}
		if (Character.isHighSurrogate(c)) {
			return 4;
		}
		if (Character.isLowSurrogate(c)) {
			return 0;
		}
		return 3;
	}

	private static boolean isWsp(char c) {
		return c == ' ' || c == '\t';
	}

	@Override
	public String toString() {
		return new String(toByteArray(), StandardCharsets.UTF_8);
	}

	// ------------------------------------------------------------------ headers

	/** All headers in order. The list is live: changes to it change the message. */
	public List<Header> getHeaders() {
		return headers;
	}

	/** The value of the first header with this name (case-insensitive), or null. */
	public String getHeader(String name) {
		for (Header h : headers) {
			if (h.getName().equalsIgnoreCase(name)) {
				return h.getValue();
			}
		}
		return null;
	}

	/** The values of every header with this name, in order. */
	public List<String> getHeaders(String name) {
		List<String> ret = new ArrayList<>();
		for (Header h : headers) {
			if (h.getName().equalsIgnoreCase(name)) {
				ret.add(h.getValue());
			}
		}
		return ret;
	}

	public Message addHeader(String name, String value) {
		checkName(name);
		headers.add(new Header(name, value));
		return this;
	}

	/**
	 * Replace the first header with this name, keeping its position, and remove any
	 * others; append it if there is none. A null value removes the header.
	 */
	public Message setHeader(String name, String value) {
		checkName(name);
		if (value == null) {
			return removeHeader(name);
		}
		boolean replaced = false;
		for (Iterator<Header> it = headers.iterator(); it.hasNext();) {
			Header h = it.next();
			if (h.getName().equalsIgnoreCase(name)) {
				if (replaced) {
					it.remove();
				} else {
					h.setName(name);
					h.setValue(value);
					replaced = true;
				}
			}
		}
		if (!replaced) {
			headers.add(new Header(name, value));
		}
		return this;
	}

	public Message removeHeader(String name) {
		headers.removeIf(h -> h.getName().equalsIgnoreCase(name));
		return this;
	}

	private static void checkName(String name) {
		if (name == null || !isFieldName(name) || name.indexOf(':') >= 0) {
			throw new IllegalArgumentException("Invalid header name: " + name);
		}
	}

	/** The decoded Subject, or null. */
	public String getSubject() {
		return EncodedWord.decode(getHeader("Subject"));
	}

	/**
	 * Set the Subject. Non-ASCII text is written as RFC 2047 encoded words, or as
	 * UTF-8 in UTF-8 header mode.
	 */
	public Message setSubject(String subject) {
		return setHeader("Subject", subject);
	}

	public List<Address> getFrom() {
		return Address.parseAddressList(getHeader("From"));
	}

	public Message setFrom(Address... from) {
		return setHeader("From", join(from));
	}

	public List<Address> getTo() {
		return Address.parseAddressList(getHeader("To"));
	}

	public Message setTo(Address... to) {
		return setHeader("To", join(to));
	}

	public List<Address> getCc() {
		return Address.parseAddressList(getHeader("Cc"));
	}

	public Message setCc(Address... cc) {
		return setHeader("Cc", join(cc));
	}

	private static String join(Address[] list) {
		if (list == null || list.length == 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (Address a : list) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(a.toUtf8String());
		}
		return sb.toString();
	}

	/**
	 * The Date header, or null if there is none.
	 *
	 * @throws ParseException if the header is present but not a valid date
	 */
	public Rfc2822Date getDate() throws ParseException {
		String d = getHeader("Date");
		return d == null ? null : Rfc2822Date.parseDate(d);
	}

	public Message setDate(Rfc2822Date date) {
		return setHeader("Date", date == null ? null : date.toString());
	}

	public String getMessageId() {
		return getHeader("Message-ID");
	}

	public Message setMessageId(String id) {
		return setHeader("Message-ID", id);
	}

	// ------------------------------------------------------------------ MIME

	/** The Content-Type; {@code text/plain; charset=us-ascii} when there is none (RFC 2045). */
	public MimeHeaderValue getContentType() {
		String ct = getHeader("Content-Type");
		MimeHeaderValue v = MimeHeaderValue.parse(ct);
		if (ct == null || v.getValue().isEmpty()) {
			v = new MimeHeaderValue("text/plain").setParameter("charset", "us-ascii");
		}
		return v;
	}

	public Message setContentType(MimeHeaderValue type) {
		return setHeader("Content-Type", type == null ? null : type.toUtf8String());
	}

	/** The media type in lower case, e.g. "text/plain" or "multipart/mixed". */
	public String getMimeType() {
		return getContentType().getValue().toLowerCase(Locale.ROOT);
	}

	public boolean isMultipart() {
		return getMimeType().startsWith("multipart/");
	}

	/** The Content-Disposition, or null if there is none. */
	public MimeHeaderValue getContentDisposition() {
		String cd = getHeader("Content-Disposition");
		return cd == null ? null : MimeHeaderValue.parse(cd);
	}

	public Message setContentDisposition(MimeHeaderValue disposition) {
		return setHeader("Content-Disposition", disposition == null ? null : disposition.toUtf8String());
	}

	/**
	 * The decoded file name: Content-Disposition filename, else Content-Type name
	 * (RFC 2231 and RFC 2047 encodings are decoded), or null.
	 */
	public String getFilename() {
		MimeHeaderValue cd = getContentDisposition();
		String name = cd == null ? null : cd.getParameter("filename");
		if (name == null) {
			name = getContentType().getParameter("name");
		}
		return name;
	}

	/** True if Content-Disposition is "attachment", or the part has a file name and isn't inline. */
	public boolean isAttachment() {
		MimeHeaderValue cd = getContentDisposition();
		if (cd != null && cd.getValue().equalsIgnoreCase("attachment")) {
			return true;
		}
		if (isAttachedMessageType() && (cd == null || !cd.getValue().equalsIgnoreCase("inline"))) {
			return true;
		}
		return (cd == null || !cd.getValue().equalsIgnoreCase("inline")) && getFilename() != null;
	}

	/** The Content-Transfer-Encoding in lower case; "7bit" when there is none. */
	public String getTransferEncoding() {
		String cte = getHeader("Content-Transfer-Encoding");
		return cte == null || cte.trim().isEmpty() ? "7bit" : cte.trim().toLowerCase(Locale.ROOT);
	}

	/** The body exactly as transferred (still transfer-encoded); null for a message with parts. */
	public byte[] getBody() {
		return body;
	}

	/** Set the body as transferred; it must already match Content-Transfer-Encoding. Removes any parts. */
	public Message setBody(byte[] body) {
		this.body = body == null ? new byte[0] : body;
		attached = null;
		parts.clear();
		preamble = null;
		epilogue = new byte[0];
		return this;
	}

	/**
	 * The body with its transfer encoding (base64 or quoted-printable) removed.
	 * For a multipart message, the raw multipart body.
	 */
	public byte[] getContent() {
		if (!parts.isEmpty()) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				writeBody(out, utf8Headers);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			return out.toByteArray();
		}
		switch (getTransferEncoding()) {
		case "base64":
			return Base64.getMimeDecoder().decode(body);
		case "quoted-printable":
			return QuotedPrintable.decode(body);
		default:
			return body.clone();
		}
	}

	/**
	 * The content as text, decoded with the Content-Type charset (UTF-8 when there
	 * is none; ISO-8859-1 when the charset is unknown, so no bytes are lost).
	 */
	public String getText() {
		String name = getContentType().getParameter("charset");
		Charset cs = StandardCharsets.UTF_8;
		if (name != null) {
			try {
				cs = Charset.forName(name.trim());
			} catch (RuntimeException e) {
				cs = StandardCharsets.ISO_8859_1;
			}
		}
		return new String(getContent(), cs);
	}

	/** Set plain text content. */
	public Message setText(String text) {
		return setText(text, "plain");
	}

	/**
	 * Set text content of type text/{subtype} (e.g. "plain" or "html"). Line breaks
	 * become CRLF. ASCII text is sent as 7bit; anything else as UTF-8 quoted-printable.
	 */
	public Message setText(String text, String subtype) {
		String normalized = text == null ? "" : text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", CRLF);
		byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
		boolean ascii = is7bit(bytes);
		MimeHeaderValue ct = new MimeHeaderValue("text/" + subtype).setParameter("charset", ascii ? "us-ascii" : "UTF-8");
		setEncodedContent(bytes, ct, true);
		return this;
	}

	/**
	 * Set content of any type. Text types get CRLF line breaks and are sent as 7bit
	 * when they are ASCII, quoted-printable otherwise; everything else as base64.
	 *
	 * @param contentType e.g. "application/pdf" or "text/csv; charset=UTF-8"
	 */
	public Message setContent(byte[] data, String contentType) {
		MimeHeaderValue ct = MimeHeaderValue.parse(contentType);
		byte[] bytes = data == null ? new byte[0] : data;
		boolean text = ct.getValue().toLowerCase(Locale.ROOT).startsWith("text/");
		if (text) {
			bytes = toCrlf(bytes); // canonical form for text (RFC 2049)
		}
		setEncodedContent(bytes, ct, text);
		return this;
	}

	/** Turn bare LF and bare CR into CRLF. */
	private static byte[] toCrlf(byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 16);
		for (int i = 0; i < data.length; i++) {
			byte b = data[i];
			if (b == '\r') {
				out.write('\r');
				out.write('\n');
				if (i + 1 < data.length && data[i + 1] == '\n') {
					i++;
				}
			} else if (b == '\n') {
				out.write('\r');
				out.write('\n');
			} else {
				out.write(b);
			}
		}
		return out.toByteArray();
	}

	private void setEncodedContent(byte[] data, MimeHeaderValue ct, boolean text) {
		String cte;
		byte[] encoded;
		if (text && is7bit(data)) {
			cte = "7bit";
			encoded = data;
		} else if (text) {
			cte = "quoted-printable";
			encoded = QuotedPrintable.encode(data, true);
		} else {
			cte = "base64";
			encoded = Base64.getMimeEncoder(76, CRLF_BYTES).encode(data);
			if (encoded.length > 0) {
				encoded = concat(encoded, CRLF_BYTES);
			}
		}
		setContentType(ct);
		setHeader("Content-Transfer-Encoding", cte);
		setBody(encoded);
		ensureMimeVersion();
	}

	/** ASCII, no NUL, no bare CR or LF, and no line longer than 998 characters. */
	private static boolean is7bit(byte[] data) {
		int col = 0;
		for (int i = 0; i < data.length; i++) {
			int b = data[i] & 0xff;
			if (b == '\r' && i + 1 < data.length && data[i + 1] == '\n') {
				col = 0;
				i++;
				continue;
			}
			if (b >= 128 || b == 0 || b == '\r' || b == '\n' || ++col > MAX_LINE) {
				return false;
			}
		}
		return true;
	}

	private void ensureMimeVersion() {
		if (!part && getHeader("MIME-Version") == null) {
			addHeader("MIME-Version", "1.0");
		}
	}

	// ------------------------------------------------------------------ parts

	/** The parts of a multipart message (empty otherwise). The list is read-only. */
	public List<Message> getParts() {
		return Collections.unmodifiableList(parts);
	}

	/**
	 * Add a part. A message that is not yet multipart becomes multipart/mixed, with
	 * its existing content (if any) as the first part.
	 */
	public Message addPart(Message p) {
		if (!isMultipart()) {
			makeMultipart("mixed");
		}
		p.part = true;
		p.removeHeader("MIME-Version");
		parts.add(p);
		body = null;
		return this;
	}

	public Message removePart(Message p) {
		parts.remove(p);
		return this;
	}

	/**
	 * Add an attachment. The file name is written in both Content-Disposition
	 * filename and Content-Type name, RFC 2231-encoded when it isn't plain ASCII.
	 * If this message isn't multipart/mixed it becomes one, with the existing
	 * content as the first part.
	 *
	 * @return the new attachment part
	 */
	public Message addAttachment(String filename, String contentType, byte[] data) {
		Message att = new Message();
		att.part = true;
		att.setContent(data, contentType);
		if (filename != null) {
			att.setContentType(att.getContentType().setParameter("name", filename));
		}
		MimeHeaderValue cd = new MimeHeaderValue("attachment");
		if (filename != null) {
			cd.setParameter("filename", filename);
		}
		att.setContentDisposition(cd);

		if (!getMimeType().equals("multipart/mixed")) {
			makeMultipart("mixed");
		}
		addPart(att);
		return att;
	}

	/** Every attachment in this message, searching nested multiparts. */
	public List<Message> getAttachments() {
		List<Message> ret = new ArrayList<>();
		collectAttachments(ret);
		return ret;
	}

	private void collectAttachments(List<Message> ret) {
		for (Message p : parts) {
			if (!p.parts.isEmpty()) {
				p.collectAttachments(ret);
			} else if (p.isAttachment()) {
				ret.add(p);
			}
		}
	}

	/**
	 * Turn this message into multipart/{subtype}. Its current content headers and
	 * body move into a first part, unless there is no content yet.
	 */
	private void makeMultipart(String subtype) {
		boolean hasContent = (body != null && body.length > 0) || !parts.isEmpty() || getHeader("Content-Type") != null;
		if (hasContent) {
			Message first = new Message();
			first.part = true;
			for (Iterator<Header> it = headers.iterator(); it.hasNext();) {
				Header h = it.next();
				if (h.getName().toLowerCase(Locale.ROOT).startsWith("content-")) {
					first.headers.add(h);
					it.remove();
				}
			}
			first.body = body;
			first.attached = attached;
			first.parts.addAll(parts);
			first.preamble = preamble;
			first.epilogue = epilogue;
			parts.clear();
			parts.add(first);
		} else {
			removeHeader("Content-Transfer-Encoding");
		}
		body = null;
		attached = null;
		preamble = null;
		epilogue = new byte[0];
		setContentType(new MimeHeaderValue("multipart/" + subtype).setParameter("boundary", newBoundary()));
		ensureMimeVersion();
	}

	private String ensureBoundary() {
		MimeHeaderValue ct = getContentType();
		String b = ct.getParameter("boundary");
		if (b == null || b.isEmpty()) {
			b = newBoundary();
			if (!ct.getValue().toLowerCase(Locale.ROOT).startsWith("multipart/")) {
				ct = new MimeHeaderValue("multipart/mixed");
			}
			setContentType(ct.setParameter("boundary", b));
		}
		return b;
	}

	/**
	 * "=_" can't appear in base64 or quoted-printable output, so with a random
	 * suffix the boundary can't collide with encoded content.
	 */
	private static String newBoundary() {
		byte[] r = new byte[12];
		RANDOM.nextBytes(r);
		StringBuilder sb = new StringBuilder("=_Part_");
		for (byte b : r) {
			sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ RFC 6532

	/** True if headers are written as raw UTF-8 (RFC 6532). */
	public boolean isUtf8Headers() {
		return utf8Headers;
	}

	/**
	 * Choose how non-ASCII header text is written: raw UTF-8 (RFC 6532; needs a
	 * server that supports SMTPUTF8) or, when false, RFC 2047/2231 encodings that
	 * every server accepts. Applies to this message and all its parts.
	 */
	public Message setUtf8Headers(boolean utf8) {
		this.utf8Headers = utf8;
		return this;
	}

	/**
	 * True if the message, as it would be written now, has raw UTF-8 in its headers
	 * or its parts' headers, so it can only be sent to a server that supports
	 * SMTPUTF8 (RFC 6531). Headers inside an attached message/global don't count:
	 * that content is part of the body.
	 */
	public boolean needsSmtpUtf8() {
		return needsSmtpUtf8(utf8Headers);
	}

	private boolean needsSmtpUtf8(boolean utf8) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			writeHeaders(out, utf8);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		for (byte b : out.toByteArray()) {
			if (b < 0) {
				return true;
			}
		}
		for (Message p : parts) {
			if (p.needsSmtpUtf8(utf8)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * For a message/rfc822 or message/global part: the attached message, parsed.
	 * Changes to it are written out with this part. Null for other parts.
	 */
	public Message getAttachedMessage() {
		return attached;
	}

	/**
	 * Attach a whole message (e.g. to forward it). It becomes a message/global part
	 * if it needs SMTPUTF8 (RFC 6532 section 3.7), otherwise message/rfc822.
	 *
	 * @return the new part
	 */
	public Message attachMessage(Message message) {
		Message p = new Message();
		p.part = true;
		p.setHeader("Content-Type", message.needsSmtpUtf8() ? "message/global" : "message/rfc822");
		p.setHeader("Content-Transfer-Encoding", "7bit");
		p.setHeader("Content-Disposition", "attachment");
		p.attached = message;
		p.body = new byte[0];
		p.body = p.attachedBody();
		if (!getMimeType().equals("multipart/mixed")) {
			makeMultipart("mixed");
		}
		addPart(p);
		return p;
	}

	/**
	 * The body for an attached message: the original bytes while the message is
	 * unchanged, otherwise the message re-written and transfer-encoded.
	 */
	private byte[] attachedBody() {
		byte[] now = attached.toByteArray();
		byte[] before;
		try {
			before = getContent();
		} catch (RuntimeException e) {
			before = null;
		}
		if (Arrays.equals(now, before)) {
			return body;
		}
		String cte = getTransferEncoding();
		boolean global = getMimeType().equals("message/global");
		if (cte.equals("7bit") || cte.equals("8bit")) {
			// pick the lightest encoding the new content allows
			if (is7bit(now)) {
				cte = "7bit";
			} else if (is8bit(now)) {
				cte = "8bit";
			} else {
				cte = global ? "base64" : "binary"; // message/rfc822 may not be base64 (RFC 2046)
			}
			setHeader("Content-Transfer-Encoding", cte);
		}
		switch (cte) {
		case "base64":
			return concat(Base64.getMimeEncoder(76, CRLF_BYTES).encode(now), CRLF_BYTES);
		case "quoted-printable":
			return QuotedPrintable.encode(now, true);
		default:
			return now;
		}
	}

	/** Like 7bit, but bytes over 127 are allowed. */
	private static boolean is8bit(byte[] data) {
		int col = 0;
		for (int i = 0; i < data.length; i++) {
			int b = data[i] & 0xff;
			if (b == '\r' && i + 1 < data.length && data[i + 1] == '\n') {
				col = 0;
				i++;
				continue;
			}
			if (b == 0 || b == '\r' || b == '\n' || ++col > MAX_LINE) {
				return false;
			}
		}
		return true;
	}

	public byte[] getPreamble() {
		return preamble;
	}

	public void setPreamble(byte[] preamble) {
		this.preamble = preamble;
	}

	public byte[] getEpilogue() {
		return epilogue;
	}

	public void setEpilogue(byte[] epilogue) {
		this.epilogue = epilogue;
	}

	// ------------------------------------------------------------------ helpers

	private static int indexOf(byte[] data, byte b, int from, int to) {
		for (int i = from; i < to; i++) {
			if (data[i] == b) {
				return i;
			}
		}
		return -1;
	}

	private static boolean startsWith(byte[] data, int at, byte[] prefix) {
		if (at + prefix.length > data.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (data[at + i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static byte[] copy(byte[] data, int from, int to) {
		byte[] ret = new byte[Math.max(0, to - from)];
		System.arraycopy(data, from, ret, 0, ret.length);
		return ret;
	}

	private static byte[] concat(byte[] a, byte[] b) {
		byte[] ret = new byte[a.length + b.length];
		System.arraycopy(a, 0, ret, 0, a.length);
		System.arraycopy(b, 0, ret, a.length, b.length);
		return ret;
	}
}
