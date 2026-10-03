# BjlEmail

Email for the Bringardner Java Library:

- `us.bringardner.net.email`: internet messages (`Message`), with MIME, RFC 2231 parameters, RFC 2047 encoded words and RFC 6532 UTF-8 headers. Messages are stored in a `FileSource`, so they can be larger than memory. `Downgrader` makes the RFC 6858 surrogate of a message with UTF-8 headers.
- `us.bringardner.net.pop3`: a POP3 server (RFC 1939), built like the FTP server in BjlNetFtp.
- `us.bringardner.net.imap`: an IMAP server (IMAP4rev2, RFC 9051, also speaking IMAP4rev1), built the same way and sharing mail with the POP3 server.
- `us.bringardner.net.smtp`: an SMTP server and mail transfer agent (RFC 5321), built the same way: it receives mail, delivers it to the same maildrops, and relays mail for other domains through a persistent queue.

Requires Java 21 and Maven. Depends on `bjl_file_system`, `bjl_net_framework` (which bring in `bjl_core` and `bjl_io`) and `bjl_dns` (for `BjlDnsMxResolver`).

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

## SMTP server

`SmtpServer` follows the same design:

| BjlNetFtp | BjlEmail | Role |
|---|---|---|
| `FtpServer` | `SmtpServer` | Accepts connections, holds the configuration |
| `FtpRequestProcessor` | `SmtpRequestProcessor` | Runs one session |
| `FtpCommandFactory` | `SmtpCommandFactory` | Maps command names to command classes |
| `FtpCommand`, `commands.*` | `SmtpCommand`, `commands.*` | One class per command |
| `FTP` | `SMTP` | Protocol constants |

Behind the sessions is `MailQueue` (package `us.bringardner.net.smtp.queue`). It delivers mail locally, relays it to other servers and sends delivery status notifications.

```java
SmtpServer relay = new SmtpServer();               // port 25
relay.setMaildropRoot(rootFileSource);             // the same root as POP3 and IMAP
relay.addLocalDomain("example.com");
relay.start();

SmtpServer submission = SmtpServer.submissionServer(false);   // port 587
submission.setQueue(relay.getQueue());             // one queue for both
submission.start();

relay.send(from, List.of(to), message);            // send mail from code
```

Or from the command line: `java us.bringardner.net.smtp.server.SmtpServer -DJSmtp.domains=example.com -DJSmtp.root=/var/mail/pop3`. This starts port 25 and, unless `JSmtp.submissionPort=0`, port 587.

### Protocol support

The server follows RFC 5321 and its pending revision, draft-ietf-emailcore-rfc5321bis (in the RFC Editor queue). It supports:

| Extension | RFC |
|---|---|
| PIPELINING | 2920 |
| SIZE | 1870 |
| 8BITMIME | 6152 |
| SMTPUTF8 | 6531 |
| ENHANCEDSTATUSCODES | 2034, 3463 |
| CHUNKING and BINARYMIME (BDAT) | 3030 |
| DSN | 3461, 3464, 6533 |
| STARTTLS | 3207 |
| AUTH PLAIN and LOGIN | 4954, 4616 |
| Message submission | 6409 |
| Implicit TLS (port 465) | 8314 |
| Null MX | 7505 |

- **Commands:** EHLO, HELO, MAIL, RCPT, DATA, BDAT, RSET, NOOP, QUIT, VRFY (always 252), EXPN (502), HELP, STARTTLS and AUTH.
- **Received headers** name the protocol per RFC 3848 and RFC 6531, e.g. `ESMTPSA` or `UTF8SMTPS`.
- **Protections:**
  - Only CRLF `.` CRLF ends DATA, which defeats "SMTP smuggling". A `.` after a bare LF is content.
  - A message with more than 100 Received headers is refused as a loop.
  - HTTP requests are refused.
  - The session closes after 20 errors or 3 failed logins.

### Who can send what

- **Mail for the local domains** (`JSmtp.domains`) is accepted from anyone, but only for existing users, aliases and `postmaster`. Unknown users get `550 5.1.1`. A `+detail` suffix is ignored when looking up the user.
- **Mail for other domains** is accepted only from authenticated users (who need the WRITE permission) or from `JSmtp.relayNetworks`. Everyone else gets `550 5.7.1 Relay access denied`, so the server is not an open relay.
- **Submission servers** (port 587/465) require AUTH and add `Date` and `Message-ID` when they're missing.
- **requireTls:** with `JSmtp.requireTls`, AUTH is offered only after STARTTLS. It is off by default, as for POP3 and IMAP; turn it on for submission on a public network.

### The queue and delivery

