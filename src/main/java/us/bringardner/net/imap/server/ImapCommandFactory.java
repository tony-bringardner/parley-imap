package us.bringardner.net.imap.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import us.bringardner.net.framework.server.ICommand;
import us.bringardner.net.framework.server.ICommandFactory;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.imap.server.commands.Append;
import us.bringardner.net.imap.server.commands.Authenticate;
import us.bringardner.net.imap.server.commands.Capability;
import us.bringardner.net.imap.server.commands.Check;
import us.bringardner.net.imap.server.commands.Close;
import us.bringardner.net.imap.server.commands.Copy;
import us.bringardner.net.imap.server.commands.Create;
import us.bringardner.net.imap.server.commands.Delete;
import us.bringardner.net.imap.server.commands.Enable;
import us.bringardner.net.imap.server.commands.Examine;
import us.bringardner.net.imap.server.commands.Expunge;
import us.bringardner.net.imap.server.commands.Fetch;
import us.bringardner.net.imap.server.commands.Idle;
import us.bringardner.net.imap.server.commands.Login;
import us.bringardner.net.imap.server.commands.Logout;
import us.bringardner.net.imap.server.commands.Lsub;
import us.bringardner.net.imap.server.commands.Move;
import us.bringardner.net.imap.server.commands.Namespace;
import us.bringardner.net.imap.server.commands.Noop;
import us.bringardner.net.imap.server.commands.Rename;
import us.bringardner.net.imap.server.commands.Search;
import us.bringardner.net.imap.server.commands.Select;
import us.bringardner.net.imap.server.commands.StartTls;
import us.bringardner.net.imap.server.commands.Status;
import us.bringardner.net.imap.server.commands.Store;
import us.bringardner.net.imap.server.commands.Subscribe;
import us.bringardner.net.imap.server.commands.Uid;
import us.bringardner.net.imap.server.commands.Unselect;
import us.bringardner.net.imap.server.commands.Unsubscribe;

/**
 * Maps IMAP command names to their command classes (as FtpCommandFactory does
 * for FTP). Commands can be replaced or added with {@link #addCommand(ICommand)}.
 */
public class ImapCommandFactory implements ICommandFactory {

	private static final long serialVersionUID = 1L;

	private static final Map<String, ICommand> commands = Collections.synchronizedMap(new HashMap<>());

	static {
		// any state (RFC 9051 section 6.1)
		addCommand(new Capability());
		addCommand(new Noop());
		addCommand(new Logout());
		// not authenticated (6.2)
		addCommand(new StartTls());
		addCommand(new Authenticate());
		addCommand(new Login());
		// authenticated (6.3)
		addCommand(new Enable());
		addCommand(new Select());
		addCommand(new Examine());
		addCommand(new Create());
		addCommand(new Delete());
		addCommand(new Rename());
		addCommand(new Subscribe());
		addCommand(new Unsubscribe());
		addCommand(new us.bringardner.net.imap.server.commands.List());
		addCommand(new Lsub());
		addCommand(new Namespace());
		addCommand(new Status());
		addCommand(new Append());
		addCommand(new Idle());
		// selected (6.4)
		addCommand(new Check());
		addCommand(new Close());
		addCommand(new Unselect());
		addCommand(new Expunge());
		addCommand(new Search());
		addCommand(new Fetch());
		addCommand(new Store());
		addCommand(new Copy());
		addCommand(new Move());
		addCommand(new Uid());
	}

	public static void addCommand(ICommand cmd) {
		commands.put(cmd.getName().toUpperCase(Locale.ROOT), cmd);
	}

	@Override
	public ICommand getCommand(IRequestContext context) {
		String name = context.getFirstToken();
		return name == null ? null : getCommand(name);
	}

	public ICommand getCommand(String name) {
		return commands.get(name.toUpperCase(Locale.ROOT));
	}
}
