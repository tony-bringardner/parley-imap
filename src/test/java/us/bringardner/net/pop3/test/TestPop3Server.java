package us.bringardner.net.pop3.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.email.Address;
import us.bringardner.net.email.Message;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.pop3.server.Maildrop;
import us.bringardner.net.pop3.server.Pop3Server;

/**
 * End-to-end tests of Pop3Server over real sockets, set up the way BjlNetFtp's
 * TestFtpBaseTestClass sets up FtpServer: a FileBasedAcl from a test resource,
 * a temp root and a generated key store for TLS.
 */
public class TestPop3Server {

	private static final String KEYSTORE = "target/pop3keystore.p12";
	public static final String KEYSTORE_PASSWORD = "peekab00";
	private static final String KEY_ALIAS = "serverkey";

	private static Pop3Server server;
	private static FileSource root;
	private static int port;

	@BeforeAll
	public static void startServer() throws Exception {
		makeTestKeystore(new File(KEYSTORE));
		System.setProperty("Pop3Server.KeyStoreName", KEYSTORE);
		System.setProperty("Pop3Server.KeyStorePassword", KEYSTORE_PASSWORD);
		System.setProperty("Pop3Server.KeyStoreType", "PKCS12");
		System.setProperty("Pop3Server." + FileBasedAcl.PROP_FILE_NAME, "Pop3TestAcl.txt");
		System.setProperty("Pop3Server." + IServer.AUTHENTICATION_PROVIDER_PROPERTY, FileBasedAcl.class.getName());

		root = FileSourceFactory.getDefaultFactory().createTempDirectory("pop3test");
		server = new Pop3Server(0, Pop3Server.POP3_NAME, false);
		server.setMaildropRoot(root);
		server.setLoginFailureDelay(0);
		server.getLogger().setLevel(Level.ERROR);
		server.startAndWait(10000);
		port = server.getLocalPort();
	}

	@AfterAll
	public static void stopServer() throws Exception {
		if (server != null) {
			server.stop();
		}
		if (root != null) {
			deleteAll(root);
		}
	}

	private static void deleteAll(FileSource f) throws IOException {
		if (f.isDirectory()) {
			for (FileSource kid : f.listFiles()) {
				deleteAll(kid);
			}
		}
		f.delete();
	}

	private static FileSource maildrop(String name) throws IOException {
		FileSource dir = server.getMaildropDirectory(name);
		if (dir.exists()) {
			deleteAll(dir);
		}
		dir.mkdirs();
		return dir;
	}

