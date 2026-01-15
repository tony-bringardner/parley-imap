/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 2026, ... <A href="http://bringardner.com/tony">Tony Bringardner</A>
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
package us.bringardner.net.smtp.server;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Properties;

import javax.net.ssl.SSLContext;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.Connection;
import us.bringardner.net.framework.IConnection;
import us.bringardner.net.framework.IConnectionFactory;
import us.bringardner.net.framework.IProcessor;
import us.bringardner.net.framework.IProcessorFactory;
import us.bringardner.net.framework.server.Server;


/**
 * @author Tony Bringardner
 *
 */
public class SmtpServer extends Server {

	/*
	 * These are the well-known ports for SMTP as of 01/2026
	 *  
	 * 	25		Default SMTP port	None	Primarily used for server-to-server email transmission. Often blocked by ISPs due to spam concerns.
	 *	465		SMTPS (Secure SMTP)	SSL	Historically used for secure email transmission, but not officially recognized as a standard.
	 *	587		Mail submission	STARTTLS	Recommended for secure email submission. Supports authentication and is widely accepted by ISPs.
	 *	2525	Alternative SMTP port	None or STARTTLS	Not an official port but often used as an alternative when standard ports are blocked.
	 */
	public static final int SMTP_PORT = 25;
	public static final int SMTP_SSL_PORT = 465;
	public static final int SMTP_STARTTLS_PORT = 587;
	public static final int SMTP_ALTERNATIVE_PORT = 2525;
	
	public static final String SMTP_NAME = "Smtp";
	private String domainName;
	
	public static final String DOMAIN_PROP = "Domain";
	public static final String CONFIG_PROP = SMTP_NAME+".properties";	
	public static final String DEBUG_PROP = SMTP_NAME+".debug";
	public static final String FILE_SOURCE_PROP = SMTP_NAME+".fileSource";
    
	public static final String EXTERNAL_ADDRESS_PROP = SMTP_NAME+".externalAddress";
	
	
	
	private FileSourceFactory factory = FileSourceFactory.getDefaultFactory();
	
	private class ServerConnection extends Connection {

		public ServerConnection( Socket socket, boolean useCRLF,Level logLevel) throws IOException {
			super(socket,useCRLF); 
			getLogger().setLevel(logLevel);
		}

		@Override
		public SSLContext getSSLContext(String sslOrTsl) throws IOException {
			String tmp = SmtpServer.this.getProtocol();
			SmtpServer.this.setProtocol(sslOrTsl);
			SSLContext ret = SmtpServer.this.getSSLContext();
			SmtpServer.this.setProtocol(tmp);
			return ret;
		}
		
	}
	
	/**
	 * @param port
	 * @param name
	 * @param secure
	 * @throws IOException 
	 */
	public SmtpServer(int port) {
		super(port, SMTP_NAME);				
		setPropertyPrefix("SmtpServer");
		setDaemon(false);
		initMe();
		getLogger().setLevel(us.bringardner.core.ILogger.Level.DEBUG);
	}
	
	public SmtpServer() {
		this(SMTP_PORT);
	}
	
	public static void main(String[] args) throws Exception {
		System.out.println("\nStarting SmtpServer with "+args.length+" args");
		for(int idx = 0; idx < args.length; idx++ ) {
			if( args[idx].startsWith("-D")) {
				String [] tmp = args[idx].substring(2).split("=");
				if( tmp.length == 2) {
					System.out.println("\t"+tmp[0]+"="+tmp[1]);
					System.setProperty(tmp[0],tmp[1]);
				} else {
					System.out.println("Invalid arg = "+args[idx]);
				}
			} else {
				System.out.println("\t"+args[idx]+"="+args[idx+1]);
				System.setProperty(args[idx++],args[idx]);
			}
		}
		
		String tmp = System.getProperty(CONFIG_PROP);
		if( tmp != null ) {
			System.out.println("Looking for "+tmp);
			File file = new File(tmp);
			InputStream in = new FileInputStream(file);
			Properties prop = System.getProperties();
			
			try {
				prop.load(in);
			} finally {
				in.close();
			}
			
			System.out.println("Loaded properties from "+tmp);
			//  Anything on the command line should override the properties file
			for(int idx = 0; idx < args.length; idx++ ) {
				System.setProperty(args[idx++],args[idx]);
			}
		}
		int port = SMTP_PORT;
		tmp = System.getProperty(SMTP_NAME+".port");
		if( tmp != null )  {
			port = Integer.parseInt(tmp);
		}
		
		SmtpServer server = new SmtpServer(port);		
		server.start();
		System.out.println("SmtpServer started on port "+port);
		
	}

	
	public String getDomainName() {
		return domainName;
	}

	public void setDomainName(String domainName) {
		this.domainName = domainName;
		setServerGreating("220 "+domainName+" Simple Mail Transport ready");
	}

	private void initMe()  {
		
		setName("SmtpServer");
		
		setProcessorFactory(new IProcessorFactory() {
			public IProcessor getProcessor() {
				
				SmtpCommandProcessor ret = new SmtpCommandProcessor();
				ret.getLogger().setLevel(SmtpServer.this.getLogger().getLevel());
				return ret;
				
			}			
		});
		
		setConnectionFactory(new IConnectionFactory() {
			public IConnection getConnection(Socket socket) throws IOException {
				return new ServerConnection(socket,true,SmtpServer.this.getLogger().getLevel());
			}			
		});
		
		//  Determine which FileSource to use
		String tmp = getProperty(FILE_SOURCE_PROP);
		if( tmp != null ) {
			tmp = tmp.toLowerCase();
			setFileSourceFactory(FileSourceFactory.getFileSourceFactory(tmp));
		} else {
			setFileSourceFactory(FileSourceFactory.getDefaultFactory());
		}
		
		tmp = getProperty(DOMAIN_PROP);
		if( tmp !=null ) {
			domainName = tmp;
		}
		if( domainName == null ) {
			try {
				InetAddress addr = InetAddress.getLocalHost();
				domainName = addr.getCanonicalHostName();
			} catch (Exception e) {
				domainName = getName();
			}
		}
		
		setServerGreating("220 "+domainName+"Simple Mail Transport ready");
	}
	
	
	public FileSourceFactory getFileSourceFactory() {
		return factory;
	}
	
	public void setFileSourceFactory(FileSourceFactory factory) {
		this.factory = factory;
	}
	
	@Override
	public void logDebug(String msg) {
		if( isSecure()) {
			msg = "(S) "+msg;
		}
		super.logDebug(msg);
	}

	@Override
	public void logDebug(String msg, Throwable error) {
		if( isSecure()) {
			msg = "(S) "+msg;
		}
		
		super.logDebug(msg, error);
	}

	/**
	 * Override for use a more robust storage system
	 * @return
	 */
	public SmtpMessage createMessage() {
		return new FileBasedSmtpMessage(this);
	}

	
}
