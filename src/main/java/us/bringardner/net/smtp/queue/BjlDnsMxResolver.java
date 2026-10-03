package us.bringardner.net.smtp.queue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import us.bringardner.net.dns.A;
import us.bringardner.net.dns.AAAA;
import us.bringardner.net.dns.DNS;
import us.bringardner.net.dns.Message;
import us.bringardner.net.dns.Mx;
import us.bringardner.net.dns.RR;
import us.bringardner.net.dns.resolve.Resolver;

/**
 * MX lookup with BjlDns (RFC 5321 section 5.1), as an alternative to the JDK's
 * DNS provider used by {@link DnsMxResolver}. Every DNS request goes through
 * BjlDns: the MX query and the A/AAAA queries for the mail hosts, so the routes
 * carry addresses and the SMTP client doesn't look anything up itself.
 * <p>
 * Two modes:
 * <ul>
 * <li><b>stub</b> (default): ask recursive DNS servers ({@code /etc/resolv.conf}
 * or a list you give), trying each in turn;</li>
 * <li><b>iterative</b>: BjlDns's own {@link Resolver}, which starts at the root
 * servers in its {@code sbelt.prop} and caches answers.</li>
 * </ul>
 * Rules: MX records by preference (random among equals); the domain's own
 * address when there is no MX ("implicit MX"); RFC 7505 null MX ("0 .") and
 * NXDOMAIN are permanent failures; timeouts and server failures are temporary.
 */
public class BjlDnsMxResolver implements MxResolver {

	public enum Mode {
		STUB, ITERATIVE
	}

	private static boolean resolverStarted;

	private final Mode mode;
	private final List<InetAddress> servers;
	private int dnsPort = 53;
	private int timeout = 3000;
	private int retries = 2;
	private int maxAddressesPerHost = 2;
	private final Random random = new Random();

	/** Stub mode with the system's DNS servers (the nameserver lines of /etc/resolv.conf). */
	public BjlDnsMxResolver() throws IOException {
		this(systemServers());
	}

	/** Stub mode with the given recursive DNS servers, tried in order. */
	public BjlDnsMxResolver(List<InetAddress> servers) {
		if (servers.isEmpty()) {
			throw new IllegalArgumentException("No DNS servers");
		}
		this.mode = Mode.STUB;
		this.servers = new ArrayList<>(servers);
	}

	private BjlDnsMxResolver(Mode mode) {
		this.mode = mode;
		this.servers = List.of();
	}

	/**
	 * Iterative mode: BjlDns's own resolver (started once per process with
	 * {@link Resolver#initResolver()}, which reads its root servers from
	 * {@code sbelt.prop} in the BjlDns directory).
	 */
	public static synchronized BjlDnsMxResolver iterative() throws IOException {
		if (!resolverStarted) {
			Resolver.initResolver();
			resolverStarted = true;
		}
		return new BjlDnsMxResolver(Mode.ITERATIVE);
	}

	/** Servers from a comma-separated list such as "192.168.1.1,1.1.1.1". */
	public static List<InetAddress> parseServers(String list) throws UnknownHostException {
		List<InetAddress> ret = new ArrayList<>();
		for (String s : list.split(",")) {
			if (!s.trim().isEmpty()) {
				ret.add(InetAddress.getByName(s.trim()));
			}
		}
		return ret;
	}

	/** The nameserver lines of /etc/resolv.conf (macOS and Linux). */
	public static List<InetAddress> systemServers() throws IOException {
		List<InetAddress> ret = new ArrayList<>();
		File f = new File("/etc/resolv.conf");
		if (f.isFile()) {
			try (BufferedReader r = new BufferedReader(new FileReader(f))) {
				String line;
				while ((line = r.readLine()) != null) {
					String[] p = line.trim().split("\\s+");
					if (p.length >= 2 && p[0].equals("nameserver")) {
						String addr = p[1];
						int pct = addr.indexOf('%'); // IPv6 zone
						ret.add(InetAddress.getByName(pct > 0 ? addr.substring(0, pct) : addr));
					}
				}
			}
		}
		if (ret.isEmpty()) {
			throw new IOException("No DNS servers in /etc/resolv.conf; give them to BjlDnsMxResolver (JSmtp.dnsServers)");
		}
		return ret;
	}

	public Mode getMode() {
		return mode;
	}

	public List<InetAddress> getServers() {
		return Collections.unmodifiableList(servers);
	}

	/** The port of the DNS servers (53; another one for tests). */
	public void setDnsPort(int dnsPort) {
		this.dnsPort = dnsPort;
	}

	/** Milliseconds to wait for each answer (stub mode). */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/** Tries per server (stub mode). */
	public void setRetries(int retries) {
		this.retries = Math.max(1, retries);
	}

	/** How many addresses of each mail host to try. */
	public void setMaxAddressesPerHost(int max) {
		this.maxAddressesPerHost = Math.max(1, max);
	}

	// ------------------------------------------------------------------ MxResolver

