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

/*
 * Main printing class
 */
package qz;

import qz.common.ByteArrayBuilder;
import qz.common.LogIt;
import qz.exception.InvalidFileTypeException;
import qz.exception.NullCommandException;
import qz.exception.NullPrintServiceException;
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
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends raw data to the printer, overriding your operating system's print
 * driver. Most useful for printers such as zebra card or barcode printers.
 *
 * @author A. Tres Finocchiaro
 */
public class PrintRaw {

    public static final Logger log = Logger.getLogger(PrintRaw.class.getName());

    private final AtomicReference<DocFlavor> docFlavor = new AtomicReference<DocFlavor>(DocFlavor.BYTE_ARRAY.AUTOSENSE);
    private final AtomicReference<DocAttributeSet> docAttr = new AtomicReference<DocAttributeSet>(null);
    private final AtomicReference<PrintRequestAttributeSet> reqAttr = new AtomicReference<PrintRequestAttributeSet>(new HashPrintRequestAttributeSet());
    private final AtomicReference<PrintService> printServiceAtomicReference = new AtomicReference<PrintService>(null);
    private final AtomicReference<ByteArrayBuilder> rawCmds = new AtomicReference<ByteArrayBuilder>(null);
    private final AtomicBoolean isFinished = new AtomicBoolean(false);
    private final AtomicReference<Charset> charset = new AtomicReference<Charset>(Charset.defaultCharset());
    private final AtomicReference<String> jobName = new AtomicReference<String>("QZ-PRINT Raw Printing");
    private final AtomicReference<String> outputPath = new AtomicReference<String>(null);
    private final AtomicReference<String> socketHost = new AtomicReference<String>(null);
    private final AtomicReference<Integer> socketPort = new AtomicReference<Integer>(null);
    private final AtomicBoolean alternatePrint = new AtomicBoolean(false);

    public PrintRaw() {
    }

    public PrintRaw(PrintService printServiceAtomicReference, String printString) throws UnsupportedEncodingException {
        this.printServiceAtomicReference.set(printServiceAtomicReference);
        this.rawCmds.set(new ByteArrayBuilder(printString.getBytes(charset.get().name())));
    }


    @SuppressWarnings("UnusedDeclaration")//TOASK: Constructor never used, delete?
    public PrintRaw(PrintService printServiceAtomicReference, String printString, DocFlavor docFlavor,
            DocAttributeSet docAttr, PrintRequestAttributeSet reqAttr, Charset charset) throws UnsupportedEncodingException {
        this.printServiceAtomicReference.set(printServiceAtomicReference);
        this.rawCmds.set(new ByteArrayBuilder(printString.getBytes(charset.name())));
        this.docFlavor.set(docFlavor);
        this.docAttr.set(docAttr);
        this.reqAttr.set(reqAttr);
        this.charset.set(charset);
    }

    //TODO: Fix or remove JavaDoc
    //TOASK: Unused, delete?
    @SuppressWarnings({"JavaDoc", "UnusedDeclaration"})
    /**
     * Constructor added version 1.4.6 for lpr raw compatibility with Lion
     *
     * @param printServiceAtomicReference
     * @param printString
     * @param charset
     * @param alternatePrint
     * @throws UnsupportedEncodingException
     */

    public PrintRaw(PrintService printServiceAtomicReference, String printString, Charset charset, boolean alternatePrint) throws UnsupportedEncodingException {
        this.printServiceAtomicReference.set(printServiceAtomicReference);
        this.rawCmds.set(new ByteArrayBuilder(printString.getBytes(charset.name())));
        this.charset.set(charset);
        this.alternatePrint.set(alternatePrint);
    }

    public ByteArrayBuilder getRawCmds() {
        if (rawCmds.get() == null) {
            rawCmds.set(new ByteArrayBuilder());
        }
        return rawCmds.get();
    }

    public byte[] getByteArray() {
        return getRawCmds().getByteArray();
    }

    public void setOutputPath(String outputPath) throws InvalidFileTypeException {
        // Check for vulnerable file extensions, such as "exe" or "bat", etc.
        // also check for
        if (FileUtilities.isBadExtension(outputPath)) {
            throw new InvalidFileTypeException("Writing file \"" +
                    outputPath + "\" is prohibited for security reason: "
                    + "Prohibited file extension.");
        } else if(FileUtilities.isBadPath(outputPath)) {
            throw new InvalidFileTypeException("Writing file \"" +
                    outputPath + "\" is prohibited for security reason: "
                    + "Prohibited directory name.");
        } else {
            this.outputPath.set(outputPath);
        }
    }

    public void setOutputSocket(String host, int port) {
        this.socketHost.set(host);
        this.socketPort.set(port);
    }

