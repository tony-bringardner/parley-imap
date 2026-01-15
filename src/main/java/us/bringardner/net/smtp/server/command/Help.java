package us.bringardner.net.smtp.server.command;

import java.io.IOException;
import java.util.Map;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;
import us.bringardner.net.smtp.server.SmtpCommandFactory;

public class Help implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "HELP (HELP)\n"
			+ "\n"
			+ "            This command causes the receiver to send helpful information\n"
			+ "            to the sender of the HELP command.  The command may take an\n"
			+ "            argument (e.g., any command name) and return more specific\n"
			+ "            information as a response.\n"
			+ "\n"
			+ "            This command has no effect on any of the reverse-path\n"
			+ "            buffer, the forward-path buffer, or the mail data buffer.\n"
			;
	
	@Override
	public String getHelp() {
		return help;
	}
	
	
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		Map<String, ICommand> commands = SmtpCommandFactory.getCommands();
						
		if( ctx.hasNext()) {
			while(ctx.hasNext()) {
				String name = ctx.getNextToken();
				ICommand cmd = commands.get(name);
				if( cmd == null ) {
					proc.reply(REPLY_552_REQUESTED_MAIL_ACTION_ABORTED,"No command named "+name);
					return;
				}
				replyHelp(proc,cmd);
			}
		} else {
			for(ICommand cmd : commands.values()) {
				replyHelp(proc,cmd);
			}
		}
		proc.reply(REPLY_250_REQUESTED_MAIL_ACTION_OKAY,"OK");
	}

	/**
	 * Send a multi line response with the help text
	 * @param proc
	 * @param cmd
	 * @throws IOException 
	 */
	private void replyHelp(ICommandProcessor proc, ICommand cmd) throws IOException {
		String [] lines = cmd.getHelp().split("\n");
		for(String line : lines) {
			proc.reply("250-"+line);
		}		
	}

	@Override
	public String getName() {
		return SMTP.HELP;
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
