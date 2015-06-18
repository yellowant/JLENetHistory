package de.jlenet.desktop.history.packet;

import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.iqrequest.IQRequestHandler;
import org.jivesoftware.smack.packet.EmptyResultIQ;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
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
		theConnection.registerIQRequestHandler(new IQRequestHandler() {

			@Override
			public IQ handleIQRequest(IQ iqRequest) {
				HistorySyncQuery query = (HistorySyncQuery) iqRequest;
				if (query.getType() != IQ.Type.get) {
					throw new Error();
				}
				if (!XmppStringUtils.parseBareJid(iqRequest.getFrom()).equals(
						jid)) {
					return error(query);
				}

				HistorySyncHashes response = query.reply(h);
				return response;
			}

			@Override
			public Mode getMode() {
				return Mode.async;
			}

			@Override
			public Type getType() {
				return Type.get;
			}

			@Override
			public String getElement() {
				return "query";
			}

			@Override
			public String getNamespace() {
				return "http://jlenet.de/histsync";
			}

		});

		// sync service
		theConnection.registerIQRequestHandler(new IQRequestHandler() {
			@Override
			public IQ handleIQRequest(IQ iqRequest) {
				HistorySyncSet sync = (HistorySyncSet) iqRequest;
				if (!XmppStringUtils.parseBareJid(iqRequest.getFrom()).equals(
						jid)) {
					return error(sync);
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
				h.store();

				if (Debug.ENABLED) {
					System.out.println("now Have: " + hln.getMessages().size());
					System.out.println("adding: " + forMe.size());
					System.out.println("for other: " + forOther.size());
				}
				return hss;
			}

			@Override
			public Mode getMode() {
				return Mode.async;
			}

			@Override
			public Type getType() {
				return Type.set;
			}
			@Override
			public String getElement() {
				return "syncSet";
			}

			@Override
			public String getNamespace() {
				return "http://jlenet.de/histsync#syncSet";
			}
		});
		theConnection.registerIQRequestHandler(new IQRequestHandler() {

			@Override
			public IQ handleIQRequest(IQ iqRequest) {
				HistorySyncSet sync = (HistorySyncSet) iqRequest;
				if (!XmppStringUtils.parseBareJid(iqRequest.getFrom()).equals(
						jid)) {
					return error(sync);
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
				if (Debug.ENABLED) {
					System.out.println("now Have: " + hln.getMessages().size());
				}
				return hss;
			}
			@Override
			public Type getType() {
				return Type.set;
			}

			@Override
			public Mode getMode() {
				return Mode.async;
			}
			@Override
			public String getElement() {
				return "syncUpdate";
			}

			@Override
			public String getNamespace() {
				return "http://jlenet.de/histsync#syncUpdate";
			}
		});

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
	private static IQ error(IQ packet) {
		IQ error = new EmptyResultIQ(packet);
		error.setType(IQ.Type.error);
		error.setError(new XMPPError(Condition.forbidden));
		error.setStanzaId(packet.getStanzaId());
		error.setTo(packet.getFrom());
		return error;

	}

}
