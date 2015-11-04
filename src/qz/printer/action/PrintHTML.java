/**
 * @author Tres Finocchiaro
 *
 * Copyright (C) 2013 Tres Finocchiaro, QZ Industries
 *
 * IMPORTANT:  This software is dual-licensed
 *
 * LGPL 2.1
 * This is free software.  This software and source code are released under
 * the "LGPL 2.1 License".  A copy of this license should be distributed with
 * this software. http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * QZ INDUSTRIES SOURCE CODE LICENSE
 * This software and source code *may* instead be distributed under the
 * "QZ Industries Source Code License", available by request ONLY.  If source
 * code for this project is to be made proprietary for an individual and/or a
 * commercial entity, written permission via a copy of the "QZ Industries Source
 * Code License" must be obtained first.  If you've obtained a copy of the
 * proprietary license, the terms and conditions of the license apply only to
 * the licensee identified in the agreement.  Only THEN may the LGPL 2.1 license
 * be voided.
 *
 */

package qz.printer.action;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.common.Constants;
import qz.printer.PrintOptions;
import qz.utils.PrintingUtilities;

import javax.print.PrintService;
import javax.print.attribute.PrintRequestAttributeSet;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrintHTML extends PrintPostScript implements PrintProcessor, Printable {

    private static final Logger log = LoggerFactory.getLogger(PrintHTML.class);

    private List<BufferedImage> snapshots;


    public PrintHTML() {
        snapshots = new ArrayList<>();
    }


    @Override
    public void parseData(JSONArray printData) throws JSONException, UnsupportedOperationException {
        WebApp.initialize();

        for(int i = 0; i < printData.length(); i++) {
            JSONObject data = printData.getJSONObject(i);
            String source = data.getString("data");

            PrintingUtilities.Format format = PrintingUtilities.Format.valueOf(data.optString("format", "AUTO").toUpperCase());
            boolean fromFile = (format == PrintingUtilities.Format.FILE) || (format == PrintingUtilities.Format.AUTO && source.startsWith("http"));

            double pageWidth = WebApp.DEFAULT_WIDTH;
            JSONObject option = data.optJSONObject("options");
            if (option != null) { pageWidth = option.optDouble("pageWidth", WebApp.DEFAULT_WIDTH); }

            try {
                snapshots.add(WebApp.capture(source, fromFile, pageWidth));
            }
            catch(IOException e) {
                throw new UnsupportedOperationException(String.format("Cannot parse (%s)%s as HTML", data.getString("format"), source), e);
            }
        }

        log.debug("Parsed {} html records", snapshots.size());
    }

    @Override
    public void print(PrintService service, PrintOptions options) throws PrinterException {
        if (snapshots.isEmpty()) {
            log.warn("Nothing to print");
            return;
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(service);
        PageFormat page = job.getPageFormat(null);

        PrintRequestAttributeSet attributes = applyDefaultSettings(options.getPSOptions(), page);

        job.setJobName(Constants.HTML_PRINT);
        job.setPrintable(this, page);

        log.info("Starting html printing ({} copies)", options.getPSOptions().getCopies());
        for(int i = 0; i < options.getPSOptions().getCopies(); i++) {
            job.print(attributes);
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (graphics == null) { throw new PrinterException("No graphics specified"); }
        if (pageFormat == null) { throw new PrinterException("No page format specified"); }

        if (pageIndex + 1 > snapshots.size()) {
            return NO_SUCH_PAGE;
        }
        log.trace("Requested page {} for printing", pageIndex);


        BufferedImage imgToPrint = snapshots.get(pageIndex);
        imgToPrint = fixColorModel(imgToPrint);

        Graphics2D graphics2D = (Graphics2D)graphics;
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        log.trace("{}", graphics2D.getRenderingHints());

        // apply image scaling
        double boundW = pageFormat.getImageableWidth();
        double boundH = pageFormat.getImageableHeight();

        int imgW;
        int imgH;

        // scale image to smallest edge, keeping size ratio
        if ((imgToPrint.getWidth() / imgToPrint.getHeight()) < (boundW / boundH)) {
            imgW = (int)(imgToPrint.getWidth() / (imgToPrint.getHeight() / boundH));
            imgH = (int)boundH;
        } else {
            imgW = (int)boundW;
            imgH = (int)(imgToPrint.getHeight() / (imgToPrint.getWidth() / boundW));
        }

        double boundX = pageFormat.getImageableX();
        double boundY = pageFormat.getImageableY();
        log.debug("Paper area: {},{}:{},{}", (int)boundX, (int)boundY, (int)boundW, (int)boundH);

        // Now we perform our rendering
        graphics2D.drawImage(imgToPrint, (int)boundX + imgToPrint.getMinX(), (int)boundY + imgToPrint.getMinY(), (int)boundX + imgW, (int)boundY + imgH,
                             imgToPrint.getMinX(), imgToPrint.getMinY(), imgToPrint.getWidth(), imgToPrint.getHeight(), null);

        // Valid page
        return PAGE_EXISTS;
    }

}
