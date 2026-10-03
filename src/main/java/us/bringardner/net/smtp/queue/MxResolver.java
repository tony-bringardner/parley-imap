package us.bringardner.net.smtp.queue;

import java.util.List;

/** Finds the servers that receive mail for a domain (RFC 5321 section 5.1). */
public interface MxResolver {

	/** A server to try: host name or address, and port. */
	final class Route {
		public final String host;
		public final int port;

		public Route(String host, int port) {
			this.host = host;
			this.port = port;
		}

		@Override
		public String toString() {
			return host + ":" + port;
		}
	}

	/**
	 * The servers for a domain (A-labels, lower case), most preferred first.
	 *
	 * @throws DeliveryException permanent if the domain doesn't exist or accepts no
	 *                           mail (null MX), temporary if DNS failed
	 */
	List<Route> resolve(String domain, int port) throws DeliveryException;
}
