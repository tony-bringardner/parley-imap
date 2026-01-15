package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;

public class Expn implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "EXPAND (EXPN)\n"
			+ "\n"
			+ "            This command asks the receiver to confirm that the argument\n"
			+ "            identifies a mailing list, and if so, to return the\n"
			+ "            membership of that list.  The full name of the users (if\n"
			+ "            known) and the fully specified mailboxes are returned in a\n"
			+ "            multiline reply.\n"
			+ "\n"
			+ "            This command has no effect on any of the reverse-path\n"
			+ "            buffer, the forward-path buffer, or the mail data buffer."
			;
	
	@Override
	public String getHelp() {
		return help;
	}
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		proc.reply(REPLY_502_COMMAND_NOT_IMPLEMENTED,"");				
	}

	@Override
	public String getName() {
		return SMTP.EXPN;
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
