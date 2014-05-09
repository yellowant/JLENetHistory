package de.jlenet.desktop.history;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public interface PositionAwareXMLPullParser extends XmlPullParser {
	public File getFile();

	public void setInput(Reader in, int startpos, File f)
			throws XmlPullParserException;

	public int getPosition();
	public int getBeforePosition();
	public void skipSubTree() throws XmlPullParserException, IOException;
}
