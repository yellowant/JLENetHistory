package de.jlenet.desktop.history;

import java.io.IOException;
import java.io.StringReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.transform.sax.TransformerHandler;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.util.StringUtils;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class HistoryMessage implements Comparable<HistoryMessage> {
	long time;
	byte[] checksum;

	String contents;
	String correspondent;
	public HistoryMessage(Message mes, long time, boolean isOutgoing) {
		this.time = time;
		contents = mes.toXML();
		correspondent = StringUtils.parseBareAddress(isOutgoing
				? mes.getTo()
				: mes.getFrom());
	}
	public HistoryMessage(String contents, long time, String correspondent) {
		this.time = time;
		this.contents = contents;
		this.correspondent = StringUtils.parseBareAddress(correspondent);
	}
	public HistoryMessage(XmlPullParser xpp) throws XmlPullParserException,
			IOException {
		time = Long.parseLong(xpp.getAttributeValue("", "time"));
		correspondent = xpp.getAttributeValue(null, "jid");
		contents = xpp.nextText();
	}
	public HistoryMessage(long time) {
		this.time = time;
	}
	public static void main(String[] args) {
		HistoryMessage hm = new HistoryMessage(
				"<message id=\"cy340-160\" to=\"juliet@capulets.lit/JLENetDesktop\" from=\"romeo@montegues.lit/Spark 2.6.3\" type=\"chat\"><body>ja, mir gehts gut</body><thread>cCYqy8</thread><x xmlns=\"jabber:x:event\"><offline/><composing/></x></message>",
				System.currentTimeMillis(), "romeo@montegues.lit");
		System.out.println(hm.getMessage().getBody());
	}
	public Message getMessage() {
		XmlPullParser xpp = new MXParser();
		try {
			xpp.setInput(new StringReader(contents));
			xpp.nextTag();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			Message parseMessage = (Message) PacketParserUtils
					.parseMessage(xpp);
			return parseMessage;
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
	public int compareTo(HistoryMessage o) {
		if (this == o) {
			return 0;
		}
		if (time > o.time) {
			return 1;
		}
		if (time < o.time) {
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
	public void serialize(TransformerHandler hd, AttributesImpl atti)
			throws SAXException {
		atti.addAttribute("", "", "time", "CDATA", Long.toString(time));
		atti.addAttribute("", "", "checksum", "CDATA",
				History.beautifyChecksum(getChecksum()));
		atti.addAttribute("", "", "jid", "CDATA", correspondent);
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
}
