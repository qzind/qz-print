package qz.printer.action;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.printer.PrintOptions;

import javax.print.PrintService;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;

public class PrintPDF implements PrintProcessor, Printable {

    private static final Logger log = LoggerFactory.getLogger(PrintPDF.class);

    @Override
    public void parseData(JSONArray printData) throws JSONException, UnsupportedOperationException {
        //TODO
    }

    @Override
    public void print(PrintService service, PrintOptions options) throws PrinterException {
        //TODO
    }


    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (graphics == null) { throw new PrinterException("No graphics specified"); }
        if (pageFormat == null) { throw new PrinterException("No page format specified"); }


        return NO_SUCH_PAGE;

        /*
        //Suppressing unused parameter warning. Need to see if it should actually be passed/used or not.
        //TOASK: Should we use pageFormat when printing PDFs?
        PDFFile pdf = getPDFFile();

        int currentPage = pageIndex + 1;

        if (pdf == null) {
            throw new PrinterException("No PDF data specified");
        }

        if (currentPage < 1 || currentPage > pdf.getNumPages()) {
            return NO_SUCH_PAGE;

        }

        // fit the PDFPage into the printing area
        Graphics2D graphics2D = (Graphics2D)graphics;
        PDFPage page = pdf.getPage(currentPage);


        // render the page
        //Rectangle imgbounds = new Rectangle(currentPage, currentPage)
        PDFRenderer pgs = new PDFRenderer(page, graphics2D, page.getPageBox().getBounds(), page.getBBox(), null);
        //PDFRenderer pgs = new PDFRenderer(page, graphics2D, getImageableRectangle(pageFormat), page.getBBox(), null);
        try {
            page.waitForFinish();

            pgs.run();
        }
        catch(InterruptedException ignore) {
        }

        return PAGE_EXISTS;

        // TODO: Proper resizing code... needs work
        */
    }

}
