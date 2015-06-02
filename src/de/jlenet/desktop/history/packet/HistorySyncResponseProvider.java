package de.jlenet.desktop.history.packet;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class HistorySyncResponseProvider extends IQProvider<HistorySyncHashes> {

	@Override
	public HistorySyncHashes parse(XmlPullParser parser, int initialDepth)
			throws XmlPullParserException, IOException, SmackException {
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
