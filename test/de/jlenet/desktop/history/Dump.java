package de.jlenet.desktop.history;

import java.io.File;

import org.jivesoftware.smack.packet.Message;

import de.jlenet.desktop.history.payloads.HistoryMessage;
import de.jlenet.desktop.history.payloads.HistoryPayload;

public class Dump {
	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("usage: <dirname>");
			return;
		}
		History h = new History(new File(args[0]));
		for (HistoryEntry string : h.getMessages(null, 0,
				System.currentTimeMillis() + 10000)) {

			System.out.println("Time: " + string.getTime());
			System.out.println("Correspondent: " + string.getCorrespondent());
			System.out.println("Outgoing: " + string.isOutgoing());
			HistoryPayload hp = string.getPayload();
			if (hp instanceof HistoryMessage) {
				HistoryMessage hm = (HistoryMessage) hp;
				Message m = hm.getParsed();
				System.out.println("Message body: " + m.getBody());
			} else {
				System.out.println("Unknown content.");
			}
			System.out.println("---");
		}

	}
}