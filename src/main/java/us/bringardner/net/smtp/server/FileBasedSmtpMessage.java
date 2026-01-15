package us.bringardner.net.smtp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;

public class FileBasedSmtpMessage implements SmtpMessage{
	public static final String PROP_STORAGE = "SmtpStorage";
	public static final String PROP_TEMP_STORAGE = "SmtpTempStorage";
	
	String from;
	List<String> to = new ArrayList<>();
	StringBuilder data = new StringBuilder();
	SmtpServer server;
	FileSource file;

	//flush buffer when it reaches this size
	int bufferMax = 2048;
	StringBuilder buffer = new StringBuilder();


	boolean closed = false;

	public FileBasedSmtpMessage (SmtpServer server) {
		this.server = server;
	}

	@Override
	public void setFrom(String from) throws IOException {

		this.from = from;		
	}

	@Override
	public void addRecipient(String forwardPath) throws IOException {
		to.add(forwardPath);		
	}

	@Override
	public void append(String data) throws IOException {
		buffer.append(data);
		buffer.append('\n');
		if( buffer.length()> bufferMax) {
			flush();
		}

	}

	private void flush() throws IOException {
		if( file == null ) {
			FileSourceFactory f = server.getFileSourceFactory();
			/*
			 mallHome=/Volumes/Data/data/services/home
			Jmail.tmpDir=/Volumes/Data/data/services/temp
			 */
			String dir = server.getProperty(PROP_TEMP_STORAGE);
			if( dir == null) {
				dir = System.getenv("TMPDIR");
				if( dir == null ) {
					throw new IOException("Can't identify a temp storage area");
				}
			}

			FileSource tmpDir = f.createFileSource(dir);
			if( !tmpDir.exists()) {
				if( !tmpDir.mkdirs()) {
					throw new IOException("Can't create a temp storage area "+tmpDir);
				}
			}
			UUID u = new UUID(System.currentTimeMillis(), System.nanoTime());
			String name = u.toString()+".tmpSmtpMsg";
			file = tmpDir.getChild(name);
		}

		if(!file.exists()) {
			// create the file
			PrintStream out = new PrintStream(file.getOutputStream());
			{
				out.println(from);
				out.println("to="+to.size());
				for(String val : to) {
					out.println(val);
				}
			}
			out.close();
		} 
		

		try(OutputStream out = file.getOutputStream(true)) {
			out.write(buffer.toString().getBytes());
		}
		buffer = new StringBuilder();

	}

	@Override
	public InputStream getInputStream() throws IOException {
		throw new IOException("Not implemented");
	}

	@Override
	public void close() throws IOException {
		if( !closed) {
			if( buffer.length()>0) {
				flush();
			}
			String dir = server.getProperty(PROP_STORAGE);
			if( dir == null) {
				throw new IOException("Can't identify a smtp storage area");			
			}
			
			FileSource smtpDir = server.getFileSourceFactory().createFileSource(dir);
			if( !smtpDir.exists()) {
				if( !smtpDir.mkdirs()) {
					throw new IOException("Can't create smtp storage at "+smtpDir);
				}
			}
			FileSource target = smtpDir.getChild(file.getName());
			if( !file.renameTo(target)) {
				// try to copy
				byte [] data = new byte[bufferMax];
				try(OutputStream out = target.getOutputStream()) {
					try (InputStream in = file.getInputStream()) {
						int got = in.read(data);
						while(got>=0) {
							if( got > 0 ) {
								out.write(data, 0, got);
							}
							got = in.read(data);
						}
					}
				}
			}
			closed = true;
			
		}
	}

}
