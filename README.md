# BjlEmail

Email for the Bringardner Java Library:

- `us.bringardner.net.email`: internet messages (`Message`), with MIME, RFC 2231 parameters, RFC 2047 encoded words and RFC 6532 UTF-8 headers. Messages are stored in a `FileSource`, so they can be larger than memory. `Downgrader` makes the RFC 6858 surrogate of a message with UTF-8 headers.
- `us.bringardner.net.pop3`: a POP3 server (RFC 1939), built like the FTP server in BjlNetFtp.
- `us.bringardner.net.imap`: an IMAP server (IMAP4rev2, RFC 9051, also speaking IMAP4rev1), built the same way and sharing mail with the POP3 server.

Requires Java 21 and Maven. Depends on `bjl_file_system` and `bjl_net_framework` (which bring in `bjl_core` and `bjl_io`).

## Build

```
mvn package
```

Tests run with a 64 MB heap, so the large-message tests only pass if message bodies stay out of memory.

## POP3 server

The design follows BjlNetFtp:

| BjlNetFtp | BjlEmail | Role |
|---|---|---|
| `FtpServer` | `Pop3Server` | Accepts connections, holds the configuration |
| `FtpRequestProcessor` | `Pop3RequestProcessor` | Runs one session |
| `FtpCommandFactory` | `Pop3CommandFactory` | Maps command names to command classes |
| `FtpCommand`, `commands.*` | `Pop3Command`, `commands.*` | One class per command |
| `FTP` | `POP3` | Protocol constants |

Supported: USER, PASS, APOP, QUIT, STAT, LIST, RETR, DELE, NOOP, RSET, TOP, UIDL (RFC 1939); CAPA and response codes (RFC 2449); STLS (RFC 2595); AUTH PLAIN (RFC 5034); UTF8 (RFC 6856).

```java
Pop3Server server = new Pop3Server();          // port 110
server.setMaildropRoot(rootFileSource);
server.start();

server.deliver("tony", message);               // local delivery, e.g. from SMTP
```

Or from the command line: `java us.bringardner.net.pop3.server.Pop3Server -DJPop3.port=1110 -DJPop3.root=/var/mail/pop3`

### Users and maildrops

- **Users** come from the access control list, set up as for FtpServer with the `Pop3Server.AuthenticationProvider` property (for example `us.bringardner.net.framework.server.FileBasedAcl` with `Pop3Server.userFile`). See `src/test/resources/Pop3TestAcl.txt`.
- **Permissions:** READ is needed to log in and read mail; WRITE to delete it.
- **Maildrops:** each user's maildrop is a directory under the root, named by the principal's `maildrop` parameter (default: the user name). Several users can share one maildrop.
- **Messages** are files in the maildrop, in name order. Names starting with "." are ignored: `Maildrop.deliver` writes a hidden temp file and renames it, so a session never sees a message half written.
- **APOP** needs the user's password stored in plain text in the access list; users with `{PBKDF2}` hashes use USER/PASS or AUTH PLAIN.

### Configuration

Each setting can be passed as a system property or set with the matching `Pop3Server` setter.

| Property | Default | Meaning |
|---|---|---|
| `JPop3.port` | 110 (995 if secure) | Port (used by `Pop3Server.main`) |
| `JPop3.secure` | false | Implicit TLS |
| `JPop3.root` | `/pop3` (`C:/pop3` on Windows) | Maildrop root |
| `JPop3.fileSource` | default factory | FileSource factory for the root |
| `JPop3.autologout` | 600000 ms | Idle sessions are closed. RFC 1939 requires at least 10 minutes. `setAutologout()` |
| `JPop3.loginFailureDelay` | 1000 ms | Delay before replying to a failed login. `setLoginFailureDelay()` |
| `JPop3.requireTls` | false | Refuse USER, PASS, APOP and AUTH until STLS (or implicit TLS). `setRequireTls()` |
| `JPop3.utf8Downgrade` | `surrogate` | What a client that didn't send UTF8 gets for a message with UTF-8 headers: the RFC 6858 surrogate, or `reject` (`-ERR [UTF8]`). `setUtf8Downgrade()` |
| `Pop3Server.KeyStoreName`, `Pop3Server.KeyStorePassword`, `Pop3Server.KeyStoreType` | none | Key store for STLS and implicit TLS. STLS is offered only when one is configured. |

### Behaviour notes

- **One session per maildrop** (RFC 1939). A second login gets `-ERR [IN-USE]`. The lock is released at QUIT or when the connection drops.
- **Deletions** are applied only at QUIT. If the connection drops, nothing is deleted.
- **Snapshot:** a session sees the messages that were in the maildrop when it logged in. Mail delivered later appears in the next session.
- **Sizes** in STAT and LIST are exact: the octets RETR sends, with CRLF line ends, before byte-stuffing. They are computed when the maildrop is opened.
- **Streaming:** RETR and TOP stream from the maildrop, so messages can be of any size.
- **Failed logins** wait `JPop3.loginFailureDelay` ms. The connection is closed after 3 failures. Unknown users get the same replies as wrong passwords.
- **Locks** are held in the server process, so only one Pop3Server should serve a maildrop root.

