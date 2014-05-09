package de.jlenet.desktop.history;

import java.io.File;
import java.io.Reader;

import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParserException;

public class PositionAwareMXParser extends MXParser
		implements
			PositionAwareXMLPullParser {
	int streamStart = 0;
	private File f;
	@Override
	public void setInput(Reader in, int startpos, File f)
			throws XmlPullParserException {
		streamStart = startpos;
		this.f = f;

		super.setInput(in);
	}
	@Override
	public void setInput(Reader in) throws XmlPullParserException {
		setInput(in, 0, null);
	}

	public File getFile() {
		return f;
	}
	@Override
	public int getPosition() {
		return bufAbsoluteStart + pos + streamStart;
	}
	@Override
	public int getBeforePosition() {
		return bufAbsoluteStart + streamStart + bufStart;
	}

}