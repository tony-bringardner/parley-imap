package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;

public class Turn implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	/*
	 * RFC 821 Simple Mail Transfer Protocol August 1982 
	 * 
         TURN (TURN)

            This command specifies that the receiver must either (1)
            send an OK reply and then take on the role of the
            sender-SMTP, or (2) send a refusal reply and retain the role
            of the receiver-SMTP.

            If program-A is currently the sender-SMTP and it sends the
            TURN command and receives an OK reply (250) then program-A
            becomes the receiver-SMTP.  Program-A is then in the initial
            state as if the transmission channel just opened, and it
            then sends the 220 service ready greeting.

            If program-B is currently the receiver-SMTP and it receives
            the TURN command and sends an OK reply (250) then program-B
            becomes the sender-SMTP.  Program-B is then in the initial
            state as if the transmission channel just opened, and it
            then expects to receive the 220 service ready greeting.

            To refuse to change roles the receiver sends the 502 reply.

         There are restrictions on the order in which these command may
         be used.

            The first command in a session must be the HELO command.
            The HELO command may be used later in a session as well.  If
            the HELO command argument is not acceptable a 501 failure
            reply must be returned and the receiver-SMTP must stay in
            the same state.

            The NOOP, HELP, EXPN, and VRFY commands can be used at any
            time during a session.

            The MAIL, SEND, SOML, or SAML commands begin a mail
            transaction.  Once started a mail transaction consists of
            one of the transaction beginning commands, one or more RCPT
            commands, and a DATA command, in that order.  A mail
            transaction may be aborted by the RSET command.  There may
            be zero or more transactions in a session.

            If the transaction beginning command argument is not
            acceptable a 501 failure reply must be returned and the
            receiver-SMTP must stay in the same state.  If the commands
            in a transaction are out of order a 503 failure reply must
            be returned and the receiver-SMTP must stay in the same
            state.

            The last command in a session must be the QUIT command.  The
            QUIT command can not be used at any other time in a session.

	 */
	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		proc.reply(REPLY_502_COMMAND_NOT_IMPLEMENTED,"");				
	}

	@Override
	public String getName() {
		return SMTP.TURN;
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
