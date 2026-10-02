package us.bringardner.net.imap.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.imap.server.ImapCommand;
import us.bringardner.net.imap.server.ImapCommandFactory;
import us.bringardner.net.imap.server.ImapParseException;
import us.bringardner.net.imap.server.ImapRequest;
import us.bringardner.net.imap.server.ImapRequestProcessor;

/** UID command args (RFC 9051 section 6.4.9): COPY, FETCH, MOVE, SEARCH, STORE or EXPUNGE with UIDs. */
public class Uid extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Uid() {
		super("UID", SELECTED);
	}

	@Override
	public void execute(ImapRequestProcessor p, ImapRequest r) throws IOException {
		if (!r.hasNext() || !r.peek().isAtom()) {
			throw new ImapParseException("UID needs a command");
		}
		ImapRequest sub = r.subCommand();
		ICommand cmd = ((ImapCommandFactory) p.getCommandFactory()).getCommand(sub.getName());
		if (!(cmd instanceof ImapCommand) || !((ImapCommand) cmd).isUidCommand()) {
			p.bad(r, "UID " + sub.getName() + " is not a valid command");
			return;
		}
		p.run((ImapCommand) cmd, sub);
	}
}
