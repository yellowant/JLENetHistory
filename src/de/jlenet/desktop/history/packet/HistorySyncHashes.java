package de.jlenet.desktop.history.packet;

import java.util.LinkedList;

import org.jivesoftware.smack.packet.IQ;

public class HistorySyncHashes extends IQ {
	public HistorySyncHashes() {
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
	public String getChildElementXML() {
		StringBuffer xml = new StringBuffer();
		xml.append("<hashes xmlns=\"http://jlenet.de/histsync#hashes\">");
		for (Hash h : hashes) {
			xml.append("<hash value=\"");
			xml.append(h.hash);
			xml.append("\" level=\"");
			xml.append(Integer.toString(h.level));
			xml.append("\" id=\"");
			xml.append(Integer.toString(h.id));
			xml.append("\"/>");
		}
		xml.append("</hashes>");
		return xml.toString();
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