	@Override
	public List<Route> resolve(String domain, int port) throws DeliveryException {
		if (domain.startsWith("[") && domain.endsWith("]")) {
			String literal = domain.substring(1, domain.length() - 1);
			if (literal.regionMatches(true, 0, "IPv6:", 0, 5)) {
				literal = literal.substring(5);
			}
			try {
				return List.of(new Route(literal, InetAddress.getByName(literal), port));
			} catch (UnknownHostException e) {
				throw DeliveryException.permanent("5.1.2", "Invalid address literal " + domain);
			}
		}
		Message answer = query(domain, DNS.MX);
		if (answer.getResponseCode() == DNS.NAME_ERROR) {
			throw DeliveryException.permanent("5.1.2", "The domain " + domain + " doesn't exist");
		}
		List<Mx> mx = new ArrayList<>();
		for (RR rr : answer.getAnswer()) {
			if (rr instanceof Mx) {
				mx.add((Mx) rr);
			}
		}
		if (mx.isEmpty()) {
			// implicit MX: the domain's own address (RFC 5321 section 5.1)
			List<InetAddress> addrs = addresses(domain, null);
			if (addrs.isEmpty()) {
				throw DeliveryException.permanent("5.1.2", "The domain " + domain + " has no MX or address record");
			}
			List<Route> ret = new ArrayList<>();
			for (InetAddress a : addrs) {
				ret.add(new Route(domain, a, port));
			}
			return ret;
		}
		if (mx.size() == 1 && host(mx.get(0)).isEmpty()) {
			throw DeliveryException.permanent("5.1.10", "The domain " + domain + " accepts no mail (null MX)");
		}
		Collections.shuffle(mx, random);
		mx.sort((x, y) -> Integer.compare(x.getPref(), y.getPref()));
		List<Route> ret = new ArrayList<>();
		DeliveryException lookupFailure = null;
		for (Mx m : mx) {
			String host = host(m);
			if (host.isEmpty()) {
				continue;
			}
			try {
				for (InetAddress a : addresses(host, answer)) {
					ret.add(new Route(host, a, port));
				}
			} catch (DeliveryException e) {
				lookupFailure = e;
			}
		}
		if (ret.isEmpty()) {
			// the MX hosts don't resolve (yet): try again later
			throw lookupFailure != null ? lookupFailure
					: DeliveryException.temporary("4.4.3", "No address for the mail servers of " + domain);
		}
		return ret;
	}

	private static String host(Mx m) {
		String h = m.getExchange();
		if (h == null) {
			return "";
		}
		h = h.trim();
		while (h.endsWith(".")) {
			h = h.substring(0, h.length() - 1);
		}
		return h.toLowerCase(Locale.ROOT);
	}

	/**
	 * The addresses of a host: from the additional section of {@code answer} if
	 * it has them (the server sends them along with MX records), else A and AAAA
	 * queries. IPv4 first.
	 */
	List<InetAddress> addresses(String host, Message answer) throws DeliveryException {
		List<InetAddress> v4 = new ArrayList<>();
		List<InetAddress> v6 = new ArrayList<>();
		if (answer != null) {
			for (RR rr : answer.getAdditional()) {
				if (rr.getName() != null && trim(rr.getName()).equalsIgnoreCase(host)) {
					add(rr, v4, v6);
				}
			}
		}
		if (v4.isEmpty() && v6.isEmpty()) {
			Message a = query(host, DNS.A);
			for (RR rr : a.getAnswer()) {
				add(rr, v4, v6);
			}
			if (a.getResponseCode() != DNS.NAME_ERROR) {
				Message aaaa = query(host, DNS.AAAA);
				for (RR rr : aaaa.getAnswer()) {
					add(rr, v4, v6);
				}
			}
		}
		List<InetAddress> ret = new ArrayList<>();
		for (int i = 0; i < v4.size() && i < maxAddressesPerHost; i++) {
			ret.add(v4.get(i));
		}
		for (int i = 0; i < v6.size() && ret.size() < maxAddressesPerHost * 2 && i < maxAddressesPerHost; i++) {
			ret.add(v6.get(i));
		}
		return ret;
	}

	private static String trim(String name) {
		return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
	}

	private static void add(RR rr, List<InetAddress> v4, List<InetAddress> v6) {
		try {
			if (rr instanceof A) {
				v4.add(InetAddress.getByAddress(((A) rr).getAddress()));
			} else if (rr instanceof AAAA) {
				v6.add(InetAddress.getByAddress(((AAAA) rr).getAddress()));
			}
		} catch (UnknownHostException e) {
			// a malformed address: skip it
		}
	}

	/**
	 * One question. Returns a NOERROR or NXDOMAIN answer; a timeout, SERVFAIL or
	 * REFUSED from every server is a temporary failure.
	 */
	Message query(String name, int type) throws DeliveryException {
		if (mode == Mode.ITERATIVE) {
			Message m = Resolver.resolve(name, type, DNS.IN);
			if (m == null) {
				throw DeliveryException.temporary("4.4.3", "DNS lookup of " + name + " failed");
			}
			return m;
		}
		String last = "no answer";
		for (InetAddress server : servers) {
			try {
				Message q = new Message();
				q.setServer(server);
				q.setPort(dnsPort);
				q.setQuestion(name, type, DNS.IN);
				q.recursiveDesiredOn();
				q.setTimeOut(timeout);
				q.setRetry(retries);
				q.setTcpFallback(true);
				Message r = q.query();
				if (r == null) {
					continue;
				}
				int rcode = r.getResponseCode();
				if (rcode == 0 || rcode == DNS.NAME_ERROR) {
					return r;
				}
				last = server.getHostAddress() + " returned rcode " + rcode;
			} catch (IOException | RuntimeException e) {
				last = server.getHostAddress() + ": " + e.getMessage();
			}
		}
		throw DeliveryException.temporary("4.4.3", "DNS lookup of " + name + " failed (" + last + ")");
	}
}
