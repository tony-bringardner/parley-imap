package us.bringardner.net.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;


public class TestAddress  {

		
	@Test()
	public void testAdress01() throws IOException {
		Address addr = Address.parseAddress("tony@bringardner.us");
		assertEquals("tony", addr.getUser());
		assertEquals("bringardner.us", addr.getDomain());
		assertNull(addr.getDisplayName());

		addr = Address.parseAddress("<tony@bringardner.us>");
		assertEquals("tony", addr.getUser());
		assertEquals("bringardner.us", addr.getDomain());
		assertNull(addr.getDisplayName());

		addr = Address.parseAddress("Tony Bringardner <tony@bringardner.us>");
		assertEquals("tony", addr.getUser());
		assertEquals("bringardner.us", addr.getDomain());
		assertEquals("Tony Bringardner", addr.getDisplayName());

	}
}
