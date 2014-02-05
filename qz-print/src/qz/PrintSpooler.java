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

package qz;

import java.applet.Applet;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.logging.Level;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterName;
import qz.exception.InvalidFileTypeException;
import qz.exception.InvalidRawImageException;
import qz.exception.NullCommandException;
import qz.exception.NullPrintServiceException;
import qz.json.JSONArray;
import qz.reflection.ReflectException;

/**
 * The PrintSpooler will maintain a list of all print jobs and their status.
 * It will also initiate threaded processes for preparing print jobs and sending
 * them to the printer with the appropriate interface.
 * 
 * @author Thomas Hart
 */
public class PrintSpooler implements Runnable {
    
    private boolean running;
    private PrintJob currentJob;
    private Thread currentJobThread;
    private JSONArray queueInfo;
    private final ArrayList<PrintJob> spool = new ArrayList<PrintJob>();
    private ListIterator<PrintJob> spoolIterator;
    private Printer currentPrinter;
    private String lastPrinterName;
    private ArrayList<Printer> printerList;
    private String printerListString;
    private FilePrinter filePrinter;
    private SerialPrinter serialPrinter;
    private PaperFormat paperSize;
    private boolean autoSize;
    private boolean logPSFeatures;
    private String endOfDocument;
    private int docsPerSpool;
    private int openJobs;
    private NetworkUtilities networkUtilities;
    private String macAddress;
    private String ipAddress;
    private boolean alternatePrint;
    private Applet applet;
    private Throwable exception;
    private PrintService defaultPS;
    private boolean serialEnabled = false;
            
    /**
     * The run loop will consistently check the spool List and call functions
     * based on the state of each PrintJob
     */
    public void run() {
        
        LogIt.log("PrintSpooler started");
        
        // Get the list of all installed printers
        printerList = new ArrayList<Printer>();
        printerListString = "";
        findAllPrinters();
        
        // Initialize system variables
        running = true;
        filePrinter = new FilePrinter();
        logPSFeatures = false;
        endOfDocument = "";
        docsPerSpool = 0;
        openJobs = 0;
        alternatePrint = false;
        exception = null;
        currentPrinter = null;
        defaultPS = PrintServiceLookup.lookupDefaultPrintService();
        
        // Main loop
        while(running) {
            synchronized(spool) {
                if(spool.size() > 0) {
                    spoolIterator = spool.listIterator();
                    JSONArray currentQueueInfo = new JSONArray();
                    while(spoolIterator.hasNext()) {

                        int jobIndex = spoolIterator.nextIndex();
                        PrintJob job = spoolIterator.next();
                        PrintJobState jobState = job.getJobState();

                        switch(jobState) {
                            case STATE_PROCESSED:
                                job.queue();
                                break;
                            case STATE_QUEUED:
                                // Get Printer Status from the job
                                if(job.getPrinter().ready()) {
                                    job.print();
                                }
                                break;
                        };

                        HashMap<String, String> jobInfo = new HashMap<String, String>();
                        jobInfo.put("id", String.valueOf(jobIndex));
                        jobInfo.put("title", job.getTitle());
                        jobInfo.put("state", jobState.name());
                        currentQueueInfo.put(jobInfo);
                    }
                    queueInfo = currentQueueInfo;
                }
            }
        }
    }
    
    /**
     * Set the applet reference
     * 
     * @param applet
     */
    public void setApplet(Applet applet) {
        this.applet = applet;
        serialPrinter = getSerialPrinter();
    }
    
    /**
     * Instantiates the <code>SerialPrinter</code>, or returns it if already
     * created.
     * @return 
     */
    private SerialPrinter getSerialPrinter() {
        try {
            Class.forName("jssc.SerialPort");
            if (serialPrinter == null) {
                serialPrinter = new SerialPrinter(applet);
                serialEnabled = true;
            }
        } catch (ClassNotFoundException c) {
            LogIt.log(Level.WARNING, "JSSC library could not be found. Serial functionality has been disabled.");
            setException(c);
        }
        return serialPrinter;
    }
    