### Internationalized mail (RFC 6856)

- **UTF8 command:** CAPA advertises `UTF8 USER`. A client that sends `UTF8` before logging in gets messages exactly as stored, including UTF-8 headers (RFC 6532). STLS is refused after UTF8, as RFC 6856 allows.
- **Other clients** never see raw UTF-8 in headers. A message with UTF-8 headers (in the message header or in any MIME part header) is sent as its RFC 6858 surrogate, built by `Downgrader`:
  - an address with a non-ASCII mailbox becomes `"José <josé@exämple.com>" <invalid@internationalized-address.invalid>`;
  - Subject and other unstructured fields are RFC 2047-encoded;
  - Content-Type and Content-Disposition parameters are RFC 2231-encoded, so attachment names survive (RFC 6858 allows dropping them);
  - any other field that isn't ASCII (Message-ID, Received, ...) is removed.

  Bodies are not changed and are still streamed from the file. LIST reports the surrogate's exact size, TOP counts the surrogate's lines, and UIDs are the same in both modes. The stored message is never modified.
- **Logins** may use UTF-8 user names and passwords (USER, PASS, APOP and AUTH PLAIN). They are prepared with SASLprep (RFC 4013), so composed and decomposed forms match. Invalid UTF-8 and prohibited characters fail the login. Passwords in the access list should be stored in their prepared (NFC) form.
- **Not implemented:** the optional LANG command (RFC 6856 section 3).

## IMAP server

`ImapServer` follows the same design as `Pop3Server`:

| BjlNetFtp | BjlEmail | Role |
|---|---|---|
| `FtpServer` | `ImapServer` | Accepts connections, holds the configuration |
| `FtpRequestProcessor` | `ImapRequestProcessor` | Runs one session |
| `FtpCommandFactory` | `ImapCommandFactory` | Maps command names to command classes |
| `FtpCommand`, `commands.*` | `ImapCommand`, `commands.*` | One class per command |
| `FTP` | `IMAP` | Protocol constants |

`ImapRequestProcessor` reads the socket itself (`ImapCommandReader`), because IMAP commands can carry literals of any size. Literals over 1 MB go to temp files, so an APPEND never has to fit in memory, and FETCH streams message content from the file.

```java
ImapServer server = new ImapServer();          // port 143
server.setMaildropRoot(rootFileSource);        // the same root as Pop3Server
server.start();

server.deliver("tony", "INBOX", message, null); // local delivery; returns the UID
```

Or from the command line: `java us.bringardner.net.imap.server.ImapServer -DJImap.port=1143 -DJImap.root=/var/mail/pop3`

### Protocol support

- **IMAP4rev2 (RFC 9051)** for clients that send `ENABLE IMAP4rev2`, and **IMAP4rev1 (RFC 3501)** for the rest. A rev1 session gets `* n RECENT`, `[UNSEEN n]`, the `SEARCH` response, `LSUB` and `CHECK`. A rev2 session gets `ESEARCH`, `LIST` in `SELECT` and `[CLOSED]` responses.
- **Commands:** CAPABILITY, NOOP, LOGOUT, STARTTLS, AUTHENTICATE PLAIN, LOGIN, ENABLE, SELECT, EXAMINE, CREATE, DELETE, RENAME, SUBSCRIBE, UNSUBSCRIBE, LIST, LSUB, NAMESPACE, STATUS, APPEND, IDLE, CHECK, CLOSE, UNSELECT, EXPUNGE, SEARCH, FETCH, STORE, COPY, MOVE and UID (COPY, FETCH, MOVE, SEARCH, STORE, EXPUNGE).
- **Extensions** (built into IMAP4rev2 and also offered to rev1 clients): SASL-IR, LITERAL+, ENABLE, IDLE, NAMESPACE, UNSELECT, UIDPLUS, ESEARCH, SEARCHRES, LIST-EXTENDED, LIST-STATUS, MOVE, SPECIAL-USE, CHILDREN, BINARY, STATUS=SIZE and UTF8=ACCEPT.
- **FETCH:** FLAGS, UID, INTERNALDATE, RFC822.SIZE, ENVELOPE, BODY, BODYSTRUCTURE, BODY[section]<partial> (HEADER, HEADER.FIELDS[.NOT], TEXT, MIME, part numbers including attached messages), BODY.PEEK, BINARY[part], BINARY.PEEK, BINARY.SIZE, and the rev1 items RFC822, RFC822.HEADER and RFC822.TEXT. Non-PEEK fetches set `\Seen`.
- **SEARCH:** all RFC 9051 keys. Header text is matched after decoding RFC 2047 encoded words. BODY and TEXT search the decoded text of the text parts. `RETURN (MIN MAX COUNT ALL SAVE)` and `$` are supported.
- **Not implemented:** CONDSTORE/QRESYNC, NOTIFY, QUOTA, ACL, METADATA, multi-APPEND, and SASL mechanisms other than PLAIN.

