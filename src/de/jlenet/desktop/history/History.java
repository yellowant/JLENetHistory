package de.jlenet.desktop.history;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.IQ;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.v1.XmlPullParser;

/**
 * Implements the message History for a chat Client. It effectively stores
 * {@link HistoryEntry}s in a set of Files.
 * 
 */
public class History {
	private ArrayList<HistoryBlock> years = new ArrayList<HistoryBlock>();
	private File base;

	/**
	 * Creates/loads a History.
	 * 
	 * @param file
	 *            the file to {@link #store()} to. And to load from.
	 */
	public History(File file) {
		base = file;
		load();
	}

	/**
	 * Adds a message to the History.
	 * 
	 * @param hm
	 *            the Message to add. (Remember to {@link #store()} if you want
	 *            the Message to be on the disk.
	 */
	public synchronized void addMessage(HistoryEntry hm) {
		((HistoryLeafNode) getAnyBlock(hm.getTime(), LEVELS)).add(hm);
		modified(hm.getTime());

	}

	/**
	 * How many bits of the timestamp will be used per sync-level.
	 */
	public static final int BITS_PER_LEVEL = 4;
	/**
	 * How many sync levels there will be.
	 */
	public static final int LEVELS = 4;
	/**
	 * What is the smallest consistent block that will be synced (in millis).
	 */
	public static final int BASE = 1000 * 60 * 60;
	/**
	 * How many child blocks are in an intermediate History block.
	 */
	public static final int CHILDREN_PER_LEVEL = 1 << BITS_PER_LEVEL;

	/**
	 * How many files are tried to create on the disk as a maximum.
	 */
	public static final int MAX_FILES = 64;

	/**
	 * Array of checksums for empty blocks. (from root to leaf)
	 */
	public static final byte[][] DUMMY_CHECKSUMS;

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

	/**
	 * Get the history block at the given location.
	 * 
	 * @param time
	 *            the timestamp (millis) for the time where this block is
	 *            located.
	 * @param level
	 *            the level where this block is located in the tree (0 is root)
	 * @return the specified block
	 */
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

	/**
	 * notify the tree of a Modification to be saved in subsequent
	 * {@link #store()}-calls'
	 * 
	 * @param time
	 *            the time at which the modification happened.
	 */
	public void modified(long time) {
		time /= BASE;
		int count = getMyCount(time);
		HistoryBlock hb = getRootBlock(count);
		hb.modified(recurseTime(time));
	}

	/**
	 * Get the root block with the given index.
	 * 
	 * @param count
	 *            the index of the root block to retrive
	 * @return the indexed root block
	 */
	public HistoryBlock getRootBlock(int count) {
		ensureSize(count);
		HistoryBlock hb = years.get(count);
		if (hb == null) {
			hb = new HistoryTreeBlock(0);
			years.set(count, hb);
		}
		return hb;
	}

	/**
	 * get the number of root blocks
	 * 
	 * @return the number of root blocks.
	 */
	protected int getLastCount() {
		return years.size();
	}

	/**
	 * Get all specified Messages.
	 * 
	 * @param bareJid
	 *            the bare JID of the user to retrive messages from
	 * @param from
	 *            the first millisecond from which messages will be retrived
	 * @param to
	 *            the last millisecond to which messages will be retrived
	 * @return the messages
	 */
	public synchronized Set<HistoryEntry> getMessages(String bareJid,
			long from, final long to) {
		HistoryEntry dummyFrom = new HistoryEntry(from);
		HistoryEntry dummyTo = new HistoryEntry(to + 1);

		TreeSet<HistoryEntry> result = new TreeSet<HistoryEntry>();
		int toHour = getMyCount(to / BASE);
		int fromHour = getMyCount(from / BASE);
		for (int i = fromHour; i <= toHour; i++) {
			addMessagesToSet(bareJid, getRootBlock(i), i == fromHour
					? recurseTime(from / BASE)
					: 0, i == toHour ? recurseTime(to / BASE) : -1, result,
					dummyFrom, dummyTo);
		}
		return result;

	}

