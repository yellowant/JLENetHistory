package de.jlenet.desktop.history.packet;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class HistorySyncQueryProvider extends IQProvider<HistorySyncQuery> {
	@Override
	public HistorySyncQuery parse(XmlPullParser parser, int initialDepth)
			throws XmlPullParserException, IOException, SmackException {
		String queryType = parser.getAttributeValue(null, "type");
		boolean path;
		if (queryType.equals("latest")) {
			path = true;
		} else if (queryType.equals("tree")) {
			path = false;
		} else {
			return null;
		}
		int level = -1;
		String le = parser.getAttributeValue(null, "level");
		if (le != null) {
			level = Integer.parseInt(le);
		}
		return new HistorySyncQuery(Long.parseLong(parser.getAttributeValue(
				null, "hour")), path, level);
	}

}