### Mail storage (shared with POP3)

- **INBOX** is the user's POP3 maildrop: the directory named by the principal's `maildrop` parameter, or the user name, under the root. POP3 and IMAP see the same messages. A POP3 deletion appears to IMAP sessions as an expunge. Mail delivered with `Maildrop.deliver` or `Pop3Server.deliver` appears in INBOX.
- **Other mailboxes** are directories under `<inbox>/.mailboxes`; a child mailbox is a subdirectory of its parent's directory. Name segments are encoded so names stay case-sensitive and safe on any file system. For example, `Sent` is stored as `_sent` and `Über` as `%C3%9Cber`. POP3 ignores names starting with "." and directories, so it only sees INBOX.
- **New users** get Sent, Drafts, Trash, Junk and Archive, marked with their RFC 6154 special use and subscribed. Turn this off with `JImap.defaultMailboxes=false`.
- **Index:** each mailbox has a hidden `.imap-index` file holding UIDVALIDITY, UIDNEXT, and each message's UID, flags and INTERNALDATE. It is replaced safely (temp file, then rename). UIDs are never reused. Message files themselves are never modified, except for the next point.
- **Line ends:** a message file found with bare LF line ends is rewritten once with CRLF, so the sizes IMAP reports are exact.
- **Sharing:** sessions in the same process share one `Mailbox` object per directory (`MailboxRegistry`), so changes reach other sessions at once (EXISTS, EXPUNGE and FETCH FLAGS, which include UID). Changes made outside the server, such as POP3 or delivery, are found by rescanning the directory, at most once a second. Only one server process should serve a root.

### Users and permissions

Users come from the access control list, set up as for Pop3Server with the `ImapServer.AuthenticationProvider` property (e.g. `FileBasedAcl` with `ImapServer.userFile`). READ is needed to log in. WRITE is needed for any change: without it, SELECT opens mailboxes read-only, and APPEND, CREATE, DELETE and RENAME get `NO [NOPERM]`. User names and passwords are prepared with SASLprep (RFC 4013), as for POP3.

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `JImap.port` | 143 (993 if secure) | Port (used by `ImapServer.main`) |
| `JImap.secure` | false | Implicit TLS |
| `JImap.root` | `JPop3.root`, else `/pop3` (`C:/pop3` on Windows) | Maildrop root, shared with POP3 |
| `JImap.fileSource` | `JPop3.fileSource`, else the default factory | FileSource factory for the root |
| `JImap.autologout` | 1800000 ms | Idle sessions get `* BYE` and are closed. RFC 9051 requires at least 30 minutes. `setAutologout()` |
| `JImap.loginFailureDelay` | 1000 ms | Delay before replying to a failed login. The connection is closed after 3 failures. `setLoginFailureDelay()` |
| `JImap.requireTls` | false | Advertise LOGINDISABLED and refuse LOGIN and AUTHENTICATE until STARTTLS. `setRequireTls()` |
| `JImap.appendLimit` | 104857600 | Largest message APPEND accepts; larger ones get `NO [TOOBIG]`. `setAppendLimit()` |
| `JImap.defaultMailboxes` | true | Create the special-use mailboxes for new users |
| `ImapServer.KeyStoreName`, `ImapServer.KeyStorePassword`, `ImapServer.KeyStoreType` | none | Key store for STARTTLS and implicit TLS. STARTTLS is offered only when one is configured. |

### Internationalized mail

- **UTF-8 sessions:** IMAP4rev2 (and `ENABLE UTF8=ACCEPT`, RFC 6855) sessions get UTF-8 mailbox names and quoted strings, and messages exactly as stored.
- **Other IMAP4rev1 sessions:**
  - Mailbox names are sent and read in modified UTF-7.
  - A message with UTF-8 headers is sent as its RFC 6858 surrogate (made by `Downgrader`, as for POP3) in FETCH, ENVELOPE, BODYSTRUCTURE and RFC822.SIZE. The surrogate is cached in the mailbox's hidden `.imap-cache` directory.
  - SEARCH LARGER/SMALLER and STATUS SIZE use the stored size.
- **APPEND** accepts the RFC 6855 `UTF8 (~{n}...)` form.

### Testing

`TestImapServer` runs both servers over real sockets. It covers:

- IMAP4rev1 and IMAP4rev2 sessions, and two sessions on one mailbox;
- IDLE and STARTTLS;
- UTF-8 handling;
- sharing with POP3;
- index persistence;
- a 70 MB message appended and fetched under the 64 MB test heap.

The server also works with Python's `imaplib` and with `curl` (`imap://`, including `--ssl-reqd`).

User names with non-ASCII characters (like the test user `jösé`) become directory names, so the JVM must use UTF-8 for file names. That is the default on macOS. On Linux, run with a UTF-8 locale (e.g. `LANG=C.UTF-8`).
