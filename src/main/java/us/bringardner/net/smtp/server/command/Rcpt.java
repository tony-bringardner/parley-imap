package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;
import us.bringardner.net.smtp.server.SmtpMessage;

public class Rcpt implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = " RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 * RECIPIENT (RCPT)\n"
			+ "\n"
			+ "            This command is used to identify an individual recipient of\n"
			+ "            the mail data; multiple recipients are specified by multiple\n"
			+ "            use of this command.\n"
			+ "\n"
			+ "            The forward-path consists of an optional list of hosts and a\n"
			+ "            required destination mailbox.  When the list of hosts is\n"
			+ "            present, it is a source route and indicates that the mail\n"
			+ "            must be relayed to the next host on the list.  If the\n"
			+ "            receiver-SMTP does not implement the relay function it may\n"
			+ "            user the same reply it would for an unknown local user\n"
			+ "            (550).\n"
			+ "\n"
			+ "            When mail is relayed, the relay host must remove itself from\n"
			+ "            the beginning forward-path and put itself at the beginning\n"
			+ "            of the reverse-path.  When mail reaches its ultimate\n"
			+ "            destination (the forward-path contains only a destination\n"
			+ "            mailbox), the receiver-SMTP inserts it into the destination\n"
			+ "            mailbox in accordance with its host mail conventions.\n"
			+ "\n"
			+ "            For example, mail received at relay host A with arguments\n"
			+ "\n"
			+ "                  FROM:<USERX@HOSTY.ARPA>\n"
			+ "                  TO:<@HOSTA.ARPA,@HOSTB.ARPA:USERC@HOSTD.ARPA>\n"
			+ "\n"
			+ "               will be relayed on to host B with arguments\n"
			+ "\n"
			+ "                  FROM:<@HOSTA.ARPA:USERX@HOSTY.ARPA>\n"
			+ "                  TO:<@HOSTB.ARPA:USERC@HOSTD.ARPA>.\n"
			+ "\n"
			+ "            This command causes its forward-path argument to be appended\n"
			+ "            to the forward-path buffer.\n"
			;
	
	
	@Override
	public String getHelp() {
		return help;
	}
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
			
		//
        //S: RCPT TO:<Brown@BBN-UNIX.ARPA>
       // R: 250 OK
		/*
		 * RCPT
               S: 250, 251
               F: 550, 551, 552, 553, 450, 451, 452
               E: 500, 501, 503, 421
               
               550 Requested action not taken: mailbox unavailable
            [E.g., mailbox not found, no access]
		 */
		String tmp1 = ctx.getRemainingTokens();
		if( !tmp1.startsWith("TO:")) {
			proc.reply(REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS,"TO: missing");	
		} else {
			System.out.println();
			String reversePath = tmp1.substring(5);

			SmtpMessage msg = (SmtpMessage) proc.getSessionValue(SmtpMessage.MESSAGE);
			if( msg == null ) {
				proc.reply(REPLY_503_BAD_SEQUENCE_OF_COMMANDS,"No message in session. Use MAIL first");
			} else {
				msg.addRecipient(reversePath);
				proc.reply(REPLY_250_REQUESTED_MAIL_ACTION_OKAY, "OK");
			}
		}

	}

	@Override
	public String getName() {
		return SMTP.RCPT_TO;
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
