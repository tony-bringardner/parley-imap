package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/** RSET (RFC 1939 section 5): unmark every message marked deleted. */
public class Rset extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Rset() {
		super(RSET);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		processor.getMaildrop().reset();
		processor.replyOk("Maildrop has " + processor.getMaildrop().getMessageCount() + " messages ("
				+ processor.getMaildrop().getTotalSize() + " octets)");
	}
}