- **Safe before 250:** accepted mail is written to `<root>/.smtp-queue` (an `.eml` and an `.env` file per message) before the server replies 250. It survives a restart.
- **Local delivery** goes into the user's INBOX, the same maildrop POP3 and IMAP use. It adds `Return-Path` and `Delivered-To`. IMAP sessions in the same process see the new message at once.
- **Aliases:** `JSmtp.aliases` names a file of lines like `sales: tony, jose@example.org`. Members may be local users or remote addresses. Alias loops are stopped.
- **Remote delivery:**
  - The queue looks up MX records. With no MX record it uses the domain's own address, and a null MX means the domain takes no mail.
  - Two resolvers are included, chosen with `JSmtp.resolver`:
    - `jdk` (default): `DnsMxResolver`, the JDK's DNS provider.
    - `bjldns`: `BjlDnsMxResolver`, which makes every DNS request with BjlDns: the MX query and the A/AAAA lookups of the mail hosts. It asks the servers in `JSmtp.dnsServers` (comma-separated), or those in `/etc/resolv.conf`, in turn.
    - `bjldns-iterative`: BjlDns's own iterative resolver, which starts from the root servers in its `sbelt.prop`.
    - Any other `MxResolver` can be set with `getDeliveryConfig().setResolver(...)`.
  - It tries hosts in order of preference, with opportunistic STARTTLS.
  - It uses SIZE, 8BITMIME, SMTPUTF8, CHUNKING/BINARYMIME and DSN when the message needs them. A message needing SMTPUTF8 is returned (5.6.7) by a server without it, as RFC 6531 requires.
- **Smart host:** set `JSmtp.relayHost=host:port` (with `JSmtp.relayUser`/`JSmtp.relayPassword`) to send all outgoing mail through your provider. Many home and cloud networks block outgoing port 25. The smart host requires TLS with a valid certificate (`JSmtp.relayTls`).
- **Retries:** temporary failures are retried on `JSmtp.queue.retry` (minutes, default `1,5,15,30,60,120`).
  - The sender gets a "delayed" notice after `JSmtp.queue.delayWarningHours` (default 4).
  - The message is returned after `JSmtp.queue.maxAgeHours` (default 120).
- **Notifications** are RFC 3464 multipart/report messages from `MAILER-DAEMON`.
  - They honour NOTIFY, RET, ENVID and ORCPT.
  - For UTF-8 addresses or messages they use `message/global-delivery-status` and `message/global`.
  - A notification is never sent about a notification.

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `JSmtp.port` | 25 | Relay port (used by `main`) |
| `JSmtp.submissionPort` / `JSmtp.submissionsPort` | 587 / 0 | Submission ports started by `main` (0 = none); 465 uses implicit TLS |
| `JSmtp.root` | `JPop3.root`, else `/pop3` | Maildrop root, shared with POP3 and IMAP; the queue is `.smtp-queue` inside it |
| `JSmtp.hostname` | the local host name | Name in the greeting, Received headers and notifications |
| `JSmtp.domains` | the host name | Local domains, comma-separated |
| `JSmtp.relayNetworks` | none | Networks that may relay without AUTH, e.g. `127.0.0.1/32,10.0.0.0/8` |
| `JSmtp.requireTls` | false | AUTH (and MAIL on submission ports) only after STARTTLS |
| `JSmtp.maxMessageSize` | 52428800 | SIZE limit |
| `JSmtp.maxRecipients` | 100 | Recipients per message |
| `JSmtp.timeout` | 300000 ms | Idle session timeout (RFC 5321 requires at least 5 minutes) |
| `JSmtp.aliases`, `JSmtp.postmaster` | none, `postmaster` | Aliases file; user who receives postmaster mail |
| `JSmtp.relayHost`, `JSmtp.relayUser`, `JSmtp.relayPassword`, `JSmtp.relayTls` | none, none, none, `required` | Smart host |
| `JSmtp.resolver`, `JSmtp.dnsServers` | `jdk`, from `/etc/resolv.conf` | MX resolver: `jdk`, `bjldns` or `bjldns-iterative`; DNS servers for `bjldns` |
| `JSmtp.tls` | `opportunistic` | STARTTLS to MX hosts: `none`, `opportunistic` or `required` |
| `JSmtp.queue.workers`, `JSmtp.queue.retry`, `JSmtp.queue.delayWarningHours`, `JSmtp.queue.maxAgeHours` | 4, `1,5,15,30,60,120`, 4, 120 | Queue settings |
| `SmtpServer.KeyStoreName`, `SmtpServer.KeyStorePassword`, `SmtpServer.KeyStoreType` | none | Key store for STARTTLS and port 465 |

### Not included

- **Sender authentication:** there is no DKIM signing (RFC 6376), SPF or DMARC checking, and no spam filtering. Large providers often reject or junk unsigned mail from a new server, so for mail to them, use a smart host or add DKIM.
- **Envelope sender:** authenticated users may use any address as the sender; it isn't checked against the login.
- **Optional extensions:** REQUIRETLS, MT-PRIORITY and DELIVERBY aren't implemented.

### Testing

`TestSmtpServer` runs two servers on real sockets. Server A (`a.test`) relays to server B (`b.test`) through a test MX resolver. The tests cover:

- local delivery, aliases, `postmaster` and relay refusal;
- submission with STARTTLS, AUTH PLAIN and AUTH LOGIN;
- bounces with DSN parameters, delay and expiry notices;
- PIPELINING, BDAT and BINARYMIME, smuggling and loop protection;
- SMTPUTF8;
- a queue that survives a restart;
- a 70 MB message under the 64 MB test heap.

`TestBjlDnsMxResolver` starts a real BjlDns `DnsServer` on a free port on 127.0.0.1, with zone files written to a temp directory (`mx.test` with two MX hosts, `implicit.test` with only an A record, `nullmx.test` with a null MX). It checks `BjlDnsMxResolver` against it and relays a message between two SMTP servers using the MX hosts it finds.

Python's `smtplib` (with `starttls()` and `login()`) works with the server.
