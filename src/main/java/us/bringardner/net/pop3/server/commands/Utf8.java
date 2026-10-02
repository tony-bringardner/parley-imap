package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;

/**
 * UTF8 (RFC 6856): enter UTF-8 mode, so messages with UTF-8 headers (RFC 6532)
 * are sent as they are stored instead of as surrogates. Valid only before login.
 */
public class Utf8 extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	public Utf8() {
		super(UTF8);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		if (context.hasNext()) {
			processor.replyErr("UTF8 takes no arguments");
			return;
		}
		processor.enableUtf8Mode();
		processor.replyOk("UTF8 enabled");
	}
}