    /**
     * Create a PrintJob and add it to the spool
     */
    public void createJob() {
        
        openJobs += 1;
        
        currentJob = new PrintJob();
        currentJobThread = new Thread(currentJob);
        currentJobThread.start();
        
        if(paperSize != null) {
            currentJob.setPaperSize(paperSize);
        }
        if(autoSize) {
            currentJob.setAutoSize(true);
        }
        
        currentJob.setLogPostScriptFeatures(logPSFeatures);
        currentJob.setAlternatePrinting(alternatePrint);
        
        synchronized(spool) {
            spool.add(currentJob);
        }
    }
    
    /**
     * Append raw data to a PrintJob
     * 
     * @param data The data to add
     * @param charset The charset of the data
     */
    public void append(ByteArrayBuilder data, Charset charset) {
        if(currentJob == null) {
            createJob();
        }
        
        currentJob.append(data, charset);
    }
    
    /**
     * Creates an image PrintJobElement and adds it to the current print job
     * 
     * @param imagePath The file path of the image
     * @param charset The charset of the file path
     * @param lang The target raw printer language to translate the image to
     * @param imageX The width of the image
     * @param imageY The height of the image
     */
    public void appendImage(ByteArrayBuilder imagePath, Charset charset, String lang, int imageX, int imageY) {
        if(currentJob == null) {
            createJob();
        }
        
        currentJob.appendImage(imagePath, charset, lang, imageX, imageY);
    }

    /**
     * Creates an image PrintJobElement and adds it to the current print job
     * 
     * @param imagePath The file path of the image
     * @param charset The charset of the file path
     * @param lang The target raw printer language to translate the image to
     * @param dotDensity The dot density of the image
     */
    public void appendImage(ByteArrayBuilder imagePath, Charset charset, String lang, int dotDensity) {
        if(currentJob == null) {
            createJob();
        }
        
        currentJob.appendImage(imagePath, charset, lang, dotDensity);
    }
    
    /**
     * appendPSImage adds an image PrintJobElement to a PostScript job
     * 
     * @param url The path of the image
     * @param charset The charset of the path
     */
    public void appendPSImage(ByteArrayBuilder url, Charset charset) {
        if(currentJob == null) {
            createJob();
        }
        
        currentJob.appendPSImage(url, charset);
    }
    
    /**
     * appendXML pulls data from an xml file/tag and appends it to a raw print
     * job
     * 
     * @param url The path of the xml file
     * @param charset The charset of the path
     * @param xmlTag The XML tag to pull the data from
     */
    public void appendXML(ByteArrayBuilder url, Charset charset, String xmlTag) {
        if(currentJob == null) {
            createJob();
        }
        
        currentJob.appendXML(url, charset, xmlTag);
    }

    /**
     * appendFile reads the contents of a file and adds the data to a raw
     * print job
     * 
     * @param url The path of the file
     * @param charset The charset of the path
     */
    public void appendFile(ByteArrayBuilder url, Charset charset) {
        
        if(!"".equals(endOfDocument)) {
            
            String[] fileData = getFileData(url, charset).split(endOfDocument);
            String[] consolidatedData;
            
            if(docsPerSpool > 1) {
                
                int dataLength = fileData.length;
                int newArrayLength = ((dataLength - (dataLength % docsPerSpool)) / docsPerSpool) + 1;

                consolidatedData = new String[newArrayLength];
                
                for(int i=0; i < newArrayLength; i++) {
                    String jobData = "";
                    for(int j=0; j < docsPerSpool; j++) {
                        int index = (i * docsPerSpool) + j;
                        if(index < dataLength) {
                            jobData += fileData[index] + endOfDocument;
                        }
                    }
                    consolidatedData[i] = jobData;
                }
                
            }
            else {
                consolidatedData = new String[fileData.length];
                
                for(int i=0; i < fileData.length; i++) {
                    consolidatedData[i] = fileData[i] + endOfDocument;
                }
                
            }
            
            for(String dataString : consolidatedData) {
                if(currentJob == null) {
                    createJob();
                }
                ByteArrayBuilder bytes = new ByteArrayBuilder();
                try {
                    bytes.append(dataString, charset);
                } catch (UnsupportedEncodingException ex) {
                    LogIt.log(Level.SEVERE, "Unsupported encoding.", ex);
                }
                currentJob.append(bytes, charset);
                currentJob = null;
            }
            
            endOfDocument = "";
            docsPerSpool = 0;
        }
        else {
            if(currentJob == null) {
                createJob();
            }
            currentJob.appendFile(url, charset);
        }
        
    }
    
