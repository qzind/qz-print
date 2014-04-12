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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.event.PrintJobEvent;
import javax.print.event.PrintJobListener;
import qz.exception.InvalidFileTypeException;
import qz.exception.NullCommandException;
import qz.exception.NullPrintServiceException;

/**
 * Sends raw data to the printer, overriding your operating system's print
 * driver. Most usefull for printers such as zebra card or barcode printers.
 *
 * @author A. Tres Finocchiaro
 */
public class PrintRaw {

    private final static String ERR = "qz.PrintRaw.print() failed.";
    private final AtomicReference<DocFlavor> docFlavor = new AtomicReference<DocFlavor>(DocFlavor.BYTE_ARRAY.AUTOSENSE);
    private final AtomicReference<DocAttributeSet> docAttr = new AtomicReference<DocAttributeSet>(null);
    private final AtomicReference<PrintRequestAttributeSet> reqAttr = new AtomicReference<PrintRequestAttributeSet>(new HashPrintRequestAttributeSet());
    private final AtomicReference<PrintService> ps = new AtomicReference<PrintService>(null);
    //private final AtomicReference<String> rawCmds = new AtomicReference<String>(null);
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

    public PrintRaw(PrintService ps, String printString) throws UnsupportedEncodingException {
        this.ps.set(ps);
        this.rawCmds.set(new ByteArrayBuilder(printString.getBytes(charset.get().name())));
    }

    public PrintRaw(PrintService ps, String printString, DocFlavor docFlavor,
            DocAttributeSet docAttr, PrintRequestAttributeSet reqAttr, Charset charset) throws UnsupportedEncodingException {
        this.ps.set(ps);
        this.rawCmds.set(new ByteArrayBuilder(printString.getBytes(charset.name())));
        this.docFlavor.set(docFlavor);
        this.docAttr.set(docAttr);
        this.reqAttr.set(reqAttr);
        this.charset.set(charset);
    }

    /**
     * Constructor added version 1.4.6 for lpr raw compatibility with Lion
     *
     * @param ps
     * @param rawCmds
     * @param charset
     * @param alternatePrint
     */
    public PrintRaw(PrintService ps, String printString, Charset charset, boolean alternatePrint) throws UnsupportedEncodingException {
        this.ps.set(ps);
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
        if (FileUtilities.isBadExtension(outputPath)) {
            throw new InvalidFileTypeException("Writing file \"" + 
                    outputPath + "\" is prohibited for security reason: "
                    + "Prohibited file extension.");
        } else {
            this.outputPath.set(outputPath);
        }
    }

    public void setOutputSocket(String host, int port) {
        this.socketHost.set(host);
        this.socketPort.set(port);
    }

    /*public boolean print(String rawCmds) throws PrintException, InterruptedException, UnsupportedEncodingException {
     this.set(rawCmds);
     return print();
     }*/
    
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
    private boolean printToSocket() throws UnknownHostException, IOException {
        LogIt.log("Printing to host " + socketHost.get() + ":" + socketPort.get());
        Socket socket = new Socket(socketHost.get(), socketPort.get());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(getRawCmds().getByteArray());
        socket.close();
        return true;
    }

