package us.bringardner.net.smtp.server.command;

import java.io.IOException;

import us.bringardner.net.framework.IConnection;
import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.server.SmtpCommand;
import us.bringardner.net.smtp.server.SmtpMessage;

public class Data implements SmtpCommand, SMTP{

	private static final long serialVersionUID = 1L;

	private static final String help = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
			+ "	 *   DATA (DATA)\n"
			+ "\n"
			+ "            The receiver treats the lines following the command as mail\n"
			+ "            data from the sender.  This command causes the mail data\n"
			+ "            from this command to be appended to the mail data buffer.\n"
			+ "            The mail data may contain any of the 128 ASCII character\n"
			+ "            codes.\n"
			+ "\n"
			+ "            The mail data is terminated by a line containing only a\n"
			+ "            period, that is the character sequence \"<CRLF>.<CRLF>\" (see\n"
			+ "            Section 4.5.2 on Transparency).  This is the end of mail\n"
			+ "            data indication.\n"
			+ "\n"
			+ "            The end of mail data indication requires that the receiver\n"
			+ "            must now process the stored mail transaction information.\n"
			+ "            This processing consumes the information in the reverse-path\n"
			+ "            buffer, the forward-path buffer, and the mail data buffer,\n"
			+ "            and on the completion of this command these buffers are\n"
			+ "            cleared.  If the processing is successful the receiver must\n"
			+ "            send an OK reply.  If the processing fails completely the\n"
			+ "            receiver must send a failure reply.\n"
			+ "\n"
			+ "            When the receiver-SMTP accepts a message either for relaying\n"
			+ "            or for final delivery it inserts at the beginning of the\n"
			+ "            mail data a time stamp line.  The time stamp line indicates\n"
			+ "            the identity of the host that sent the message, and the\n"
			+ "            identity of the host that received the message (and is\n"
			+ "            inserting this time stamp), and the date and time the\n"
			+ "            message was received.  Relayed messages will have multiple\n"
			+ "            time stamp lines.\n"
			+ "\n"
			+ "            When the receiver-SMTP makes the \"final delivery\" of a\n"
			+ "            message it inserts at the beginning of the mail data a\n"
			+ "            return path line.  The return path line preserves the\n"
			+ "            information in the <reverse-path> from the MAIL command.\n"
			+ "            Here, final delivery means the message leaves the SMTP\n"
			+ "            world.  Normally, this would mean it has been delivered to\n"
			+ "            the destination user, but in some cases it may be further\n"
			+ "            processed and transmitted by another mail system.\n"
			+ "\n"
			+ "               It is possible for the mailbox in the return path be\n"
			+ "               different from the actual sender's mailbox, for example,\n"
			+ "               if error responses are to be delivered a special error\n"
			+ "               handling mailbox rather than the message senders.\n"
			+ "\n"
			+ "            The preceding two paragraphs imply that the final mail data\n"
			+ "            will begin with a  return path line, followed by one or more\n"
			+ "            time stamp lines.  These lines will be followed by the mail\n"
			+ "            data header and body [2].  See Example 8.\n"
			+ "\n"
			+ "            Special mention is needed of the response and further action\n"
			+ "            required when the processing following the end of mail data\n"
			+ "            indication is partially successful.  This could arise if\n"
			+ "            after accepting several recipients and the mail data, the\n"
			+ "            receiver-SMTP finds that the mail data can be successfully\n"
			+ "            delivered to some of the recipients, but it cannot be to\n"
			+ "            others (for example, due to mailbox space allocation\n"
			+ "            problems).  In such a situation, the response to the DATA\n"
			+ "            command must be an OK reply.  But, the receiver-SMTP must\n"
			+ "            compose and send an \"undeliverable mail\" notification\n"
			+ "            message to the originator of the message.  Either a single\n"
			+ "            notification which lists all of the recipients that failed\n"
			+ "            to get the message, or separate notification messages must\n"
			+ "            be sent for each failed recipient (see Example 7).  All\n"
			+ "            undeliverable mail notification messages are sent using the\n"
			+ "            MAIL command (even if they result from processing a SEND,\n"
			+ "            SOML, or SAML command).\n"
			+ "\n"
			+ "            Example of Return Path and Received Time Stamps\n"
			+ "\n"
			+ "      Return-Path: <@GHI.ARPA,@DEF.ARPA,@ABC.ARPA:JOE@ABC.ARPA>\n"
			+ "      Received: from GHI.ARPA by JKL.ARPA ; 27 Oct 81 15:27:39 PST\n"
			+ "      Received: from DEF.ARPA by GHI.ARPA ; 27 Oct 81 15:15:13 PST\n"
			+ "      Received: from ABC.ARPA by DEF.ARPA ; 27 Oct 81 15:01:59 PST\n"
			+ "      Date: 27 Oct 81 15:01:01 PST\n"
			+ "      From: JOE@ABC.ARPA\n"
			+ "      Subject: Improved Mailing System Installed\n"
			+ "      To: SAM@JKL.ARPA\n"
			+ "\n"
			+ "      This is to inform you that ...\n"
			+ "";

	@Override
	public void execute(ICommandProcessor proc, IRequestContext ctx) throws IOException {
		/*
		 *  DATA
               I: 354 -> data -> S: 250
                                 F: 552, 554, 451, 452

			451 Requested action aborted: error in processing
         	452 Requested action not taken: insufficient system storage
         	552 Requested mail action aborted: exceeded storage allocation
         	554 Transaction failed
		 */


		SmtpMessage msg = (SmtpMessage) proc.getSessionValue(SmtpMessage.MESSAGE);
		if( msg == null ) {
			proc.reply(REPLY_503_BAD_SEQUENCE_OF_COMMANDS,"No message in session.Use MAIL first");
			return;
		} 

		try {
			IConnection con = proc.getConnection();
			String line = null;
			con.writeLine(REPLY_354_START_MAIL_INPUT+" Start mail input; end with <CRLF>.<CRLF>");

			/*
			 * 4.5.2.  TRANSPARENCY

         Without some provision for data transparency the character
         sequence "<CRLF>.<CRLF>" ends the mail text and cannot be sent
         by the user.  In general, users are not aware of such
         "forbidden" sequences.  To allow all user composed text to be
         transmitted transparently the following procedures are used.

            1. Before sending a line of mail text the sender-SMTP checks
            the first character of the line.  If it is a period, one
            additional period is inserted at the beginning of the line.

            2. When a line of mail text is received by the receiver-SMTP
            it checks the line.  If the line is composed of a single
            period it is the end of mail.  If the first character is a
            period and there are other characters on the line, the first
            character is deleted.
			 */
			while((line=con.readLine()) !=null && !line.equals(".")) {
				if( line.startsWith(".")) {
					line = line.substring(1);
				}
				msg.append(line);
			}

			msg.close();

			proc.reply(REPLY_250_REQUESTED_MAIL_ACTION_OKAY,"");
		} catch (Throwable e) {
			proc.reply(REPLY_554_TRANSACTION_FAILED,e.toString());
		}
		
		proc.getSessionValues().remove(SmtpMessage.MESSAGE);
	}


	@Override
	public String getHelp() {
		return help;
	}

	@Override
	public String getName() {
		return SMTP.DATA;
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