    /**
     * appendRtfFile reads the contents of an RTF file and adds the data to a 
     * PostScript print job
     * 
     * @param url The path of the file
     * @param charset The charset of the path
     */
    public void appendRtfFile(ByteArrayBuilder url, Charset charset) {
        if(currentJob == null) {
            createJob();
        }
        currentJob.appendRtfFile(url, charset);
    }
    
    /**
     * appendHTML adds an HTML type PrintJobElement to an HTML PrintJob
     * 
     * @param html The HTML to add
     * @param charset The charset of the HTML
     */
    public void appendHTML(ByteArrayBuilder html, Charset charset) {
        if(currentJob == null) {
            createJob();
        }
        
        currentJob.appendHTML(html, charset);
    }
    
    /**
     * appendPDF adds a PDF file PrintJobElement to a PostScript PrintJob
     * 
     * @param url
     * @param charset 
     */
    public void appendPDF(ByteArrayBuilder url, Charset charset) {
        if(currentJob == null) {
            createJob();
        }
        
        currentJob.appendPDF(url, charset);
    }
    
    /**
     * print will prepare the currentJob (or list of open jobs) for printing
     * 
     * @return Whether the print routine was successful
     */
    public boolean print() {
        if(currentPrinter == null) {
            LogIt.log("A printer has not been selected.");
            setException(new NullPrintServiceException("A printer has not been selected."));
            return false;
        }
        lastPrinterName = currentPrinter.getName();
        
        if(openJobs == 0) {
            LogIt.log(Level.WARNING, "No data has been provided.");
            setException(new NullCommandException("No data has been provided."));
            return false;
        }
        else if(openJobs == 1) {
            currentJob.setPrinter(currentPrinter);
            try {
                currentJob.prepareJob();
            }
            catch (InvalidRawImageException ex) {
                LogIt.log(Level.SEVERE, "Raw image error.", ex);
                setException(ex);
            }
            catch (NullCommandException ex) {
                LogIt.log(Level.SEVERE, "No data has been provided.", ex);
                setException(ex);
            }
            currentJob = null;
            openJobs = 0;
            return true;
        }
        else {
            synchronized(spool) {
                while(openJobs > 0) {
                    PrintJob job = spool.get(spool.size() - openJobs);
                    job.setPrinter(currentPrinter);
                    try {
                        job.prepareJob();
                    }
                    catch (InvalidRawImageException ex) {
                        LogIt.log(Level.SEVERE, "Raw image error.", ex);
                        setException(ex);
                    }
                    catch (NullCommandException ex) {
                        LogIt.log(Level.SEVERE, "No data has been provided.", ex);
                        setException(ex);
                    }
                    openJobs -= 1;
                }
            }
            currentJob = null;
            return true;
        }
    }
    
    /**
     * printToFile will set the file output path and prepare the current job
     * 
     * @param filePath The output file to write to
     */
    public void printToFile(String filePath) {
        if(currentJob != null) {
            lastPrinterName = "File";
            try {
                filePrinter.setOutputPath(filePath);
                currentJob.setPrinter(filePrinter);
                currentJob.prepareJob();
            } catch (InvalidRawImageException ex) {
                LogIt.log(Level.SEVERE, "Raw image error.", ex);
                setException(ex);
            } catch (NullCommandException ex) {
                LogIt.log(Level.SEVERE, "No data has been provided.", ex);
                setException(ex);
            } catch (InvalidFileTypeException ex) {
                LogIt.log(Level.SEVERE, "Invalid file type.", ex);
                setException(ex);
            }
            currentJob = null;
        }
        else {
            LogIt.log(Level.SEVERE, "No data has been provided.");
            setException(new NullCommandException("No data has been provided."));
        }
    }
    
