package us.bringardner.net.pop3.server.commands;

import java.io.IOException;
import us.bringardner.net.framework.server.IPermission;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;
import us.bringardner.net.pop3.server.Maildrop;


/**
 * DELE msg (RFC 1939 section 5): mark a message deleted; it is removed at QUIT.
 * Needs the WRITE permission.
 */
public class Dele extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Dele() {
		super(DELE);
	}

	@Override
	public IPermission getPermission() {
		return WRITE_PERMISSION;
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		String arg = context.getNextToken();
		if (arg == null) {
			processor.replyErr("DELE needs a message number");
			return;
		}
		Maildrop.Entry e = message(processor, arg);
		if (e != null) {
			int number = parseNumber(arg);
			processor.getMaildrop().delete(number);
			processor.replyOk("Message " + number + " deleted");
		}
	}
}