	/**
	 * Adds all specified messages to the given set.
	 * 
	 * @param bareJid
	 *            the jid to search for
	 * @param block
	 *            the block to search in
	 * @param from
	 *            the first millisecond
	 * @param to
	 *            the last millisecond
	 * @param target
	 *            the set to add it to
	 * @param dummyFrom
	 *            a lower dummy message that will be used for
	 *            {@link Comparable#compareTo(Object)}
	 * @param dummyTo
	 *            a upper dummy message that will be used for
	 *            {@link Comparable#compareTo(Object)}
	 */
	private void addMessagesToSet(String bareJid, HistoryBlock block,
			long from, long to, Set<HistoryEntry> target,
			HistoryEntry dummyFrom, HistoryEntry dummyTo) {
		if (block instanceof HistoryLeafNode) {
			for (HistoryEntry historyMessage : ((HistoryLeafNode) block)
					.getMessages().subSet(dummyFrom, dummyTo)) {
				if (bareJid == null
						|| historyMessage.getCorrespondent().equals(bareJid)) {
					target.add(historyMessage);
				}
			}
			return;
		}
		int myFrom = getMyCount(from);
		long recurse = recurseTime(to);
		if (to == -1) {
			recurse = -1;
		}
		int myTo = to == -1L ? CHILDREN_PER_LEVEL - 1 : getMyCount(to);
		HistoryTreeBlock htb = (HistoryTreeBlock) block;
		for (int i = myFrom; i <= myTo; i++) {
			addMessagesToSet(bareJid, htb.getBlock(i), i == myFrom
					? recurseTime(from)
					: 0, i == myTo ? recurse : -1, target, dummyFrom, dummyTo);

		}

	}

	/**
	 * Increases the size of this history so that {@link #years} will be of at
	 * least <code>count</code> size
	 * 
	 * @param count
	 *            the minimal size
	 */
	private void ensureSize(int count) {
		years.ensureCapacity(count);
		while (years.size() <= count) {
			years.add(null);
		}
	}

	/**
	 * Stores all changes in this history to the underlying file.
	 */
	public synchronized void store() {
		if (Debug.ENABLED) {
			System.out.println("Lazy store begin");
		}
		for (int i = 0; i < years.size(); i++) {
			if (years.get(i) != null) {
				int files = reconcile(years.get(i), Integer.toString(i),
						MAX_FILES);
				if (Debug.ENABLED) {
					System.out.println("In " + files + " files");
				}
			}
		}
		if (Debug.ENABLED) {
			System.out.println("Lazy store end");
		}
	}

