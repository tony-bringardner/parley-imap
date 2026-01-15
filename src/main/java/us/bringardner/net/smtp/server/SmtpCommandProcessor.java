package us.bringardner.net.smtp.server;

import java.util.Map;
import java.util.TreeMap;

import us.bringardner.net.framework.server.AbstractCommandProcessor;
import us.bringardner.net.framework.server.ICommandFactory;
import us.bringardner.net.smtp.SMTP;

public class SmtpCommandProcessor extends AbstractCommandProcessor implements SMTP {

	private static final long serialVersionUID = 1L;
	private static Map<Integer,String> map;
	
	static {
		map = new TreeMap<Integer, String>();
		map.put(211,"SYSTEM STATUS");
		map.put(214,"HELP MESSAGE");
		map.put(220,"<DOMAIN> SERVICE READY");
		map.put(221,"<DOMAIN> SERVICE CLOSING TRANSMISSION CHANNEL");
		map.put(250,"REQUESTED MAIL ACTION OKAY");
		map.put(251,"USER NOT LOCAL");
		map.put(252,"CANNOT VRFY USER");
		map.put(354,"START MAIL INPUT");
		map.put(421,"<DOMAIN> SERVICE NOT AVAILABLE");
		map.put(450,"REQUESTED MAIL ACTION NOT TAKEN");
		map.put(451,"REQUESTED ACTION ABORTED");
		map.put(452,"REQUESTED ACTION NOT TAKEN");
		map.put(455,"SERVER UNABLE TO ACCOMMODATE PARAMETERS");
		map.put(500,"SYNTAX ERROR");
		map.put(501,"SYNTAX ERROR IN PARAMETERS OR ARGUMENTS");
		map.put(502,"COMMAND NOT IMPLEMENTED");
		map.put(503,"BAD SEQUENCE OF COMMANDS");
		map.put(504,"COMMAND PARAMETER NOT IMPLEMENTED");
		map.put(550,"REQUESTED ACTION NOT TAKEN");
		map.put(551,"USER NOT LOCAL");
		map.put(552,"REQUESTED MAIL ACTION ABORTED");
		map.put(553,"REQUESTED ACTION NOT TAKEN");
		map.put(554,"TRANSACTION FAILED");
		map.put(555,"MAIL FROM/RCPT TO PARAMETERS NOT RECOGNIZED OR NOT IMPLEMENTED");

	}
	
	@Override
	public String translateResponseCode(int responseCode) {
		// not translation required
		/**
		 * The framework uses generic code (100,200,300,400,500).  
		 * While not 
		 */
		String ret = ""+responseCode;
		
		return ret;
	}

	@Override
	public ICommandFactory getCommandFactory() {
		return new SmtpCommandFactory();
	}
}
