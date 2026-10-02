package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/** NOOP (RFC 1939 section 5). */
public class Noop extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Noop() {
		super(NOOP);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		processor.replyOk("");
	}
}
