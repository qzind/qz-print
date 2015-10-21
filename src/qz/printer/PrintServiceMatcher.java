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

package qz.printer;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.PrinterName;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrintServiceMatcher {

    private static final Logger log = LoggerFactory.getLogger(PrintServiceMatcher.class);

    private static PrintService[] printers = null;


    public static PrintService[] findPrinters() {
        return findPrinters(false);
    }

    public static PrintService[] findPrinters(boolean forceSearch) {
        if (forceSearch || printers == null || printers.length == 0) {
            printers = PrintServiceLookup.lookupPrintServices(null, null);
            log.info("Found {} printers", printers.length);
        }

        return printers;
    }

    /**
     * Finds a printer in the PrintServices listing and returns it's respective PrintService.
     * <p>
     * If a PrintService is supplied, the same PrintService is returned.
     * If an Object is supplied, it calls the "toString()" method and
     * then does a standard name search.
     * </p>
     *
     * @param printerNameObject The object holding the name of the printer to search for.
     * @return PrintService ps for RawPrint(ps, cmds)
     */
    public static PrintService findPrinter(Object printerNameObject) {
        PrintService exact = null;
        PrintService begins = null;
        PrintService partial = null;

        String printerName;
        if (printerNameObject == null) {
            return null;
        } else if (printerNameObject instanceof String) {
            printerName = "\\Q" + printerNameObject + "\\E";
        } else if (printerNameObject instanceof PrintService) {
            return (PrintService)printerNameObject;
        } else {
            printerName = "\\Q" + printerNameObject.toString() + "\\E";
        }

        // Get print service list
        findPrinters();

        log.debug("Printer specified: {}", printerName);

        Pattern patternExact = Pattern.compile("\\b" + printerName + "\\b", Pattern.CASE_INSENSITIVE);
        Pattern patternStart = Pattern.compile("\\b" + printerName, Pattern.CASE_INSENSITIVE);
        Pattern patternPartial = Pattern.compile(printerName, Pattern.CASE_INSENSITIVE);

        // Search for best match by name
        for(PrintService ps : printers) {
            String sysPrinter = ps.getAttribute(PrinterName.class).getValue();

            Matcher matchExact = patternExact.matcher(sysPrinter);
            Matcher matchStart = patternStart.matcher(sysPrinter);
            Matcher matchPartial = patternPartial.matcher(sysPrinter);

            if (matchExact.find()) {
                exact = ps;
                log.trace("Printer name match: {}", sysPrinter);
            } else if (matchStart.find()) {
                begins = ps;
                log.trace("Printer name beginning match: {}", sysPrinter);
            } else if (matchPartial.find()) {
                partial = ps;
                log.trace("Printer name partial match: {}", sysPrinter);
            }
        }

        // Return closest match
        if (exact != null) {
            log.info("Using best match: {}", exact.getName());
            return exact;
        } else if (begins != null) {
            log.info("Using best match: {}", begins.getName());
            return begins;
        } else if (partial != null) {
            log.info("Using best match: {}", partial.getName());
            return partial;
        }

        // Couldn't find printer
        log.warn("Printer not found: {}", printerName);
        return null;
    }


    public static JSONArray getPrintersJSON() throws JSONException {
        JSONArray list = new JSONArray();

        findPrinters(false);
        for(PrintService printService : printers) {
            list.put(printService.getName());
        }

        return list;
    }

    public static String getPrinterJSON(String query) throws JSONException {
        PrintService service = PrintServiceMatcher.findPrinter(query);
        String printerName = null;
        if (service != null) { printerName = service.getName(); }

        return printerName;
    }

}