    /**
     * printToHost will set the remote host and prepare the current job
     * 
     * @param jobHost The remote host to send to
     * @param jobPort The port on the remote host
     */
    public void printToHost(String jobHost, int jobPort) {
        if(currentJob != null) {
            lastPrinterName = "Remote Host";
            currentJob.setHostOutput(jobHost, jobPort);
            try {
                currentJob.prepareJob();
            } catch (InvalidRawImageException ex) {
                LogIt.log(Level.SEVERE, "Raw image error.", ex);
                setException(ex);
            } catch (NullCommandException ex) {
                LogIt.log(Level.SEVERE, "No data has been provided.", ex);
                setException(ex);
            }
            currentJob = null;
        }
        else {
            LogIt.log(Level.SEVERE, "No data has been provided.");
            setException(new NullCommandException("No data has been provided."));
        }
    }
    
    /**
     * Cancel a job
     * @param jobIndex The index of the job to cancel
     */
    public void cancelJob(int jobIndex) {
        synchronized(spool) {
            PrintJob job = spool.get(jobIndex);
            job.cancel();
            spool.set(jobIndex, job);
        }
    }
    
    /**
     * Get the queue info as a JSONArray
     * 
     * @return The queueInfo JSONArray
     */
    public JSONArray getQueueInfo() {
        return queueInfo;
    }
    
    /**
     * Returns a string with the contents of the job data. This is only really
     * useful for Raw data PrintJobs
     * 
     * @param jobIndex The index of the job to get info for
     * @return A String representation of the job data
     */
    public String getJobInfo(int jobIndex) {
        PrintJob job = spool.get(jobIndex);
        String jobInfo = job.getInfo();
        LogIt.log("Job Data: " + jobInfo);
        return jobInfo;
    }

