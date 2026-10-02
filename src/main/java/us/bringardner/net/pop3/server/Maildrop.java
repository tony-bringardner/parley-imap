package us.bringardner.net.pop3.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.email.Downgrader;
import us.bringardner.net.email.Message;

/**
 * A user's maildrop: a directory (a {@link FileSource}) holding one message per
 * file. Files whose names start with "." are ignored, so a message being
 * delivered (written to a hidden temp file, then renamed) is never seen half
 * written.
 * <p>
 * A Maildrop is a snapshot taken when the session logs in (RFC 1939): messages
 * are numbered 1..n in name order, mail delivered later is seen in the next
 * session, and deletions are only marks until {@link #commit()} (QUIT).
 * Messages are only read through streams, so they can be of any size.
 * <p>
 * A maildrop opened for a session that is not in UTF-8 mode (RFC 6856) presents
 * a message with non-ASCII headers (RFC 6532) as its RFC 6858 surrogate (see
 * {@link Downgrader}): RETR and TOP send the surrogate and LIST reports its size.
 */
public class Maildrop {

	public static final String MESSAGE_SUFFIX = ".eml";

	private static final AtomicLong SEQUENCE = new AtomicLong();
	private static final SecureRandom RANDOM = new SecureRandom();

	/** One message of the maildrop. */
	public static final class Entry {
		private final FileSource file;
		private final long size;
		private final String uid;
		private final boolean surrogate;
		private boolean deleted;

		Entry(FileSource file, long size, String uid, boolean surrogate) {
			this.file = file;
			this.size = size;
			this.uid = uid;
			this.surrogate = surrogate;
		}

		/**
		 * True if the message has non-ASCII headers and the session isn't in UTF-8
		 * mode, so it is sent as its surrogate (or refused with [UTF8]).
		 */
		public boolean isSurrogate() {
			return surrogate;
		}

		public FileSource getFile() {
			return file;
		}

		/**
		 * Size in octets as sent by RETR (CRLF line ends, before byte-stuffing); for
		 * a surrogate, the surrogate's size.
		 */
		public long getSize() {
			return size;
		}

		/** The unique id for UIDL (RFC 1939 section 7), stable across sessions. */
		public String getUid() {
			return uid;
		}

		public boolean isDeleted() {
			return deleted;
		}
	}

	private final FileSource directory;
	private final List<Entry> entries;

	private Maildrop(FileSource directory, List<Entry> entries) {
		this.directory = directory;
		this.entries = entries;
	}

	/** Open a maildrop in UTF-8 mode (messages are presented as they are stored). */
	public static Maildrop open(FileSource directory) throws IOException {
		return open(directory, true);
	}

