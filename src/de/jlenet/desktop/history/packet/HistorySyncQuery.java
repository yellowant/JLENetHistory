package de.jlenet.desktop.history.packet;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.packet.IQ;

import de.jlenet.desktop.history.History;
import de.jlenet.desktop.history.HistoryBlock;
import de.jlenet.desktop.history.HistoryTreeBlock;

public class HistorySyncQuery extends IQ {
	long hour;
	int level;
	boolean path;
	public HistorySyncQuery(long hour, boolean path, int level) {
		this.hour = hour;
		this.path = path;
		this.level = level;
		setType(Type.GET);
	}

	@Override
	public String getChildElementXML() {
		return "<query xmlns=\"http://jlenet.de/histsync\" type=\""
				+ (path ? "latest" : "tree") + "\" hour=\"" + hour
				+ (level == -1 ? "" : "\" level=\"" + level) + "\"/>";
	}

	public HistorySyncHashes reply(History h) {
		HistorySyncHashes response = new HistorySyncHashes();
		response.setTo(getFrom());
		response.setType(Type.RESULT);
		response.setPacketID(getPacketID());
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
	public HistorySyncHashes execute(Connection conn) {
		return (HistorySyncHashes) History.getResponse(conn, this);
	}

}
