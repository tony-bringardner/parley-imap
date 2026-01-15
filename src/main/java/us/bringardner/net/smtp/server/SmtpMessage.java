package us.bringardner.net.smtp.server;

import java.io.IOException;
import java.io.InputStream;

public interface SmtpMessage extends AutoCloseable {
	
	// used to identify session objects
	public static final String MESSAGE = "Message";
	
	void setFrom(String tmp) throws IOException;

	// Recipient 
	void addRecipient(String forwardPath) throws IOException;
	
	void append(String data) throws IOException;
	
	InputStream getInputStream() throws IOException;
	
	void close() throws IOException;
	
}
