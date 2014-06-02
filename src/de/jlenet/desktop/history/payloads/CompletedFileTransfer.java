package de.jlenet.desktop.history.payloads;

import org.jivesoftware.smack.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;

import de.jlenet.desktop.history.History;

public class CompletedFileTransfer extends HistoryPayload {
	// <file filename="name" succeded="y" filesize="" targetPath=""
	// targetResource="" targetChecksum="">,
	String filename;
	boolean succeeded;
	long filesize;
	String targetPath;
	String targetResource;
	byte[] targetChecksum;

	public CompletedFileTransfer(String filename, boolean succeeded,
			long filesize, String targetPath, String targetResource,
			byte[] targetChecksum) {
		this.filename = filename;
		this.succeeded = succeeded;
		this.filesize = filesize;
		this.targetPath = targetPath;
		this.targetResource = targetResource;
		this.targetChecksum = targetChecksum;
	}
	public CompletedFileTransfer(XmlPullParser xpp) {
		filename = xpp.getAttributeValue(null, "filename");
		succeeded = xpp.getAttributeValue(null, "succeeded").equals("y");
		filesize = Long.parseLong(xpp.getAttributeValue(null, "filesize"));
		targetPath = xpp.getAttributeValue(null, "targetPath");
		targetResource = xpp.getAttributeValue(null, "targetResource");
		targetChecksum = History.parseChecksum(xpp.getAttributeValue(null,
				"targetChecksum"));
	}
	public String getFilename() {
		return filename;
	}
	public long getFilesize() {
		return filesize;
	}
	public byte[] getTargetChecksum() {
		return targetChecksum;
	}
	public String getTargetPath() {
		return targetPath;
	}
	public String getTargetResource() {
		return targetResource;
	}

	@Override
	public String toXML() {
		return "<file filename=\"" + StringUtils.escapeForXML(filename)
				+ "\" succeeded=\"" + (succeeded ? "y" : "n")
				+ "\" filesize=\"" + filesize + "\" target=\""
				+ StringUtils.escapeForXML(targetPath) + "\" targetResource=\""
				+ StringUtils.escapeForXML(targetResource)
				+ "\" targetChecksum=\""
				+ History.beautifyChecksum(targetChecksum) + "\"/>";
	}
}
