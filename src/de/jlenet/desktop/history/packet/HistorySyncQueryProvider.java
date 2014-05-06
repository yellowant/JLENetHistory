package de.jlenet.desktop.history.packet;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

public class HistorySyncQueryProvider implements IQProvider {

	@Override
	public IQ parseIQ(XmlPullParser parser) throws Exception {
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
