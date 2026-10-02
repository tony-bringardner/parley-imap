package us.bringardner.net.pop3;

/**
 * POP3 protocol constants (RFC 1939, with RFC 2449 CAPA and response codes,
 * RFC 2595 STLS and RFC 5034 AUTH).
 */
public interface POP3 {

	int POP3_PORT = 110;
	/** POP3 over implicit TLS (RFC 8314). */
	int POP3S_PORT = 995;

	/** Reply code for a positive reply ("+OK"); any other code is sent as "-ERR". */
	int REPLY_OK = 1;
	/** Reply code for a negative reply ("-ERR"). */
	int REPLY_ERR = 0;

	String OK = "+OK";
	String ERR = "-ERR";
	/** Prefix of an AUTH continuation request (RFC 5034). */
	String CONTINUE = "+ ";
	/** Ends a multi-line reply. */
	String TERMINATOR = ".";

	// commands, RFC 1939
	String USER = "USER";
	String PASS = "PASS";
	String APOP = "APOP";
	String QUIT = "QUIT";
	String STAT = "STAT";
	String LIST = "LIST";
	String RETR = "RETR";
	String DELE = "DELE";
	String NOOP = "NOOP";
	String RSET = "RSET";
	String TOP = "TOP";
	String UIDL = "UIDL";
	// RFC 2449, RFC 2595, RFC 5034
	String CAPA = "CAPA";
	String STLS = "STLS";
	String AUTH = "AUTH";
	// RFC 6856
	String UTF8 = "UTF8";

	// extended response codes, RFC 2449 and RFC 3206
	String CODE_IN_USE = "[IN-USE]";
	String CODE_LOGIN_DELAY = "[LOGIN-DELAY]";
	String CODE_AUTH = "[AUTH]";
	String CODE_SYS_TEMP = "[SYS/TEMP]";
	String CODE_SYS_PERM = "[SYS/PERM]";
	/** RFC 6856: the message needs UTF-8 mode. */
	String CODE_UTF8 = "[UTF8]";
}
