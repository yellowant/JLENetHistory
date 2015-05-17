package de.jlenet.desktop.history.packet;

import org.jivesoftware.smack.packet.IQ;

public class HistorySyncUpdateResponse extends IQ {
	String status;

	public HistorySyncUpdateResponse(String status) {
		this.status = status;
	}
	public String getStatus() {
		return status;
	}

	@Override
	public String getChildElementXML() {
		StringBuilder sb = new StringBuilder();
		sb.append("<syncStatus"
				+ " xmlns=\"http://jlenet.de/histsync#syncStatus\" status=\"");
		sb.append(status);
		sb.append("\"/>");
		return sb.toString();
	}
}
