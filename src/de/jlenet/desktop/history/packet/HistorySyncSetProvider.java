package de.jlenet.desktop.history.packet;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

import de.jlenet.desktop.history.HistoryEntry;

public class HistorySyncSetProvider implements IQProvider {

	@Override
	public IQ parseIQ(XmlPullParser parser) throws Exception {
		HistorySyncSet hsh = new HistorySyncSet(Long.parseLong(parser
				.getAttributeValue(null, "hour")), parser.getAttributeValue(
				null, "checksum"));
		while (parser.nextTag() == XmlPullParser.START_TAG) {
			hsh.addMessage(new HistoryEntry(parser));
		}
		return hsh;
	}

}
