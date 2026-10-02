package us.bringardner.net.pop3.server.commands;

import java.io.IOException;
import java.util.ArrayList;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;
import us.bringardner.net.pop3.server.Maildrop;


/** UIDL [msg] (RFC 1939 section 7): the unique id of one message, or of every message. */
public class Uidl extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Uidl() {
		super(UIDL);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		Maildrop drop = processor.getMaildrop();
		String arg = context.getNextToken();
		if (arg != null) {
			Maildrop.Entry e = message(processor, arg);
			if (e != null) {
				processor.replyOk(parseNumber(arg) + " " + e.getUid());
			}
			return;
		}
		java.util.List<String> lines = new ArrayList<>();
		java.util.List<Maildrop.Entry> entries = drop.getEntries();
		for (int i = 0; i < entries.size(); i++) {
			if (!entries.get(i).isDeleted()) {
				lines.add((i + 1) + " " + entries.get(i).getUid());
			}
		}
		processor.replyMultiLine("Unique-id listing follows", lines);
	}
}
