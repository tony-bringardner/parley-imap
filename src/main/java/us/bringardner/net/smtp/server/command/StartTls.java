package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;

public class StartTls implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	/*
	 * RFC 821 Simple Mail Transfer Protocol August 1982 
	 * 
	 */
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		proc.reply(REPLY_502_COMMAND_NOT_IMPLEMENTED,"");				
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
