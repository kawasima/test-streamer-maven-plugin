package net.unit8.maven.plugins;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author kawasima
 */
public class ReportHandler extends DefaultHandler {
    private boolean inTestSuite =false;
    private File reportsDirectory;
    private StringBuilder outputXml;
    private String suiteName;
    private SummaryReport summaryReport;

    public ReportHandler(File reportsDirectory) {
        this.reportsDirectory = reportsDirectory;
        summaryReport = new SummaryReport();
    }

    private int getIntValue(Attributes attributes, String qName) {
        String val = attributes.getValue(qName);
        if (val == null)
            return 0;
        else
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ex) {
                return 0;
            }
    }

    private float getFloatValue(Attributes attributes, String qName) {
        String val = attributes.getValue(qName);
        if (val == null)
            return 0.0f;
        else
            try {
                return Float.parseFloat(val);
            } catch (NumberFormatException ex) {
                return 0.0f;
            }
    }

    public SummaryReport getSummaryReport() {
        return summaryReport;
    }
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if(qName.equals("testsuite")) {
            inTestSuite = true;
            suiteName = attributes.getValue("name");
            outputXml = new StringBuilder(4096);
            outputXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
            summaryReport.setTests(summaryReport.getTests() + getIntValue(attributes, "tests"));
            summaryReport.setFailures(summaryReport.getFailures() + getIntValue(attributes, "failures"));
            summaryReport.setErrors(summaryReport.getErrors() + getIntValue(attributes, "errors"));
            summaryReport.setSkipped(summaryReport.getSkipped() + getIntValue(attributes, "skipped"));
            summaryReport.setElapsedTime(summaryReport.getElapsedTime() + getFloatValue(attributes, "time"));
        }

        if (inTestSuite) {
            outputXml.append("<").append(qName);
            for (int i=0; i<attributes.getLength(); i++) {
                outputXml.append(" ")
                        .append(attributes.getQName(i))
                        .append("=\"")
                        .append(attributes.getValue(i))
                        .append("\"");
            }
            outputXml.append(">");
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (inTestSuite) {
            if (qName.equals("testsuite")) {
                outputXml.append("</testsuite>");
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(new File(reportsDirectory, "TEST-" + suiteName + ".xml"));
                    fos.write(outputXml.toString().getBytes("UTF-8"));
                } catch (IOException ex) {
                    throw new IORuntimeException(ex);
                } finally {
                    if (fos != null)
                        try { fos.close(); } catch (IOException ignore) {}
                }

                inTestSuite = false;
            } else {
                outputXml.append("</").append(qName).append(">");
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        outputXml.append(new String(ch, start, length));
    }
}
