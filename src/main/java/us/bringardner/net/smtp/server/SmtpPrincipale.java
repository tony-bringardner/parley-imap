package us.bringardner.net.smtp.server;

import us.bringardner.net.framework.server.AbstractPrincipal;

public class SmtpPrincipale extends AbstractPrincipal {
	String domain;
	String fullName;
	
	public SmtpPrincipale(String name) {
		super(name);		
	}

	@Override
	public boolean authenticate(byte[] credentials) {
		return false;
	}

}
