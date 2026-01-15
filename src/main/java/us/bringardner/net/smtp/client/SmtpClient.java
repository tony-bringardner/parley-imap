package us.bringardner.net.smtp.client;

import static us.bringardner.net.framework.IGenericResponseCode.REPLY_100_GENERIC_POSITIVE_PRELIMINARY;
import static us.bringardner.net.framework.IGenericResponseCode.REPLY_200_GENERIC_OK;
import static us.bringardner.net.framework.IGenericResponseCode.REPLY_300_GENERIC_TEMPOARY_OK;
import static us.bringardner.net.framework.IGenericResponseCode.REPLY_400_GENERIC_TEMPOARY_ERROR;
import static us.bringardner.net.framework.IGenericResponseCode.REPLY_500_GENERIC_ERROR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.core.BaseObject;
import us.bringardner.net.framework.IGenericResponseCode;
import us.bringardner.net.framework.client.CommandClient;
import us.bringardner.net.framework.client.ICommandClient;
import us.bringardner.net.framework.client.ICommandResponse;
import us.bringardner.net.framework.client.ICommandResponseFactory;
import us.bringardner.net.framework.client.SingleCommandResponse;
import us.bringardner.net.smtp.SMTP;

public class SmtpClient extends CommandClient implements SMTP {

	class MultilineResponseReader extends BaseObject implements ICommandResponse {

		int responseCode = -1;
		String responseText = "";
		String [] responseLines=new String [0];
		
		/*
		 * The reply text may be longer than a single line; in these cases
	      the complete text must be marked so the sender-SMTP knows when it
	      can stop reading the reply.  This requires a special format to
	      indicate a multiple line reply.

	         The format for multiline replies requires that every line,
	         except the last, begin with the reply code, followed
	         immediately by a hyphen, "-" (also known as minus), followed by
	         text.  The last line will begin with the reply code, followed
	         immediately by <SP>, optionally some text, and <CRLF>.

	            For example:
	                                123-First line
	                                123-Second line
	                                123-234 text beginning with numbers
	                                123 The last line

	         In many cases the sender-SMTP then simply needs to search for
	         the reply code followed by <SP> at the beginning of a line, and
	         ignore all preceding lines.  In a few cases, there is important
	         data for the sender in the reply "text".  The sender will know
	         these cases from the current context.
		 */
		@Override
		public void readResonse(ICommandClient client) throws IOException {
			//  Set this in case some error occurs.
			responseCode = IGenericResponseCode.REPLY_500_GENERIC_ERROR;
			StringBuilder buf = new StringBuilder();
			List<String> lines = new ArrayList<String>();
			String line = "";
			while((line=client.readLine())!=null && line.indexOf('-') ==4) {
				buf.append(line);
				buf.append("\r\n");
				lines.add(line.substring(4));
			}
			if( line == null ) {
				logError("Last response line is null");
				buf.append("500 response is null");
			} else {
				String tmp = line.substring(0,3);
				try {
					responseCode = Integer.parseInt(tmp);
				} catch (Exception e) {
					buf.append("can't parse response code="+e);
				}
			}
			
			responseText = buf.toString();
		}

		@Override
		public int translateResponseCode(String code) {
			//  just parse the int
			int ret = Integer.parseInt(code);
			return ret;
		}
		
		@Override
		public String toString() {
			return "Code='"+getResponseCode()+"' text='"+getResponseText()+"'";
		}

		
		@Override
		public int getResponseCode() {
			return responseCode;
		}

		@Override
		public String getResponseText() {
			return responseText;
		}

		@Override
		public String[] getFullResponse() {
			return responseLines;
		}

		public boolean isError() {
			return getResponseCode() >= REPLY_500_GENERIC_ERROR ;
		}

		public boolean isPositive() {
			return getResponseCode() >= REPLY_200_GENERIC_OK && getResponseCode() < REPLY_300_GENERIC_TEMPOARY_OK; 
		}

		public boolean isPositiveIntermediate() {
			return getResponseCode() >= REPLY_300_GENERIC_TEMPOARY_OK && getResponseCode() < REPLY_400_GENERIC_TEMPOARY_ERROR;
		}

		public boolean isPositivePreliminary() {
			return getResponseCode() >= REPLY_100_GENERIC_POSITIVE_PRELIMINARY && getResponseCode() < REPLY_200_GENERIC_OK;
		}

		public boolean isTemporaryError() {
			return getResponseCode() >= REPLY_400_GENERIC_TEMPOARY_ERROR && getResponseCode() < REPLY_500_GENERIC_ERROR;
		}
		
	}
	
	private volatile ICommandResponseFactory responseFactory;

	public SmtpClient(String string, int port) {
		super(string, port);
	}


	
	@Override
	public ICommandResponseFactory getCommandResponseFactory() {
		if(responseFactory==null) {
			synchronized (this) {
				if( responseFactory==null) {
					responseFactory = new ICommandResponseFactory() {

						public ICommandResponse getCommandResponse() {	
							return new SingleCommandResponse() {
								/**
								 * 
								  The reply text may be longer than a single line; in these cases
							      the complete text must be marked so the sender-SMTP knows when it
							      can stop reading the reply.  This requires a special format to
							      indicate a multiple line reply.
						
							         The format for multiline replies requires that every line,
							         except the last, begin with the reply code, followed
							         immediately by a hyphen, "-" (also known as minus), followed by
							         text.  The last line will begin with the reply code, followed
							         immediately by <SP>, optionally some text, and <CRLF>.
						
							            For example:
							                                123-First line
							                                123-Second line
							                                123-234 text beginning with numbers
							                                123 The last line
						
							         In many cases the sender-SMTP then simply needs to search for
							         the reply code followed by <SP> at the beginning of a line, and
							         ignore all preceding lines.  In a few cases, there is important
							         data for the sender in the reply "text".  The sender will know
							         these cases from the current context.		 
								 */
								@Override
								public void readResonse(ICommandClient client) throws IOException {
								//  Set this in case some error occurs.
									code = IGenericResponseCode.REPLY_500_GENERIC_ERROR;
									StringBuilder buf = new StringBuilder();
									List<String> lines = new ArrayList<String>();
									String line = "";
									while((line=client.readLine())!=null && line.indexOf('-') ==3) {
										buf.append(line.substring(4));
										buf.append("\n");
										lines.add(line);
									}
									if( line == null ) {
										logError("Last response line is null");
										buf.append("500 response is null");
									} else {
										lines.add(line);

										String tmp = line.substring(0,3);
										try {
											code = Integer.parseInt(tmp);
										} catch (Exception e) {
											buf.append("can't parse response code="+e);
										}
									}
									fullResponse = lines;
									text = buf.toString();
								}
							};
						}	
					};
				}
			}
		}
		return responseFactory;
	}
	
}
