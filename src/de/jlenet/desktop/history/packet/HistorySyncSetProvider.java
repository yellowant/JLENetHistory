package de.jlenet.desktop.history.packet;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import de.jlenet.desktop.history.HistoryEntry;

public class HistorySyncSetProvider extends IQProvider<HistorySyncSet> {
	@Override
	public HistorySyncSet parse(XmlPullParser parser, int initialDepth)
			throws XmlPullParserException, IOException, SmackException {
		if (!parser.getName().equals("syncSet")
				&& !parser.getName().equals("syncUpdate")) {
			throw new SmackException("invalid type" + parser.getName());
		}

		HistorySyncSet hsh = new HistorySyncSet(Long.parseLong(parser
				.getAttributeValue(null, "hour")), parser.getAttributeValue(
				null, "checksum"), parser.getName().equals("syncUpdate"));
		while (parser.nextTag() == XmlPullParser.START_TAG) {
			hsh.addMessage(new HistoryEntry(parser));
		}
		return hsh;
	}

}
