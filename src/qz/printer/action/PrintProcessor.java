package qz.printer.action;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import qz.printer.PrintOptions;

import javax.print.PrintException;
import javax.print.PrintService;
import java.awt.print.PrinterException;

public interface PrintProcessor {


    /**
     * Used to parse information passed from the web API for printing.
     *
     * @param printData JSON Array of printer data
     */
    void parseData(JSONArray printData) throws JSONException, UnsupportedOperationException;


    /**
     * Used to setup and send documents to the specified printing {@code service}.
     *
     * @param service PrintService to use for printing
     * @param options Printing options to use for the print job
     */
    void print(PrintService service, PrintOptions options) throws PrintException, PrinterException;


}
