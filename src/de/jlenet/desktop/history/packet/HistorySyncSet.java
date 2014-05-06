package de.jlenet.desktop.history.packet;

import java.io.StringWriter;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jivesoftware.smack.packet.IQ;

import de.jlenet.desktop.history.HistoryMessage;

public class HistorySyncSet extends IQ {
	SortedSet<HistoryMessage> messages = new TreeSet<HistoryMessage>();
	long hour;
	String checksum;
	public HistorySyncSet(long hour, String checksum) {
		this.hour = hour;
		this.checksum = checksum;
	}
	public void addMessage(HistoryMessage message) {
		messages.add(message);
	}
	public SortedSet<HistoryMessage> getMessages() {
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
		for (HistoryMessage hm : messages) {
			sw.append(hm.toXML());
		}
		sw.write("</syncSet>");
		return sw.toString();
	}
	public static void main(String[] args) {
		HistorySyncSet hss = new HistorySyncSet(0, null);
		hss.messages.add(new HistoryMessage("<msg>]]>", 0));
		System.out.println(hss.getChildElementXML());
	}
	public long getHour() {
		return hour;
	}
	public void setMessages(SortedSet<HistoryMessage> forOther) {
		messages = forOther;
	}
	public String getChecksum() {
		return checksum;
	}

}
