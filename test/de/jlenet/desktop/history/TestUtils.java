package de.jlenet.desktop.history;

import org.junit.Assert;
import org.junit.Test;

public class TestUtils {
	@Test
	public void testChksum() {
		String data = "abf0128ab0a0b0c00010200004";
		Assert.assertEquals(data,
				History.beautifyChecksum(History.parseChecksum(data)));
		String data2 = "abf0128AB0F0b0c00FFF0F00F0200004";
		Assert.assertEquals(data2.toLowerCase(),
				History.beautifyChecksum(History.parseChecksum(data2)));
	}
}