	private static FileSource deliver(FileSource dir, String raw) throws IOException {
		return Maildrop.deliver(dir, new java.io.ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
	}

	// ------------------------------------------------------------------ client

	/** A minimal POP3 client that speaks the protocol on a raw socket. */
	static final class Client implements Closeable {
		private Socket socket;
		private InputStream in;
		private OutputStream out;
		final String greeting;

		Client() throws IOException {
			socket = new Socket("localhost", port);
			socket.setSoTimeout(30000);
			in = new BufferedInputStream(socket.getInputStream());
			out = socket.getOutputStream();
			greeting = readLine();
		}

		/** One line as bytes without CRLF, or null at end of stream. */
		byte[] readLineBytes() throws IOException {
			ByteArrayOutputStream line = new ByteArrayOutputStream();
			int b;
			while ((b = in.read()) >= 0 && b != '\n') {
				line.write(b);
			}
			if (b < 0 && line.size() == 0) {
				return null;
			}
			byte[] ret = line.toByteArray();
			if (ret.length > 0 && ret[ret.length - 1] == '\r') {
				ret = java.util.Arrays.copyOf(ret, ret.length - 1);
			}
			return ret;
		}

		String readLine() throws IOException {
			byte[] b = readLineBytes();
			return b == null ? null : new String(b, StandardCharsets.UTF_8);
		}

		void sendRaw(byte[] line) throws IOException {
			out.write(line);
			out.write(new byte[] {'\r', '\n'});
			out.flush();
		}

		void send(String line) throws IOException {
			out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
		}

		String cmd(String line) throws IOException {
			send(line);
			return readLine();
		}

		/** The lines of a multi-line response, un-stuffed, without the final ".". */
		List<String> multi() throws IOException {
			List<String> ret = new ArrayList<>();
			String line;
			while ((line = readLine()) != null && !line.equals(".")) {
				ret.add(line.startsWith("..") ? line.substring(1) : line);
			}
			return ret;
		}

		/** The content of a multi-line response, un-stuffed, with CRLF line ends. */
		byte[] content() throws IOException {
			ByteArrayOutputStream ret = new ByteArrayOutputStream();
			byte[] line;
			while ((line = readLineBytes()) != null && !(line.length == 1 && line[0] == '.')) {
				int off = line.length > 1 && line[0] == '.' && line[1] == '.' ? 1 : 0;
				ret.write(line, off, line.length - off);
				ret.write('\r');
				ret.write('\n');
			}
			return ret.toByteArray();
		}

		/** Digest and length of a multi-line response, streamed. */
		long[] digestContent(MessageDigest md) throws IOException {
			long n = 0;
			byte[] line;
			while ((line = readLineBytes()) != null && !(line.length == 1 && line[0] == '.')) {
				int off = line.length > 1 && line[0] == '.' && line[1] == '.' ? 1 : 0;
				md.update(line, off, line.length - off);
				md.update(new byte[] {'\r', '\n'});
				n += line.length - off + 2;
			}
			return new long[] {n};
		}

		void login(String user, String password) throws IOException {
			assertTrue(cmd("USER " + user).startsWith("+OK"));
			String r = cmd("PASS " + password);
			assertTrue(r.startsWith("+OK"), r);
		}

		void startTls() throws Exception {
			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(null, new TrustManager[] {new TrustAll()}, null);
			SSLSocket ssl = (SSLSocket) ctx.getSocketFactory().createSocket(socket, "localhost", port, true);
			ssl.setUseClientMode(true);
			ssl.startHandshake();
			socket = ssl;
			in = new BufferedInputStream(ssl.getInputStream());
			out = ssl.getOutputStream();
		}

		@Override
		public void close() throws IOException {
			socket.close();
		}
	}

	static final class TrustAll implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	// ------------------------------------------------------------------ tests

	@Test
	public void testGreetingAndCapa() throws Exception {
		try (Client c = new Client()) {
			assertTrue(c.greeting.startsWith("+OK "), c.greeting);
			assertTrue(Pattern.compile("<[^<>]+@[^<>]+>").matcher(c.greeting).find(), "APOP timestamp: " + c.greeting);
			assertTrue(c.cmd("CAPA").startsWith("+OK"));
			List<String> caps = c.multi();
			for (String cap : new String[] {"TOP", "UIDL", "USER", "SASL PLAIN", "STLS", "RESP-CODES", "PIPELINING"}) {
				assertTrue(caps.contains(cap), cap + " in " + caps);
			}
			assertTrue(c.cmd("QUIT").startsWith("+OK"));
			assertNull(c.readLine(), "closed after QUIT");
		}
	}

	private static final String RAW_LF = "From: someone@example.com\nSubject: lf only\n\n.starts with a dot\nline two\n..two dots\n.\nlast line without newline";

	@Test
	public void testRetrieve() throws Exception {
		FileSource dir = maildrop("tony");
		Message built = new Message();
		built.setFrom(new Address("Tony", "tony", "bringardner.us"));
		built.setSubject("Grüße");
		built.setText("Hello\n.\nBye\n");
		server.deliver("tony", built);
		deliver(dir, RAW_LF);

		byte[] expected1 = built.toByteArray();
		byte[] expected2 = (RAW_LF.replace("\n", "\r\n") + "\r\n").getBytes(StandardCharsets.UTF_8);
		long total = expected1.length + expected2.length;

		try (Client c = new Client()) {
			assertEquals("+OK Send PASS", c.cmd("USER tony"));
			assertEquals("+OK Maildrop has 2 messages (" + total + " octets)", c.cmd("PASS secret"));
			assertEquals("+OK 2 " + total, c.cmd("STAT"));

			assertTrue(c.cmd("LIST").startsWith("+OK 2 messages"));
			assertEquals(List.of("1 " + expected1.length, "2 " + expected2.length), c.multi());
			assertEquals("+OK 2 " + expected2.length, c.cmd("LIST 2"));
			assertTrue(c.cmd("LIST 3").startsWith("-ERR"));
			assertTrue(c.cmd("LIST x").startsWith("-ERR"));

			assertEquals("+OK " + expected1.length + " octets", c.cmd("RETR 1"));
			byte[] got1 = c.content();
			assertArrayEquals(expected1, got1);
			assertEquals("Grüße", Message.parse(got1).getSubject());

			assertEquals("+OK " + expected2.length + " octets", c.cmd("RETR 2"));
			assertEquals(new String(expected2, StandardCharsets.UTF_8), new String(c.content(), StandardCharsets.UTF_8));

			assertTrue(c.cmd("UIDL").startsWith("+OK"));
			List<String> uids = c.multi();
			assertEquals(2, uids.size());
			String uid1 = uids.get(0).substring(2);
			assertTrue(uid1.endsWith(".eml"), uid1);
			assertEquals("+OK 1 " + uid1, c.cmd("UIDL 1"));

			assertTrue(c.cmd("RETR").startsWith("-ERR"));
			assertTrue(c.cmd("NOOP").startsWith("+OK"));
			assertTrue(c.cmd("QUIT").startsWith("+OK"));
		}
		// nothing deleted
		assertEquals(2, Maildrop.open(dir).getMessageCount());
	}

	@Test
	public void testTop() throws Exception {
		FileSource dir = maildrop("tony");
		deliver(dir, "Subject: top\r\nX-A: 1\r\n\r\nbody 1\r\n.body 2\r\nbody 3\r\n");
		try (Client c = new Client()) {
			c.login("tony", "secret");
			assertTrue(c.cmd("TOP 1 0").startsWith("+OK"));
			assertEquals(List.of("Subject: top", "X-A: 1", ""), c.multi());
			assertTrue(c.cmd("TOP 1 2").startsWith("+OK"));
			assertEquals(List.of("Subject: top", "X-A: 1", "", "body 1", ".body 2"), c.multi());
			assertTrue(c.cmd("TOP 1 99").startsWith("+OK"));
			assertEquals(6, c.multi().size());
			assertTrue(c.cmd("TOP 1").startsWith("-ERR"));
			assertTrue(c.cmd("TOP 1 -1").startsWith("-ERR"));
			c.cmd("QUIT");
		}
	}

	@Test
	public void testDeleteResetAndUpdate() throws Exception {
		FileSource dir = maildrop("tony");
		FileSource f1 = deliver(dir, "Subject: one\r\n\r\n1\r\n");
		FileSource f2 = deliver(dir, "Subject: two\r\n\r\n2\r\n");

		// deleted but the connection is dropped without QUIT: nothing is removed
		try (Client c = new Client()) {
			c.login("tony", "secret");
			assertEquals("+OK Message 1 deleted", c.cmd("DELE 1"));
		}
		Thread.sleep(200);
		assertTrue(f1.exists());

		try (Client c = new Client()) {
			c.login("tony", "secret");
			assertEquals("+OK Message 1 deleted", c.cmd("DELE 1"));
			assertTrue(c.cmd("DELE 1").contains("already deleted"));
			assertTrue(c.cmd("RETR 1").startsWith("-ERR"));
			assertTrue(c.cmd("STAT").startsWith("+OK 1 "));
			c.cmd("LIST");
			assertEquals(1, c.multi().size());
			assertTrue(c.cmd("RSET").startsWith("+OK Maildrop has 2 messages"));
			assertTrue(c.cmd("STAT").startsWith("+OK 2 "));
			assertEquals("+OK Message 2 deleted", c.cmd("DELE 2"));
			assertTrue(c.cmd("QUIT").startsWith("+OK"));
		}
		assertTrue(f1.exists());
		assertFalse(f2.exists());
	}

	@Test
	public void testBadLogins() throws Exception {
		maildrop("tony");
		try (Client c = new Client()) {
			assertTrue(c.cmd("PASS secret").startsWith("-ERR"), "PASS without USER");
			assertEquals("+OK Send PASS", c.cmd("USER nobody"), "unknown users get the same reply");
			assertTrue(c.cmd("PASS secret").startsWith("-ERR [AUTH]"));
			c.cmd("USER tony");
			assertTrue(c.cmd("PASS wrong").startsWith("-ERR [AUTH]"));
			c.cmd("USER tony");
			assertTrue(c.cmd("PASS wrong again").startsWith("-ERR [AUTH]"));
			assertNull(c.readLine(), "closed after 3 failed logins");
		}
	}

	@Test
	public void testStateChecks() throws Exception {
		maildrop("tony");
		try (Client c = new Client()) {
			assertTrue(c.cmd("STAT").startsWith("-ERR"));
			assertTrue(c.cmd("RETR 1").startsWith("-ERR"));
			assertTrue(c.cmd("XYZZY").startsWith("-ERR Unknown command"));
			assertTrue(c.cmd("").startsWith("-ERR"));
			c.login("tony", "secret");
			assertTrue(c.cmd("USER tony").startsWith("-ERR"));
			assertTrue(c.cmd("STLS").startsWith("-ERR"));
			assertTrue(c.cmd("stat").startsWith("+OK"), "commands are case-insensitive");
			c.cmd("CAPA");
			List<String> caps = c.multi();
			assertFalse(caps.contains("USER"));
			assertFalse(caps.contains("STLS"));
			c.cmd("QUIT");
		}
	}

	@Test
	public void testMaildropInUse() throws Exception {
		FileSource team = maildrop("team");
		deliver(team, "Subject: shared\r\n\r\nx\r\n");
		try (Client a = new Client(); Client b = new Client()) {
			a.cmd("USER team1");
			assertEquals("+OK Maildrop has 1 messages (22 octets)", a.cmd("PASS secret"));
			b.cmd("USER team2");
			assertTrue(b.cmd("PASS secret").startsWith("-ERR [IN-USE]"));
			a.cmd("QUIT");
			Thread.sleep(100);
			b.cmd("USER team2");
			assertTrue(b.cmd("PASS secret").startsWith("+OK"), "unlocked after QUIT");
			b.cmd("QUIT");
		}
		// a dropped connection releases the lock too
		try (Client a = new Client()) {
			a.login("team1", "secret");
		}
		Thread.sleep(200);
		try (Client b = new Client()) {
			b.login("team2", "secret");
			b.cmd("QUIT");
		}
	}

	@Test
	public void testApop() throws Exception {
		maildrop("tony");
		maildrop("hashed");
		try (Client c = new Client()) {
			Matcher m = Pattern.compile("<[^<>]+>").matcher(c.greeting);
			assertTrue(m.find());
			String digest = md5(m.group() + "secret");
			assertTrue(c.cmd("APOP tony 0123456789abcdef0123456789abcdef").startsWith("-ERR [AUTH]"));
			assertTrue(c.cmd("APOP tony " + digest).startsWith("+OK Maildrop has"));
			c.cmd("QUIT");
		}
		try (Client c = new Client()) {
			Matcher m = Pattern.compile("<[^<>]+>").matcher(c.greeting);
			assertTrue(m.find());
			// a hashed password can't be used for APOP...
			assertTrue(c.cmd("APOP hashed " + md5(m.group() + "hashedpw")).startsWith("-ERR [AUTH]"));
			// ...but works with USER/PASS
			c.login("hashed", "hashedpw");
			c.cmd("QUIT");
		}
	}

	private static String md5(String s) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (byte b : MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8))) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private static String plain(String authzid, String user, String password) {
		return Base64.getEncoder().encodeToString((authzid + "\0" + user + "\0" + password).getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void testAuthPlain() throws Exception {
		maildrop("tony");
		try (Client c = new Client()) {
			assertTrue(c.cmd("AUTH CRAM-MD5").startsWith("-ERR"));
			assertEquals("+ ", c.cmd("AUTH PLAIN"));
			assertTrue(c.cmd("*").startsWith("-ERR"), "cancelled");
			assertTrue(c.cmd("AUTH PLAIN !!!").startsWith("-ERR"));
			assertTrue(c.cmd("AUTH PLAIN " + plain("other", "tony", "secret")).startsWith("-ERR [AUTH]"));
			assertEquals("+ ", c.cmd("AUTH PLAIN"));
			assertTrue(c.cmd(plain("", "tony", "secret")).startsWith("+OK Maildrop has"));
			c.cmd("QUIT");
		}
		try (Client c = new Client()) {
			assertTrue(c.cmd("AUTH PLAIN " + plain("tony", "tony", "secret")).startsWith("+OK"));
			c.cmd("QUIT");
		}
	}

	@Test
	public void testPermissions() throws Exception {
		maildrop("reader");
		deliver(server.getMaildropDirectory("reader"), "Subject: r\r\n\r\nx\r\n");
		try (Client c = new Client()) {
			c.login("reader", "secret");
			assertTrue(c.cmd("RETR 1").startsWith("+OK"));
			c.multi();
			assertTrue(c.cmd("DELE 1").startsWith("-ERR Permission denied"));
			c.cmd("QUIT");
		}
		try (Client c = new Client()) {
			c.cmd("USER nopop");
			assertTrue(c.cmd("PASS secret").startsWith("-ERR [AUTH]"), "no READ permission: no POP access");
		}
	}

	@Test
	public void testStls() throws Exception {
		maildrop("tony");
		try (Client c = new Client()) {
			assertEquals("+OK Begin TLS negotiation", c.cmd("STLS"));
			c.startTls();
			c.cmd("CAPA");
			List<String> caps = c.multi();
			assertFalse(caps.contains("STLS"), "STLS is not offered again");
			assertTrue(c.cmd("STLS").startsWith("-ERR"));
			c.login("tony", "secret");
			assertTrue(c.cmd("STAT").startsWith("+OK"));
			c.cmd("QUIT");
		}
	}

	@Test
	public void testRequireTls() throws Exception {
		maildrop("tony");
		server.setRequireTls(true);
		try (Client c = new Client()) {
			c.cmd("CAPA");
			List<String> caps = c.multi();
			assertFalse(caps.contains("USER"));
			assertTrue(caps.contains("STLS"));
			assertTrue(c.cmd("USER tony").startsWith("-ERR [AUTH]"));
			assertTrue(c.cmd("AUTH PLAIN " + plain("", "tony", "secret")).startsWith("-ERR [AUTH]"));
			c.cmd("STLS");
			c.startTls();
			c.login("tony", "secret");
			c.cmd("QUIT");
		} finally {
			server.setRequireTls(false);
		}
	}

	@Test
	public void testLargeMessageIsStreamed() throws Exception {
		// 30 MB with a 64 MB heap: RETR has to stream it
		FileSource dir = maildrop("tony");
		long lines = 400_000;
		byte[] line = "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456\r\n".getBytes(StandardCharsets.US_ASCII);
		MessageDigest expected = MessageDigest.getInstance("SHA-256");
		FileSource f = dir.getChild(".big.tmp");
		try (OutputStream out = new java.io.BufferedOutputStream(f.getOutputStream())) {
			byte[] head = "Subject: big\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
			out.write(head);
			expected.update(head);
			for (long i = 0; i < lines; i++) {
				out.write(line);
				expected.update(line);
			}
		}
		assertTrue(f.renameTo(dir.getChild("big.eml")));
		long size = 16 + lines * line.length;

		try (Client c = new Client()) {
			c.login("tony", "secret");
			assertEquals("+OK " + size + " octets", c.cmd("RETR 1"));
			MessageDigest got = MessageDigest.getInstance("SHA-256");
			assertEquals(size, c.digestContent(got)[0]);
			assertArrayEquals(expected.digest(), got.digest());
			c.cmd("QUIT");
		}
	}

	@Test
	public void testMaildropPathsStayInsideTheRoot() throws Exception {
		String rootPath = server.getMaildropRoot().getCanonicalPath();
		for (String p : new String[] {"../../etc", "/abs/path", "a/../../b", "..\\..\\x"}) {
			String dir = server.getMaildropDirectory(p).getCanonicalPath();
			assertTrue(dir.startsWith(rootPath + File.separator) || dir.startsWith(rootPath + "/"), p + " -> " + dir);
		}
	}

	// ------------------------------------------------------------------ UTF8 (RFC 6856)

	private static final String UTF8_MESSAGE =
			"From: José Núñez <josé@exämple.com>\r\n"
			+ "To: Tony <tony@bringardner.us>\r\n"
			+ "Subject: Grüße 日本語\r\n"
			+ "Message-ID: <abc@exämple.com>\r\n"
			+ "MIME-Version: 1.0\r\n"
			+ "Content-Type: multipart/mixed; boundary=b\r\n"
			+ "\r\n"
			+ "--b\r\n"
			+ "Content-Type: text/plain; charset=UTF-8\r\n"
			+ "Content-Transfer-Encoding: 8bit\r\n"
			+ "\r\n"
			+ "Hällo\r\n"
			+ ".dot line\r\n"
			+ "--b\r\n"
			+ "Content-Type: application/pdf; name=\"résumé.pdf\"\r\n"
			+ "Content-Disposition: attachment; filename=\"résumé.pdf\"\r\n"
			+ "\r\n"
			+ "%PDF\r\n"
			+ "--b--\r\n";
	private static final String EIGHT_BIT_BODY = "Subject: ascii headers\r\n\r\nbödy in UTF-8\r\n";

	@Test
	public void testUtf8Command() throws Exception {
		maildrop("tony");
		try (Client c = new Client()) {
			c.cmd("CAPA");
			assertTrue(c.multi().contains("UTF8 USER"));
			assertTrue(c.cmd("UTF8 x").startsWith("-ERR"));
			assertEquals("+OK UTF8 enabled", c.cmd("UTF8"));
			c.cmd("CAPA");
			List<String> caps = c.multi();
			assertTrue(caps.contains("UTF8 USER"));
			assertFalse(caps.contains("STLS"), "no STLS after UTF8");
			assertTrue(c.cmd("STLS").startsWith("-ERR"), "RFC 6856: STLS after UTF8 is refused");
			c.login("tony", "secret");
			assertTrue(c.cmd("UTF8").startsWith("-ERR"), "only before login");
			c.cmd("QUIT");
		}
	}

	@Test
	public void testUtf8AndLegacyClients() throws Exception {
		FileSource dir = maildrop("tony");
		FileSource stored = deliver(dir, UTF8_MESSAGE);
		deliver(dir, EIGHT_BIT_BODY);
		byte[] original = UTF8_MESSAGE.getBytes(StandardCharsets.UTF_8);
		byte[] eightBit = EIGHT_BIT_BODY.getBytes(StandardCharsets.UTF_8);

		String uidUtf8;
		// a UTF8 client gets the message as stored
		try (Client c = new Client()) {
			c.cmd("UTF8");
			c.login("tony", "secret");
			assertEquals("+OK 1 " + original.length, c.cmd("LIST 1"));
			assertEquals("+OK " + original.length + " octets", c.cmd("RETR 1"));
			assertArrayEquals(original, c.content());
			uidUtf8 = c.cmd("UIDL 1");
			c.cmd("QUIT");
		}

		// a legacy client gets the RFC 6858 surrogate
		try (Client c = new Client()) {
			c.login("tony", "secret");
			String list = c.cmd("LIST 1");
			long size = Long.parseLong(list.substring(list.lastIndexOf(' ') + 1));
			assertEquals("+OK " + size + " octets", c.cmd("RETR 1"));
			byte[] surrogate = c.content();
			assertEquals(size, surrogate.length, "LIST matches RETR");
			String text = new String(surrogate, StandardCharsets.UTF_8);
			String headers = text.substring(0, text.indexOf("\r\n\r\n"));
			assertTrue(headers.chars().allMatch(ch -> ch < 128), headers);

			Message m = Message.parse(surrogate);
			assertFalse(us.bringardner.net.email.Downgrader.needsUtf8(m), "part headers are ASCII too");
			assertEquals("Grüße 日本語", m.getSubject());
			assertEquals("invalid", m.getFrom().get(0).getUser());
			assertTrue(m.getFrom().get(0).getDisplayName().contains("josé@exämple.com"));
			assertEquals("tony", m.getTo().get(0).getUser());
			assertNull(m.getHeader("Message-ID"));
			assertEquals("Hällo\r\n.dot line", m.getParts().get(0).getText(), "8-bit body unchanged, dot line intact");
			assertEquals("résumé.pdf", m.getParts().get(1).getFilename());

			// TOP counts lines of the surrogate (RFC 6858 section 4)
			assertTrue(c.cmd("TOP 1 0").startsWith("+OK"));
			assertEquals(headers + "\r\n\r\n", new String(c.content(), StandardCharsets.UTF_8));
			assertTrue(c.cmd("TOP 1 3").startsWith("+OK"));
			String top3 = new String(c.content(), StandardCharsets.UTF_8);
			assertTrue(top3.startsWith(headers + "\r\n\r\n"));
			assertEquals(3, top3.substring(headers.length() + 4).split("\r\n").length);

			// a message with only an 8-bit body is sent as stored
			assertEquals("+OK 2 " + eightBit.length, c.cmd("LIST 2"));
			c.cmd("RETR 2");
			assertArrayEquals(eightBit, c.content());

			assertEquals(uidUtf8, c.cmd("UIDL 1"), "same UID either way");
			c.cmd("QUIT");
		}
		assertTrue(stored.exists(), "the stored message is not changed");
		try (InputStream in = stored.getInputStream()) {
			assertArrayEquals(original, in.readAllBytes());
		}
	}

	@Test
	public void testUtf8RejectPolicy() throws Exception {
		FileSource dir = maildrop("tony");
		deliver(dir, UTF8_MESSAGE);
		deliver(dir, "Subject: plain\r\n\r\nx\r\n");
		server.setUtf8Downgrade(Pop3Server.Utf8Downgrade.REJECT);
		try {
			try (Client c = new Client()) {
				c.login("tony", "secret");
				assertTrue(c.cmd("RETR 1").startsWith("-ERR [UTF8]"));
				assertTrue(c.cmd("TOP 1 0").startsWith("-ERR [UTF8]"));
				assertTrue(c.cmd("LIST 1").startsWith("-ERR [UTF8]"));
				assertTrue(c.cmd("RETR 2").startsWith("+OK"));
				c.content();
				c.cmd("QUIT");
			}
			try (Client c = new Client()) {
				c.cmd("UTF8");
				c.login("tony", "secret");
				assertTrue(c.cmd("RETR 1").startsWith("+OK"));
				assertArrayEquals(UTF8_MESSAGE.getBytes(StandardCharsets.UTF_8), c.content());
				c.cmd("QUIT");
			}
		} finally {
			server.setUtf8Downgrade(Pop3Server.Utf8Downgrade.SURROGATE);
		}
	}

	@Test
	public void testUtf8Logins() throws Exception {
		maildrop("jösé");
		// the ACL has "jösé / pässwörd" (NFC); decomposed input is normalized by SASLprep
		try (Client c = new Client()) {
			c.cmd("USER jo\u0308se\u0301");
			assertTrue(c.cmd("PASS pa\u0308sswo\u0308rd").startsWith("+OK"));
			c.cmd("QUIT");
		}
		// invalid UTF-8 is refused
		try (Client c = new Client()) {
			c.sendRaw(new byte[] {'U', 'S', 'E', 'R', ' ', 'j', (byte) 0xC3});
			c.readLine();
			assertTrue(c.cmd("PASS pässwörd").startsWith("-ERR [AUTH]"));
		}
		// APOP with UTF-8: MD5 of the timestamp and the prepared password
		try (Client c = new Client()) {
			Matcher m = Pattern.compile("<[^<>]+>").matcher(c.greeting);
			assertTrue(m.find());
			assertTrue(c.cmd("APOP jösé " + md5(m.group() + "pässwörd")).startsWith("+OK"));
			c.cmd("QUIT");
		}
		// AUTH PLAIN with UTF-8
		try (Client c = new Client()) {
			assertTrue(c.cmd("AUTH PLAIN " + plain("", "jösé", "pässwörd")).startsWith("+OK"));
			c.cmd("QUIT");
		}
	}

	// ------------------------------------------------------------------ key store

	/** A self-signed key store for TLS, made with keytool (as TestFtpBaseTestClass does). */
	public static synchronized void makeTestKeystore(File file) throws Exception {
		if (file.isFile()) {
			try (InputStream in = new java.io.FileInputStream(file)) {
				KeyStore ks = KeyStore.getInstance("PKCS12");
				ks.load(in, KEYSTORE_PASSWORD.toCharArray());
				if (ks.isKeyEntry(KEY_ALIAS)) {
					return;
				}
			} catch (Exception e) {
				// make a new one
			}
		}
		Files.deleteIfExists(file.toPath());
		File dir = file.getAbsoluteFile().getParentFile();
		if (dir != null) {
			dir.mkdirs();
		}
		boolean windows = System.getProperty("os.name").toLowerCase().contains("windows");
		String keytool = Paths.get(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool").toString();
		Process p = new ProcessBuilder(keytool, "-genkeypair", "-noprompt", "-alias", KEY_ALIAS,
				"-dname", "CN=localhost, OU=Test, O=Bringardner, C=US", "-keystore", file.getPath(),
				"-storetype", "PKCS12", "-storepass", KEYSTORE_PASSWORD, "-keypass", KEYSTORE_PASSWORD,
				"-keyalg", "RSA", "-keysize", "2048", "-validity", "3650")
				.redirectErrorStream(true).start();
		String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(p.waitFor(60, TimeUnit.SECONDS), "keytool didn't finish");
		assertEquals(0, p.exitValue(), "keytool failed: " + output);
	}
}
