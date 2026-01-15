package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;
import us.bringardner.net.smtp.server.SmtpServer;

public class Quit implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = " RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 *  QUIT (QUIT)\n"
			+ "\n"
			+ "            This command specifies that the receiver must send an OK\n"
			+ "            reply, and then close the transmission channel.\n"
			+ "\n"
			+ "            The receiver should not close the transmission channel until\n"
			+ "            it receives and replies to a QUIT command (even if there was\n"
			+ "            an error).  The sender should not close the transmission\n"
			+ "            channel until it send a QUIT command and receives the reply\n"
			+ "            (even if there was an error response to a previous command).\n"
			+ "            If the connection is closed prematurely the receiver should\n"
			+ "            act as if a RSET command had been received (canceling any\n"
			+ "            pending transaction, but not undoing any previously\n"
			+ "            completed transaction), the sender should act as if the\n"
			+ "            command or transaction in progress had received a temporary\n"
			+ "            error (4xx).\n"
			+ "";
	
	@Override
	public String getHelp() {
		return help;
	}
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {

		SmtpServer svr = (SmtpServer) proc.getServer();
		
		proc.reply(REPLY_221_DOMAIN_SERVICE_CLOSING_TRANSMISSION_CHANNEL,svr.getDomainName()+" Service closing transmission channel");
		proc.stop();		
	}

	@Override
	public String getName() {
		return SMTP.QUIT;
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
