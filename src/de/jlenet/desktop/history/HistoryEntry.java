package de.jlenet.desktop.history;

import java.io.IOException;
import java.io.StringReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.transform.sax.TransformerHandler;

import org.jivesoftware.smack.packet.Message;
import org.jxmpp.util.XmppStringUtils;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import de.jlenet.desktop.history.payloads.CompletedFileTransfer;
import de.jlenet.desktop.history.payloads.HistoryMessage;
import de.jlenet.desktop.history.payloads.HistoryPayload;

public class HistoryEntry implements Comparable<HistoryEntry> {
	long time;
	byte[] checksum;

	String correspondent;
	boolean isOutgoing;
	String contents;

	HistoryPayload parsed;

	public HistoryEntry(Message mes, long time, boolean isOutgoing) {
		this.time = time;
		parsed = new HistoryMessage(mes);
		contents = mes.toXML().toString();
		correspondent = XmppStringUtils.parseBareJid(isOutgoing
				? mes.getTo()
				: mes.getFrom());
		this.isOutgoing = isOutgoing;
	}
	public HistoryEntry(HistoryPayload contents, long time,
			String correspondent, boolean isOutgoing) {
		this(contents.toXML(), time, correspondent, isOutgoing);
		parsed = contents;
	}
	public HistoryEntry(String contents, long time, String correspondent,
			boolean isOutgoing) {
		this.time = time;
		this.contents = contents;
		this.correspondent = XmppStringUtils.parseBareJid(correspondent);
		this.isOutgoing = isOutgoing;
	}
	public HistoryEntry(XmlPullParser xpp) throws XmlPullParserException,
			IOException {
		time = Long.parseLong(xpp.getAttributeValue("", "time"));
		correspondent = xpp.getAttributeValue(null, "jid");
		isOutgoing = xpp.getAttributeValue(null, "outgoing").equals("y");
		contents = xpp.nextText();
	}
	public HistoryEntry(long time) {
		this.time = time;
	}
	public HistoryPayload getPayload() {
		if (parsed != null) {
			return parsed;
		}
		XmlPullParser xpp = new MXParser();
		try {
			xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
			xpp.setInput(new StringReader(contents));
			xpp.nextTag();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			if (xpp.getName().equals("message")) {
				parsed = new HistoryMessage(xpp);
			} else if (xpp.getName().equals("file")) {
				parsed = new CompletedFileTransfer(xpp);
			} else {
				System.err.println("warning, accessing unknown history entry: "
						+ xpp.getName());
			}
			if (xpp.getDepth() != 1) {
				System.err.println("Warning: unclean pull parser");
			}
			return parsed;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	public byte[] getChecksum() {
		if (checksum != null) {
			return checksum;
		}
		try {
			MessageDigest md = MessageDigest.getInstance("SHA1");
			md.update(isOutgoing ? (byte) 1 : 0);
			md.update(Long.toString(time).getBytes());
			md.update(contents.getBytes());
			checksum = md.digest();
			return checksum;
		} catch (NoSuchAlgorithmException e) {
			throw new Error();
		}
	}

	public long getTime() {
		return time;
	}
	@Override
	public int compareTo(HistoryEntry o) {
		if (this == o) {
			return 0;
		}
		if (time > o.time) {
			return 1;
		}
		if (time < o.time) {
			return -1;
		}
		if (isOutgoing && !o.isOutgoing) {
			return 1;
		}
		if (!isOutgoing && o.isOutgoing) {
			return -1;
		}
		if (contents == o.contents) {
			return 0;
		}

		if (contents == null) {
			return -1;
		} else if (o.contents == null) {
			return 1;
		}
		return contents.compareTo(o.contents);
	}
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof HistoryEntry)) {
			return false;
		}
		return compareTo((HistoryEntry) obj) == 0;
	}
	public void serialize(TransformerHandler hd, AttributesImpl atti)
			throws SAXException {
		atti.addAttribute("", "", "time", "CDATA", Long.toString(time));
		atti.addAttribute("", "", "checksum", "CDATA",
				History.beautifyChecksum(getChecksum()));
		atti.addAttribute("", "", "jid", "CDATA", correspondent);
		atti.addAttribute("", "", "outgoing", "CDATA", isOutgoing ? "y" : "n");
		hd.startElement("", "", "msg", atti);
		atti.clear();
		hd.startCDATA();
		char[] c = contents.toCharArray();
		hd.characters(c, 0, c.length);
		hd.endCDATA();
		hd.endElement("", "", "msg");
	}
	public CharSequence toXML() {
		StringBuffer xml = new StringBuffer();
		xml.append("<msg time=\"");
		xml.append(time);
		xml.append("\" jid=\"");
		xml.append(correspondent);
		if (isOutgoing) {
			xml.append("\" outgoing=\"y");
		} else {
			xml.append("\" outgoing=\"n");
		}

		xml.append("\" checksum=\"");
		xml.append(History.beautifyChecksum(getChecksum()));
		xml.append("\"><![CDATA[");//
		xml.append(contents.replace("]]>", "]]]><![CDATA[]>"));
		xml.append("]]></msg>");
		return xml;
	}
	public String getContents() {
		return contents;
	}
	public String getCorrespondent() {
		return correspondent;
	}
	public boolean isOutgoing() {
		return isOutgoing;
	}
}
