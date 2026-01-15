package us.bringardner.net.smtp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.io.ILineReader;
import us.bringardner.io.ILineWriter;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.io.filesource.memory.MemoryFileSourceFactory;
import us.bringardner.net.framework.client.CommandClient;
import us.bringardner.net.framework.client.ICommandResponse;
import us.bringardner.net.framework.server.IAccessControlList;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.framework.server.PropertyAuthenticator;
import us.bringardner.net.smtp.SMTP;
import us.bringardner.net.smtp.client.SmtpClient;

public class TestServer implements SMTP {

	static SmtpServer server;
	static int port = 2525;
	static String domainName = "foo.bar.com";
	static String greetingExpect = "220 foo.bar.com Simple Mail Transport ready";
	
	@BeforeAll
	public static void setup() throws Exception {

		System.setProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY, PropertyAuthenticator.class.getCanonicalName());
		System.setProperty("user0","user1@"+domainName+", password  , SEND_MAIL|REC_MAIL");
		System.setProperty("user1","user2@"+domainName+", password  , SEND_MAIL|REC_MAIL");
		
		System.setProperty(FileBasedSmtpMessage.PROP_TEMP_STORAGE,"/temp");
		System.setProperty(FileBasedSmtpMessage.PROP_STORAGE,"/smtp");
		
		server = new SmtpServer() ;
		server.setDomainName(domainName);
		server.setPort(port);
		
		FileSourceFactory f = new MemoryFileSourceFactory();
		server.setFileSourceFactory(f);
		
