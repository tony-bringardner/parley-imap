package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;
import us.bringardner.net.pop3.server.Maildrop;


/**
 * RETR msg (RFC 1939 section 5): send a message. It is streamed from the
 * maildrop, so it can be of any size. A session without UTF8 gets the RFC 6858
 * surrogate of a message with UTF-8 headers (or -ERR [UTF8], see Pop3Server).
 */
public class Retr extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Retr() {
		super(RETR);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		String arg = context.getNextToken();
		if (arg == null) {
			processor.replyErr("RETR needs a message number");
			return;
		}
		Maildrop.Entry e = message(processor, arg);
		if (e != null && !processor.rejectUtf8(e)) {
			processor.replyContent(e.getSize() + " octets", out -> processor.getMaildrop().writeMessage(e, out));
		}
	}
}
