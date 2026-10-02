package us.bringardner.net.pop3.server.commands;

import java.io.IOException;
import java.util.ArrayList;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/**
 * CAPA (RFC 2449): list the server's capabilities. Valid in every state; the
 * login capabilities are listed only before login.
 */
public class Capa extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	public Capa() {
		super(CAPA);
	}

	@Override
	public boolean isValidIn(Pop3RequestProcessor.State state) {
		return state != Pop3RequestProcessor.State.UPDATE;
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		java.util.List<String> caps = new ArrayList<>();
		caps.add(TOP);
		caps.add(UIDL);
		caps.add("RESP-CODES");
		caps.add("AUTH-RESP-CODE");
		caps.add("PIPELINING");
		caps.add(UTF8 + " USER"); // RFC 6856: UTF8 command and UTF-8 user names and passwords
		if (processor.getState() == Pop3RequestProcessor.State.AUTHORIZATION) {
			if (!processor.isLoginBlockedUntilTls()) {
				caps.add(USER);
				caps.add("SASL " + Auth.PLAIN);
			}
			if (!processor.isTls() && !processor.isUtf8Mode() && processor.getPop3Server().isTlsAvailable()) {
				caps.add(STLS);
			}
		}
		caps.add("IMPLEMENTATION BjlEmail-Pop3");
		processor.replyMultiLine("Capability list follows", caps);
	}
}
