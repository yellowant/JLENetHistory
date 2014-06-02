package de.jlenet.desktop.history.payloads;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.xmlpull.v1.XmlPullParser;

public class HistoryMessage extends HistoryPayload {
	public HistoryMessage(Message mes) {
		parsed = mes;
	}

	public HistoryMessage(XmlPullParser xpp) throws Exception {
		parsed = (Message) PacketParserUtils.parseMessage(xpp);
	}

	Message parsed = null;
	public Message getParsed() {
		return parsed;
	}
	@Override
	public String toXML() {
		return parsed.toXML();
	}

}
