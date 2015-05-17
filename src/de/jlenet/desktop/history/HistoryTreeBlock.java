package de.jlenet.desktop.history;

import static de.jlenet.desktop.history.History.BITS_PER_LEVEL;
import static de.jlenet.desktop.history.History.LEVELS;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.transform.sax.TransformerHandler;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class HistoryTreeBlock extends HistoryBlock {
	HistoryBlock[] children = new HistoryBlock[1 << BITS_PER_LEVEL];
	int level;

	public HistoryTreeBlock(int level) {
		this.level = level;
	}

	int offset;
	File myPosition;

	/**
	 * 
	 * Creates a new HistoryTreeBlock.
	 * 
	 * @param level
	 * @param xpp
	 *            positioned on first element
	 * @param start
	 * @param chksum
	 * @throws XmlPullParserException
	 * @throws IOException
	 */
	public HistoryTreeBlock(int level, PositionAwareXMLPullParser xpp,
			int start, String chksum) throws XmlPullParserException,
			IOException {
		offset = start;
		myPosition = xpp.getFile();
		this.level = level;
		if (chksum != null) {
			checksum = History.parseChecksum(chksum);
		} else {
			System.err.println("Warning: missing checksum");
		}
		if (level == 1 || level == 2) {
			children = null;
			while (xpp.getEventType() == XmlPullParser.START_TAG) {
				xpp.skipSubTree();
				xpp.nextTag();
			}
			return;
		}
		if (level > 3) {
			throw new Error();
		}
		load(xpp);
	}

	private void load(PositionAwareXMLPullParser xpp)
			throws XmlPullParserException, IOException, Error {
		while (xpp.getEventType() == XmlPullParser.START_TAG) {
			children[Integer.parseInt(xpp.getAttributeValue("", "id"))] = HistoryBlock
					.parse(level + 1, xpp);
			if (xpp.getEventType() != XmlPullParser.END_TAG) {
				throw new Error("" + xpp.getEventType() + ";" + xpp.getName());
			}
			xpp.nextTag();
		}
	}

	@Override
	public byte[] getChecksum() {
		if (checksum != null) {
			return checksum;
		}
		try {
			MessageDigest md = MessageDigest.getInstance("SHA1");
			for (HistoryBlock hb : children) {
				if (hb == null) {
					md.update(History.DUMMY_CHECKSUMS[level]);
				} else {
					md.update(hb.getChecksum());
				}
			}
			return checksum = md.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new Error();
		}

	}

	public HistoryBlock getBlock(int count) {
		return getBlock(count, false);
	}

	public HistoryBlock getBlock(int count, boolean peek) {
		ensureLoaded();
		HistoryBlock hb = children[count];
		if (hb == null) {
			if (peek) {
				return null;
			}
			if (level + 1 >= LEVELS) {
				hb = new HistoryLeafNode();
			} else {
				hb = new HistoryTreeBlock(level + 1);
			}
			children[count] = hb;
		}
		return hb;
	}

	@Override
	public void serialize(BlockOutput block) throws SAXException {
		TransformerHandler hd = block.getHandler();
		AttributesImpl atti = block.getA();
		if (children == null) {
			PositionAwareMXParser pamp = new PositionAwareMXParser();
			try {
				InputStreamReader fr = new InputStreamReader(
						new FileInputStream(myPosition), "UTF-8");
				fr.skip(offset);
				block.flush();
				pamp.setInput(fr, offset, myPosition);
				pamp.nextTag();// root
				pamp.nextTag();// firstChild
				int start = pamp.getBeforePosition();
				while (pamp.getEventType() == XmlPullParser.START_TAG) {
					pamp.skipSubTree();
					pamp.nextTag();
				}
				int end = pamp.getBeforePosition();
				fr.close();
				fr = new InputStreamReader(new FileInputStream(myPosition),
						"UTF-8");
				fr.skip(start);
				int len = end - start;
				char[] buffer = new char[8192];
				while (len > 0) {
					int toRead = Math.min(len, buffer.length);
					int read = fr.read(buffer, 0, toRead);
					if (read <= 0) {
						fr.close();
						throw new EOFException();
					}
					block.w.write(buffer, 0, read);
					len -= read;
				}
				fr.close();
			} catch (XmlPullParserException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
		ensureLoaded();
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null && !children[i].isEmpty()) {
				atti.addAttribute("", "", "id", "CDATA", Integer.toString(i));
				atti.addAttribute("", "", "checksum", "CDATA",
						History.beautifyChecksum(children[i].getChecksum()));
				hd.startElement("", "", "block", atti);
				atti.clear();
				children[i].serialize(block);
				hd.endElement("", "", "block");
			}
		}
	}

	@Override
	public void modified(long time) {
		super.modified(time);
		HistoryBlock hb = getBlock(History.getMyCount(time));
		hb.modified(History.recurseTime(time));
		checksum = null;
	}

	public void setBlock(int index, HistoryBlock htb) {
		ensureLoaded();
		children[index] = htb;
	}

	@Override
	public void ensureLoaded() {
		if (children != null) {
			return;
		}
		if (Debug.ENABLED) {
			System.out.println("Lazy loading " + myPosition + ":" + offset);
		}
		PositionAwareMXParser pamp = new PositionAwareMXParser();
		try {
			InputStreamReader fr = new InputStreamReader(new FileInputStream(
					myPosition), "UTF-8");
			fr.skip(offset);
			pamp.setInput(fr, offset, myPosition);
			pamp.nextTag();// root
			pamp.nextTag();// firstChild
			children = new HistoryBlock[History.CHILDREN_PER_LEVEL];
			load(pamp);
			if (pamp.getDepth() != 1) {
				System.err.println("Something is badly wrong");
			}
			fr.close();

		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean isEmpty() {
		if (children == null) {
			return false;
		}
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null && !children[i].isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
