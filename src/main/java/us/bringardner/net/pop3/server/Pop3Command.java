package us.bringardner.net.pop3.server;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.framework.server.Permission;

/**
 * A POP3 command. Like FtpCommand, permissions are checked against the access
 * control list: READ is needed to open a maildrop, WRITE to delete messages.
 */
public interface Pop3Command extends ICommand {

	IPermission READ_PERMISSION = new Permission("READ");
	IPermission WRITE_PERMISSION = new Permission("WRITE");

	void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException;

	/** True if the command may be used in this session state (RFC 1939 section 3). */
	boolean isValidIn(Pop3RequestProcessor.State state);
}
