package net.unit8.teststreamer.mojo;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * Writes reports to the given directory.
 *
 * @author kawasima
 */
public class ReportWriteTask implements Callable {
    private InputStream in;
    private File reportsDirectory;

    public ReportWriteTask(InputStream in, File reportsDirectory) {
        this.in = in;
        this.reportsDirectory = reportsDirectory;
    }

    @Override
    public SummaryReport call() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            SAXParser parser = factory.newSAXParser();
            ReportHandler handler = new ReportHandler(reportsDirectory);
            parser.parse(in, handler);
            return handler.getSummaryReport();
        } catch (Exception e) {
            return null;
        }
    }
}
