package de.jlenet.desktop.history;

import java.io.IOException;

import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public abstract class HistoryBlock {
	boolean modified = false;
	boolean ownFile = false;
	byte[] checksum;
	int filesCount = 0;

	public abstract byte[] getChecksum();

	public abstract void serialize(BlockOutput out)
			throws SAXException;

	public void modified(long time) {
		modified = true;
	}

	public static HistoryBlock parse(int i, PositionAwareXMLPullParser xpp)
			throws XmlPullParserException, IOException {
		String chksum = xpp.getAttributeValue(null, "checksum");
		int start = xpp.getBeforePosition();
		if (xpp.nextTag() == XmlPullParser.END_TAG) {
			return null;
		}
		if (xpp.getName().equals("msg")) {
			return new HistoryLeafNode(xpp);
		} else {
			return new HistoryTreeBlock(i, xpp, start, chksum);
		}
	}

	public abstract void ensureLoaded();

	public abstract boolean isEmpty();
}
