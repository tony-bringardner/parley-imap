package us.bringardner.net.pop3.server.commands;

import java.io.IOException;
import java.util.ArrayList;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;
import us.bringardner.net.pop3.server.Maildrop;


/** LIST [msg] (RFC 1939 section 5): the size of one message, or of every message. */
public class List extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public List() {
		super(LIST);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		Maildrop drop = processor.getMaildrop();
		String arg = context.getNextToken();
		if (arg != null) {
			Maildrop.Entry e = message(processor, arg);
			if (e != null && !processor.rejectUtf8(e)) {
				processor.replyOk(parseNumber(arg) + " " + e.getSize());
			}
			return;
		}
		java.util.List<String> lines = new ArrayList<>();
		java.util.List<Maildrop.Entry> entries = drop.getEntries();
		for (int i = 0; i < entries.size(); i++) {
			if (!entries.get(i).isDeleted()) {
				lines.add((i + 1) + " " + entries.get(i).getSize());
			}
		}
		processor.replyMultiLine(drop.getMessageCount() + " messages (" + drop.getTotalSize() + " octets)", lines);
	}
}
