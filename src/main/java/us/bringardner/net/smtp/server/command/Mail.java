package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.IAccessControlList;
import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;
import us.bringardner.net.smtp.server.SmtpMessage;
import us.bringardner.net.smtp.server.SmtpServer;

public class Mail implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 *     MAIL (MAIL)\n"
			+ "\n"
			+ "            This command is used to initiate a mail transaction in which\n"
			+ "            the mail data is delivered to one or more mailboxes.  The\n"
			+ "            argument field contains a reverse-path.\n"
			+ "\n"
			+ "            The reverse-path consists of an optional list of hosts and\n"
			+ "            the sender mailbox.  When the list of hosts is present, it\n"
			+ "            is a \"reverse\" source route and indicates that the mail was\n"
			+ "            relayed through each host on the list (the first host in the\n"
			+ "            list was the most recent relay).  This list is used as a\n"
			+ "            source route to return non-delivery notices to the sender.\n"
			+ "            As each relay host adds itself to the beginning of the list,\n"
			+ "            it must use its name as known in the IPCE to which it is\n"
			+ "            relaying the mail rather than the IPCE from which the mail\n"
			+ "            came (if they are different).  In some types of error\n"
			+ "            reporting messages (for example, undeliverable mail\n"
			+ "            notifications) the reverse-path may be null (see Example 7).\n"
			+ "\n"
			+ "            This command clears the reverse-path buffer, the\n"
			+ "            forward-path buffer, and the mail data buffer; and inserts\n"
			+ "            the reverse-path information from this command into the\n"
			+ "            reverse-path buffer."
			;
	
	@Override
	public String getHelp() {
		return help;
	}
	
	/*
	 * 
	 */
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		//   S: MAIL FROM:<Smith@ISI-VAXA.ARPA>
		//R: 250 OK
		/*
		 *  MAIL
               S: 250
               F: 552, 451, 452
               E: 500, 501, 421
                 551 User not local; please try <forward-path>
      550 Requested action not taken: mailbox unavailable
            [E.g., mailbox not found, no access]
		 */

		String tmp1 = ctx.getRemainingTokens();
		if( !tmp1.startsWith("FROM:")) {
			proc.reply(REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS,"FROM: missing");	
		} else {
			String reversePath = tmp1.substring(5);
			
			SmtpMessage msg = (SmtpMessage) proc.getSessionValue(SmtpMessage.MESSAGE);
			if( msg != null ) {
				proc.reply(REPLY_503_BAD_SEQUENCE_OF_COMMANDS,"Messag ealready exists. Try RSET");
			} else {
				SmtpServer svr = (SmtpServer)proc.getServer();
				
				IAccessControlList acl = svr.getAccessControl();
				IPrincipal p = acl.getPrincipal(reversePath);
				if( p == null ) {
					proc.reply(REPLY_550_REQUESTED_ACTION_NOT_TAKEN,"Requested action not taken: mailbox unavailable");				
				} else if( !p.hasPermission(SEND_MAIL_PERMISSION)) {
					proc.reply(REPLY_550_REQUESTED_ACTION_NOT_TAKEN,"Requested action not taken: user not autherized");
				} else {
					msg = svr.createMessage();
					proc.setSessionValue(SmtpMessage.MESSAGE, msg);
					msg.setFrom(reversePath);
					proc.reply(REPLY_250_REQUESTED_MAIL_ACTION_OKAY, "OK");
				}
			}
		}

	}

	@Override
	public String getName() {
		return SMTP.MAIL_FROM;
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
