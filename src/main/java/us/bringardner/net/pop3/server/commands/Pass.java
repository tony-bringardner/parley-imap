package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/**
 * PASS password (RFC 1939 section 7). The password is the rest of the line, so
 * it may contain spaces.
 */
public class Pass extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	public Pass() {
		super(PASS);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		String user = (String) processor.removeTempValue(USER);
		if (user == null) {
			processor.replyErr("Send USER first");
			return;
		}
		String line = context.getCommandLine();
		int idx = line.indexOf(' ');
		String password = idx < 0 ? "" : line.substring(idx + 1);
		processor.replyLoginResult(processor.login(user, password));
	}
}