    /**
     * A brute-force, however surprisingly elegant way to send a file to a networked
     * printer. The socket host can be an IP Address or Host Name.  The port
     * 9100 is a standard HP/JetDirect and may work well.
     *
     * Please note that this will completely bypass the Print Spooler, so the
     * Operating System will have absolutely no printer information.  This is
     * printing "blind".
     * @throws UnknownHostException
     * @throws IOException
     */
    private boolean printToSocket() throws IOException {
        log.info("Printing to host " + socketHost.get() + ":" + socketPort.get());
        Socket socket = null;
        DataOutputStream out = null;
        try {
            socket = new Socket(socketHost.get(), socketPort.get());
            out = new DataOutputStream(socket.getOutputStream());
            out.write(getRawCmds().getByteArray());
        } finally {
            if (out != null) {
                out.close();
            }
            if (socket != null) {
                socket.close();
            }
            socketHost.set(null);
            socketPort.set(null);
        }
        return true;
    }
    
    public boolean printToFile() throws PrintException, IOException {
        log.info("Printing to file: " + outputPath.get());
        OutputStream out = new FileOutputStream(outputPath.get());
        out.write(this.getRawCmds().getByteArray());
        out.close();
        return true;
    }

    /**
     * Constructs a
     * <code>javax.print.SimpleDoc</code> with the previously defined byte
     * array.
     *
     * @return True if print job created successfully
     * @throws PrintException
     * @throws InterruptedException
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
     * @throws IOException
     * @throws PrintException
     * @throws InterruptedException
     */
    public boolean print(byte[] data) throws IOException, PrintException, InterruptedException {
        if (printServiceAtomicReference.get() == null) {
            throw new NullPrintServiceException("qz.PrintRaw.print() failed, no print service.");
        } else if (rawCmds.get() == null) {
            throw new NullCommandException("qz.PrintRaw.print() failed, no commands.");
        } else if (outputPath.get() != null) {
            return printToFile();
        } else if (socketHost.get() != null) {
            return printToSocket();
        } else if (alternatePrint.get()) {
            return alternatePrint();
        }

        SimpleDoc doc;
        if (data != null) {
            doc = new SimpleDoc(data, docFlavor.get(), docAttr.get());
        } else {
            doc = new SimpleDoc(getRawCmds().getByteArray(), docFlavor.get(), docAttr.get());
        }

        reqAttr.get().add(new JobName(jobName.get(), Locale.getDefault()));
        DocPrintJob printJob = printServiceAtomicReference.get().createPrintJob();
        printJob.addPrintJobListener(new PrintJobListener() {
            //@Override //JDK 1.6
            public void printDataTransferCompleted(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobCompleted(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobFailed(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobCanceled(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobNoMoreEvents(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobRequiresAttention(PrintJobEvent printJobEvent) {
                LogIt.log(printJobEvent);
            }
        });

        log.info("Sending print job to printer: \"" + printServiceAtomicReference.get().getName() + "\"");
        printJob.print(doc, reqAttr.get());

        while (!isFinished.get()) {
            Thread.sleep(100);
        }

        log.info("Print job received by printer: \"" + printServiceAtomicReference.get().getName() + "\"");

        //clear(); - Ver 1.0.8+ : Should be done from Applet instead now
        return true;
    }

    /**
     * Alternate printing mode for CUPS capable OSs, issues lp via command line
     * on Linux, BSD, Solaris, OSX, etc. This will never work on windows.
     *
     * @return boolean indicating success
     * @throws PrintException
     */
    public boolean alternatePrint() throws PrintException {
        File tmpFile = new File("/tmp/qz-spool-" + System.currentTimeMillis());
        try {
            outputPath.set(tmpFile.getAbsolutePath());
            if (printToFile()) {
                String shellCmd = "/usr/bin/lp -d \"" + printServiceAtomicReference.get().getName()
                        + "\" -o raw \"" + tmpFile.getAbsolutePath() + "\";";
                log.info("Runtime Exec running: " + shellCmd);
                Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", shellCmd});
                process.waitFor();
                processStream(process);
            }
        } catch (Throwable t) {
            throw new PrintException(t.getLocalizedMessage());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpFile.delete();
            outputPath.set(null);
            //isFinished.set(true);
        }

        return true;
    }

    private void processStream(Process process) throws Throwable {
        BufferedReader buf = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String tmp;
        String output = "";
        while ((tmp = buf.readLine()) != null) {
            if (output.length() == 0) {
                output = tmp;
            } else {
                output = output.concat("\n" + tmp);
            }
        }
        log.info("Runtime Exec returned: " + output);
        if (process.exitValue() != 0) {
            throw new PrintException("Alternate printing returned a non-zero value (" + process.exitValue() + "). " + output);
        }
    }

    // public void reprint() throws PrintException, InterruptedException {
    //     print(reprint.get() == null ? rawCmds.get() : reprint.get());
    // }

    /**
     * Convenience method for RawPrint constructor and print method
     * @param printService printService to set
     * @param rawCmds commands to use
     * @return success status
     * @throws IOException
     * @throws PrintException
     * @throws InterruptedException
     */
    public static boolean print(PrintService printService, String rawCmds) throws IOException, PrintException, InterruptedException {
        PrintRaw printRaw = new PrintRaw(printService, rawCmds);
        return printRaw.print();
    }

    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public DocAttributeSet getDocAttributeSet() {
        return docAttr.get();
    }

    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public void setDocAttributeSet(DocAttributeSet docAttr) {
        this.docAttr.set(docAttr);
    }

    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public DocFlavor getDocFlavor() {
        return docFlavor.get();
    }

    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public void setDocFlavor(DocFlavor docFlavor) {
        this.docFlavor.set(docFlavor);
    }

    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public PrintRequestAttributeSet getPrintRequestAttributeSet() {
        return reqAttr.get();
    }

    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public void setPrintRequestAttributeSet(PrintRequestAttributeSet reqAttr) {
        this.reqAttr.set(reqAttr);
    }

    /**
     * Returns the
     * <code>PrintService</code> used internally to the
     * <code>PrintRaw</code> object.
     *
     * @return printService to be used
     */
    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public PrintService getPrintService() {
        return printServiceAtomicReference.get();
    }

    /**
     * Sets the
     * <code>PrintService</code> used internally to the
     * <code>PrintRaw</code> object.
     *
     * @param printService printService to set
     */
    public void setPrintService(PrintService printService) {
        this.printServiceAtomicReference.set(printService);
    }

    /**
     * Returns the raw print data as a
     * <code>String</code>
     *
     * @return
     *
     * public String get() { return this.rawCmds.get();
    }
     */
    public String getOutput() {
        try {
            return new String(this.getRawCmds().getByteArray(), charset.get().name());
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not decode byte array into String for display. "
                    + "This may be normal for mixed or uncommon charsets.", e);
        }
        return null;
    }

    /**
     * Sets the raw print data (or print commands) to blank <scode>String</code>
     */
    public void clear() {
        getRawCmds().clear();
        log.info("Print buffer has been cleared.");
    }

    /**
     * Append the specified
     * <code>String</code> data to the raw stream of data
     *
     * @param stringToAppend the string to append to the stream
     */
    public void append(String stringToAppend) throws UnsupportedEncodingException {
        getRawCmds().append(stringToAppend, charset.get());
    }

    /**
     * Append the
     * <code>byte</code> array data to the raw stream of data
     *
     * @param bytesToAppend the bytes to append to stream
     */
    public void append(byte[] bytesToAppend) {
        this.getRawCmds().append(bytesToAppend);
    }

    /**
     * Sets the
     * <code>Charset</code> (character set) to use, example "US-ASCII" for use
     * when decoding byte arrays. TODO: Test this parameter.
     *
     * @param charset the Charset to use
     */
    public void setCharset(Charset charset) {
        this.charset.set(charset);
        log.info("Current printer charset encoding: " + charset.name());
    }

    /**
     * Return the character set, example "US-ASCII" for use when decoding byte
     * arrays. TODO: Test this parameter.
     *
     * @return the Charset in use
     */
    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public Charset getCharset() {
        return this.charset.get();
    }

    /**
     * Returns whether or not the print data is clear. This usually happens
     * shortly after a print, or when
     * <code>clear()</code> is explicitely called.
     *
     * @return boolean indicating if print data is clear
     */
    public boolean isClear() {
        //try {
        //    return this.rawCmds.get().getBytes(charset.get().name()).length == 0;
        //} catch (UnsupportedEncodingException e) {
        return getRawCmds().getLength() == 0;
        //}
    }

    public void setJobName(String jobName) {
        this.jobName.set(jobName);
    }

    public void setAlternatePrinting(boolean alternatePrint) {
        this.alternatePrint.set(alternatePrint);
    }

    /*This warning is suppresed because this is a non-implemented method that *shouldn't* be used... */
    public void setCopies(@SuppressWarnings("UnusedParameters") int copies) {
        log.log(Level.WARNING, "Copies is unsupported for printHTML()",
                new UnsupportedOperationException("Copies attribute for HTML 1.0 data has not yet been implemented"));
    }

    public int getCopies() {
        log.log(Level.WARNING, "Copies is unsupported for printHTML()",
                new UnsupportedOperationException("Copies attribute for HTML 1.0 data has not yet been implemented"));
        return -1;
    }

    @SuppressWarnings("UnusedDeclaration")
    //TOASK: Unused, delete?
    public String getJobName() {
        return this.jobName.get();
    }

    public void setPrintParameters(PrintApplet rawPrintApplet) {
        setPrintParameters(rawPrintApplet.getJobName(), rawPrintApplet.isAlternatePrinting(), rawPrintApplet.getCopies());
    }

    public void setPrintParameters(String jobName, boolean alternatePrinting, int copies){
        // RKC: PROBLEM >>> this.setPrintService(rawPrintApplet.getPrintService());
        this.setJobName(jobName.replace(" ___ ", " Raw "));
        // RKC: PROBLEM >>> this.setCharset(rawPrintApplet.getCharset());
        this.setAlternatePrinting(alternatePrinting);

        // Copies not implemented yet, so don't call
        /*
        if (copies > 0) {
            setCopies(copies);
        }
        */

        this.clear();
    }

    public boolean contains(String searchString) throws UnsupportedEncodingException {
        return contains(searchString.getBytes(charset.get().name()));
    }

    public boolean contains(byte[] searchBytes) {
        return ByteUtilities.indicesOfSublist(this.getByteArray(), searchBytes).length > 0;
    }
}
