package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/** STAT (RFC 1939 section 5): "+OK count size" for the messages not marked deleted. */
public class Stat extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Stat() {
		super(STAT);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		processor.replyOk(processor.getMaildrop().getMessageCount() + " " + processor.getMaildrop().getTotalSize());
	}
}
