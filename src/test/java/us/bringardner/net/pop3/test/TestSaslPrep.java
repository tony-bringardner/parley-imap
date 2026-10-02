package us.bringardner.net.pop3.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import us.bringardner.net.email.SaslPrep;

/** The examples from RFC 4013 section 3, and the other rules. */
public class TestSaslPrep {

	@Test
	public void testRfc4013Examples() {
		assertEquals("IX", SaslPrep.prepare("I\u00ADX", false), "SOFT HYPHEN mapped to nothing");
		assertEquals("user", SaslPrep.prepare("user", false), "no transformation");
		assertEquals("USER", SaslPrep.prepare("USER", false), "case preserved");
		assertEquals("a", SaslPrep.prepare("\u00AA", false), "output is NFKC");
		assertEquals("IX", SaslPrep.prepare("\u2168", false), "output is NFKC");
		assertNull(SaslPrep.prepare("\u0007", false), "prohibited character");
		assertNull(SaslPrep.prepare("\u06271", false), "bidirectional check");
	}

	@Test
	public void testOtherRules() {
		assertEquals("p\u00E4ssw\u00F6rd", SaslPrep.prepare("pa\u0308sswo\u0308rd", false), "decomposed input is composed");
		assertEquals("a b", SaslPrep.prepare("a\u00A0b", false), "non-ASCII space becomes SPACE");
		assertEquals("ab", SaslPrep.prepare("a\uFE0Fb", false), "variation selector mapped to nothing");
		assertNull(SaslPrep.prepare("a\uFFFDb", false), "replacement character (invalid UTF-8)");
		assertNull(SaslPrep.prepare("a\uE000", false), "private use");
		assertNull(SaslPrep.prepare("a\u202Eb", false), "direction override");
		assertEquals("\u06271\u0628", SaslPrep.prepare("\u06271\u0628", false), "RandAL first and last");
		assertNull(SaslPrep.prepare("\u0627a\u0628", false), "RandAL mixed with L");
		assertEquals("a\u0378", SaslPrep.prepare("a\u0378", false), "unassigned: allowed in a query");
		assertNull(SaslPrep.prepare("a\u0378", true), "unassigned: not in a stored string");
		assertNull(SaslPrep.prepare(null, false));
	}
}