    /**
     * Get a list of all printers current installed and update the local
     * printerList variable
     */
    public void findAllPrinters() {
        
        PrintService[] psList;
        
        psList = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService ps : psList) {
            PrintServiceAttributeSet psa = ps.getAttributes();
            
            if(!"".equals(printerListString)) {
                printerListString += ",";
            }
            String printerName = psa.get(PrinterName.class).toString();
            printerListString += printerName;
            
            Printer printer;
            
            if(ps.isDocFlavorSupported(DocFlavor.INPUT_STREAM.POSTSCRIPT)) {
                printer = (PSPrinter)new PSPrinter();
            }
            else {
                printer = (RawPrinter)new RawPrinter();
            }
            
            printer.setPrintService(ps);
            printer.setName(printerName);
            printerList.add(printer);
        }
        
    }
    
    /**
     * Return the printer list
     *
     * @return A comma delimited string of printers
     */
    public String getPrinters() {
        return printerListString;
    }

    /**
     * Search for a printer by name. This function uses a set of searches to 
     * attempt to find the intended printer.
     * 
     * @param printerName The name (or partial name) of the printer to find
     */
    public void findPrinter(String printerName) {
        
        currentPrinter = null;
        
        // If printer name is null, get default printer
        if(printerName == null) {
            ListIterator<Printer> iterator = printerList.listIterator();
            while(iterator.hasNext()) {
                Printer printer = iterator.next();
                if(printer.getPrintService().equals(defaultPS)) {
                    currentPrinter = printer;
                    break;
                }
            }
        }
        else {
            // Do a 3 pass compare to match the printer. Look for an exact match
            // then a match containing the string, then a lowercase match 
            // containing the string
            
            // First Pass - exact match
            ListIterator<Printer> exactMatch = printerList.listIterator();
            while(exactMatch.hasNext()) {
                Printer printer = exactMatch.next();
                if(printer.getName().equals(printerName)) {
                    currentPrinter = printer;
                    break;
                }
            }
            // Second Pass (if needed) - contains match
            if(currentPrinter == null) {
                ListIterator<Printer> containsMatch = printerList.listIterator();
                while(containsMatch.hasNext()) {
                    Printer printer = containsMatch.next();
                    if(printer.getName().indexOf(printerName) != -1) {
                        currentPrinter = printer;
                        break;
                    }
                }
            }
            // Third Pass (if needed) - lowercase contains match
            if(currentPrinter == null) {
                ListIterator<Printer> containsMatch = printerList.listIterator();
                while(containsMatch.hasNext()) {
                    Printer printer = containsMatch.next();
                    if(printer.getName().toLowerCase().indexOf(printerName.toLowerCase()) != -1) {
                        currentPrinter = printer;
                        break;
                    }
                }
            }
        }
        
        if(currentPrinter != null) {
            LogIt.log("Found printer \"" + currentPrinter.getName() + "\".");
        }
        else {
            LogIt.log(Level.WARNING, "Could not find printer with name containing \"" + printerName + "\".");
        }
    }

    /**
     * Set the current printer.
     * 
     * @param printerIndex The index of the printer in printerList
     */
    public void setPrinter(int printerIndex) {
        currentPrinter = printerList.get(printerIndex);
    }

    /**
     * Get the current printer's name.
     * 
     * @return The current printer's name.
     */
    public String getPrinter() {
        if(currentPrinter != null) {
            return currentPrinter.getName();
        }
        else {
            return null;
        }
    }
    
    /**
     * Get the printer name of the printer used in the last printed job
     * 
     * @return The last printer's name
     */
    public String getLastPrinter() {
        if(lastPrinterName != null) {
            return lastPrinterName;
        }
        else {
            return null;
        }
    }

    /**
     * Set the paper size for new jobs.
     * 
     * @param paperSize 
     */
    public void setPaperSize(PaperFormat paperSize) {
        this.paperSize = paperSize;
        
    }

    /**
     * Toggle whether PostScript autosizing should be enabled
     * 
     * @param autoSize 
     */
    public void setAutoSize(boolean autoSize) {
        this.autoSize = autoSize;
    }
    
    /**
     * Return the logPSFeatures boolean
     * 
     * @return logPSFeatures
     */
    public boolean getLogPostScriptFeatures() {
        return logPSFeatures;
    }

    /**
     * Set the PostScript feature logging variable
     * 
     * @param logPSFeatures The new value
     */
    public void setLogPostScriptFeatures(boolean logPSFeatures) {
        this.logPSFeatures = logPSFeatures;
        if(currentJob != null) {
            currentJob.setLogPostScriptFeatures(logPSFeatures);
        }
        LogIt.log("Console logging of PostScript printing features set to \"" + logPSFeatures + "\"");
    }

    /**
     * Set the character that marks the end of the document in spooled raw
     * print jobs
     * 
     * @param endOfDocument The end of document character
     */
    public void setEndOfDocument(String endOfDocument) {
        this.endOfDocument = endOfDocument;
        LogIt.log("End of Document set to " + this.endOfDocument);
    }

    /**
     * Set the documents to spool together per job in spooled raw print jobs
     * 
     * @param docsPerSpool The number of documents per spooled job
     */
    public void setDocumentsPerSpool(int docsPerSpool) {
        this.docsPerSpool = docsPerSpool;
        LogIt.log("Documents per Spool set to " + this.docsPerSpool);
    }

    /**
     * getFileData will read a file's data and return it as a String
     * 
     * @param url The path of the file.
     * @param charset The charset of the file
     * @return A String of the file's data
     */
    public String getFileData(ByteArrayBuilder url, Charset charset) {
        
        String file;
        String data = null;
        
        try {
            file = new String(url.getByteArray(), charset.name());
            data = new String(FileUtilities.readRawFile(file), charset.name());
        } catch (UnsupportedEncodingException ex) {
            LogIt.log(Level.SEVERE, "Unsupported encoding.", ex);
        } catch (IOException ex) {
            LogIt.log(Level.SEVERE, "Could not retrieve file data.", ex);
        }
        
        return data;
    }

    /**
     * Find the machine's ip and mac address and set the local variables
     */
    public void findNetworkInfo() {
        
        if(networkUtilities == null) {
            try {
                networkUtilities = new NetworkUtilities();
            } catch (SocketException ex) {
                LogIt.log(Level.SEVERE, "Socket error.", ex);
            } catch (ReflectException ex) {
                LogIt.log(Level.SEVERE, "Reflection error.", ex);
            } catch (UnknownHostException ex) {
                LogIt.log(Level.SEVERE, "Unknown host.", ex);
            }
        }
        
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    networkUtilities.gatherNetworkInfo();
                } catch (IOException ex) {
                    LogIt.log(Level.SEVERE, "Could not gather network info.", ex);
                } catch (ReflectException ex) {
                    LogIt.log(Level.SEVERE, "Reflection error.", ex);
                }
                return null;
            }
        });

        macAddress = networkUtilities.getHardwareAddress();
        ipAddress = networkUtilities.getInetAddress();
        LogIt.log("Found Network Adapter. MAC: " + macAddress + " IP: " + ipAddress);

    }

    /**
     * Getter for Mac Address
     * 
     * @return The machine's Mac Address
     */
    public String getMac() {
        return macAddress;
    }

    /**
     * Getter for IP Address
     * 
     * @return The machine's IP Address
     */
    public String getIP() {
        return ipAddress;
    }
    
    /**
     * Turn alternate printing on or off
     * 
     * @param alternatePrint The new value of alternatePrint
     */
    public void useAlternatePrinting(boolean alternatePrint) {
        this.alternatePrint = alternatePrint;
        
        if(currentJob != null) {
            currentJob.setAlternatePrinting(alternatePrint);
        }
        
        LogIt.log("Alternate printing set to " + alternatePrint);
    }
    
    /**
     * Getter for alternate printing setting
     * @return Alternate printing boolean
     */
    public boolean isAlternatePrinting() {
        return alternatePrint;
    }

    /**
     * findPorts starts the process of finding the list of serial ports.
     */
    public void findPorts() {
        if(serialEnabled) {
            serialPrinter.findPorts();
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
        }
    }

    /**
     * Return a comma delimited String of all available serial ports
     * 
     * @return The list of ports
     */
    public String getPorts() {
        if(serialEnabled) {
            return serialPrinter.getPorts();
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
            return "";
        }
    }

    /**
     * openPort creates a port reference and opens it.
     * 
     * @param portName The name of the port to open
     */
    public void openPort(String portName) {
        if(serialEnabled) {
            serialPrinter.openPort(portName);
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
        }
    }

    /**
     * closePort closes the currently open port. A port name is provided but is
     * only used in the log. Since only one port can be open at a time, 
     * closePort does not require you to specify the correct port.
     * 
     * @param portName The name of the port to close. Only used in log.
     */
    public void closePort(String portName) {
        if(serialEnabled) {
            serialPrinter.closePort(portName);
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
        }
    }

    /**
     * Set the character to mark the beginning of returned serial data.
     * 
     * @param serialBegin The beginning character.
     */
    public void setSerialBegin(ByteArrayBuilder serialBegin) {
        if(serialEnabled) {
            serialPrinter.setSerialBegin(serialBegin);
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
        }
    }

    /**
     * Set the character to mark the ending of returned serial data.
     * 
     * @param serialEnd The ending character.
     */
    public void setSerialEnd(ByteArrayBuilder serialEnd) {
        if(serialEnabled) {
            serialPrinter.setSerialEnd(serialEnd);
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
        }

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
        if(serialEnabled) {
            serialPrinter.setSerialProperties(baud, dataBits, stopBits, parity, flowControl);
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
        }
    }

    /**
     * Send serial data to the opened port.
     * 
     * @param serialData A string of the data to send.
     */
    public void sendSerialData(String serialData) {
        if(serialEnabled) {
            serialPrinter.send(serialData);
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
        }
    }

    /**
     * Get any returned serial data.
     * 
     * @return The returned data
     */
    public String getReturnData() {
        if(serialEnabled) {
            return serialPrinter.getReturnData();
        }
        else {
            LogIt.log(Level.WARNING, "Serial functionality has been disabled.");
            return "";
        }
    }

    /**
     * Set the current exception. This set of functions is used to share
     * exception information with the JavaScript layer
     * 
     * @param t The Throwable attached to the Exception
     */
    public void setException(Throwable t) {
        this.exception = t;
    }
    
    /**
     * Clear the current exception.
     */
    public void clearException() {
        exception = null;
    }
    
    /**
     * Get the current exception
     * 
     * @return The current exception
     */
    public Throwable getException() {
        return exception;
    }
    
}
