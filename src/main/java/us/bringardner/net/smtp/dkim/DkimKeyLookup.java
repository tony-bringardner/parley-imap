package us.bringardner.net.smtp.dkim;

import us.bringardner.net.dns.resolve.LookupResult;

/**
 * Finds the TXT records of a DKIM key name (selector._domainkey.domain).
 * <p>
 * The SMTP server uses BjlDns ({@code BjlDnsMxResolver::txt}, or
 * {@code us.bringardner.net.dns.resolve.Lookup::txt}); tests use a map.
 */
@FunctionalInterface
public interface DkimKeyLookup {

	/** The TXT records of a name, each one's strings joined. */
	LookupResult<String> txt(String name);
}
