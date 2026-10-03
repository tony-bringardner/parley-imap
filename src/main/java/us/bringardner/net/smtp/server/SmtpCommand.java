package us.bringardner.net.smtp.server;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.Permission;

/**
 * An SMTP command. As for FtpCommand, Pop3Command and ImapCommand, permissions
 * come from the access control list: a user needs WRITE to send mail after AUTH.
 */
public interface SmtpCommand extends ICommand {

	IPermission SEND_PERMISSION = new Permission("WRITE");

	/**
	 * Run the command.
	 *
	 * @param args the rest of the command line after the verb and one space ("" if none)
	 */
	void execute(SmtpRequestProcessor processor, String args) throws IOException;
}
