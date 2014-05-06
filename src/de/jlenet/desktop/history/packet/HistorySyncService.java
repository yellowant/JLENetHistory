package de.jlenet.desktop.history.packet;

import java.util.Iterator;
import java.util.TreeSet;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ServiceDiscoveryManager;

import de.jlenet.desktop.history.History;
import de.jlenet.desktop.history.HistoryLeafNode;
import de.jlenet.desktop.history.HistoryMessage;

public class HistorySyncService {

	public static void initialize(final Connection theConnection,
			final History h) {
		final String jid = StringUtils
				.parseBareAddress(theConnection.getUser());
		// Hash fetch service
		theConnection.addPacketListener(new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				try {
					if (!StringUtils.parseBareAddress(packet.getFrom()).equals(
							jid)) {
						error(packet, theConnection);
						return;
					}

					HistorySyncQuery query = (HistorySyncQuery) packet;
					if (query.getType() == IQ.Type.GET) {
						HistorySyncHashes response = query.reply(h);
						theConnection.sendPacket(response);
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}

		}, new PacketTypeFilter(HistorySyncQuery.class));
		final boolean DEBUG_SYNC = false;

		// sync service
		theConnection.addPacketListener(new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				if (!StringUtils.parseBareAddress(packet.getFrom()).equals(jid)) {
					error(packet, theConnection);
					return;
				}

				HistorySyncSet sync = (HistorySyncSet) packet;
				if (DEBUG_SYNC) {
					System.out.println("sync pack");
				}
				if (sync.getType() != IQ.Type.SET) {
					return;
				}
				HistoryLeafNode hln = (HistoryLeafNode) h.getAnyBlock(
						sync.getHour() * History.BASE, History.LEVELS);
				if (DEBUG_SYNC) {
					System.out.println("Have: " + hln.getMessages().size());
					System.out.println("Got: " + sync.getMessages().size());
				}
				TreeSet<HistoryMessage> forMe = new TreeSet<HistoryMessage>();
				TreeSet<HistoryMessage> forOther = new TreeSet<HistoryMessage>();
				Iterator<HistoryMessage> have = hln.getMessages().iterator();
				Iterator<HistoryMessage> got = sync.getMessages().iterator();
				HistoryMessage currentGot = null;
				HistoryMessage currentHave = null;
				while (have.hasNext() || got.hasNext() || currentHave != null
						|| currentGot != null) {
					if (currentGot == null && got.hasNext()) {
						currentGot = got.next();
					}
					if (currentHave == null && have.hasNext()) {
						currentHave = have.next();
					}
					if (currentHave == null && currentGot == null) {
						// Should never happen;
						System.out.println("this should never happen");
						break;
					}
					if (currentGot == null
							|| (currentHave != null && currentHave
									.compareTo(currentGot) < 0)) {
						// current Have is alone
						forOther.add(currentHave);
						currentHave = null;
					} else if (currentHave == null
							|| currentHave.compareTo(currentGot) > 0) {
						// current Got is alone
						forMe.add(currentGot);
						currentGot = null;
					} else {
						currentHave = null;
						currentGot = null;
					}
				}
				hln.getMessages().addAll(forMe);
				h.modified(sync.getHour() * History.BASE);
				// Construct response
				HistorySyncSet hss = new HistorySyncSet(sync.getHour(), History
						.beautifyChecksum(hln.getChecksum()));
				hss.setMessages(forOther);
				hss.setType(IQ.Type.RESULT);
				hss.setTo(sync.getFrom());
				hss.setPacketID(sync.getPacketID());
				theConnection.sendPacket(hss);

				if (DEBUG_SYNC) {
					System.out.println("now Have: " + hln.getMessages().size());
					System.out.println("adding: " + forMe.size());
					System.out.println("for other: " + forOther.size());
				}
			}
		}, new PacketTypeFilter(HistorySyncSet.class));
		ProviderManager.getInstance().addIQProvider("query",
				"http://jlenet.de/histsync", new HistorySyncQueryProvider());
		ProviderManager.getInstance().addIQProvider("hashes",
				"http://jlenet.de/histsync#hashes",
				new HistorySyncResponseProvider());
		ProviderManager.getInstance().addIQProvider("syncSet",
				"http://jlenet.de/histsync#syncSet",
				new HistorySyncSetProvider());
		ServiceDiscoveryManager manager = ServiceDiscoveryManager
				.getInstanceFor(theConnection);
		manager.addFeature("http://jlenet.de/histsync#disco");
	}
	private static void error(Packet packet, Connection theConnection) {
		IQ error = new IQ() {

			@Override
			public String getChildElementXML() {
				return null;
			}

		};
		error.setType(IQ.Type.ERROR);
		error.setError(new XMPPError(Condition.forbidden));
		error.setPacketID(packet.getPacketID());
		error.setTo(packet.getFrom());
		theConnection.sendPacket(error);

	}

}
