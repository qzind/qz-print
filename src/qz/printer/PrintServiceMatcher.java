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

public class PrintServiceMatcher {

    private static final Logger log = LoggerFactory.getLogger(PrintServiceMatcher.class);

    public static PrintService[] getPrintServices() {
        PrintService[] printers = PrintServiceLookup.lookupPrintServices(null, null);
        log.debug("Found {} printers", printers.length);

        return printers;
    }

    /**
     * Finds {@code PrintService} by looking at any matches to {@code printerSearch}.
     *
     * @param printerSearch Search query to compare against service names.
     */
    public static PrintService matchService(String printerSearch) {
        PrintService exact = null;
        PrintService begins = null;
        PrintService partial = null;

        log.debug("Searching for PrintService matching {}", printerSearch);
        printerSearch = printerSearch.toLowerCase();

        // Search services for matches
        PrintService[] printers = getPrintServices();
        for(PrintService ps : printers) {
            String printerName = ps.getName().toLowerCase();

            if (printerName.equals(printerSearch)) {
                exact = ps;
            }
            if (printerName.startsWith(printerSearch)) {
                begins = ps;
            }
            if (printerName.contains(printerSearch)) {
                partial = ps;
            }
        }

        // Return closest match
        PrintService use = null;
        if (exact != null) {
            use = exact;
        } else if (begins != null) {
            use = begins;
        } else if (partial != null) {
            use = partial;
        }

        if (use != null) {
            log.debug("Found match: {}", use.getName());
        } else {
            log.warn("Printer not found: {}", printerSearch);
        }

        return use;
    }


    public static JSONArray getPrintersJSON() throws JSONException {
        JSONArray list = new JSONArray();

        PrintService[] printers = getPrintServices();
        for(PrintService ps : printers) {
            list.put(ps.getName());
        }

        return list;
    }

    public static String getPrinterJSON(String query) throws JSONException {
        PrintService service = PrintServiceMatcher.matchService(query);

        if (service != null) {
            return service.getName();
        } else {
            return null;
        }
    }

}
