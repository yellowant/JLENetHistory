package de.jlenet.desktop.history;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Set;

import javax.xml.transform.TransformerException;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import de.jlenet.desktop.history.payloads.CompletedFileTransfer;
public class TestStoreAndLoad {
	public static final long TEST_BASE = (1398959418266L / History.BASE)
			* History.BASE;

	File base = new File("testHistory");
	@Before
	public void clean() {
		if (base.exists()) {
			rm(base);
		}
		base.mkdirs();
	}
	private void rm(File target) {
		for (File f : target.listFiles()) {
			if (f.isDirectory()) {
				rm(f);
			}
			f.delete();
		}

	}
	@Test
	public void testStoreAndLoad() {
		History h = new History(base);
		h.addMessage(new HistoryEntry("<message>test</message>", TEST_BASE,
				"romeo@montagues.lit", false));
		h.store();
		System.out.println("Editing");
		for (int i = 0; i < 32; i++) {
			h.addMessage(new HistoryEntry("<message>test</message>", TEST_BASE
					+ 60 * 60 * 1000 * i, "romeo@montagues.lit", false));
		}
		h.store();
		h = new History(base);
	}
	@Test
	public void testStoreAndLoad3() throws SAXException, IOException,
			TransformerException {
		History h = new History(base);
		for (int i = 0; i < 512; i++) {
			h.addMessage(new HistoryEntry("<message>test" + i + "</message>",
					TEST_BASE + 60 * 60 * 1000L * i
							* History.CHILDREN_PER_LEVEL
							* History.CHILDREN_PER_LEVEL + 10,
					"romeo@montagues.lit", false));
			h.store();
			int sum = 0;
			for (int j = 5; j < h.getLastCount(); j++) {
				sum += h.getRootBlock(j).filesCount;
			}
			assertEquals(base.listFiles().length, sum);
		}

		History h2 = new History(base);
		File f = new File(base, "6.xml");
		h2.compact("6", (HistoryTreeBlock) h2.getRootBlock(6));
		byte[] oldData = readFile(f);
		String s = new String(oldData);
		oldData = (s.replace("> <block id", "><block id")).replaceAll(
				">[\r\n]+<", "><").getBytes();
		h2.compact("6", (HistoryTreeBlock) h2.getRootBlock(6));
		HistoryTreeBlock htb = (HistoryTreeBlock) h2.getRootBlock(7);
		htb.getBlock(0).getChecksum();
		byte[] newData = readFile(f);
		s = new String(newData);
		newData = (s.replace("> <block id", "><block id").replaceAll(
				">[\r\n]+<", "><")).getBytes();
		assertArrayEquals(oldData, newData);
	}
	private byte[] readFile(File f) throws IOException {
		byte[] buf = new byte[(int) f.length()];
		RandomAccessFile raf = new RandomAccessFile(f, "r");
		raf.readFully(buf);
		raf.close();
		return buf;
	}
	@Test
	public void testStoreAndLoad2() {
		History h = new History(base);
		for (int i = 0; i < 2048; i++) {
			h.addMessage(new HistoryEntry("<message>test" + i + "</message>",
					TEST_BASE + 60 * 60 * 1000L * i + 10,
					"romeo@montagues.lit", true));
			h.store();
			assertEquals(base.listFiles().length, h.getRootBlock(5).filesCount);
		}
		for (int i = 0; i < 2048; i++) {
			assertEquals(
					i + 1,
					h.getMessages("romeo@montagues.lit", TEST_BASE,
							TEST_BASE + 60 * 60 * 1000L * i + 10).size());
		}
		History h2 = new History(base);
		for (int i = 0; i < 2048; i++) {
			assertEquals(
					i + 1,
					h.getMessages("romeo@montagues.lit", TEST_BASE,
							TEST_BASE + 60 * 60 * 1000L * i + 10).size());
		}

		assertEquals(base.listFiles().length, h2.getRootBlock(5).filesCount);
		try {
			h2.compact("5", (HistoryTreeBlock) h2.getRootBlock(5));
		} catch (TransformerException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		assertEquals(1, h2.getRootBlock(5).filesCount);
		assertEquals(1, base.listFiles().length);
		h2 = new History(base);
		for (int i = 0; i < 2048; i++) {
			assertEquals(
					i + 1,
					h.getMessages("romeo@montagues.lit", TEST_BASE,
							TEST_BASE + 60 * 60 * 1000L * i + 10).size());
		}

	}
	@Test
	public void testUncleanRec() {
		write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><history/>", new File(
				base, "5_14_13_15.xml.ready"));
		write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><history/>", new File(
				base, "5_14_13_15_1.xml"));
		write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><history/>", new File(
				base, "5_14_13_15_3.xml"));
		new History(base);
		assertArrayEquals(new File[]{new File(base, "5_14_13_15.xml")},
				base.listFiles());
	}

	@Test
	public void testUncleanRec2() {
		write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><history/>", new File(
				base, "5_14_13_15.xml.new"));
		write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><history/>", new File(
				base, "5_14_13_15_1.xml"));
		write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><history/>", new File(
				base, "5_14_13_15_3.xml"));
		assertEquals(
				0,
				new History(base).getMessages("romeo@montagues.lit", 0,
						TEST_BASE + History.BASE * 1000L).size());
		assertArrayEquals(new File[]{new File(base, "5_14_13_15_1.xml"),
				new File(base, "5_14_13_15_3.xml")}, base.listFiles());
	}
	@Test
	public void testJidGet() {
		History h = new History(base);
		h.addMessage(new HistoryEntry("romeoMSG", History.BASE,
				"romeo@montagues.lit", false));
		h.addMessage(new HistoryEntry("julietMSG", History.BASE,
				"juliet@capulets.lit", true));
		h.addMessage(new HistoryEntry("julietMSG1", History.BASE,
				"juliet@capulets.lit", true));
		h.store();
		assertEquals(
				1,
				new History(base).getMessages("romeo@montagues.lit", 0,
						TEST_BASE + History.BASE * 1000L).size());
		assertEquals(
				2,
				new History(base).getMessages("juliet@capulets.lit", 0,
						TEST_BASE + History.BASE * 1000L).size());
		assertEquals(
				0,
				new History(base).getMessages("mercutio@montagues.lit", 0,
						TEST_BASE + History.BASE * 1000L).size());
	}

	@Test
	public void testMixedType() {
		History h = new History(base);
		HistoryEntry ft = new HistoryEntry(new CompletedFileTransfer(
				"test.xml", true, 1024 * 16, "C:\\test.xml", "JLENet",
				new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0}), TEST_BASE, "romeo@montagues.lit", true);
		h.addMessage(ft);
		HistoryEntry msg = new HistoryEntry("romeoMSG", TEST_BASE + 1,
				"romeo@montagues.lit", false);
		h.addMessage(msg);
		h.store();
		History h2 = new History(base);
		Set<HistoryEntry> msgs = h2.getMessages("romeo@montagues.lit",
				TEST_BASE - 10, TEST_BASE + 10);
		HistoryEntry[] he = msgs.toArray(new HistoryEntry[2]);
		assertEquals(2, he.length);
		Arrays.sort(he);
		assertEquals(ft, he[0]);
		assertEquals(msg, he[1]);
		assertEquals(1024 * 16,
				((CompletedFileTransfer) he[0].getPayload()).getFilesize());

	}
	private void write(String string, File file) {
		FileWriter fw;
		try {
			fw = new FileWriter(file);
			fw.write(string);
			fw.close();
		} catch (IOException e) {
			throw new Error(e);
		}

	}
}
