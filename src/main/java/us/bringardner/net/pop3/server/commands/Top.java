package us.bringardner.net.pop3.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.pop3.server.Pop3RequestProcessor;
import us.bringardner.net.pop3.server.Maildrop;


/** TOP msg n (RFC 1939 section 7): the headers and the first n lines of the body. */
public class Top extends BaseCommand {

	private static final long serialVersionUID = 1L;

	public Top() {
		super(TOP);
	}

	@Override
	public void execute(Pop3RequestProcessor processor, IRequestContext context) throws IOException {
		String arg = context.getNextToken();
		String count = context.getNextToken();
		int lines = parseNumber(count);
		if (arg == null || lines < 0) {
			processor.replyErr("TOP needs a message number and a number of lines");
			return;
		}
		Maildrop.Entry e = message(processor, arg);
		if (e != null && !processor.rejectUtf8(e)) {
			processor.replyContent("Top of message follows", out -> processor.getMaildrop().writeTop(e, lines, out));
		}
	}
}
