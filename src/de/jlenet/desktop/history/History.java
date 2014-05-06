package de.jlenet.desktop.history;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;

public class History {
	ArrayList<HistoryBlock> years = new ArrayList<HistoryBlock>();
	File base;
	public History(File file) {
		base = file;
		load();
	}
	public void addMessage(HistoryMessage hm) {
		((HistoryLeafNode) getAnyBlock(hm.getTime(), LEVELS)).add(hm);
		modified(hm.getTime());

	}
	public static final int BITS_PER_LEVEL = 4;
	public static final int LEVELS = 4;
	public static final int BASE = 1000 * 60 * 60;
	public static final int CHILDREN_PER_LEVEL = 1 << BITS_PER_LEVEL;

	public static final int MAX_FILES = 16;

	public static final byte[][] DUMMY_CHECKSUMS;

	public static final boolean DEBUG_STORE = false;

	static {
		byte[][] chks = new byte[LEVELS][];
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA1");
			chks[LEVELS - 1] = md.digest();
			md.reset();
			for (int i = LEVELS - 1; i > 0; i--) {
				for (int j = 0; j < 1 << BITS_PER_LEVEL; j++) {
					md.update(chks[i]);
				}
				chks[i - 1] = md.digest();
				md.reset();
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		DUMMY_CHECKSUMS = chks;
	}

	public HistoryBlock getAnyBlock(long time, int level) {
		time /= BASE;
		int count = getMyCount(time);
		HistoryBlock hb = getRootBlock(count);
		time = recurseTime(time);
		for (int i = 0; i < LEVELS && i < level; i++) {
			hb = ((HistoryTreeBlock) hb).getBlock(getMyCount(time));
			time = recurseTime(time);
		}
		return hb;
	}
	public static long recurseTime(long time) {
		time = (time & ((1L << BITS_PER_LEVEL * LEVELS) - 1)) << BITS_PER_LEVEL;
		return time;
	}
	public static int getMyCount(long time) {
		return (int) (time >> (BITS_PER_LEVEL * LEVELS));
	}
	public void modified(long time) {
		time /= BASE;
		int count = getMyCount(time);
		HistoryBlock hb = getRootBlock(count);
		hb.modified(recurseTime(time));
	}
	public HistoryBlock getRootBlock(int count) {
		ensureSize(count);
		HistoryBlock hb = years.get(count);
		if (hb == null) {
			hb = new HistoryTreeBlock(0);
			years.set(count, hb);
		}
		return hb;
	}

	private void ensureSize(int count) {
		years.ensureCapacity(count);
		while (years.size() <= count) {
			years.add(null);
		}
	}
	public static final long TEST_BASE = (1398959418266L / BASE) * BASE;
	public static void main(String[] args)
			throws TransformerConfigurationException, SAXException,
			TransformerFactoryConfigurationError, IOException {
		// 1398959418266 = 1.5.2014, 17:50+n
		// loadBlock(input);
		History hi = new History(new File("history"));
		HistoryMessage hm = new HistoryMessage(
				"<message id=\"cy340-160\" to=\"felix@dogcraft.de/JLENetDesktop\" from=\"felix@dogcraft.de/Spark 2.6.3\" type=\"chat\"><body>message</body></message>",
				TEST_BASE);
		HistoryMessage hm2 = new HistoryMessage(
				"<message id=\"cy340-161\" to=\"felix@dogcraft.de/JLENetDesktop\" from=\"felix@dogcraft.de/Spark 2.6.3\" type=\"chat\"><body>message2</body></message>",
				TEST_BASE + 2);
		HistoryMessage hm3 = new HistoryMessage(
				"<message id=\"cy340-162\" to=\"felix@dogcraft.de/JLENetDesktop\" from=\"felix@dogcraft.de/Spark 2.6.3\" type=\"chat\"><body>message3</body></message>",
				TEST_BASE + 3);
		hi.addMessage(hm);
		hi.addMessage(hm2);

		hi.store();
		hi = new History(new File("history1"));
		hi.addMessage(hm);
		hi.addMessage(hm3);
		hi.store();
	}
	public void store() {
		for (int i = 0; i < years.size(); i++) {
			if (years.get(i) != null) {
				int files = reconcile(years.get(i), Integer.toString(i),
						MAX_FILES);
				if (DEBUG_STORE) {
					System.out.println("In " + files + " files");
				}
			}
		}
	}
	private int reconcile(HistoryBlock historyBlock, String prefix, int maxfiles) {
		if (!historyBlock.modified) {
			return historyBlock.filesCount;
		}
		try {

			if (historyBlock instanceof HistoryTreeBlock) {
				HistoryTreeBlock hb = ((HistoryTreeBlock) historyBlock);
				int count = hb.ownFile ? 1 : 0;
				for (int i = 0; i < CHILDREN_PER_LEVEL; i++) {
					HistoryBlock child = hb.getBlock(i);
					count += reconcile(child, prefix + "_" + i, maxfiles);
				}
				if (count >= maxfiles) {
					compact(prefix, hb);
					count = 1;
				}
				hb.modified = false;
				return hb.filesCount = count;
			}
			if (DEBUG_STORE) {
				System.out.println("storing: " + prefix + ";" + base);
			}
			historyBlock.modified = false;
			export(historyBlock, new FileOutputStream(new File(base, prefix
					+ ".xml")));
			return historyBlock.filesCount = 1;
		} catch (Exception e) {
			throw new Error(e);
		}
	}
	public void compact(String prefix, HistoryTreeBlock hb)
			throws TransformerFactoryConfigurationError,
			TransformerConfigurationException, SAXException, IOException {
		File file = new File(base, prefix + ".xml.new");
		export(hb, new FileOutputStream(file));
		File ready = new File(prefix + ".xml.ready");
		file.renameTo(ready);
		file = ready;
		if (DEBUG_STORE) {
			System.out.println("compressing: " + prefix + ";" + base);
		}
		hb.filesCount = 1;
		clean(hb, prefix);
		hb.ownFile = true;
		file.renameTo(new File(base, prefix + ".xml"));
		hb.filesCount = 1;
	}
	private void clean(HistoryBlock historyBlock, String prefix) {
		if (historyBlock.filesCount == 0) {
			return;
		}
		if (DEBUG_STORE) {
			System.out.println("cleaning: " + prefix);
		}
		if (historyBlock instanceof HistoryTreeBlock) {
			HistoryTreeBlock ht = (HistoryTreeBlock) historyBlock;
			for (int i = 0; i < CHILDREN_PER_LEVEL; i++) {
				clean(ht.getBlock(i), prefix + "_" + i);
			}
		}
		new File(base, prefix + ".xml").delete();
		historyBlock.filesCount = 0;
		historyBlock.ownFile = false;

	}
	private static HistoryBlock loadBlock(Reader input, int level)
			throws IOException {

		try {
			XmlPullParser xpp = new MXParser();
			xpp.setInput(input);

			xpp.nextTag();
			HistoryBlock htb = HistoryBlock.parse(level, xpp);
			if (!xpp.getName().equals("history")) {
				throw new IOException(xpp.getName());
			}
			if (xpp.next() != XmlPullParser.END_DOCUMENT) {
				throw new IOException();
			}
			return htb;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
	public static void export(HistoryBlock hb, OutputStream os)
			throws TransformerFactoryConfigurationError,
			TransformerConfigurationException, SAXException, IOException {
		OutputStreamWriter wr = new OutputStreamWriter(os);
		TransformerHandler hd = createSax(wr);

		hd.startDocument();
		AttributesImpl atti = new AttributesImpl();
		hd.startElement("", "", "history", atti);
		hb.serialize(hd, atti);
		hd.endElement("", "", "history");
		hd.endDocument();
		wr.flush();
		wr.close();
	}
	public static TransformerHandler createSax(Writer wr)
			throws TransformerFactoryConfigurationError,
			TransformerConfigurationException {
		StreamResult streamResult = new StreamResult(wr);

		SAXTransformerFactory tf = (SAXTransformerFactory) SAXTransformerFactory
				.newInstance();
		TransformerHandler hd = tf.newTransformerHandler();
		Transformer serializer = hd.getTransformer();
		serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		serializer.setOutputProperty(OutputKeys.INDENT, "yes");
		hd.setResult(streamResult);
		return hd;
	}
	public static String beautifyChecksum(byte[] data) {
		StringBuffer sb = new StringBuffer(data.length * 2);
		for (int i = 0; i < data.length; i++) {
			sb.append(Integer.toHexString((data[i] >> 4) & 0xF));
			sb.append(Integer.toHexString(data[i] & 0xF));
		}
		return sb.toString();
	}
	public static IQ getResponse(Connection conn, IQ packet) {
		PacketCollector collector = conn.createPacketCollector(new AndFilter(
				new PacketIDFilter(packet.getPacketID()), new IQTypeFilter(
						IQ.Type.RESULT)));

		conn.sendPacket(packet);

		// Wait up to 5 seconds for a result.
		IQ result = (IQ) collector.nextResult(SmackConfiguration
				.getPacketReplyTimeout());
		// Stop queuing results
		collector.cancel();
		return result;
	}
	private void load() {
		for (File f : base.listFiles()) {
			if (f.getName().endsWith(".xml.new")) {
				f.delete();
			}
			if (f.getName().endsWith(".xml.ready")) {
				String prefix = f.getName();
				prefix = prefix.substring(0, prefix.length() - 10);
				for (File f2 : base.listFiles()) {
					if (f2.getName().startsWith(prefix)
							&& !f2.getName().endsWith(".ready")) {
						f2.delete();
					}
				}
				f.renameTo(new File(base, prefix + ".xml"));
			}
		}

		File[] files = base.listFiles();
		Arrays.sort(files, new Comparator<File>() {

			@Override
			public int compare(File o1, File o2) {
				String s1 = o1.getName();
				String s2 = o2.getName();
				return s1.compareTo(s2);
			}
		});
		for (File f : files) {
			if (f.getName().endsWith(".xml")) {
				String prefix = f.getName().substring(0,
						f.getName().length() - 4);

				try {
					FileReader input = new FileReader(f);
					String[] parts = prefix.split("_");
					HistoryBlock block2 = loadBlock(input, parts.length - 1);
					if (block2 != null) {
						block2.ownFile = true;
						block2.filesCount = 1;
					}
					input.close();
					if (parts.length == 1) {
						int block = Integer.parseInt(prefix);
						ensureSize(block);
						years.set(block, block2);
					} else {
						HistoryBlock htb = getRootBlock(Integer
								.parseInt(parts[0]));
						htb.filesCount++;
						for (int i = 1; i < parts.length - 1; i++) {
							htb = ((HistoryTreeBlock) htb).getBlock(Integer
									.parseInt(parts[i]));
							htb.filesCount++;
						}
						((HistoryTreeBlock) htb).setBlock(
								Integer.parseInt(parts[parts.length - 1]),
								block2);
					}
				} catch (IOException e) {
					throw new Error("With file " + f, e);
				}
			}
		}
	}
	public static int getHoursPerBlock(int i) {
		if (i > LEVELS || i < 0) {
			return 0;
		}

		return 1 << (BITS_PER_LEVEL * (LEVELS - i));
	}
}
