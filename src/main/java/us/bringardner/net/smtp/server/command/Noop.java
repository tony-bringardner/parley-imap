package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;

public class Noop implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 *  NOOP (NOOP)\n"
			+ "\n"
			+ "            This command does not affect any parameters or previously\n"
			+ "            entered commands.  It specifies no action other than that\n"
			+ "            the receiver send an OK reply.\n"
			+ "\n"
			+ "            This command has no effect on any of the reverse-path\n"
			+ "            buffer, the forward-path buffer, or the mail data buffer.";
	
	@Override
	public String getHelp() {
		return help;
	}
	
	/*
	 * 
	 */
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		proc.reply(REPLY_200_GENERIC_OK,"");				
	}

	@Override
	public String getName() {
		return SMTP.NOOP;
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
