package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;

public class Helo implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 * HELLO (HELO)\n"
			+ "\n"
			+ "            This command is used to identify the sender-SMTP to the\n"
			+ "            receiver-SMTP.  The argument field contains the host name of\n"
			+ "            the sender-SMTP.\n"
			+ "\n"
			+ "            The receiver-SMTP identifies itself to the sender-SMTP in\n"
			+ "            the connection greeting reply, and in the response to this\n"
			+ "            command.\n"
			+ "\n"
			+ "            This command and an OK reply to it confirm that both the\n"
			+ "            sender-SMTP and the receiver-SMTP are in the initial state,\n"
			+ "            that is, there is no transaction in progress and all state\n"
			+ "            tables and buffers are cleared.\n"
			+ "            \n"
			+ "         S: HELO USC-ISIF.ARPA\n"
			+ "         R: 250 BBN-UNIX.ARPA\n"
			+ ""
			;
	
	@Override
	public String getHelp() {
		return help;
	}
	
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		String name = ctx.getRemainingTokens();
		if( proc.getSessionValues().isEmpty()) {
			proc.reply(REPLY_250_REQUESTED_MAIL_ACTION_OKAY,name);
		} else {
			proc.reply(REPLY_503_BAD_SEQUENCE_OF_COMMANDS,"Not in initial state");
		}		
	}

	@Override
	public String getName() {
		return SMTP.HELO;
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
