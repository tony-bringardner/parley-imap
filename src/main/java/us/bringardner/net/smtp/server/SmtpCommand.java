package us.bringardner.net.smtp.server;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.Permission;

public interface SmtpCommand extends ICommand{
	public static IPermission NO_PERMISSION_REQUIREDC = new Permission("NO_PERMISSION_REQUIREDC");
	public static IPermission SEND_MAIL_PERMISSION = new Permission("SEND_MAIL");
	public static IPermission RECIEVE_MAIL_PERMISSION = new Permission("REC_MAIL");
	
}
