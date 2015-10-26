package qz.printer.action;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.printer.PrintOptions;

import javax.print.PrintService;
import javax.print.attribute.PrintRequestAttributeSet;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrintPDF extends PrintPostScript implements PrintProcessor {

    private static final Logger log = LoggerFactory.getLogger(PrintPDF.class);

    private static final String JOB_NAME = "QZ-PRINT PDF Printing";

    private List<PDDocument> pdfs;


    public PrintPDF() {
        pdfs = new ArrayList<>();
    }

    @Override
    public void parseData(JSONArray printData) throws JSONException, UnsupportedOperationException {
        for(int i = 0; i < printData.length(); i++) {
            JSONObject data = printData.getJSONObject(i);

            try {
                pdfs.add(PDDocument.load(new URL(data.getString("data")).openStream()));
            }
            catch(IOException e) {
                throw new UnsupportedOperationException(String.format("Cannot parse (%s)%s as a PDF file", data.getString("type"), data.getString("data")), e);
            }
        }

        log.debug("Parsed {} files for printing", pdfs.size());
    }

    @Override
    public void print(PrintService service, PrintOptions options) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(service);
        PageFormat page = job.getPageFormat(null);

        PrintRequestAttributeSet attributes = applyDefaultSettings(options.getPSOptions(), page);
        log.trace("{}", Arrays.toString(attributes.toArray()));

        Book book = new Book();
        for(PDDocument doc : pdfs) {
            book.append(new PDFPrintable(doc), page, doc.getNumberOfPages());
        }

        job.setJobName(JOB_NAME);
        job.setPageable(book);

        for(int i = 0; i < options.getPSOptions().getCopies(); i++) {
            job.print(attributes);
        }

        for(PDDocument doc : pdfs) {
            try { doc.close(); } catch(IOException ignore) {}
        }
    }

}
