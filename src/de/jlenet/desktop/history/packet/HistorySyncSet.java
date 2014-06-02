package de.jlenet.desktop.history.packet;

import java.io.StringWriter;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jivesoftware.smack.packet.IQ;

import de.jlenet.desktop.history.HistoryEntry;

public class HistorySyncSet extends IQ {
	SortedSet<HistoryEntry> messages = new TreeSet<HistoryEntry>();
	long hour;
	String checksum;
	public HistorySyncSet(long hour, String checksum) {
		this.hour = hour;
		this.checksum = checksum;
	}
	public void addMessage(HistoryEntry message) {
		messages.add(message);
	}
	public SortedSet<HistoryEntry> getMessages() {
		return messages;
	}
	@Override
	public String getChildElementXML() {
		StringWriter sw = new StringWriter();
		sw.write("<syncSet xmlns=\"http://jlenet.de/histsync#syncSet\" hour=\"");
		sw.write(Long.toString(hour));
		sw.write("\"");
		if (checksum != null) {
			sw.write(" checksum=\"");
			sw.write(checksum);
			sw.write("\"");
		}
		sw.write(">");
		for (HistoryEntry hm : messages) {
			sw.append(hm.toXML());
		}
		sw.write("</syncSet>");
		return sw.toString();
	}
	public long getHour() {
		return hour;
	}
	public void setMessages(SortedSet<HistoryEntry> forOther) {
		messages = forOther;
	}
	public String getChecksum() {
		return checksum;
	}

}
