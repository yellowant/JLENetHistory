package de.jlenet.desktop.history;

import java.util.HashSet;
import java.util.LinkedList;

import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.util.XmppStringUtils;

import de.jlenet.desktop.history.packet.HistorySyncHashes;
import de.jlenet.desktop.history.packet.HistorySyncQuery;
import de.jlenet.desktop.history.packet.HistorySyncSet;
import de.jlenet.desktop.history.packet.HistorySyncUpdateResponse;

public class SyncerService {
	History h;
	XMPPConnection theConnection;
	HashSet<String> str = new HashSet<>();
	private String self;

	public SyncerService(History h, XMPPConnection theConnection) {
		this.h = h;
		this.theConnection = theConnection;
		self = theConnection.getUser();
		theConnection.addAsyncStanzaListener(new StanzaListener() {
			@Override
			public void processPacket(Stanza packet) {
				Presence p = (Presence) packet;
				final String from = p.getFrom();
				if (XmppStringUtils.parseBareJid(from).equals(
						XmppStringUtils.parseBareJid(self))
						&& !from.equals(self)) {
					System.out.println(from);
					System.out.println(((Presence) packet).getType());
					if (((Presence) packet).getType() == Type.available) {
						if (!str.contains(from)) {
							str.add(from); // user is coming online
							new Thread(new Runnable() {

								@Override
								public void run() {
									try {
										sync(from);
									} catch (NotConnectedException e) {
										e.printStackTrace();
									}

								}
							}).start();
						}
					} else if (((Presence) packet).getType() == Type.unavailable) {
						str.remove(from);
					}
				}
			}
		}, new StanzaFilter() {

			@Override
			public boolean accept(Stanza stanza) {
				return stanza instanceof Presence;
			}
		});
	}
	private void sync(String peer) throws NotConnectedException {
		ServiceDiscoveryManager sdm = ServiceDiscoveryManager
				.getInstanceFor(theConnection);
		try {
			System.out.println("sync, but waiting for android to be ready !!!");
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println(peer);
			try {
				DiscoverInfo disco = sdm.discoverInfo(peer);
				if (disco.containsFeature("http://jlenet.de/histsync#disco")) {
					System.out.println("sync starting");
					long currentTimeMillis = System.currentTimeMillis();
					synchronized (h) {
						syncRec(h.getAnyBlock(currentTimeMillis, 0),
								currentTimeMillis / History.BASE, 0, peer);
						h.store();
					}
					System.out.println("sync done");
					return;
				} else {
					System.out.println("not supported!");
					// sync not acceptable
				}
			} catch (NoResponseException e) {
				System.out.println("not supported!");
				// sync not acceptable
			}
		} catch (XMPPException e) {
			e.printStackTrace();
		}

	}
	public void syncToAll(final HistoryEntry hm) {
		final LinkedList<String> cp = new LinkedList<>();
		synchronized (str) {
			cp.addAll(str);
		}
		if (Debug.ENABLED) {
			System.out.println("Syncing with all (=" + cp + ")");
		}

		new Thread() {
			@Override
			public void run() {
				synchronized (h) {
					for (String peer : cp) {
						h.addMessage(hm);
						HistorySyncSet hss = new HistorySyncSet(
								hm.getTime() / History.BASE,
								History.beautifyChecksum(((HistoryLeafNode) h
										.getAnyBlock(hm.getTime(),
												History.LEVELS)).getChecksum()),
								true);
						hss.addMessage(hm);
						hss.setType(org.jivesoftware.smack.packet.IQ.Type.set);
						hss.setTo(peer);
						if (Debug.ENABLED) {
							System.out.println("Sending: " + hss.toXML());
						}
						HistorySyncUpdateResponse response;
						try {
							response = (HistorySyncUpdateResponse) History
									.getResponse(theConnection, hss);
						} catch (NotConnectedException e) {
							System.out
									.println("no sync possible due to disconnect");
							return;
						}
						if (Debug.ENABLED) {
							System.out.println("Quick-Update resulted in: "
									+ response.getStatus());
						}
					}

				}
			}
		}.start();
	}
	private void syncRec(HistoryBlock local, long hour, int level, String peer)
			throws NotConnectedException {
		hour &= ~((1L << History.BITS_PER_LEVEL * (History.LEVELS - level)) - 1);
		// clear irrelevant bits
		if (local instanceof HistoryLeafNode) {
			HistorySyncSet hss = new HistorySyncSet(hour, null, false);
			hss.setType(IQ.Type.set);
			hss.setTo(peer);
			hss.setMessages(((HistoryLeafNode) local).getMessages());
			HistorySyncSet response = (HistorySyncSet) History.getResponse(
					theConnection, hss);
			System.out.println(response.getMessages().size());
			((HistoryLeafNode) local).getMessages().addAll(
					response.getMessages());
			h.modified(hour * History.BASE);
			String localCS = History.beautifyChecksum(local.getChecksum());
			String remoteCS = response.getChecksum();
			if (localCS.equals(remoteCS)) {
				System.out.println("Synchronize succeded: "
						+ ((HistoryLeafNode) local).getMessages().size());
			} else {
				System.out.println(localCS + "<=>" + remoteCS);
				System.out.println("FAIL!!!! FATAL");
			}
			return;
		}
		HistoryTreeBlock htb = (HistoryTreeBlock) local;
		HistorySyncQuery query = new HistorySyncQuery(hour, false, level);
		query.setTo(peer);
		HistorySyncHashes hsh = query.execute(theConnection);
		for (HistorySyncHashes.Hash hash : hsh.getHashes()) {
			String localHash = History.beautifyChecksum(htb.getBlock(
					hash.getId()).getChecksum());
			if (!hash.getHash().equals(localHash)) {
				syncRec(htb.getBlock(hash.getId()), hour + hash.getId()
						* History.getHoursPerBlock(level + 1), level + 1, peer);
			}
		}

	}
}
