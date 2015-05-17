package de.jlenet.desktop.history;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.TransformerHandler;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class BlockOutput {
	OutputStream cos;
	Writer w;
	TransformerHandler h;

	private AttributesImpl a = new AttributesImpl();

	public BlockOutput(File out) throws TransformerException, IOException {
		this.cos = new FileOutputStream(out);
		this.w = new OutputStreamWriter(cos);
		this.h = History.createSax(w);
	}

	public TransformerHandler getHandler() {
		return h;
	}

	public void close() throws IOException {
		cos.close();
	}

	public AttributesImpl getA() {
		return a;
	}

	public void flush() throws SAXException, IOException {
		h.characters(new char[]{' '}, 0, 1);
		w.flush();
	}
}