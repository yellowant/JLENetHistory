package de.jlenet.desktop.history.packet;

import java.util.LinkedList;

import org.jivesoftware.smack.packet.IQ;

public class HistorySyncHashes extends IQ {
	public HistorySyncHashes() {
		super("hashes", "http://jlenet.de/histsync#hashes");
	}
	public class Hash {
		String hash;
		int level;
		int id;
		public Hash(String hash, int level, int id) {
			this.hash = hash;
			this.level = level;
			this.id = id;
		}
		@Override
		public String toString() {
			return id + "@" + level + ":" + hash;
		}
		public String getHash() {
			return hash;
		}
		public int getLevel() {
			return level;
		}
		public int getId() {
			return id;
		}
	}
	LinkedList<Hash> hashes = new LinkedList<HistorySyncHashes.Hash>();
	public void addHash(String hash, int level, int id) {
		hashes.add(new Hash(hash, level, id));
	}
	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
			IQChildElementXmlStringBuilder xml) {
		xml.rightAngleBracket();
		for (Hash h : hashes) {
			xml.halfOpenElement("hash");
			xml.attribute("value", h.hash);
			xml.attribute("level", h.level);
			xml.attribute("id", h.id);
			xml.closeEmptyElement();
		}
		return xml;
	}
	@Override
	public String toString() {
		StringBuffer strb = new StringBuffer();
		strb.append("hashes: \n");
		for (Hash h : hashes) {
			strb.append(h);
			strb.append("\n");
		}
		return strb.toString();
	}
	public LinkedList<Hash> getHashes() {
		return hashes;
	}

}
