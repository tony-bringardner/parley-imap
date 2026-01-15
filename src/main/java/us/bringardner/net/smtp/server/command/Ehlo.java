package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;
import us.bringardner.net.smtp.server.SmtpCommandFactory;

public class Ehlo implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 1425                SMTP Service Extensions            February 1993"
			+ "EHLO (EHLO)\n"
			+ "\n"
			+ "   A client SMTP supporting SMTP service extensions should start an SMTP\n"
			+ "   session by issuing the EHLO command instead of the HELO command. If\n"
			+ "   the SMTP server supports the SMTP service extensions it will give a\n"
			+ "   successful response (see section 4.1), a failure response (see 4.2),\n"
			+ "   or an error response (4.3). If the SMTP server does not support any\n"
			+ "   SMTP service extensions it will generate an error response (see\n"
			+ "   section 4.4).\n"
			+ "\n"
			+ "   The syntax for this command, using the ABNF notation of [2], is:\n"
			+ "\n"
			+ "               ehlo-cmd ::= \"EHLO\" SP domain CR LF\n"
			+ "\n"
			+ "   If successful, the server SMTP responds with code 250.  On failure,\n"
			+ "   the server SMTP responds with code 550.  On error, the server SMTP\n"
			+ "   responds with one of codes 500, 501, 502, 504, or 421.\n"
			+ "\n"
			+ "   This command is issued instead of the HELO command, and may be issued\n"
			+ "   at any time that a HELO command would be appropriate.  That is, if\n"
			+ "   the EHLO command is issued, and a successful response is returned,\n"
			+ "   then a subsequent HELO or EHLO command will result in the server SMTP\n"
			+ "   replying with code 503.  A client SMTP must not cache any information\n"
			+ "   returned if the EHLO command succeeds. That is, a client SMTP must\n"
			+ "   issue the EHLO command at the start of each SMTP session if\n"
			+ "   information about extended facilities is needed.";
	
	@Override
	public String getHelp() {
		return help;
	}
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		
		String client = ctx.getRemainingTokens();
		String host = proc.getServer().getName();
		proc.reply("250-"+host+" Welcomes "+client);
		for(ICommand cmd : SmtpCommandFactory.getExtensions().values() ) {
			proc.reply("250-"+cmd.getName());
		}
		
		proc.reply(REPLY_250_REQUESTED_MAIL_ACTION_OKAY,"end of "+getName());				
	}

	@Override
	public String getName() {
		return SMTP.EHLO;
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
