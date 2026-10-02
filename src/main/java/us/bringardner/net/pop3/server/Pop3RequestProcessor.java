package us.bringardner.net.pop3.server;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import us.bringardner.core.ILogger;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.email.SaslPrep;
import us.bringardner.net.framework.IConnection;
import us.bringardner.net.framework.server.AbstractCommandProcessor;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IAccessControlList;
import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.POP3;

/**
 * One POP3 session (RFC 1939). Like FtpRequestProcessor, it reads command lines
 * and runs the command classes from {@link Pop3CommandFactory}; it also holds the
 * session state (AUTHORIZATION, TRANSACTION, UPDATE) and the open {@link Maildrop}.
 */
public class Pop3RequestProcessor extends AbstractCommandProcessor implements POP3 {

	private static final long serialVersionUID = 1L;

	/** Principal parameter: the user's maildrop directory, relative to the maildrop root. */
	public static final String PARAMETER_MAILDROP = "maildrop";

	/** Session states, RFC 1939 section 3. */
	public enum State {
		AUTHORIZATION, TRANSACTION, UPDATE
	}

	/** The outcome of a login attempt. */
	public enum LoginResult {
		OK, FAILED, IN_USE, TLS_REQUIRED, ERROR
	}

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int MAX_LOGIN_ATTEMPTS = 3;

	private volatile State state = State.AUTHORIZATION;
	/** UTF-8 mode, set by the UTF8 command (RFC 6856). */
	private volatile boolean utf8Mode;
	private final transient Map<String, Object> tempStorage = new HashMap<>();
	/** The APOP timestamp sent in the greeting (RFC 1939 section 7). */
	private final String timestamp;
	private transient Maildrop maildrop;
	private String lockKey;
	private int loginAttempts;
	private String lastError;

	/**
	 * Serializes replies. A lock, not synchronized, as in FtpRequestProcessor: a
	 * reply can block on a slow client.
	 */
	private final ReentrantLock replyLock = new ReentrantLock();

	public Pop3RequestProcessor() {
		super();
		setCommandFactory(new Pop3CommandFactory());
		setName("Pop3RequestProcessor");
		setPropertyPrefix("Pop3RequestProcessor");
		timestamp = "<" + ProcessHandle.current().pid() + "." + System.currentTimeMillis() + "."
				+ Long.toHexString(RANDOM.nextLong() & Long.MAX_VALUE) + "@" + hostName() + ">";
	}

	private static String hostName() {
		try {
			return java.net.InetAddress.getLocalHost().getHostName();
		} catch (IOException | RuntimeException e) {
			return "localhost";
		}
	}

	@Override
	public void run() {
		try {
			reply(REPLY_OK, getServer().getName() + " POP3 server ready " + timestamp);
			super.run();
		} catch (Throwable e) {
			if (!(e instanceof SocketException)) {
				logError("An error occurred in the POP3 session; closing the connection.", e);
			}
		} finally {
			// RFC 1939: without QUIT there is no UPDATE state, so nothing is deleted
			releaseMaildrop();
		}
	}

	/**
	 * Run a command line: unknown commands, commands that aren't valid in the
	 * current state and commands the user lacks permission for get -ERR.
	 */
	@Override
	protected void processLine(String line, Map<String, String> cmdUsed) throws IOException {
		IRequestContext context = getRequestContextFactory().getRequestContext(line);
		String first = context.getFirstToken();
		if (first == null || first.isEmpty()) {
			reply(REPLY_ERR, "Empty command");
			return;
		}
		if (isDebugEnabled()) {
			logDebug("Received command=" + first);
		}
		ICommand command = getCommandFactory().getCommand(context);
		if (!(command instanceof Pop3Command)) {
			reply(REPLY_ERR, "Unknown command " + sanitize(first));
			return;
		}
		Pop3Command cmd = (Pop3Command) command;
		if (isDebug()) {
			cmdUsed.put(cmd.getName(), cmd.getName());
		}
		if (!cmd.isValidIn(state)) {
			reply(REPLY_ERR, cmd.getName() + " is not valid in the " + state + " state");
			return;
		}
		if (state == State.TRANSACTION && cmd.requiresAuthorization() && !isAuthorized(cmd.getPermission())) {
			reply(REPLY_ERR, "Permission denied for " + cmd.getName());
			return;
		}
		cmd.execute(this, context);
	}

