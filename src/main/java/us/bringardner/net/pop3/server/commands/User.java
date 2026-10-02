package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/**
 * USER name (RFC 1939 section 7). The reply is always +OK, so it doesn't reveal
 * which user names exist; PASS checks the name and password together.
 */
public class User extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	public User() {
		super(USER);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		if (processor.isLoginBlockedUntilTls()) {
			processor.replyErr(CODE_AUTH + " Use STLS before logging in");
			return;
		}
		String user = context.getRemainingTokens();
		if (user == null || user.trim().isEmpty()) {
			processor.replyErr("USER needs a user name");
			return;
		}
		processor.setTempValue(USER, user.trim());
		processor.replyOk("Send PASS");
	}
}
