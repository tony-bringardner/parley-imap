package us.bringardner.net.imap.server.commands;

import java.io.IOException;
import java.util.List;

import us.bringardner.net.imap.server.ImapRequest;
import us.bringardner.net.imap.server.ImapRequestProcessor;
import us.bringardner.net.imap.server.store.Mailbox;
import us.bringardner.net.imap.server.store.MessageInfo;

/**
 * MOVE set mailbox (RFC 9051 section 6.4.8, RFC 6851): COPYUID comes in an
 * untagged OK, then the EXPUNGE responses, then the tagged OK.
 */
public class Move extends Copy {

	private static final long serialVersionUID = 1L;

	public Move() {
		super("MOVE");
	}

	@Override
	protected boolean checkSource(ImapRequestProcessor p, ImapRequest r) throws IOException {
		return checkReadWrite(p, r);
	}

	@Override
	protected List<MessageInfo> transfer(ImapRequestProcessor p, Mailbox source, Mailbox target, List<MessageInfo> list)
			throws IOException {
		// every view, this one included, is told about the expunges
		return source.moveTo(target, list, null);
	}

	@Override
	protected void done(ImapRequestProcessor p, ImapRequest r, String code) throws IOException {
		if (code != null) {
			p.untagged(OK + " [" + code + "] Moved");
		}
		p.ok(r, "MOVE completed");
	}
}
