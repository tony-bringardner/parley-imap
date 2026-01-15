package us.bringardner.net.smtp.server;

import java.util.HashMap;
import java.util.Map;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.ICommandFactory;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.server.command.Data;
import us.bringardner.net.smtp.server.command.Ehlo;
import us.bringardner.net.smtp.server.command.Expn;
import us.bringardner.net.smtp.server.command.Helo;
import us.bringardner.net.smtp.server.command.Help;
import us.bringardner.net.smtp.server.command.Mail;
import us.bringardner.net.smtp.server.command.Noop;
import us.bringardner.net.smtp.server.command.Quit;
import us.bringardner.net.smtp.server.command.Rcpt;
import us.bringardner.net.smtp.server.command.RequireTls;
import us.bringardner.net.smtp.server.command.Rset;
import us.bringardner.net.smtp.server.command.StartTls;
import us.bringardner.net.smtp.server.command.Vrfy;

public class SmtpCommandFactory implements ICommandFactory {

	private static final long serialVersionUID = 1L;
	private static Map<String, ICommand> commands;
	private static Map<String, ICommand> extensions;
	
	
	public static Map<String, ICommand> getCommands() {
		return commands;
	}

	public static Map<String, ICommand> getExtensions() {
		return extensions;
	}

	static {
		commands = new HashMap<String, ICommand>();
		extensions = new HashMap<String, ICommand>();
		/*
		 * (The mandatory SMTP commands, according to [5], are HELO,
   		 *  MAIL, RCPT, DATA, RSET, VRFY, NOOP, and QUIT.)
		 */
		addCommand(new Data());
		
		addCommand(new Ehlo());
		registerExtension(new Expn());
		
		addCommand(new Helo());
		registerExtension(new Help());
		
		addCommand(new Mail());
		addCommand(new Noop());
		
		addCommand(new Quit());
		addCommand(new Rcpt());
		registerExtension(new RequireTls());
		addCommand(new Rset());
		registerExtension(new StartTls());
		addCommand(new Vrfy());
		
	}
	
	/**
	 * RFC 821	Simple Mail Transfer Protocol	August 1982
	 *
	 * The commands consist of a command code followed by an argument
         field.  Command codes are four alphabetic characters.  Upper
         and lower case alphabetic characters are to be treated
         identically.  Thus, any of the following may represent the mail
         command:

            MAIL    Mail    mail    MaIl    mAIl

	 * @param cmd
	 */
	public static void addCommand(ICommand cmd){
		commands.put(cmd.getName().toUpperCase(),cmd);
	}
	
	public static void registerExtension(ICommand cmd){
		extensions.put(cmd.getName().toUpperCase(),cmd);
	}
	
	@Override
	public ICommand getCommand(IRequestContext context) {
		String name = context.getFirstToken().toUpperCase();
		ICommand ret = commands.get(name);
		if( ret == null ) {
			ret = extensions.get(name);
		}
		
		return ret;
	}

}
