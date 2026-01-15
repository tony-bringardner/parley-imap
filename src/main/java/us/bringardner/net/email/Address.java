package us.bringardner.net.email;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 5322                Internet Message Format             October 2008
 * 
 * 3.4.  Address Specification

   Addresses occur in several message header fields to indicate senders
   and recipients of messages.  An address may either be an individual
   mailbox, or a group of mailboxes.

   address         =   mailbox / group
   mailbox         =   name-addr / addr-spec
   name-addr       =   [display-name] angle-addr
   angle-addr      =   [CFWS] "<" addr-spec ">" [CFWS] /
                       obs-angle-addr
   group           =   display-name ":" [group-list] ";" [CFWS]
   display-name    =   phrase
   mailbox-list    =   (mailbox *("," mailbox)) / obs-mbox-list
   address-list    =   (address *("," address)) / obs-addr-list
   group-list      =   mailbox-list / CFWS / obs-group-list
   
 * 
 * 3.4.1.  Addr-Spec Specification
 * 
 * addr-spec       =   local-part "@" domain
 *
 * local-part      =   dot-atom / quoted-string / obs-local-part
 *
 *  domain          =   dot-atom / domain-literal / obs-domain
 *
 * domain-literal  =   [CFWS] "[" *([FWS] dtext) [FWS] "]" [CFWS]
 *
 * dtext           =   %d33-90 /          ; Printable US-ASCII
 *                      %d94-126 /         ;  characters not including
 *                      obs-dtext          ;  "[", "]", or "\"
 */
public class Address {
	
	
	
	static String rx = "(?<user>[a-zA-Z0-9._%+-]+)@(?<domain>[a-zA-Z0-9.-]+)";
	
	
	public static Address parseAddress(String addressText1) {
		Address ret = new Address();
		String addressText = addressText1;
		
		int idx = addressText.indexOf('<');
		if( idx >= 0 ) {
			String tmp = addressText.substring(0,idx).trim();
			if( !tmp.isEmpty()) {
				ret.displayName=tmp;
			}
			addressText = addressText.substring(idx+1);
			idx = addressText.indexOf('>');
			if( idx >= 0 ) {
				addressText = addressText.substring(0,idx).trim();
			}
		}
		
		idx = addressText.indexOf('@');
		if( idx > 0 ) {
			ret.user = addressText.substring(0,idx).trim();
			ret.domain = addressText.substring(idx+1).trim();
		}
			
		return ret;
	}
	
	
	protected String displayName;
	// localPart is more commonly refereed to as user
	protected String user;
	protected String domain;
	
	
	
	Address() {		
	}
	
	public Address(String displayName, String user, String domain) {
		this(user,domain);
		this.displayName = displayName;
	}

	public Address(String user, String domain) {
		this.user = user;
		this.domain = domain;
	}
	
	public Address(String addressText) {
		
	}
	
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public String getDomain() {
		return domain;
	}
	public void setDomain(String domain) {
		this.domain = domain;
	}

	public static Address parseAddressx(String addressText1) {
		Address ret = new Address();
		String addressText = addressText1;
		
		String myrx=rx;
		int idx = addressText.indexOf('<');
		if( idx >= 0 ) {
			String tmp = addressText.substring(0,idx).trim();
			if( !tmp.isEmpty()) {
				ret.displayName=tmp;
			}
			addressText = addressText.substring(idx+1);
			idx = addressText.indexOf('>');
			if( idx >= 0 ) {
				addressText = addressText.substring(0,idx).trim();
			}
		}
		
		Pattern p = Pattern.compile(myrx);
		Matcher m = p.matcher(addressText);
	
		if(m.matches()) {
			ret.user = m.group("user");
			ret.domain = m.group("domain");
		}
		
		return ret;
	}
	
	
	
}
