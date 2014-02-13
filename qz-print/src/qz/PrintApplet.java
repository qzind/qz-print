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

package qz;

import java.applet.Applet;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.logging.Level;
import qz.json.JSONArray;

/**
 * The PrintApplet is the main component of the Applet. It provides function
 * definitions for all functionality accessible through JavaScript as well as
 * initialization code for the applet, JavaScript callback tools and the spooler
 * 
 * @author Thomas Hart
 */
public class PrintApplet extends Applet {
    
    /**
     * The version number for the applet
     */
    public static final String VERSION = "2.0.0";
    
    private BrowserTools btools;
    private PrintSpooler spooler;
    private Charset charset;
    
    @Override
    public void start() {
        
        super.start();
        
        LogIt.log("Applet Started");
        
        btools = new BrowserTools(this);
        spooler = new PrintSpooler();
        spooler.setApplet(this);
        
        new Thread(spooler).start();
        
        charset = Charset.defaultCharset();
        
        btools.notifyBrowser("qzReady");
        
    }
    
    @Override
    public void stop() {
        spooler.closePort("");
        super.stop();
    }
    
    /* 
     * Javascript methods that the browser can call
     * All functions below this line are accessible to the JavaScript interface.
     */
    
    /**
     * Cancel a job
     * @param jobIndex The index of the job to cancel
     */
    public void cancelJob(int jobIndex) {
        spooler.cancelJob(jobIndex);
    }
    
    /**
     * getQueueInfo gets the queue info JSONArray from the spooler and passes
     * it back to the JavaScript interface.
     * 
     * @return A JSONArray representing the current state of the queue
     */
    public String getQueueInfo() {
        JSONArray queueInfo = spooler.getQueueInfo();
        if(queueInfo != null) {
            return queueInfo.toString();
        }
        else {
            return new JSONArray().toString();
        }
    }
    
    /**
     * Returns a string with the contents of the job data. This is only really
     * useful for Raw data PrintJobs
     * 
     * @param jobIndex The index of the job to get info for
     * @return A String representation of the job data
     */
    public String getJobInfo(int jobIndex) {
        return spooler.getJobInfo(jobIndex);
    }
    
    /**
     * Calling findPrinter with no input will find the default printer
     */
    public void findPrinter() {
        findPrinter(null);
    }
    
    /**
     * Search for a printer by name. This function uses a set of searches to 
     * attempt to find the intended printer. Call qzDoneFinding() in the browser
     * when complete.
     * 
     * @param printerName The name (or partial name) of the printer to find
     */
    public void findPrinter(String printerName) {
        spooler.findPrinter(printerName);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneFinding");
    }
    
    /**
     * Get the current printer's name.
     * 
     * @return The current printer's name.
     */
    public String getPrinter() {
        return spooler.getPrinter();
    }
    
    /**
     * Get the printer name of the printer used in the last printed job
     * 
     * @return The last printer's name
     */
    public String getLastPrinter() {
        return spooler.getLastPrinter();
    }
    
    /**
     * Return the list of printers
     *
     * @return A comma delimited string of printers
     */
    public String getPrinters() {
        
        try {
            String printerListString = spooler.getPrinters();
            return printerListString;
        }
        catch(NullPointerException ex) {
            LogIt.log(Level.WARNING, "Could not get printer list.", ex);
            return "";
        }
        
    }
    
    /**
     * Set the current printer.
     * 
     * @param printerIndex The index of the printer in printerList
     */
    public void setPrinter(int printerIndex) {
        spooler.setPrinter(printerIndex);
    }
    
