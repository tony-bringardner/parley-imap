package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/**
 * APOP name digest (RFC 1939 section 7): digest is the MD5 of the greeting's
 * timestamp followed by the user's password, so the password is never sent.
 * Works only for users whose password is stored in plain text.
 */
public class Apop extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	public Apop() {
		super(APOP);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		String user = context.getNextToken();
		String digest = context.getNextToken();
		if (user == null || digest == null || context.hasNext()) {
			processor.replyErr("APOP needs a user name and a digest");
			return;
		}
		processor.replyLoginResult(processor.loginApop(user, digest));
	}
}
