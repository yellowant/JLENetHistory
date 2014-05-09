package de.jlenet.desktop.history;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import static org.junit.Assert.*;
public class TestStoreAndLoad {
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
		h.addMessage(new HistoryMessage("<message>test</message>",
				History.TEST_BASE));
		h.store();
		System.out.println("Editing");
		for (int i = 0; i < 32; i++) {
			h.addMessage(new HistoryMessage("<message>test</message>",
					History.TEST_BASE + 60 * 60 * 1000 * i));
		}
		h.store();
		h = new History(base);
	}
	@Test
	public void testStoreAndLoad3() throws TransformerConfigurationException,
			TransformerFactoryConfigurationError, SAXException, IOException {
		History h = new History(base);
		for (int i = 0; i < 512; i++) {
			h.addMessage(new HistoryMessage("<message>test" + i + "</message>",
					History.TEST_BASE + 60 * 60 * 1000L * i
							* History.CHILDREN_PER_LEVEL
							* History.CHILDREN_PER_LEVEL + 10));
			h.store();
			int sum = 0;
			for (int j = 5; j < h.getLastCount(); j++) {
				sum += h.getRootBlock(j).filesCount;
			}
			assertEquals(base.listFiles().length, sum);
		}

		History h2 = new History(base);
		File f = new File(base, "6.xml");
		byte[] oldData = readFile(f);
		h2.compact("6", (HistoryTreeBlock) h2.getRootBlock(6));
		byte[] newData = readFile(f);
		assertArrayEquals(oldData, newData);
	}
	private byte[] readFile(File f) throws FileNotFoundException, IOException {
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
			h.addMessage(new HistoryMessage("<message>test" + i + "</message>",
					History.TEST_BASE + 60 * 60 * 1000L * i + 10));
			h.store();
			assertEquals(base.listFiles().length, h.getRootBlock(5).filesCount);
		}
		for (int i = 0; i < 2048; i++) {
			assertEquals(
					i + 1,
					h.getMessages(History.TEST_BASE,
							History.TEST_BASE + 60 * 60 * 1000L * i + 10)
							.size());
		}
		History h2 = new History(base);
		for (int i = 0; i < 2048; i++) {
			assertEquals(
					i + 1,
					h.getMessages(History.TEST_BASE,
							History.TEST_BASE + 60 * 60 * 1000L * i + 10)
							.size());
		}

		assertEquals(base.listFiles().length, h2.getRootBlock(5).filesCount);
		try {
			h2.compact("5", (HistoryTreeBlock) h2.getRootBlock(5));
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
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
					h.getMessages(History.TEST_BASE,
							History.TEST_BASE + 60 * 60 * 1000L * i + 10)
							.size());
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
				new History(base).getMessages(0,
						History.TEST_BASE + History.BASE * 1000L).size());
		assertArrayEquals(new File[]{new File(base, "5_14_13_15_1.xml"),
				new File(base, "5_14_13_15_3.xml")}, base.listFiles());
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