    /**
     * Appends String <var>data</var> to the spool after converting to a byte 
     * array, which will add it to the current job or start a new one.
     * 
     * @param data 
     */
    public void append(String data) {
        ByteArrayBuilder bytes = new ByteArrayBuilder();
        try {
            bytes.append(data, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append data.", ex);
        }
        spooler.append(bytes, charset);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Converts String base64 into a byte array then appends the array to the
     * spool, which will add it to the current job or start a new one
     * 
     * @param base64 
     */
    public void append64(String base64) {
        
        byte[] base64Array = null;
        try {
            base64Array = Base64.decode(base64);
        } catch (IOException ex) {
            LogIt.log(Level.SEVERE, "Could not append data.", ex);
        }
        
        ByteArrayBuilder data = new ByteArrayBuilder(base64Array);
        spooler.append(data, charset);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Converts Hex String data into a byte array then appends the array to the
     * spool, which will add it to the current job or start a new one
     * 
     * @param hexString 
     */
    public void appendHex(String hexString) {
        byte[] bytes = ByteUtilities.hexStringToByteArray(hexString);
        ByteArrayBuilder data = new ByteArrayBuilder(bytes);
        spooler.append(data, charset);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Appends an image to the current print job
     * 
     * @param imagePath The file path of the image
     * @param lang The target raw printer language to translate the image to
     */
    public void appendImage(String imagePath, String lang) {
        
        ByteArrayBuilder bytes = new ByteArrayBuilder();
        try {
            bytes.append(imagePath, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append image.", ex);
        }
        spooler.appendImage(bytes, charset, lang, 0, 0);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Appends an image to the current print job
     * 
     * @param imagePath The file path of the image
     * @param lang The target raw printer language to translate the image to
     * @param imageX The width of the image
     * @param imageY The height of the image
     */
    public void appendImage(String imagePath, String lang, int imageX, int imageY) {
        
        ByteArrayBuilder bytes = new ByteArrayBuilder();
        try {
            bytes.append(imagePath, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append image.", ex);
        }
        spooler.appendImage(bytes, charset, lang, imageX, imageY);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Appends an image to the current print job
     * 
     * @param imagePath The file path of the image
     * @param lang The target raw printer language to translate the image to
     * @param dotDensityString The dot density of the image
     */
    public void appendImage(String imagePath, String lang, String dotDensityString) {
        
        int dotDensity = 32;
        
        if (dotDensityString.equalsIgnoreCase("single")) {
            dotDensity = 32;
        } else if (dotDensityString.equalsIgnoreCase("double")) {
            dotDensity = 33;
        } else if (dotDensityString.equalsIgnoreCase("triple")) {
            dotDensity = 39;
        } else {
            LogIt.log(Level.WARNING, "Cannot translate dotDensity value of '"
                    + dotDensityString + "'.  Using '" + dotDensity + "'.");
        }
        
        appendImage(imagePath, lang, dotDensity);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Appends an image to the current print job
     * 
     * @param imagePath The file path of the image
     * @param lang The target raw printer language to translate the image to
     * @param dotDensity The dot density of the image
     */
    public void appendImage(String imagePath, String lang, int dotDensity) {
        ByteArrayBuilder bytes = new ByteArrayBuilder();
        try {
            bytes.append(imagePath, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append image.", ex);
        }
        spooler.appendImage(bytes, charset, lang, dotDensity);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Appends a PostScript image to the current print job
     * 
     * @param url The path of the image to append
     */    
    public void appendImage(String url) {
        // if appendImage is called without a lang, it's a postscript job
        ByteArrayBuilder bytes = new ByteArrayBuilder();
        try {
            bytes.append(url, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append image.", ex);
        }
        spooler.appendPSImage(bytes, charset);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Gets the first XML node identified by <code>tagName</code>, reads its
     * contents and appends it to the buffer. Assumes XML content is base64
     * formatted.
     * 
     * @param url
     * @param xmlTag 
     */
    public void appendXML(String url, String xmlTag) {
        ByteArrayBuilder bytes = new ByteArrayBuilder();
        try {
            bytes.append(url, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append XML.", ex);
        }
        spooler.appendXML(bytes, charset, xmlTag);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * appendFile will read a text file and append the data directly without
     * any translation
     * 
     * @param url 
     */
    public void appendFile(String url) {
        ByteArrayBuilder bytes = new ByteArrayBuilder();
            
        try {
            bytes.append(url, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append file.", ex);
        }
        spooler.appendFile(bytes, charset);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * appendRTF will read an RTF file and append the data to a new or
     * existing PostScript job
     * 
     * NOTE: This function requires Java >= 1.6
     * 
     * @param url 
     */
    public void appendRTF(String url) {
        ByteArrayBuilder bytes = new ByteArrayBuilder();
            
        try {
            bytes.append(url, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Could not append file.", ex);
        }
        spooler.appendRtfFile(bytes, charset);
        
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * appendHTML adds an HTML element to an HTML PrintJob
     * 
     * @param html The HTML to add
     */
    public void appendHTML(String html) {
        ByteArrayBuilder bytes = new ByteArrayBuilder();
        
        try {
            bytes.append(html, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "HTML could not be appended.", ex);
        }
        spooler.appendHTML(bytes, charset);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * appendPDF adds a PDF file to a PostScript PrintJob
     * 
     * @param url
     */
    public void appendPDF(String url) {
        ByteArrayBuilder bytes = new ByteArrayBuilder();
            
        try {
            bytes.append(url, charset);
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "PDF File could not be appended.", ex);
        }
        spooler.appendPDF(bytes, charset);
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneAppending");
    }
    
    /**
     * Trigger the current job to start preparing and queue for printing.
     * 
     * @return A boolean representing the print routine's success
     */
    public boolean print() {
        Boolean success = spooler.print();
        if(success) {
            LogIt.log("Print Successful");
        }
        else {
            LogIt.log(Level.WARNING, "Print Failed");
        }
        btools.notifyBrowser("qzDonePrinting");
        return success;
    }
    
    /**
     * printToFile will set the file output path and prepare the current job
     * 
     * @param filePath The output file to write to
     */
    public void printToFile(String filePath) {
        spooler.printToFile(filePath);
        btools.notifyBrowser("qzDonePrinting");
    }
    
    /**
     * printToHost will set the remote host and prepare the current job
     * 
     * @param jobHost The remote host to send to
     * @param jobPort The port on the remote host
     */
    public void printToHost(String jobHost, int jobPort) {
        spooler.printToHost(jobHost, jobPort);
        btools.notifyBrowser("qzDonePrinting");
    }
    
    /**
     * Gets the current version of the qz-print applet
     * 
     * @return The current version of the applet
     */
    public String getVersion() {
        return VERSION;
    }
    
    /**
     * Get the current exception
     * 
     * @return The current exception
     */
    public Throwable getException() {
        return spooler.getException();
    }

    /**
     * Clear the last exception.
     */
    public void clearException() {
        spooler.clearException();
    }
    
    /**
     * Set the charset to use for encoding. This allows users to use non-ASCII
     * characters in raw data as well as file/image paths.
     * 
     * @param charset The charset to use
     */
    public void setEncoding(String charset) {
        // Example:  Charset.forName("US-ASCII");
        LogIt.log("Default charset encoding: " + Charset.defaultCharset().name());
        try {
            this.charset = Charset.forName(charset);
            LogIt.log("Current applet charset encoding: " + this.charset.name());
        } catch (IllegalCharsetNameException e) {
            LogIt.log(Level.WARNING, "Could not find specified charset encoding: "
                    + charset + ". Using default.", e);
        }
    }
    
    /**
     * Set the paper size for new jobs.
     * 
     * @param width The paper size's target width
     * @param height The paper size's target height
     */
    public void setPaperSize(String width, String height) {
        PaperFormat paperSize = PaperFormat.parseSize(width, height);
        spooler.setPaperSize(paperSize);
        LogIt.log(Level.INFO, "Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }

    /**
     * Clear a previously set paper size
     */
    public void clearPaperSize() {
        spooler.clearPaperSize();
        LogIt.log(Level.INFO, "Paper size has been cleared.");
    }
    
    /**
     * Set the paper size for new jobs.
     * 
     * @param width The paper size's target width
     * @param height The paper size's target height
     */
    public void setPaperSize(float width, float height) {
        PaperFormat paperSize = new PaperFormat(width, height);
        spooler.setPaperSize(paperSize);
        LogIt.log(Level.INFO, "Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }

    /**
     * Set the paper size for new jobs.
     * 
     * @param width The paper size's target width
     * @param height The paper size's target height
     * @param units The measurement units to use
     */
    public void setPaperSize(float width, float height, String units) {
        PaperFormat paperSize = PaperFormat.parseSize("" + width, "" + height, units);
        spooler.setPaperSize(paperSize);
        LogIt.log(Level.INFO, "Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }
    
    /**
     * Toggle whether PostScript autosizing should be enabled
     * 
     * @param autoSize 
     */
    public void setAutoSize(boolean autoSize) {
        spooler.setAutoSize(autoSize);
        LogIt.log(Level.INFO, "Auto size has been set to " + Boolean.toString(autoSize));
    }
    
    /**
     * Set the left margin for the current job
     * 
     * @param leftMargin
     */
    public void setLeftMargin(int leftMargin) {
        spooler.setLeftMargin(leftMargin);
        LogIt.log(Level.INFO, "Left margin set to " + leftMargin);
    }
    
    /**
     * Set the top margin for the current job
     * 
     * @param topMargin
     */
    public void setTopMargin(int topMargin) {
        spooler.setTopMargin(topMargin);
        LogIt.log(Level.INFO, "Top margin set to " + topMargin);
    }
    
    /**
     * Return the logPSFeatures boolean
     * 
     * @return logPSFeatures
     */
    public boolean getLogPostScriptFeatures() {
        return spooler.getLogPostScriptFeatures();
    }
    
    /**
     * Set the PostScript feature logging variable
     * 
     * @param logPSFeatures The new value
     */
    public void setLogPostScriptFeatures(boolean logPSFeatures) {
        spooler.setLogPostScriptFeatures(logPSFeatures);
    }
    
    /**
     * Set the number of copies to print for the current or a new job.
     * 
     * NOTE: Do not recommend using this with endOfDocument spooling
     * 
     * @param copies The number of copies to print
     */
    public void setCopies(int copies) {
        spooler.setCopies(copies);
    }
    
    /**
     * Set the character that marks the end of the document in spooled raw
     * print jobs
     * 
     * @param endOfDocument The end of document character
     */
    public void setEndOfDocument(String endOfDocument) {
        spooler.setEndOfDocument(endOfDocument);
    }
    
    /**
     * Set the documents to spool together per job in spooled raw print jobs
     * 
     * @param docsPerSpool The number of documents per spooled job
     */
    public void setDocumentsPerSpool(int docsPerSpool) {
        spooler.setDocumentsPerSpool(docsPerSpool);
    }
    
    /**
     * Find the machine's ip and mac address and set the local variables
     */
    public void findNetworkInfo() {
        spooler.findNetworkInfo();
        // Deprecated callback. Remove in a future version.
        btools.notifyBrowser("qzDoneFindingNetwork");
    }
    
    /**
     * Getter for Mac Address
     * 
     * @return The machine's Mac Address
     */
    public String getMac() {
        return spooler.getMac();
    }
    
    /**
     * Getter for IP Address
     * 
     * @return The machine's IP Address
     */
    public String getIP() {
        return spooler.getIP();
    }
    
    /**
     * useAlternatePrinting() with no variable defaults to true
     */
    public void useAlternatePrinting() {
        this.useAlternatePrinting(true);
    }

    /**
     * Turn alternate printing on or off
     * 
     * @param alternatePrint The new value of alternatePrint
     */
    public void useAlternatePrinting(boolean alternatePrint) {
        spooler.useAlternatePrinting(alternatePrint);
    }
    
    /**
     * Getter for alternate printing setting
     * @return Alternate printing boolean
     */
    public boolean isAlternatePrinting() {
        return spooler.isAlternatePrinting();
    }

    /**
     * findPorts starts the process of finding the list of serial ports.
     */
    public void findPorts() {
        spooler.findPorts();
        btools.notifyBrowser("qzDoneFindingPorts");
    }
    
    /**
     * Return a comma delimited String of all available serial ports
     * 
     * @return The list of ports
     */
    public String getPorts() {
        return spooler.getPorts();
    }
    
    /**
     * openPort creates a port reference and opens it.
     * 
     * @param portName The name of the port to open
     */
    public void openPort(String portName) {
        spooler.openPort(portName);
    }
    
    /**
     * closePort closes the currently open port. A port name is provided but is
     * only used in the log. Since only one port can be open at a time, 
     * closePort does not require you to specify the correct port.
     * 
     * @param portName The name of the port to close. Only used in log.
     */
    public void closePort(String portName) {
        spooler.closePort(portName);
    }
    
    /**
     * Set the character to mark the beginning of returned serial data.
     * 
     * @param serialBegin The beginning character.
     */
    public void setSerialBegin(String serialBegin) {
        ByteArrayBuilder serialBeginBytes = new ByteArrayBuilder(serialBegin.getBytes());
        spooler.setSerialBegin(serialBeginBytes);
    }
    
    /**
     * Set the character to mark the ending of returned serial data.
     * 
     * @param serialEnd The ending character.
     */
    public void setSerialEnd(String serialEnd) {
        ByteArrayBuilder serialEndBytes = new ByteArrayBuilder(serialEnd.getBytes());
        spooler.setSerialEnd(serialEndBytes);
    }
    
    /**
     * Sets the properties for communicating with serial ports.
     * 
     * @param baud
     * @param dataBits
     * @param stopBits
     * @param parity
     * @param flowControl
     */
    public void setSerialProperties(int baud, int dataBits, String stopBits, int parity, String flowControl) {
        setSerialProperties(Integer.toString(baud), Integer.toString(dataBits),
                stopBits, Integer.toString(parity), flowControl);
    }

    /**
     * Sets the properties for communicating with serial ports.
     * 
     * @param baud
     * @param dataBits
     * @param stopBits
     * @param parity
     * @param flowControl
     */
    public void setSerialProperties(String baud, String dataBits, String stopBits, String parity, String flowControl) {
        spooler.setSerialProperties(baud, dataBits, stopBits, parity, flowControl);
    }
    
    /**
     * Send serial data to the specified port.
     * 
     * @param portName The port name to send data to
     * @param serialData A string of the data to send.
     */
    public void send(String portName, String serialData) {
        // portName is only used for display. Get current port name from SerialPrinter
        spooler.sendSerialData(serialData);
    }
    
    /**
     * Get any returned serial data.
     * 
     * @return The returned data
     */
    public String getReturnData() {
        return spooler.getReturnData();
    }
    
    /*
     * Deprecated functions. These should be removed in a future version.
     */
    
    /**
     * Check if the appending operation is complete.
     * 
     * @return Whether the element is done appending
     * @deprecated This function is no longer needed as appending is instant.
     */
    @Deprecated
    public boolean isDoneAppending() {
        LogIt.log(Level.WARNING, "isDoneAppending() has been deprecated and will be removed in a future version.");
        return true;
    }
    
    /**
     * Check if the printing operation is complete.
     * 
     * @return Whether the job is done printing
     * @deprecated This function is no longer useful in a spooling context.
     * Use {@link getQueueInfo()} to get information about existing jobs.
     */
    @Deprecated
    public boolean isDonePrinting() {
        LogIt.log(Level.WARNING, "isDonePrinting() has been deprecated and will be removed in a future version. Try using getQueueInfo().");
        return true;
    }
    
    /**
     * Check whether multiple instances are currently allowed.
     * 
     * @return The current value of allowMultipleInstances
     * @deprecated This functionality is no longer supported.
     */
    @Deprecated
    public boolean getAllowMultipleInstances() {
        LogIt.log(Level.WARNING, "getAllowMultipleInstances() has been deprecated and will be removed in a future version. This functionality is no longer supported.");
        return false;
    }
    
    /**
     * Set whether multiple instances are currently allowed.
     * 
     * @param newValue The value to be set
     * @deprecated This functionality is no longer supported.
     */
    @Deprecated
    public void allowMultipleInstances(Boolean newValue) {
        LogIt.log(Level.WARNING, "allowMultipleInstances() has been deprecated and will be removed in a future version. This functionality is no longer supported.");
    }
    
    /**
     * @return Boolean representing whether the applet is done finding a printer
     * @deprecated Backwards compatability function. Finding printers is done 
     * instantly now, so it's always "done finding"
     * 
     */
    @Deprecated
    public boolean isDoneFinding() {
        LogIt.log(Level.WARNING, "isDoneFinding() has been deprecated and will be removed in a future version. This functionality is no longer needed.");
        return true;
    }
    
    /**
     * Stub function for backwards compatability
     * @return A boolean representing the print routine's success
     * @deprecated PrintJobs will determine their type when printing
     */
    @Deprecated
    public boolean printPS() {
        LogIt.log(Level.WARNING, "printPS() has been deprecated and will be removed in a future version. You can use \"print()\" for all types of print jobs.");
        return print();
    }
    
    /**
     * Stub function for backwards compatability
     * @return A boolean representing the print routine's success
     * @deprecated PrintJobs will determine their type when printing
     */
    @Deprecated
    public boolean printHTML() {
        LogIt.log(Level.WARNING, "printHTML() has been deprecated and will be removed in a future version. You can use \"print()\" for all types of print jobs.");
        return print();
    }
}
