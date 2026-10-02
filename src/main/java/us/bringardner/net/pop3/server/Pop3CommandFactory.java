package us.bringardner.net.pop3.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.ICommandFactory;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.commands.Apop;
import us.bringardner.net.pop3.server.commands.Auth;
import us.bringardner.net.pop3.server.commands.Capa;
import us.bringardner.net.pop3.server.commands.Dele;
import us.bringardner.net.pop3.server.commands.List;
import us.bringardner.net.pop3.server.commands.Noop;
import us.bringardner.net.pop3.server.commands.Pass;
import us.bringardner.net.pop3.server.commands.Quit;
import us.bringardner.net.pop3.server.commands.Retr;
import us.bringardner.net.pop3.server.commands.Rset;
import us.bringardner.net.pop3.server.commands.Stat;
import us.bringardner.net.pop3.server.commands.Stls;
import us.bringardner.net.pop3.server.commands.Top;
import us.bringardner.net.pop3.server.commands.Uidl;
import us.bringardner.net.pop3.server.commands.User;
import us.bringardner.net.pop3.server.commands.Utf8;

/**
 * Maps POP3 command names to their command classes (as FtpCommandFactory does
 * for FTP). Commands can be replaced or added with {@link #addCommand(ICommand)}.
 */
public class Pop3CommandFactory implements ICommandFactory {

	private static final long serialVersionUID = 1L;

	private static final Map<String, ICommand> commands = Collections.synchronizedMap(new HashMap<>());

	static {
		// AUTHORIZATION state (RFC 1939, RFC 2595, RFC 5034, RFC 6856)
		addCommand(new User());
		addCommand(new Pass());
		addCommand(new Apop());
		addCommand(new Auth());
		addCommand(new Stls());
		addCommand(new Utf8());
		// any state (RFC 2449)
		addCommand(new Capa());
		addCommand(new Quit());
		// TRANSACTION state
		addCommand(new Stat());
		addCommand(new List());
		addCommand(new Retr());
		addCommand(new Dele());
		addCommand(new Noop());
		addCommand(new Rset());
		addCommand(new Top());
		addCommand(new Uidl());
	}

	public static void addCommand(ICommand cmd) {
		commands.put(cmd.getName().toUpperCase(Locale.ROOT), cmd);
	}

	/** The command for the line's first token, or null if there is none. */
	@Override
	public ICommand getCommand(IRequestContext context) {
		String name = context.getFirstToken();
		return name == null ? null : getCommand(name);
	}

	public ICommand getCommand(String name) {
		return commands.get(name.toUpperCase(Locale.ROOT));
	}
}