    public boolean printToFile() throws PrintException, IOException {
        LogIt.log("Printing to file: " + outputPath.get());
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
     * @return True if print job created successfull
     * @throws PrintException
     * @throws InterruptedException
     * @throws UnsupportedEncodingException
     */
    public boolean print() throws IOException, InterruptedException, PrintException, UnsupportedEncodingException {
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
     * @param offset
     * @param end
     * @return
     * @throws PrintException
     * @throws InterruptedException
     * @throws UnsupportedEncodingException
     */
    public boolean print(byte[] data) throws IOException, PrintException, InterruptedException, UnsupportedEncodingException {
        if (ps.get() == null) {
            throw new NullPrintServiceException(ERR);
        } else if (rawCmds.get() == null) {
            throw new NullCommandException(ERR);
        } else if (outputPath.get() != null) {
            return printToFile();
        } else if (socketHost.get() != null) {
            return printToSocket();
        } else if (alternatePrint.get()) {
            return alternatePrint();
        }


        //byte[] orig = this.getByteArray().getByteArray();

        //LogIt.log(new String(orig));

        //System.arraycopy(end, end, end, end, end);
        // For printing a specific range, only used for autospooling support
        /*if (offset > 0 && end > 0 && end > offset) { 
         //LogIt.log("Start: " + offset + ": " + orig[offset] + ", End: " + end + ": " + orig[end]);
         byte[] printBytes = new byte[end - offset];
         int counter = 0;
         for (int i = offset; i < end; i++) {
         printBytes[counter++] = orig[i];
         }
         doc = new SimpleDoc(printBytes, docFlavor.get(), docAttr.get());
         }*/

        SimpleDoc doc;
        if (data != null) {
            doc = new SimpleDoc(data, docFlavor.get(), docAttr.get());
        } else {
            doc = new SimpleDoc(getRawCmds().getByteArray(), docFlavor.get(), docAttr.get());
        }

        reqAttr.get().add(new JobName(jobName.get(), Locale.getDefault()));
        DocPrintJob pj = ps.get().createPrintJob();
        pj.addPrintJobListener(new PrintJobListener() {
            //@Override //JDK 1.6
            public void printDataTransferCompleted(PrintJobEvent pje) {
                LogIt.log(pje);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobCompleted(PrintJobEvent pje) {
                LogIt.log(pje);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobFailed(PrintJobEvent pje) {
                LogIt.log(pje);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobCanceled(PrintJobEvent pje) {
                LogIt.log(pje);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobNoMoreEvents(PrintJobEvent pje) {
                LogIt.log(pje);
                isFinished.set(true);
            }

            //@Override //JDK 1.6
            public void printJobRequiresAttention(PrintJobEvent pje) {
                LogIt.log(pje);
            }
        });

        LogIt.log("Sending print job to printer: \"" + ps.get().getName() + "\"");
        pj.print(doc, reqAttr.get());

        while (!isFinished.get()) {
            Thread.sleep(100);
        }

        LogIt.log("Print job received by printer: \"" + ps.get().getName() + "\"");

        //clear(); - Ver 1.0.8+ : Should be done from Applet instead now
        return true;
    }

    /**
     * Alternate printing mode for CUPS capable OSs, issues lp via command line
     * on Linux, BSD, Solaris, OSX, etc. This will never work on windows.
     *
     * @return
     * @throws PrintException
     */
    public boolean alternatePrint() throws PrintException {
        File tmpFile = new File("/tmp/qz-spool-" + System.currentTimeMillis());
        try {
            outputPath.set(tmpFile.getAbsolutePath());
            if (printToFile()) {
                String shellCmd = "/usr/bin/lp -d \"" + ps.get().getName()
                        + "\" -o raw \"" + tmpFile.getAbsolutePath() + "\";";
                LogIt.log("Runtime Exec running: " + shellCmd);
                Process pr = Runtime.getRuntime().exec(new String[]{"bash", "-c", shellCmd});
                pr.waitFor();
                processStream(pr);
            }
        } catch (Throwable t) {
            throw new PrintException(t.getLocalizedMessage());
        } finally {
            tmpFile.delete();
            outputPath.set(null);
            //isFinished.set(true);
        }

        return true;
    }

    private void processStream(Process pr) throws Throwable {
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String tmp;
        String output = "";
        while ((tmp = buf.readLine()) != null) {
            if (output.length() == 0) {
                output = tmp;
            } else {
                output = output.concat("\n" + tmp);
            }
        }
        LogIt.log("Runtime Exec returned: " + output);
        if (pr.exitValue() != 0) {
            throw new PrintException("Alternate printing returned a non-zero value (" + pr.exitValue() + "). " + output);
        }
    }

    // public void reprint() throws PrintException, InterruptedException {
    //     print(reprint.get() == null ? rawCmds.get() : reprint.get());
    // }
    /**
     * Convenience method for RawPrint constructor and print method
     *
     * @param ps The PrintService object
     * @param commands The RAW commands to be sent directly to the printer
     * @return True if print job created successfull
     * @throws javax.print.PrintException
     */
    public static boolean print(PrintService ps, String rawCmds) throws IOException, PrintException, InterruptedException, UnsupportedEncodingException {
        PrintRaw p = new PrintRaw(ps, rawCmds);
        return p.print();
    }

    public DocAttributeSet getDocAttributeSet() {
        return docAttr.get();
    }

    public void setDocAttributeSet(DocAttributeSet docAttr) {
        this.docAttr.set(docAttr);
    }

    public DocFlavor getDocFlavor() {
        return docFlavor.get();
    }

    public void setDocFlavor(DocFlavor docFlavor) {
        this.docFlavor.set(docFlavor);
    }

    public PrintRequestAttributeSet getPrintRequestAttributeSet() {
        return reqAttr.get();
    }

    public void setPrintRequestAttributeSet(PrintRequestAttributeSet reqAttr) {
        this.reqAttr.set(reqAttr);
    }

    /**
     * Returns the
     * <code>PrintService</code> used internally to the
     * <code>PrintRaw</code> object.
     *
     * @return
     */
    public PrintService getPrintService() {
        return ps.get();
    }

    /**
     * Sets the
     * <code>PrintService</code> used internally to the
     * <code>PrintRaw</code> object.
     *
     * @param ps
     */
    public void setPrintService(PrintService ps) {
        this.ps.set(ps);
    }

    /**
     * Sets the raw print data, overriding any existing data
     *
     * @param s
     *
     * public void set(String s) { this.rawCmds.set(s);
    }
     */
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
            LogIt.log(Level.WARNING, "Could not decode byte array into String for display. "
                    + "This may be normal for mixed or uncommon charsets.", e);
        }
        return null;
    }

    /**
     * Sets the raw print data (or print commands) to blank <scode>String</code>
     */
    public void clear() {
        getRawCmds().clear();
        LogIt.log(Level.INFO, "Print buffer has been cleared.");
    }

    /**
     * Append the specified
     * <code>String</code> data to the raw stream of data
     *
     * @param s
     */
    public void append(String s) throws UnsupportedEncodingException {
        getRawCmds().append(s, charset.get());
    }

    /**
     * Append the
     * <code>byte</code> array data to the raw stream of data
     *
     * @param b
     */
    public void append(byte[] b) {
        this.getRawCmds().append(b);
    }

    /**
     * Sets the
     * <code>Charset</code> (character set) to use, example "US-ASCII" for use
     * when decoding byte arrays. TODO: Test this parameter.
     *
     * @param charset
     */
    public void setCharset(Charset charset) {
        this.charset.set(charset);
        LogIt.log("Current printer charset encoding: " + charset.name());
    }

    /**
     * Return the character set, example "US-ASCII" for use when decoding byte
     * arrays. TODO: Test this parameter.
     *
     * @return
     */
    public Charset getCharset() {
        return this.charset.get();
    }

    /**
     * Returns whether or not the print data is clear. This usually happens
     * shortly after a print, or when
     * <code>clear()</code> is explicitely called.
     *
     * @return
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

    public String getJobName() {
        return this.jobName.get();
    }

    public void setPrintParameters(PrintApplet rpa) {
        this.setPrintService(rpa.getPrintService());
        this.setJobName(rpa.getJobName().replace(" ___ ", " Raw "));
        this.setCharset(rpa.getCharset());
        this.setAlternatePrinting(rpa.isAlternatePrinting());
        this.clear();
    }

    /**
     * Iterates through byte array finding matches of a sublist of bytes.
     * Returns an array of positions. TODO: Make this natively Iterable.
     *
     * @param array
     * @return
     */
    //public int[] indicesOfSublist(byte[] sublist) {
    //    return ByteUtilities.indicesOfSublist(this.getRawCmds().getByteArray(), sublist);
    //}
    public boolean contains(String s) throws UnsupportedEncodingException {
        return contains(s.getBytes(charset.get().name()));
    }

    public boolean contains(byte[] bytes) {
        return ByteUtilities.indicesOfSublist(this.getByteArray(), bytes).length > 0;
    }
}
