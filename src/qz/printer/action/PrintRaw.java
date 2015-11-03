/**
 * @author Tres Finocchiaro
 *
 * Copyright (C) 2013 Tres Finocchiaro, QZ Industries
 *
 * IMPORTANT: This software is dual-licensed
 *
 * LGPL 2.1 This is free software. This software and source code are released
 * under the "LGPL 2.1 License". A copy of this license should be distributed
 * with this software. http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * QZ INDUSTRIES SOURCE CODE LICENSE This software and source code *may* instead
 * be distributed under the "QZ Industries Source Code License", available by
 * request ONLY. If source code for this project is to be made proprietary for
 * an individual and/or a commercial entity, written permission via a copy of
 * the "QZ Industries Source Code License" must be obtained first. If you've
 * obtained a copy of the proprietary license, the terms and conditions of the
 * license apply only to the licensee identified in the agreement. Only THEN may
 * the LGPL 2.1 license be voided.
 *
 */
package qz.printer.action;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.common.ByteArrayBuilder;
import qz.common.LogIt;
import qz.exception.InvalidFileTypeException;
import qz.exception.NullCommandException;
import qz.exception.NullPrintServiceException;
import qz.printer.PrintOptions;
import qz.utils.ByteUtilities;
import qz.utils.FileUtilities;

import javax.print.*;
import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;
import java.io.*;
import java.net.Socket;
import java.util.Locale;


/**
 * Sends raw data to the printer, overriding your operating system's print
 * driver. Most useful for printers such as zebra card or barcode printers.
 *
 * @author A. Tres Finocchiaro
 */
public class PrintRaw implements PrintProcessor {

    private static final Logger log = LoggerFactory.getLogger(PrintRaw.class);

    private ByteArrayBuilder commands;

    //private DocFlavor docFlavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
    //private DocAttributeSet docAttr;

    //private PrintRequestAttributeSet reqAttr = new HashPrintRequestAttributeSet();
    //private Boolean isFinished = false;

    public PrintRaw() {
        commands = new ByteArrayBuilder();
    }


    public void parseData(JSONArray printData) throws JSONException, UnsupportedOperationException {
        //TODO
    }

    public void print(PrintService service, PrintOptions options) {
        //TODO
    }


    /**
     * A brute-force, however surprisingly elegant way to send a file to a networked
     * printer. The socket host can be an IP Address or Host Name.  The port
     * 9100 is a standard HP/JetDirect and may work well.
     * <p/>
     * Please note that this will completely bypass the Print Spooler, so the
     * Operating System will have absolutely no printer information.  This is
     * printing "blind".
     */
    private boolean printToSocket() throws IOException {
        /*
        log.info("Printing to host " + socketHost + ":" + socketPort);
        Socket socket = null;
        DataOutputStream out = null;
        try {
            socket = new Socket(socketHost, socketPort);
            out = new DataOutputStream(socket.getOutputStream());
            out.write(getCommands().getByteArray());
        }
        finally {
            if (out != null) {
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
            socketHost = null;
            socketPort = null;
        }
        */
        return true;
    }

    public boolean printToFile() throws PrintException, IOException {
        /*
        log.info("Printing to file: " + outputPath);
        OutputStream out = new FileOutputStream(outputPath);
        out.write(getCommands().getByteArray());
        out.close();
        */
        return true;
    }

    /**
     * Constructs a
     * <code>javax.print.SimpleDoc</code> with the previously defined byte
     * array.
     *
     * @return True if print job created successfully
     */
    public boolean print() throws IOException, InterruptedException, PrintException {
        return print(null);
    }

    /**
     * Constructs a
     * <code>javax.print.SimpleDoc</code> with the previously defined byte
     * array. If and
     * <code>offset</code> and
     * <code>end</code> are specified, prints the subarray of the original byte
     * array.
     *
     * @param data Data to print
     * @return boolean indicating success
     */
    public boolean print(byte[] data) throws IOException, PrintException, InterruptedException {
        /*
        if (printService == null) {
            throw new NullPrintServiceException("qz.printer.action.PrintRaw.print() failed, no print service.");
        } else if (commands == null) {
            throw new NullCommandException("qz.printer.action.PrintRaw.print() failed, no commands.");
        } else if (outputPath != null) {
            return printToFile();
        } else if (socketHost != null) {
            return printToSocket();
        } else if (alternatePrint) {
            return alternatePrint();
        }

        SimpleDoc doc;
        if (data != null) {
            doc = new SimpleDoc(data, docFlavor, docAttr);
        } else {
            doc = new SimpleDoc(getCommands().getByteArray(), docFlavor, docAttr);
        }

        reqAttr.add(new JobName(JOB_NAME, Locale.getDefault()));
        DocPrintJob printJob = printService.createPrintJob();
        printJob.addPrintJobListener(new PrintJobListener() {
            //@Override //JDK 1.6
            public void printDataTransferCompleted(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished = true;
            }

            //@Override //JDK 1.6
            public void printJobCompleted(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished = true;
            }

            //@Override //JDK 1.6
            public void printJobFailed(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished = true;
            }

            //@Override //JDK 1.6
            public void printJobCanceled(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished = true;
            }

            //@Override //JDK 1.6
            public void printJobNoMoreEvents(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished = true;
            }

            //@Override //JDK 1.6
            public void printJobRequiresAttention(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
            }
        });

        log.info("Sending print job to printer: \"" + printService.getName() + "\"");
        printJob.print(doc, reqAttr);

        while(!isFinished) {
            Thread.sleep(100);
        }

        log.info("Print job received by printer: \"" + printService.getName() + "\"");

        //clear(); - Ver 1.0.8+ : Should be done from Applet instead now
        */
        return true;
    }

    /**
     * Alternate printing mode for CUPS capable OSs, issues lp via command line
     * on Linux, BSD, Solaris, OSX, etc. This will never work on windows.
     *
     * @return boolean indicating success
     */
    public boolean alternatePrint() throws PrintException {
        /*
        File tmpFile = new File("/tmp/qz-spool-" + System.currentTimeMillis());
        try {
            outputPath = tmpFile.getAbsolutePath();
            if (printToFile()) {
                String shellCmd = "/usr/bin/lp -d \"" + printService.getName()
                        + "\" -o raw \"" + tmpFile.getAbsolutePath() + "\";";
                log.info("Runtime Exec running: " + shellCmd);
                Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", shellCmd});
                process.waitFor();
                processStream(process);
            }
        }
        catch(Throwable t) {
            throw new PrintException(t.getLocalizedMessage());
        }
        finally {
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
            outputPath = null;
            //isFinished = true;
        }
        */

        return true;
    }

    private void processStream(Process process) throws Throwable {
        /*
        BufferedReader buf = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String tmp;
        String output = "";
        while((tmp = buf.readLine()) != null) {
            if (output.isEmpty()) {
                output = tmp;
            } else {
                output = output.concat("\n" + tmp);
            }
        }
        log.info("Runtime Exec returned: " + output);
        if (process.exitValue() != 0) {
            throw new PrintException("Alternate printing returned a non-zero value (" + process.exitValue() + "). " + output);
        }
        */
    }

}
