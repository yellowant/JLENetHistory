package de.jlenet.desktop.history;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.transform.sax.TransformerHandler;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import static de.jlenet.desktop.history.History.*;

public class HistoryTreeBlock extends HistoryBlock {
	HistoryBlock[] children = new HistoryBlock[1 << BITS_PER_LEVEL];
	int level;
	public HistoryTreeBlock(int level) {
		this.level = level;
	}
	public HistoryTreeBlock(int level, XmlPullParser xpp)
			throws XmlPullParserException, IOException {
		if (level > 3) {
			throw new Error();
		}
		this.level = level;
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
		HistoryBlock hb = children[count];
		if (hb == null) {
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
	public void serialize(TransformerHandler hd, AttributesImpl atti)
			throws SAXException {
		for (int i = 0; i < children.length; i++) {
			if (children[i] != null) {
				atti.addAttribute("", "", "id", "CDATA", Integer.toString(i));
				atti.addAttribute("", "", "checksum", "CDATA",
						History.beautifyChecksum(children[i].getChecksum()));
				hd.startElement("", "", "block", atti);
				atti.clear();
				children[i].serialize(hd, atti);
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
		children[index] = htb;
	}
}