	/** Echo client text in a reply without control characters. */
	static String sanitize(String text) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length() && sb.length() < 40; i++) {
			char c = text.charAt(i);
			sb.append(c < 32 || c == 127 ? '?' : c);
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ replies

	@Override
	public void reply(String text) throws IOException {
		replyLock.lock();
		try {
			super.reply(text);
		} finally {
			replyLock.unlock();
		}
	}

	/** "+OK" for {@link POP3#REPLY_OK}, otherwise "-ERR". */
	@Override
	public String translateResponseCode(int code) {
		return code == REPLY_OK ? OK : ERR;
	}

	public void replyOk(String text) throws IOException {
		reply(REPLY_OK, text);
	}

	public void replyErr(String text) throws IOException {
		reply(REPLY_ERR, text);
	}

	/**
	 * A multi-line reply: the status line, the lines (dot-stuffed) and the
	 * terminating ".", sent with one flush.
	 */
	public void replyMultiLine(String status, List<String> lines) throws IOException {
		replyLock.lock();
		try {
			IConnection con = getConnection();
			con.writeLine(OK + " " + status);
			for (String line : lines) {
				con.writeLine(line.startsWith(".") ? "." + line : line);
			}
			con.writeLine(TERMINATOR);
			con.flush();
		} finally {
			replyLock.unlock();
		}
	}

	/**
	 * Send "+OK status" and then message content written by {@code body} to the
	 * raw socket (dot-stuffed, CRLF line ends, terminated by "."). Used for RETR
	 * and TOP, so a message of any size is streamed from the maildrop.
	 */
	public void replyContent(String status, ContentWriter body) throws IOException {
		replyLock.lock();
		try {
			IConnection con = getConnection();
			con.writeLine(OK + " " + status);
			con.flush();
			OutputStream raw = new BufferedOutputStream(new FilterOutputStream(con.getSocket().getOutputStream()) {
				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					out.write(b, off, len);
				}

				@Override
				public void close() throws IOException {
					flush(); // never close the socket's stream
				}
			}, 64 * 1024);
			try (DotStuffingOutputStream out = new DotStuffingOutputStream(raw)) {
				body.write(out);
			}
		} finally {
			replyLock.unlock();
		}
	}

	/** Writes message content for {@link #replyContent(String, ContentWriter)}. */
	@FunctionalInterface
	public interface ContentWriter {
		void write(OutputStream out) throws IOException;
	}

	/** Read one line from the client (for AUTH continuations). */
	public String readLine() throws IOException {
		return getConnection().readLine();
	}

	// ------------------------------------------------------------------ login

	public State getState() {
		return state;
	}

	public Maildrop getMaildrop() {
		return maildrop;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public Pop3Server getPop3Server() {
		return (Pop3Server) getServer();
	}

	/** True if the connection uses TLS (implicit, or after STLS). */
	public boolean isTls() {
		IConnection con = getConnection();
		return con != null && con.isSecure();
	}

	/** True if logins must wait for TLS on this connection. */
	public boolean isLoginBlockedUntilTls() {
		return getPop3Server().isRequireTls() && !isTls();
	}

	/** True after a successful UTF8 command (RFC 6856). */
	public boolean isUtf8Mode() {
		return utf8Mode;
	}

	/** Enter UTF-8 mode (RFC 6856): messages are sent with their UTF-8 headers. */
	public void enableUtf8Mode() {
		utf8Mode = true;
	}

	/**
	 * Log in with a user name and password (USER/PASS or AUTH PLAIN). Both are
	 * prepared with SASLprep (RFC 6856 section 2.2, RFC 4616); invalid UTF-8 or
	 * prohibited characters fail the login.
	 */
	public LoginResult login(String user, String password) {
		if (isLoginBlockedUntilTls()) {
			return LoginResult.TLS_REQUIRED;
		}
		user = SaslPrep.prepare(user, false);
		password = SaslPrep.prepare(password, false);
		if (user == null || password == null || user.isEmpty()) {
			return LoginResult.FAILED;
		}
		IPrincipal p = getServer().authenticate(user, password.getBytes(StandardCharsets.UTF_8));
		return p == null ? LoginResult.FAILED : openMaildrop(p);
	}

	/**
	 * Log in with APOP (RFC 1939 section 7): the digest is MD5(timestamp + secret).
	 * This needs the user's password stored in plain text in the access control
	 * list; users with hashed passwords can't use APOP. The user name and the
	 * stored password are prepared with SASLprep (RFC 6856 section 2.2).
	 */
	public LoginResult loginApop(String user, String digest) {
		if (isLoginBlockedUntilTls()) {
			return LoginResult.TLS_REQUIRED;
		}
		user = SaslPrep.prepare(user, false);
		if (user == null) {
			return LoginResult.FAILED;
		}
		IAccessControlList acl = getServer().getAccessControl();
		IPrincipal stored = acl == null ? null : acl.getPrincipal(user);
		byte[] secret = stored == null ? null : stored.getCredentials();
		if (secret == null || new String(secret, StandardCharsets.UTF_8).startsWith(FileBasedAcl.HASH_PREFIX)) {
			return LoginResult.FAILED;
		}
		String prepared = SaslPrep.prepare(new String(secret, StandardCharsets.UTF_8), true);
		if (prepared == null) {
			return LoginResult.FAILED;
		}
		String expected = md5Hex(timestamp.getBytes(StandardCharsets.US_ASCII), prepared.getBytes(StandardCharsets.UTF_8));
		if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
				digest.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
			return LoginResult.FAILED;
		}
		IPrincipal p = getServer().authenticate(user, secret);
		return p == null ? LoginResult.FAILED : openMaildrop(p);
	}

	static String md5Hex(byte[] a, byte[] b) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(a);
			md.update(b);
			StringBuilder sb = new StringBuilder();
			for (byte x : md.digest()) {
				sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Lock and open the user's maildrop, entering the TRANSACTION state. */
	private LoginResult openMaildrop(IPrincipal p) {
		if (!getServer().isAuthorized(p, Pop3Command.READ_PERMISSION)) {
			return LoginResult.FAILED;
		}
		try {
			Pop3Server server = getPop3Server();
			FileSource dir = server.getMaildropDirectory(p);
			String key = dir.getCanonicalPath();
			if (!server.lockMaildrop(key, this)) {
				return LoginResult.IN_USE;
			}
			try {
				maildrop = Maildrop.open(dir, utf8Mode);
			} catch (IOException | RuntimeException e) {
				server.unlockMaildrop(key, this);
				throw e;
			}
			lockKey = key;
			setPrincipal(p);
			state = State.TRANSACTION;
			tempStorage.clear();
			return LoginResult.OK;
		} catch (IOException | RuntimeException e) {
			logError("Can't open the maildrop of " + p.getName(), e);
			lastError = e.getMessage();
			return LoginResult.ERROR;
		}
	}

	/** The message of the last error opening a maildrop. */
	public String getLastError() {
		return lastError;
	}

	/** Reply to a failed login; after too many failures the connection is closed. */
	public void replyLoginResult(LoginResult result) throws IOException {
		switch (result) {
		case OK:
			replyOk("Maildrop has " + maildrop.getMessageCount() + " messages (" + maildrop.getTotalSize() + " octets)");
			break;
		case IN_USE:
			replyErr(CODE_IN_USE + " The maildrop is already in use");
			break;
		case TLS_REQUIRED:
			replyErr(CODE_AUTH + " Use STLS before logging in");
			break;
		case ERROR:
			replyErr(CODE_SYS_TEMP + " The maildrop can't be opened");
			break;
		default:
			loginFailedDelay();
			replyErr(CODE_AUTH + " Invalid user name or password");
			if (++loginAttempts >= MAX_LOGIN_ATTEMPTS) {
				stop();
			}
		}
	}

	/** Wait before replying to a failed login (see Pop3Server.setLoginFailureDelay). */
	public void loginFailedDelay() {
		int delay = getPop3Server().getLoginFailureDelay();
		if (delay > 0) {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public void setTempValue(String key, Object value) {
		tempStorage.put(key, value);
	}

	public Object getTempValue(String key) {
		return tempStorage.get(key);
	}

	public Object removeTempValue(String key) {
		return tempStorage.remove(key);
	}

	/** STLS: switch the connection to TLS. Any USER given before is forgotten (RFC 2595). */
	public void startTls() throws IOException {
		getConnection().negotiateSecureSocket("TLS");
		tempStorage.clear();
	}

	/**
	 * True if this message can't be sent in this session: it needs UTF-8 mode and
	 * the server is set to reject instead of sending a surrogate. Replies
	 * "-ERR [UTF8]" if so.
	 */
	public boolean rejectUtf8(Maildrop.Entry e) throws IOException {
		if (e.isSurrogate() && getPop3Server().getUtf8Downgrade() == Pop3Server.Utf8Downgrade.REJECT) {
			replyErr(CODE_UTF8 + " The message has UTF-8 headers; send UTF8 first");
			return true;
		}
		return false;
	}

	/**
	 * QUIT in the TRANSACTION state: enter UPDATE, remove the deleted messages and
	 * release the maildrop.
	 *
	 * @return the number of messages that could not be removed
	 */
	public int update() {
		state = State.UPDATE;
		int failed;
		try {
			failed = maildrop.commit();
		} catch (RuntimeException e) {
			logError("Error removing deleted messages", e);
			failed = 1;
		}
		releaseMaildrop();
		return failed;
	}

	private void releaseMaildrop() {
		if (lockKey != null) {
			getPop3Server().unlockMaildrop(lockKey, this);
			lockKey = null;
		}
	}

	@Override
	protected ILogger getLogger(String name) {
		return super.getLogger("Pop3RequestProcessor");
	}
}
