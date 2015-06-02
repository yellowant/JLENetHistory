package de.jlenet.desktop.history.packet;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;

import de.jlenet.desktop.history.History;
import de.jlenet.desktop.history.HistoryBlock;
import de.jlenet.desktop.history.HistoryTreeBlock;

public class HistorySyncQuery extends IQ {
	long hour;
	int level;
	boolean path;
	public HistorySyncQuery(long hour, boolean path, int level) {
		super("query", "http://jlenet.de/histsync");
		this.hour = hour;
		this.path = path;
		this.level = level;
		setType(Type.get);
	}
	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
			IQChildElementXmlStringBuilder xml) {
		xml.attribute("type", (path ? "latest" : "tree"));
		xml.attribute("hour", Long.toString(hour));
		if (level != -1) {
			xml.attribute("level", level);
		}
		xml.setEmptyElement();
		return xml;
	}

	public HistorySyncHashes reply(History h) {
		HistorySyncHashes response = new HistorySyncHashes();
		response.setTo(getFrom());
		response.setType(Type.result);
		response.setStanzaId(getStanzaId());
		if (path) {
			int myCount = History.getMyCount(hour);
			HistoryBlock block = h.getRootBlock(myCount);
			long rest = History.recurseTime(hour);
			for (int i = 0; i < History.LEVELS
					&& block instanceof HistoryTreeBlock; i++) {
				response.addHash(History.beautifyChecksum(block.getChecksum()),
						i, myCount);
				block = ((HistoryTreeBlock) block).getBlock(myCount = History
						.getMyCount(rest));
				rest = History.recurseTime(rest);
			}
			response.addHash(History.beautifyChecksum(block.getChecksum()),
					History.LEVELS, myCount);

		} else {
			HistoryTreeBlock block = (HistoryTreeBlock) h.getRootBlock(History
					.getMyCount(hour));
			long rest = History.recurseTime(hour);
			for (int i = 0; i < History.LEVELS - 1
					&& block instanceof HistoryTreeBlock && i < level; i++) {
				block = (HistoryTreeBlock) block.getBlock(History
						.getMyCount(rest));
				rest = History.recurseTime(rest);
			}
			for (int i = 0; i < History.CHILDREN_PER_LEVEL; i++) {
				response.addHash(History.beautifyChecksum(block.getBlock(i)
						.getChecksum()), level + 1, i);
			}

		}
		return response;
	}
	public HistorySyncHashes execute(XMPPConnection conn)
			throws NotConnectedException {
		return (HistorySyncHashes) History.getResponse(conn, this);
	}

}
