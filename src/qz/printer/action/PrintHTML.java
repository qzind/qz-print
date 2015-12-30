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
import qz.printer.PrintOptions;
import qz.utils.PrintingUtilities;

import java.awt.print.Printable;
import java.io.IOException;

public class PrintHTML extends PrintImage implements PrintProcessor, Printable {

    private static final Logger log = LoggerFactory.getLogger(PrintHTML.class);


    public PrintHTML() {
        super();
    }


    @Override
    public void parseData(JSONArray printData, PrintOptions options) throws JSONException, UnsupportedOperationException {
        try {
            WebApp.initialize();

            for(int i = 0; i < printData.length(); i++) {
                JSONObject data = printData.getJSONObject(i);
                String source = data.getString("data");

                PrintingUtilities.Format format = PrintingUtilities.Format.valueOf(data.optString("format", "AUTO").toUpperCase());
                boolean fromFile = (format == PrintingUtilities.Format.FILE) || (format == PrintingUtilities.Format.AUTO && source.startsWith("http"));

                double pageWidth = WebApp.DEFAULT_WIDTH;
                double pageZoom = 1.0;
                JSONObject option = data.optJSONObject("options");
                if (option != null) {
                    pageWidth = option.optDouble("pageWidth", WebApp.DEFAULT_WIDTH);
                    pageZoom = option.optDouble("pageZoom", 1.0);
                }

                try {
                    images.add(WebApp.capture(source, fromFile, pageWidth, pageZoom));
                }
                catch(IOException e) {
                    //JavaFX image loader becomes null if webView is too large, throwing an IllegalArgumentException on screen capture attempt
                    if (e.getCause() != null && e.getCause() instanceof IllegalArgumentException) {
                        throw new UnsupportedOperationException("Image or Density is too large for HTML printing", e);
                    }

                    throw new UnsupportedOperationException(String.format("Cannot parse (%s)%s as HTML", format, source), e);
                }
            }

            log.debug("Parsed {} html records", images.size());
        }
        catch(NoClassDefFoundError e) {
            throw new UnsupportedOperationException("JavaFX libraries not found", e);
        }
    }

}
