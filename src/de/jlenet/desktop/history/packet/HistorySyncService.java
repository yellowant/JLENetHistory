package de.jlenet.desktop.history.packet;

import java.util.Arrays;
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

import de.jlenet.desktop.history.Debug;
import de.jlenet.desktop.history.History;
import de.jlenet.desktop.history.HistoryEntry;
import de.jlenet.desktop.history.HistoryLeafNode;

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

		// sync service
		theConnection.addPacketListener(new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				if (!StringUtils.parseBareAddress(packet.getFrom()).equals(jid)) {
					error(packet, theConnection);
					return;
				}

				HistorySyncSet sync = (HistorySyncSet) packet;
				if (sync.isUpdate()) {
					return;
				}
				if (Debug.ENABLED) {
					System.out.println("sync pack");
				}
				if (sync.getType() != IQ.Type.SET) {
					return;
				}
				HistoryLeafNode hln = (HistoryLeafNode) h.getAnyBlock(
						sync.getHour() * History.BASE, History.LEVELS);
				if (Debug.ENABLED) {
					System.out.println("Have: " + hln.getMessages().size());
					System.out.println("Got: " + sync.getMessages().size());
				}
				TreeSet<HistoryEntry> forMe = new TreeSet<HistoryEntry>();
				TreeSet<HistoryEntry> forOther = new TreeSet<HistoryEntry>();
				Iterator<HistoryEntry> have = hln.getMessages().iterator();
				Iterator<HistoryEntry> got = sync.getMessages().iterator();
				HistoryEntry currentGot = null;
				HistoryEntry currentHave = null;
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
				h.store();

				if (Debug.ENABLED) {
					System.out.println("now Have: " + hln.getMessages().size());
					System.out.println("adding: " + forMe.size());
					System.out.println("for other: " + forOther.size());
				}
			}
		}, new PacketTypeFilter(HistorySyncSet.class));
		theConnection.addPacketListener(new PacketListener() {

			@Override
			public void processPacket(Packet packet) {
				if (!StringUtils.parseBareAddress(packet.getFrom()).equals(jid)) {
					error(packet, theConnection);
					return;
				}

				HistorySyncSet sync = (HistorySyncSet) packet;
				if (!sync.isUpdate()) {
					return;
				}
				if (sync.getType() != IQ.Type.SET) {
					return;
				}
				HistoryLeafNode hln = (HistoryLeafNode) h.getAnyBlock(
						sync.getHour() * History.BASE, History.LEVELS);
				if (Debug.ENABLED) {
					System.out.println("Have: " + hln.getMessages().size());
					System.out.println("Got: " + sync.getMessages().size());
				}

				hln.getMessages().addAll(sync.getMessages());
				h.modified(sync.getHour() * History.BASE);
				h.store();

				byte[] myChecksum = hln.getChecksum();
				String status;
				if (!Arrays.equals(myChecksum,
						History.parseChecksum(sync.getChecksum()))) {
					status = "success";
				} else {
					status = "mismatch";
				}
				HistorySyncUpdateResponse hss = new HistorySyncUpdateResponse(
						status);
				hss.setType(IQ.Type.RESULT);
				hss.setTo(sync.getFrom());
				hss.setPacketID(sync.getPacketID());
				if (Debug.ENABLED) {
					System.out.println("update was: " + status);
				}
				theConnection.sendPacket(hss);

				if (Debug.ENABLED) {
					System.out.println("now Have: " + hln.getMessages().size());
				}
			}
		}, new PacketTypeFilter(HistorySyncSet.class));
		ProviderManager.getInstance().addIQProvider("query",
				"http://jlenet.de/histsync", new HistorySyncQueryProvider());
		ProviderManager.getInstance().addIQProvider("hashes",
				"http://jlenet.de/histsync#hashes",
				new HistorySyncResponseProvider());

		HistorySyncSetProvider setProvider = new HistorySyncSetProvider();
		ProviderManager.getInstance().addIQProvider("syncSet",
				"http://jlenet.de/histsync#syncSet", setProvider);
		ProviderManager.getInstance().addIQProvider("syncUpdate",
				"http://jlenet.de/histsync#syncUpdate", setProvider);
		ProviderManager.getInstance().addIQProvider("syncStatus",
				"http://jlenet.de/histsync#syncStatus", setProvider);
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
