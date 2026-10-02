package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;


/**
 * STLS (RFC 2595): switch the connection to TLS. Offered only when the server
 * has a key store and the connection isn't already using TLS.
 */
public class Stls extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	public Stls() {
		super(STLS);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		if (processor.isUtf8Mode()) {
			// RFC 6856 section 2.1: clients must not send STLS after UTF8
			processor.replyErr("STLS is not allowed after UTF8");
			return;
		}
		if (processor.isTls()) {
			processor.replyErr("Already using TLS");
			return;
		}
		if (!processor.getPop3Server().isTlsAvailable()) {
			processor.replyErr("TLS is not available");
			return;
		}
		processor.replyOk("Begin TLS negotiation");
		processor.startTls();
	}
}
