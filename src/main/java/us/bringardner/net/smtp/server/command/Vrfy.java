package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.AbstractPrincipal;
import us.bringardner.net.framework.server.IAccessControlList;
import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;

public class Vrfy implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 * VERIFY (VRFY)\n"
			+ "\n"
			+ "            This command asks the receiver to confirm that the argument\n"
			+ "            identifies a user.  If it is a user name, the full name of\n"
			+ "            the user (if known) and the fully specified mailbox are\n"
			+ "            returned.\n"
			+ "\n"
			+ "            This command has no effect on any of the reverse-path\n"
			+ "            buffer, the forward-path buffer, or the mail data buffer.\n"
			+ "\n"
			+ "			S: VRFY Smith\n"
			+ "			R: 250 Fred Smith <Smith@USC-ISIF.ARPA>\n"
			+ "\n"
			+ "         Or\n"
			+ "\n"
			+ "            S: VRFY Smith\n"
			+ "            R: 251 User not local; will forward to <Smith@USC-ISIQ.ARPA>\n"
			+ "\n"
			+ "         Or\n"
			+ "\n"
			+ "            S: VRFY Jones\n"
			+ "            R: 550 String does not match anything.\n"
			+ "\n"
			+ "         Or\n"
			+ "\n"
			+ "            S: VRFY Jones\n"
			+ "            R: 551 User not local; please try <Jones@USC-ISIQ.ARPA>\n"
			+ "\n"
			+ "         Or\n"
			+ "\n"
			+ "            S: VRFY Gourzenkyinplatz\n"
			+ "            R: 553 User ambiguous."
			;
	
	@Override
	public String getHelp() {
		return help;
	}
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		String name=ctx.getRemainingTokens();
		IPrincipal principal = new AbstractPrincipal(name) {			
			@Override
			public boolean authenticate(byte[] credentials) {
				return false;
			}
		};
		
		IAccessControlList acl = proc.getServer().getAccessControl();
		if( acl == null || acl.checkPermission(principal, RECIEVE_MAIL_PERMISSION)) {
			proc.reply(REPLY_551_USER_NOT_LOCAL,name+" is not a valid user.");				
		} else {
			proc.reply(REPLY_252_CANNOT_VRFY_USER,name);
		}
		
	}

	@Override
	public String getName() {
		return SMTP.VRFY;
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
