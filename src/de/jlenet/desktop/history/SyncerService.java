package de.jlenet.desktop.history;

import java.util.HashSet;
import java.util.LinkedList;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Type;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;

import de.jlenet.desktop.history.packet.HistorySyncHashes;
import de.jlenet.desktop.history.packet.HistorySyncQuery;
import de.jlenet.desktop.history.packet.HistorySyncSet;
import de.jlenet.desktop.history.packet.HistorySyncUpdateResponse;

public class SyncerService {
	History h;
	Connection theConnection;
	HashSet<String> str = new HashSet<>();
	private String self;

	public SyncerService(History h, Connection theConnection) {
		this.h = h;
		this.theConnection = theConnection;
		self = theConnection.getUser();
		theConnection.addPacketListener(new PacketListener() {
			@Override
			public void processPacket(Packet packet) {
				Presence p = (Presence) packet;
				final String from = p.getFrom();
				if (StringUtils.parseBareAddress(from).equals(
						StringUtils.parseBareAddress(self))
						&& !from.equals(self)) {
					System.out.println(from);
					System.out.println(((Presence) packet).getType());
					if (((Presence) packet).getType() == Type.available) {
						if (!str.contains(from)) {
							str.add(from); // user is coming online
							new Thread(new Runnable() {

								@Override
								public void run() {
									//sync(from);

								}
							}).start();
						}
					} else if (((Presence) packet).getType() == Type.unavailable) {
						str.remove(from);
					}
				}
			}
		}, new PacketFilter() {

			@Override
			public boolean accept(Packet packet) {
				return packet instanceof Presence;
			}
		});
	}
	private void sync(String peer) {
		ServiceDiscoveryManager sdm = ServiceDiscoveryManager
				.getInstanceFor(theConnection);
		try {
			System.out.println("sync, but waiting for android to be ready !!!");
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println(peer);
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
						HistorySyncSet hss = new HistorySyncSet(hm.getTime()
								/ History.BASE,
								History.beautifyChecksum(((HistoryLeafNode) h
										.getAnyBlock(hm.getTime(),
												History.LEVELS)).getChecksum()));
						hss.addMessage(hm);
						hss.setType(org.jivesoftware.smack.packet.IQ.Type.SET);
						hss.setUpdate(true);
						hss.setTo(peer);
						if (Debug.ENABLED) {
							System.out.println("Sending: " + hss.toXML());
						}
						HistorySyncUpdateResponse response = (HistorySyncUpdateResponse) History
								.getResponse(theConnection, hss);
						if (Debug.ENABLED) {
							System.out.println("Quick-Update resulted in: "
									+ response.getStatus());
						}
					}

				}
			}
		}.start();
	}
	private void syncRec(HistoryBlock local, long hour, int level, String peer) {
		hour &= ~((1L << History.BITS_PER_LEVEL * (History.LEVELS - level)) - 1);
		// clear irrelevant bits
		if (local instanceof HistoryLeafNode) {
			HistorySyncSet hss = new HistorySyncSet(hour, null);
			hss.setType(IQ.Type.SET);
			hss.setTo(peer);
			hss.setMessages(((HistoryLeafNode) local).getMessages());
			HistorySyncSet response = (HistorySyncSet) History.getResponse(
					theConnection, hss);
			System.out.println(response == null);
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
