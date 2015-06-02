package de.jlenet.desktop.history.packet;

import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.EmptyResultIQ;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jxmpp.util.XmppStringUtils;

import de.jlenet.desktop.history.Debug;
import de.jlenet.desktop.history.History;
import de.jlenet.desktop.history.HistoryEntry;
import de.jlenet.desktop.history.HistoryLeafNode;

public class HistorySyncService {

	public static void initialize(final XMPPConnection theConnection,
			final History h) {
		final String jid = XmppStringUtils
				.parseBareJid(theConnection.getUser());
		// Hash fetch service
		theConnection.addAsyncStanzaListener(new StanzaListener() {

			@Override
			public void processPacket(Stanza packet) {
				try {
					HistorySyncQuery query = (HistorySyncQuery) packet;
					if (query.getType() != IQ.Type.get) {
						return;
					}
					if (!XmppStringUtils.parseBareJid(packet.getFrom()).equals(
							jid)) {
						error(query, theConnection);
						return;
					}

					HistorySyncHashes response = query.reply(h);
					theConnection.sendStanza(response);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}

		}, new StanzaTypeFilter(HistorySyncQuery.class));

		// sync service
		theConnection.addAsyncStanzaListener(new StanzaListener() {

			@Override
			public void processPacket(Stanza packet)
					throws NotConnectedException {
				HistorySyncSet sync = (HistorySyncSet) packet;
				if (sync.getType() != IQ.Type.set) {
					return;
				}
				if (!XmppStringUtils.parseBareJid(packet.getFrom()).equals(jid)) {
					error(sync, theConnection);
					return;
				}

				if (sync.isUpdate()) {
					return;
				}
				if (Debug.ENABLED) {
					System.out.println("sync pack");
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
						.beautifyChecksum(hln.getChecksum()), false);
				hss.setMessages(forOther);
				hss.setType(IQ.Type.result);
				hss.setTo(sync.getFrom());
				hss.setStanzaId(sync.getStanzaId());
				theConnection.sendStanza(hss);
				h.store();

				if (Debug.ENABLED) {
					System.out.println("now Have: " + hln.getMessages().size());
					System.out.println("adding: " + forMe.size());
					System.out.println("for other: " + forOther.size());
				}
			}
		}, new StanzaTypeFilter(HistorySyncSet.class));
		theConnection.addAsyncStanzaListener(new StanzaListener() {

			@Override
			public void processPacket(Stanza packet)
					throws NotConnectedException {
				HistorySyncSet sync = (HistorySyncSet) packet;
				if (sync.getType() != IQ.Type.set) {
					return;
				}
				if (!XmppStringUtils.parseBareJid(packet.getFrom()).equals(jid)) {
					error(sync, theConnection);
					return;
				}

				if (!sync.isUpdate()) {
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
				hss.setType(IQ.Type.result);
				hss.setTo(sync.getFrom());
				hss.setStanzaId(sync.getStanzaId());
				if (Debug.ENABLED) {
					System.out.println("update was: " + status);
				}
				theConnection.sendStanza(hss);

				if (Debug.ENABLED) {
					System.out.println("now Have: " + hln.getMessages().size());
				}
			}
		}, new StanzaTypeFilter(HistorySyncSet.class));
		ProviderManager.addIQProvider("query", "http://jlenet.de/histsync",
				new HistorySyncQueryProvider());
		ProviderManager.addIQProvider("hashes",
				"http://jlenet.de/histsync#hashes",
				new HistorySyncResponseProvider());

		HistorySyncSetProvider setProvider = new HistorySyncSetProvider();
		ProviderManager.addIQProvider("syncSet",
				"http://jlenet.de/histsync#syncSet", setProvider);
		ProviderManager.addIQProvider("syncUpdate",
				"http://jlenet.de/histsync#syncUpdate", setProvider);
		ProviderManager.addIQProvider("syncStatus",
				"http://jlenet.de/histsync#syncStatus",
				new HistorySyncUpdateResponse.Provider());

		ServiceDiscoveryManager manager = ServiceDiscoveryManager
				.getInstanceFor(theConnection);
		manager.addFeature("http://jlenet.de/histsync#disco");
	}
	private static void error(IQ packet, XMPPConnection theConnection)
			throws NotConnectedException {
		IQ error = new EmptyResultIQ(packet);
		error.setType(IQ.Type.error);
		error.setError(new XMPPError(Condition.forbidden));
		error.setStanzaId(packet.getStanzaId());
		error.setTo(packet.getFrom());
		theConnection.sendStanza(error);

	}

}
