package de.jlenet.desktop.history;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.transform.sax.TransformerHandler;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class HistoryLeafNode extends HistoryBlock {
	TreeSet<HistoryEntry> hmsg = new TreeSet<HistoryEntry>();
	byte[] checksum;
	public HistoryLeafNode(XmlPullParser xpp) throws XmlPullParserException,
			IOException {
		while (xpp.getEventType() == XmlPullParser.START_TAG) {
			hmsg.add(new HistoryEntry(xpp));
			xpp.nextTag();
		}
	}

	public HistoryLeafNode() {
	}

	@Override
	public byte[] getChecksum() {
		if (checksum != null) {
			return checksum;
		}
		try {
			MessageDigest md = MessageDigest.getInstance("SHA1");
			for (HistoryEntry hb : hmsg) {
				md.update(hb.getChecksum());
			}
			return checksum = md.digest();
		} catch (NoSuchAlgorithmException e) {
			throw new Error();
		}
	}

	public void add(HistoryEntry hm) {
		hmsg.add(hm);
	}

	@Override
	public void serialize(BlockOutput block)
			throws SAXException {
		TransformerHandler hd = block.getHandler();
		AttributesImpl atti = block.getA();
		for (HistoryEntry message : hmsg) {
			message.serialize(hd, atti);
		}
	}

	@Override
	public void modified(long time) {
		super.modified(time);
		checksum = null;
	}

	public SortedSet<HistoryEntry> getMessages() {
		return hmsg;
	}

	public void setMessages(TreeSet<HistoryEntry> ts) {
		hmsg = ts;
	}

	@Override
	public void ensureLoaded() {
		// is always loaded
	}

	@Override
	public boolean isEmpty() {
		return hmsg.isEmpty();
	}

}
