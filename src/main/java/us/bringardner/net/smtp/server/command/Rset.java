package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;

public class Rset implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 *  RESET (RSET)\n"
			+ "\n"
			+ "            This command specifies that the current mail transaction is\n"
			+ "            to be aborted.  Any stored sender, recipients, and mail data\n"
			+ "            must be discarded, and all buffers and state tables cleared.\n"
			+ "            The receiver must send an OK reply."
			;
	
	@Override
	public String getHelp() {
		return help;
	}
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
			
		proc.getSessionValues().clear();
		proc.reply(REPLY_250_REQUESTED_MAIL_ACTION_OKAY, "OK");
	}

	@Override
	public String getName() {
		return SMTP.RSET;
	}

	@Override
	public IPermission getPermission() {
	
		return NO_PERMISSION_REQUIREDC;
	}
	
	@Override
	public boolean requiresAuthorization() {
		return false;
	}

}