	/**
	 * Open a maildrop, creating its directory if it doesn't exist yet.
	 *
	 * @param utf8Mode false for a session that didn't send UTF8 (RFC 6856):
	 *                 messages with non-ASCII headers are presented as surrogates
	 */
	public static Maildrop open(FileSource directory, boolean utf8Mode) throws IOException {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new IOException("Can't create maildrop " + directory.getAbsolutePath());
		}
		if (!directory.isDirectory()) {
			throw new IOException("Not a directory: " + directory.getAbsolutePath());
		}
		List<FileSource> files = new ArrayList<>();
		FileSource[] list = directory.listFiles();
		if (list != null) {
			for (FileSource f : list) {
				if (!f.getName().startsWith(".") && f.isFile()) {
					files.add(f);
				}
			}
		}
		files.sort(Comparator.comparing(FileSource::getName));
		List<Entry> entries = new ArrayList<>(files.size());
		for (FileSource f : files) {
			DotStuffingOutputStream.Counter c = count(f);
			boolean surrogate = false;
			long size = c.getCount();
			// a message without any 8-bit byte can't have non-ASCII headers
			if (!utf8Mode && c.hasNonAscii()) {
				Message m = Message.parse(f);
				if (Downgrader.needsUtf8(m)) {
					surrogate = true;
					Downgrader.downgrade(m);
					DotStuffingOutputStream.Counter sc = new DotStuffingOutputStream.Counter();
					m.writeTo(sc);
					size = sc.getCount();
				}
			}
			entries.add(new Entry(f, size, uidFor(f.getName()), surrogate));
		}
		return new Maildrop(directory, entries);
	}

	public FileSource getDirectory() {
		return directory;
	}

	/** All entries, including ones marked deleted, numbered from 1. */
	public List<Entry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	/** The number of messages not marked deleted. */
	public int getMessageCount() {
		int n = 0;
		for (Entry e : entries) {
			if (!e.deleted) {
				n++;
			}
		}
		return n;
	}

	/** The size in octets of the messages not marked deleted. */
	public long getTotalSize() {
		long n = 0;
		for (Entry e : entries) {
			if (!e.deleted) {
				n += e.size;
			}
		}
		return n;
	}

	/** True if the number is in 1..n (whether or not the message is deleted). */
	public boolean exists(int number) {
		return number >= 1 && number <= entries.size();
	}

	/** Message {@code number} (from 1), or null if there is none or it is marked deleted. */
	public Entry get(int number) {
		if (!exists(number)) {
			return null;
		}
		Entry e = entries.get(number - 1);
		return e.deleted ? null : e;
	}

	/** Mark a message deleted; false if there is no such message or it already is. */
	public boolean delete(int number) {
		Entry e = get(number);
		if (e == null) {
			return false;
		}
		e.deleted = true;
		return true;
	}

	/** Unmark every deleted message (RSET). */
	public void reset() {
		for (Entry e : entries) {
			e.deleted = false;
		}
	}

	/**
	 * Remove the messages marked deleted (the UPDATE state after QUIT).
	 *
	 * @return the number of messages that could not be removed
	 */
	public int commit() {
		int failed = 0;
		for (Entry e : entries) {
			if (e.deleted) {
				try {
					if (e.file.exists() && !e.file.delete()) {
						failed++;
					}
				} catch (IOException ex) {
					failed++;
				}
			}
		}
		return failed;
	}

	/**
	 * Write a whole message (for RETR) to a {@link DotStuffingOutputStream}: the
	 * stored message, or its surrogate.
	 */
	public void writeMessage(Entry e, OutputStream out) throws IOException {
		if (e.surrogate) {
			surrogate(e).writeTo(out);
			return;
		}
		try (InputStream in = new BufferedInputStream(e.file.getInputStream(), 64 * 1024)) {
			byte[] buf = new byte[64 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
		}
	}

	/**
	 * Write the headers, the blank line and the first {@code lines} lines of the
	 * body (for TOP, RFC 1939 section 7). For a surrogate, the lines are those of
	 * the surrogate (RFC 6858 section 4). Only the part needed is read.
	 */
	public void writeTop(Entry e, int lines, OutputStream out) throws IOException {
		TopOutputStream top = new TopOutputStream(out, lines);
		try {
			writeMessage(e, top);
		} catch (TopOutputStream.Done done) {
			// enough lines written
		}
	}

	/** The surrogate (RFC 6858) of a stored message; its bodies are read from the file. */
	public Message surrogate(Entry e) throws IOException {
		Message m = Message.parse(e.file);
		Downgrader.downgrade(m);
		return m;
	}

	/** Passes on the headers and the first n body lines, then stops the writer. */
	static final class TopOutputStream extends java.io.FilterOutputStream {
		static final class Done extends IOException {
			private static final long serialVersionUID = 1L;

			Done() {
				super("TOP complete", null);
			}
		}

		private final int lines;
		private boolean inHeader = true;
		private boolean lineHasContent;
		private int bodyLines;
		private int prev = '\n';

		TopOutputStream(OutputStream out, int lines) {
			super(out);
			this.lines = lines;
		}

		@Override
		public void write(int b) throws IOException {
			b &= 0xff;
			if (!inHeader && prev == '\n' && bodyLines >= lines) {
				throw new Done();
			}
			out.write(b);
			if (b == '\n') {
				if (inHeader) {
					if (!lineHasContent) {
						inHeader = false; // the blank line between headers and body
					}
				} else {
					bodyLines++;
				}
				lineHasContent = false;
			} else if (b != '\r') {
				lineHasContent = true;
			}
			prev = b;
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			for (int i = off; i < off + len; i++) {
				write(b[i]);
			}
		}

		/** Doesn't close the stream it writes to. */
		@Override
		public void close() throws IOException {
			flush();
		}
	}

	/** Count a stored message as RETR sends it. */
	static DotStuffingOutputStream.Counter count(FileSource f) throws IOException {
		DotStuffingOutputStream.Counter c = new DotStuffingOutputStream.Counter();
		try (InputStream in = new BufferedInputStream(f.getInputStream(), 64 * 1024)) {
			byte[] buf = new byte[64 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) {
				c.write(buf, 0, n);
			}
		}
		return c;
	}

	/**
	 * A UID from a file name: the name itself if it is 1-70 characters in
	 * 0x21-0x7E (RFC 1939 section 7), otherwise a SHA-256 of it.
	 */
	static String uidFor(String name) {
		boolean ok = !name.isEmpty() && name.length() <= 70;
		for (int i = 0; i < name.length() && ok; i++) {
			char c = name.charAt(i);
			ok = c >= 0x21 && c <= 0x7e;
		}
		if (ok) {
			return name;
		}
		try {
			StringBuilder sb = new StringBuilder();
			for (byte x : MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8))) {
				sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// ------------------------------------------------------------------ delivery

	/**
	 * Store a message in a maildrop directory. It is written to a hidden temp file
	 * and then renamed, so a session never sees it half written. Messages are
	 * named so that name order is delivery order.
	 *
	 * @return the stored message file
	 */
	public static FileSource deliver(FileSource directory, Message message) throws IOException {
		return deliver(directory, out -> message.writeTo(out));
	}

	/** Store a message read from a stream (to its end; it isn't closed). */
	public static FileSource deliver(FileSource directory, InputStream message) throws IOException {
		return deliver(directory, out -> message.transferTo(out));
	}

	@FunctionalInterface
	private interface Writer {
		void write(OutputStream out) throws IOException;
	}

	private static FileSource deliver(FileSource directory, Writer writer) throws IOException {
		if (!directory.exists() && !directory.mkdirs()) {
			throw new IOException("Can't create maildrop " + directory.getAbsolutePath());
		}
		String name = String.format("%013d.%08d.%08x%s", System.currentTimeMillis(),
				SEQUENCE.incrementAndGet() % 100_000_000L, RANDOM.nextInt(), MESSAGE_SUFFIX);
		FileSource tmp = directory.getChild("." + name + ".tmp");
		FileSource target = directory.getChild(name);
		try (OutputStream out = new BufferedOutputStream(tmp.getOutputStream(), 64 * 1024)) {
			writer.write(out);
		} catch (IOException | RuntimeException e) {
			tmp.delete();
			throw e;
		}
		if (!tmp.renameTo(target)) {
			tmp.delete();
			throw new IOException("Can't store the message as " + target.getAbsolutePath());
		}
		return target;
	}
}
