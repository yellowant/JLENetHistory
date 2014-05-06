package de.jlenet.desktop.history.packet;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

public class HistorySyncResponseProvider implements IQProvider {

	@Override
	public IQ parseIQ(XmlPullParser parser) throws Exception {
		HistorySyncHashes hsh = new HistorySyncHashes();
		while (parser.nextTag() == XmlPullParser.START_TAG) {
			hsh.addHash(parser.getAttributeValue("", "value"),
					Integer.parseInt(parser.getAttributeValue("", "level")),
					Integer.parseInt(parser.getAttributeValue("", "id")));
			parser.nextTag();
		}
		return hsh;
	}

}
