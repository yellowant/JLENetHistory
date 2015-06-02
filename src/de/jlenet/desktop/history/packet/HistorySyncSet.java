package de.jlenet.desktop.history.packet;

import java.util.SortedSet;
import java.util.TreeSet;

import org.jivesoftware.smack.packet.IQ;

import de.jlenet.desktop.history.HistoryEntry;

public class HistorySyncSet extends IQ {
	SortedSet<HistoryEntry> messages = new TreeSet<HistoryEntry>();
	long hour;
	String checksum;
	boolean update = false;

	public HistorySyncSet(long hour, String checksum, boolean update) {
		super(update ? "syncUpdate" : "syncSet",
				"http://jlenet.de/histsync#sync" + (update ? "Update" : "Set"));
		this.hour = hour;
		this.checksum = checksum;
		this.update = update;
	}
	public void addMessage(HistoryEntry message) {
		messages.add(message);
	}
	public SortedSet<HistoryEntry> getMessages() {
		return messages;
	}
	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
			IQChildElementXmlStringBuilder xml) {
		xml.attribute("hour", Long.toString(hour));
		if (checksum != null) {
			xml.attribute("checksum", checksum);
		}
		xml.rightAngleBracket();
		for (HistoryEntry hm : messages) {
			xml.append(hm.toXML());
		}
		return xml;
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
	public boolean isUpdate() {
		return update;
	}

}