		server.start();
		long start = System.currentTimeMillis();
		while(!server.isRunning()) {
			if(System.currentTimeMillis()-start> 2000) {
				throw new RuntimeException("Server failed to start");
			}
			Thread.sleep(10);
		}

	}

	@AfterAll
	public static void teardown() throws Exception {
		if( server !=null) {
			long start = System.currentTimeMillis();
			server.stop();
			while(server.isRunning()) {
				if(System.currentTimeMillis()-start> 5000) {
					throw new RuntimeException("Server failed to stop");
				}
				Thread.sleep(10);				
			}
		}
	}

	@Test()
	public void testHelp01() throws IOException {
		String helpExpect = "RFC 821 Simple Mail Transfer Protocol August 1982 \n"
				+ "	 * VERIFY (VRFY)\n"
				+ "\n"
				+ "            This command asks the receiver to confirm that the argument\n"
				+ "            identifies a user.  If it is a user name, the full name of\n"
				+ "            the user (if known) and the fully specified mailbox are\n"
				+ "            returned.\n"
				+ "\n"
				+ "            This command has no effect on any of the reverse-path\n"
				+ "            buffer, the forward-path buffer, or the mail data buffer.\n"
				+ "\n"
				+ "			S: VRFY Smith\n"
				+ "			R: 250 Fred Smith <Smith@USC-ISIF.ARPA>\n"
				+ "\n"
				+ "         Or\n"
				+ "\n"
				+ "            S: VRFY Smith\n"
				+ "            R: 251 User not local; will forward to <Smith@USC-ISIQ.ARPA>\n"
				+ "\n"
				+ "         Or\n"
				+ "\n"
				+ "            S: VRFY Jones\n"
				+ "            R: 550 String does not match anything.\n"
				+ "\n"
				+ "         Or\n"
				+ "\n"
				+ "            S: VRFY Jones\n"
				+ "            R: 551 User not local; please try <Jones@USC-ISIQ.ARPA>\n"
				+ "\n"
				+ "         Or\n"
				+ "\n"
				+ "            S: VRFY Gourzenkyinplatz\n"
				+ "            R: 553 User ambiguous."
				;
		
		
		
		String [] expectLines = ("250-RFC 821 Simple Mail Transfer Protocol August 1982 \n"
				+ "250-	 * VERIFY (VRFY)\n"
				+ "250-\n"
				+ "250-            This command asks the receiver to confirm that the argument\n"
				+ "250-            identifies a user.  If it is a user name, the full name of\n"
				+ "250-            the user (if known) and the fully specified mailbox are\n"
				+ "250-            returned.\n"
				+ "250-\n"
				+ "250-            This command has no effect on any of the reverse-path\n"
				+ "250-            buffer, the forward-path buffer, or the mail data buffer.\n"
				+ "250-\n"
				+ "250-			S: VRFY Smith\n"
				+ "250-			R: 250 Fred Smith <Smith@USC-ISIF.ARPA>\n"
				+ "250-\n"
				+ "250-         Or\n"
				+ "250-\n"
				+ "250-            S: VRFY Smith\n"
				+ "250-            R: 251 User not local; will forward to <Smith@USC-ISIQ.ARPA>\n"
				+ "250-\n"
				+ "250-         Or\n"
				+ "250-\n"
				+ "250-            S: VRFY Jones\n"
				+ "250-            R: 550 String does not match anything.\n"
				+ "250-\n"
				+ "250-         Or\n"
				+ "250-\n"
				+ "250-            S: VRFY Jones\n"
				+ "250-            R: 551 User not local; please try <Jones@USC-ISIQ.ARPA>\n"
				+ "250-\n"
				+ "250-         Or\n"
				+ "250-\n"
				+ "250-            S: VRFY Gourzenkyinplatz\n"
				+ "250-            R: 553 User ambiguous.\n"
				+ "250 OK").split("\n");
		
		try(SmtpClient client = new SmtpClient("localhost",port) ){
			if(!client.connect()) {
				throw new IOException("Can't connect");
			}
			String greeting = client.readLine();
			assertEquals(greetingExpect, greeting);
			
			ICommandResponse resp = client.executeCommand(SMTP.HELP,SMTP.VRFY);
			
			assertTrue(resp.isPositive());
			
			String text = resp.getResponseText().trim();
			assertEquals(helpExpect, text);
			String [] lines = resp.getFullResponse();
			
			assertEquals(expectLines.length, lines.length);
			for (int idx = 0; idx < lines.length; idx++) {
				assertEquals(expectLines[idx], lines[idx]);
			}
			
			resp = client.executeCommand(SMTP.QUIT);
			assertTrue(resp.isPositive());
		}
	}
	
	@Test()
	public void testAcl01() throws IOException {
		
		IAccessControlList acl = server.getAccessControl();
		byte[] password = "password".getBytes();

		IPrincipal p = acl.getPrincipal("user1@"+domainName);
		
		assertNotNull(p.authenticate(password), "user1 was not authenticated");

		
		List<IPermission> perms = p.getPermisssions();
		for(IPermission perm : perms) {
			System.out.println(perm);
		}
		Map<Object, Object> parms = p.getParameters();
		for(Object key: parms.keySet()) {
			Object val = parms.get(key);
			System.out.println(key+"="+val);
		}
	}
	
	@Test()
	public void testServer01() throws IOException {
		// Test required SMTP commands HELO, MAIL, RCPT, DATA, RSET, VRFY, NOOP, and QUIT
		//220 bringardner.us Simple Mail Transport Service Ready
		try(CommandClient client = new CommandClient("localhost",port)){
			if(!client.connect()) {
				throw new IOException("Can't connect");
			}
			String greeting = client.readLine();
			System.out.println(greeting);
			ICommandResponse resp = client.executeCommand(SMTP.HELO,"name");
			assertTrue(resp.isPositive());
			
			resp = client.executeCommand(SMTP.NOOP);
			assertTrue(resp.isPositive());
			
			resp = client.executeCommand(SMTP.RSET);
			assertTrue(resp.isPositive());
			
			resp = client.executeCommand(SMTP.VRFY,"user1@"+domainName);
			assertTrue(resp.isPositive());
			
			// MAIL <SP> FROM:<reverse-path> <CRLF>
			//resp = client.executeCommand(SMTP.MAIL_FROM,"FROM:user1@foo.bar.com");
			//assertTrue(resp.isPositive());
			
            //RCPT <SP> TO:<forward-path> <CRLF>
			//resp = client.executeCommand(SMTP.RCPT_TO,"TO:user2@foo.bar.com");
			//assertTrue(resp.isPositive());
			
            //DATA <CRLF>
			//resp = client.executeCommand(SMTP.DATA);
			//assertTrue(resp.isPositive());
			
			//  now send an email
			//ILineWriter out = client.getWriter();
			
			//ILineReader in = client.getReader();
			
			
			resp = client.executeCommand(SMTP.QUIT);
			assertTrue(resp.isPositive());
		}
	}

	@Test()
	public void testHelp02() throws IOException {
		
		try(SmtpClient client = new SmtpClient("localhost",port) ){
			if(!client.connect()) {
				throw new IOException("Can't connect");
			}
			String greeting = client.readLine();
			assertEquals(greetingExpect, greeting);
			
			//  get help for ALL commands
			ICommandResponse resp = client.executeCommand(SMTP.HELP);
			
			assertTrue(resp.isPositive());
			
			
			String [] lines = resp.getFullResponse();
			// This will change overtime as commands are added
			// 250 is the minimum with help from all the mandatory commands
			assertTrue(lines.length>=250);
			
			resp = client.executeCommand(SMTP.QUIT);
			assertTrue(resp.isPositive());
		}
	}

	@Test()
	public void testMail01() throws IOException {

		try(CommandClient client = new CommandClient("localhost",port)){
			if(!client.connect()) {
				throw new IOException("Can't connect");
			}
			String greeting = client.readLine();
			System.out.println(greeting);
			ICommandResponse resp = client.executeCommand(SMTP.HELO,"name");
			assertTrue(resp.isPositive());
			
			
			// MAIL <SP> FROM:<reverse-path> <CRLF>
			resp = client.executeCommand(SMTP.MAIL_FROM,"FROM:user1@"+domainName);
			assertTrue(resp.isPositive());
			
	        //RCPT <SP> TO:<forward-path> <CRLF>
			resp = client.executeCommand(SMTP.RCPT_TO,"TO:user2@"+domainName);
			assertTrue(resp.isPositive());
			
	        //DATA <CRLF>
			
			//  now send an email
			ILineWriter out = client.getWriter();
			ILineReader in = client.getReader();
			out.writeLine("DATA");
			String tmp = in.readLine();
			assertTrue(tmp.startsWith(""+REPLY_354_START_MAIL_INPUT));
			
			String expect = "Received: from MIT-AI.ARPA by USC-ISIE.ARPA ;\n"
					+ "2 Nov 81 22:40:10 UT\n"
					+ "Date: 2 Nov 81 22:33:44\n"
					+ "From: John Q. Public <JQP@MIT-AI.ARPA>\n"
					+ "Subject:  The Next Meeting of the Board\n"
					+ "To: Jones@BBN-Vax.ARPA\n"
					+ "\n"
					+ "Bill:\n"
					+ "The next meeting of the board of directors will be\n"
					+ "on Tuesday.\n"
					+ ".\n"
					+ ".Another metting to be held on Thursday."
					+ "                                             John."
					;
			
			String lines [] = expect.split("\n");
			
			for(String line : lines) {
				if( line.length()>0) {
					if(line.charAt(0) == '.') {
						line = "."+line;
					}
				}
				out.writeLine(line);
				client.flush();
			}
			out.writeLine(".");
			client.flush();
			tmp = in.readLine();
			assertTrue(tmp.startsWith(""+REPLY_250_REQUESTED_MAIL_ACTION_OKAY));
			
			resp = client.executeCommand(SMTP.QUIT);
			assertTrue(resp.isPositive());
			
			FileSourceFactory f = server.getFileSourceFactory();
			FileSource dir = f.createFileSource("/temp");
			assertTrue(dir.exists(),"Temp dir does not exist");
			FileSource [] kids = dir.listFiles();
			//  the temp file has been renamed so there should not be a file in the temp dir
			assertEquals(0, kids.length,"temp dir kids wrong len");
			
			dir = f.createFileSource("/smtp");
			assertTrue(dir.exists(),"smtp dir doess not exists");
			kids = dir.listFiles();
			for(FileSource file : kids) {
				System.out.println(file);
			}
			assertEquals(1, kids.length,"smtp kids wrong len");
			try(InputStream input = kids[0].getInputStream()) {
				String actual = new String(input.readAllBytes());
				String actualLines [] = actual.split("\n");
				
				boolean ok = expect.equals(actual);
				System.out.println("ok="+ok);
			}
		}
	}

}
