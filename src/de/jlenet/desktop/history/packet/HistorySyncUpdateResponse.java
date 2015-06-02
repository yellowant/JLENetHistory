package de.jlenet.desktop.history.packet;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class HistorySyncUpdateResponse extends IQ {
	public static class Provider extends IQProvider<HistorySyncUpdateResponse> {

		@Override
		public HistorySyncUpdateResponse parse(XmlPullParser parser,
				int initialDepth) throws XmlPullParserException, IOException,
				SmackException {
			String status = parser.getAttributeValue(null, "status");
			parser.nextTag();
			return new HistorySyncUpdateResponse(status);
		}

	}
	String status;

	public HistorySyncUpdateResponse(String status) {
		super("syncStatus", "http://jlenet.de/histsync#syncStatus");
		this.status = status;
	}
	public String getStatus() {
		return status;
	}
	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
			IQChildElementXmlStringBuilder xml) {
		xml.attribute("status", status);
		xml.setEmptyElement();
		return xml;
	}
}