	/**
	 * Updates all necessary files for the given block.
	 * 
	 * @param historyBlock
	 *            the block to store
	 * @param prefix
	 *            the filename prefix
	 * @param maxfiles
	 *            the maximum number of files allowed for this block
	 * @return the number of files used by this block now.
	 */
	private int reconcile(HistoryBlock historyBlock, String prefix, int maxfiles) {
		if (!historyBlock.modified) {
			return historyBlock.filesCount;
		}
		try {

			if (historyBlock instanceof HistoryTreeBlock) {
				HistoryTreeBlock hb = ((HistoryTreeBlock) historyBlock);
				int count = hb.ownFile ? 1 : 0;
				for (int i = 0; i < CHILDREN_PER_LEVEL; i++) {
					HistoryBlock child = hb.getBlock(i, true);
					if (child == null) {
						continue;
					}
					count += reconcile(child, prefix + "_" + i, maxfiles / 2);
				}
				if (count >= maxfiles) {
					compact(prefix, hb);
					count = 1;
				}
				hb.modified = false;
				return hb.filesCount = count;
			}
			if (Debug.ENABLED) {
				System.out.println("storing: " + prefix + ";" + base);
			}
			historyBlock.modified = false;
			File newFile = new File(base, prefix + ".xml.new");
			export(historyBlock, newFile);
			File newFileName = new File(base, prefix + ".xml");
			newFileName.delete();
			newFile.renameTo(newFileName);
			return historyBlock.filesCount = 1;
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	/**
	 * Compacts the given block to exactly one file (with the given prefix)
	 * 
	 * @param prefix
	 *            the prefix of the file.
	 * @param hb
	 *            the block to store
	 * @throws TransformerFactoryConfigurationError
	 *             if XML is spooky
	 * @throws TransformerConfigurationException
	 *             if XML is spooky
	 * @throws SAXException
	 *             if XML is spooky
	 * @throws IOException
	 *             if harddrive is nervous
	 * @throws TransformerException
	 */
	protected void compact(String prefix, HistoryTreeBlock hb)
			throws SAXException, IOException, TransformerException {
		if (Debug.ENABLED) {
			System.out.println(Arrays.toString(base.list()));
		}
		File file = new File(base, prefix + ".xml.new");
		export(hb, file);
		File ready = new File(base, prefix + ".xml.ready");
		if (!file.renameTo(ready)) {
			throw new IOException("error renaming file from " + file + " to "
					+ ready);
		}
		file = ready;
		if (Debug.ENABLED) {
			System.out.println("compressing: " + prefix + ";" + base);
		}
		hb.filesCount = 1;
		clean(hb, prefix);
		hb.ownFile = true;
		File target = new File(base, prefix + ".xml");
		if (!file.renameTo(target)) {
			throw new IOException("error renaming file from " + file + " to "
					+ target);
		}
		if (Debug.ENABLED) {
			System.out.println(Arrays.toString(base.list()));
		}
		hb.filesCount = 1;
		// unload
		hb.children = null;
		hb.offset = 0;
		hb.myPosition = target;
	}

	/**
	 * Deletes all files of this block.
	 * 
	 * @param historyBlock
	 *            the block to delete files for
	 * @param prefix
	 *            the filename prefix to delete the files
	 */
	private void clean(HistoryBlock historyBlock, String prefix) {
		if (historyBlock.filesCount == 0) {
			return;
		}
		if (Debug.ENABLED) {
			System.out.println("cleaning: " + prefix);
		}
		if (historyBlock instanceof HistoryTreeBlock) {
			HistoryTreeBlock ht = (HistoryTreeBlock) historyBlock;
			for (int i = 0; i < CHILDREN_PER_LEVEL; i++) {
				HistoryBlock block = ht.getBlock(i, true);
				if (block == null) {
					continue;
				}
				clean(block, prefix + "_" + i);
			}
		}
		new File(base, prefix + ".xml").delete();
		historyBlock.filesCount = 0;
		historyBlock.ownFile = false;

	}

	/**
	 * Loads a given file.
	 * 
	 * @param f
	 *            the file to load
	 * @param level
	 *            the level of which this block is.
	 * @return the loaded block
	 * @throws IOException
	 *             if the file doesnt want to be read.
	 */
	private static HistoryBlock loadBlock(File f, int level) throws IOException {

		try {
			InputStreamReader input = new InputStreamReader(
					new FileInputStream(f), "UTF-8");

			PositionAwareXMLPullParser xpp = new PositionAwareMXParser();
			xpp.setInput(input, 0, f);

			xpp.nextTag();
			HistoryBlock htb = HistoryBlock.parse(level, xpp);
			if (!xpp.getName().equals("history")) {
				throw new IOException(xpp.getName() + ";" + xpp.getPosition());
			}
			if (xpp.next() != XmlPullParser.END_DOCUMENT) {
				throw new IOException();
			}
			input.close();
			return htb;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	/**
	 * Export the given block to a output stream.
	 * 
	 * @param hb
	 *            the block to write out.
	 * @param os
	 *            the stream to write to.
	 * @throws TransformerException
	 */
	public static void export(HistoryBlock hb, File os) throws SAXException,
			IOException, TransformerException {
		BlockOutput out = new BlockOutput(os);
		TransformerHandler hd = out.getHandler();
		AttributesImpl atti = out.getA();
		hd.startDocument();
		atti.addAttribute(null, null, "checksum", "CDATA",
				beautifyChecksum(hb.getChecksum()));
		hd.startElement("", "", "history", atti);
		atti.clear();
		hb.serialize(out);
		hd.endElement("", "", "history");
		hd.endDocument();
		out.close();
	}

	/**
	 * Creates a default-configured SAX writer. (to this stream)
	 * 
	 * @param wr
	 *            the Writer to which this SAX-transformer will write to
	 * @return the created transformer.
	 */
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

	/**
	 * Creates a canonical representation for this checksum.
	 * 
	 * @param data
	 *            bytes of data
	 * @return string containing 2 chars per byte
	 */
	public static String beautifyChecksum(byte[] data) {
		StringBuffer sb = new StringBuffer(data.length * 2);
		for (int i = 0; i < data.length; i++) {
			sb.append(Integer.toHexString((data[i] >> 4) & 0xF));
			sb.append(Integer.toHexString(data[i] & 0xF));
		}
		return sb.toString();
	}

	/**
	 * Parses a canonical checksum representation back to a byte array
	 * 
	 * @param data
	 *            the checksum
	 * @return bytes containing one byte per 2 hex-chars
	 */
	public static byte[] parseChecksum(String data) {
		byte[] sb = new byte[data.length() / 2];
		for (int i = 0; i < sb.length; i++) {
			sb[i] = (byte) Integer.parseInt(data.substring(i * 2, i * 2 + 2),
					16);
		}
		return sb;
	}

	/**
	 * Sends out an IQ package and waits for a response
	 * 
	 * @param conn
	 *            the connection to send the package
	 * @param packet
	 *            the packet to send
	 * @return the response (in default response time (def. 5sec)).
	 * @throws NotConnectedException
	 */
	public static IQ getResponse(XMPPConnection conn, IQ packet)
			throws NotConnectedException {
		for (int ctr = 0; ctr < 10; ctr++) {
			if (ctr != 0) {
				System.out.println("ctr: " + ctr);
				new Error().printStackTrace(System.out);
			}
			PacketCollector collector = conn
					.createPacketCollector(new AndFilter(new StanzaIdFilter(
							packet.getStanzaId()), IQTypeFilter.RESULT));

			conn.sendStanza(packet);

			// Wait up to 5 seconds for a result.
			IQ result = (IQ) collector.nextResult(SmackConfiguration
					.getDefaultPacketReplyTimeout());
			// Stop queuing results
			collector.cancel();
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Loads all files from the storage location.
	 */
	private void load() {
		for (File f : base.listFiles()) {
			if (f.getName().endsWith(".xml.new")) {
				System.out.println("Warning: deleting corrupt data");
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
					String[] parts = prefix.split("_");
					HistoryBlock block2 = loadBlock(f, parts.length - 1);
					if (block2 != null) {
						block2.ownFile = true;
						block2.filesCount = 1;
					}
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

	/**
	 * returns the number of Atomic blocks in the given level block.
	 * 
	 * @param i
	 *            the level.
	 * @return the number of atomic blocks
	 */
	public static int getHoursPerBlock(int i) {
		if (i > LEVELS || i < 0) {
			return 0;
		}

		return 1 << (BITS_PER_LEVEL * (LEVELS - i));
	}

	/**
	 * returns the time that needs to be given to {@link #recurseTime(long)} and
	 * to {@link #getMyCount(long)} for the underlying level.
	 * 
	 * @param time
	 *            the time to adjust
	 * @return the new time
	 * @see #getMyCount(long)
	 */
	public static long recurseTime(long time) {
		time = (time & ((1L << BITS_PER_LEVEL * LEVELS) - 1)) << BITS_PER_LEVEL;
		return time;
	}

	/**
	 * Returns the index for the given block.
	 * 
	 * Iterating over all block is done the following way:<br>
	 * <code><pre>
	 * long millis = ...;
	 * for(int level = 0; level <= LEVELS; level++){
	 * 	int myIndex = getMyCount(millis);
	 *  // In level "level" take block index "myIndex"
	 * 	millis = recurseTime(millis);
	 * }</pre>
	 * </code>
	 * 
	 * @param time
	 *            the time (see {@link #recurseTime(long)} and {@link #BASE})
	 * @return the index
	 */
	public static int getMyCount(long time) {
		return (int) (time >> (BITS_PER_LEVEL * LEVELS));
	}

}
