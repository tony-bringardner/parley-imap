/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 2026,... <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *   
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~V000.00.00-V000.00.00-
 */
/*
 * Created on Jan 12, 2026
 *
 */
package us.bringardner.net.smtp;


/**
 * @author Tony Bringardner
 *
 */
public interface SMTP {

	/*
	 *	RFC 5321                          SMTP                      October 2008
	 */
	public static final int SMTP_PORT = 25;
	public static final int SMTP_SSL_PORT = 465;

	
	//  RFC 821 Simple Mail Transfer Protocol August 1982
	//HELO	Initiates the SMTP session and identifies the client.	HELO client.example.com
	public static final String HELO = "HELO";
	//MAIL FROM	Specifies the sender's email address and starts a new mail transaction.	MAIL FROM:<sender@example.com>
	public static final String MAIL_FROM = "MAIL";
	//RCPT TO	Specifies the recipient's email address. This can be repeated for multiple recipients.	RCPT TO:<recipient@example.com>
	public static final String RCPT_TO = "RCPT";
	//DATA	Indicates the start of the email content transfer.	DATA
	public static final String DATA = "DATA";
	//QUIT	Ends the SMTP session.	QUIT
	public static final String QUIT = "QUIT";
	//RSET	Resets the current mail transaction without closing the connection.	RSET
	public static final String RSET = "RSET";
	//NOOP	No operation command, used to check if the server is responsive.	NOOP
	public static final String NOOP = "NOOP";
	//VRFY	Verifies if a specified email address exists on the server.	VRFY user@example.com
	public static final String VRFY = "VRFY";
	//HELP	Requests a list of commands supported by the server.	HELP
	public static final String HELP = "HELP";

	public static final String TURN = "TURN";

	// RFC 1425                SMTP Service Extensions            February 1993
	//EHLO	Extended version of HELO, used if the server supports ESMTP.	EHLO client.example.com
	public static final String EHLO = "EHLO";

	//RFC 3207     SMTP Service Extension - Secure SMTP over TLS February 2002
	public static final String STARTTLS = "STARTTLS";

	//RFC 5321                          SMTP                      October 2008
	public static final String EXPN = "EXPN";

	//RFC 8689     SMTP Service Extension - SMTP Require TLS Option
	public static final String REQUIRETLS = "REQUIRETLS";

	//  Non - RFC values.
	public static final String DEBUG = "DEBUG"; 
	public static final String NOT_IMPLEMENTED = "not implemented.";

	public static final int REPLY_214_HELP_MESSAGE = 214;
	public static final int REPLY_220_DOMAIN_SERVICE_READY = 220;
	public static final int REPLY_221_DOMAIN_SERVICE_CLOSING_TRANSMISSION_CHANNEL = 221;
	public static final int REPLY_250_REQUESTED_MAIL_ACTION_OKAY = 250;
	public static final int REPLY_251_USER_NOT_LOCAL = 251;
	public static final int REPLY_252_CANNOT_VRFY_USER = 252;
	public static final int REPLY_354_START_MAIL_INPUT = 354;
	public static final int REPLY_421_DOMAIN_SERVICE_NOT_AVAILABLE = 421;
	public static final int REPLY_450_REQUESTED_MAIL_ACTION_NOT_TAKEN = 450;
	public static final int REPLY_451_REQUESTED_ACTION_ABORTED = 451;
	public static final int REPLY_452_REQUESTED_ACTION_NOT_TAKEN = 452;
	public static final int REPLY_455_SERVER_UNABLE_TO_ACCOMMODATE_PARAMETERS = 455;
	public static final int REPLY_500_SYNTAX_ERROR = 500;
	public static final int REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS = 501;
	public static final int REPLY_502_COMMAND_NOT_IMPLEMENTED = 502;
	public static final int REPLY_503_BAD_SEQUENCE_OF_COMMANDS = 503;
	public static final int REPLY_504_COMMAND_PARAMETER_NOT_IMPLEMENTED = 504;
	public static final int REPLY_550_REQUESTED_ACTION_NOT_TAKEN = 550;
	public static final int REPLY_551_USER_NOT_LOCAL = 551;
	public static final int REPLY_552_REQUESTED_MAIL_ACTION_ABORTED = 552;
	public static final int REPLY_553_REQUESTED_ACTION_NOT_TAKEN = 553;
	public static final int REPLY_554_TRANSACTION_FAILED = 554;
	public static final int REPLY_555_MAIL_FROM_RCPT_TO_PARAMETERS_NOT_RECOGNIZED_OR_NOT_IMPLEMENTED = 555;



}
