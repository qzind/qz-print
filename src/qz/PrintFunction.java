package qz;

import qz.common.*;
import qz.exception.InvalidFileTypeException;
import qz.exception.NullPrintServiceException;
import qz.exception.SerialException;
import qz.printer.ImageWrapper;
import qz.printer.LanguageType;
import qz.printer.PaperFormat;
import qz.printer.PrintServiceMatcher;
import qz.reflection.ReflectException;
import qz.utils.ByteUtilities;
import qz.utils.FileUtilities;
import qz.utils.NetworkUtilities;

import javax.imageio.ImageIO;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.applet.Applet;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PrintFunction extends Applet {

    private static final Logger log = Logger.getLogger(PrintFunction.class.getName());

    protected LanguageType lang;
    protected PrintService ps;
    protected PrintRaw printRaw;
    protected SerialIO serialIO;
    protected PrintPostScript printPS;
    protected PrintHTML printHTML;
    //protected NetworkHashMap networkHashMap;
    protected NetworkUtilities networkUtilities;
    protected Throwable t;
    protected PaperFormat paperSize;
    protected boolean reprint;
    protected boolean psPrint;
    protected boolean htmlPrint;
    protected boolean alternatePrint;
    protected boolean logFeaturesPS;
    protected int imageX = 0;
    protected int imageY = 0;
    protected int dotDensity = 32;

    protected String jobName;
    protected String file;
    //protected String orientation;
    //protected Boolean maintainAspect;
    protected int copies = -1;
    protected Charset charset = Charset.defaultCharset();
    //protected String pageBreak; // For spooling pages one at a time
    protected int documentsPerSpool = 0;
    protected String endOfDocument;
    //protected String manualBreak = "%/SPOOL/%";


    public void useAlternatePrinting() {
        this.useAlternatePrinting(true);
    }

    public void useAlternatePrinting(boolean alternatePrint) {
        this.alternatePrint = alternatePrint;
    }

    public boolean isAlternatePrinting() {
        return this.alternatePrint;
    }

    protected boolean isRawAutoSpooling() throws UnsupportedEncodingException {
        return documentsPerSpool > 0 && endOfDocument != null && !getPrintRaw().isClear() && getPrintRaw().contains(endOfDocument);
    }

    public void setLogPostScriptFeatures(boolean logFeaturesPS) {
        this.logFeaturesPS = logFeaturesPS;
        log.info("Console logging of PostScript printing features set to \"" + logFeaturesPS + "\"");
    }

    public boolean getLogPostScriptFeatures() {
        return this.logFeaturesPS;
    }

    protected void resetVariables() {
        jobName = "QZ-PRINT ___ Printing";
        psPrint = false;
        logFeaturesPS = false;
        alternatePrint = false;
    }

    /**
     * Returns a comma separated list of printer names.
     * @return Comma separated list containing printer names
     */
    public String getPrinters() {
        return PrintServiceMatcher.getPrinterListing();
    }

    /**
     * Returns a comma separated list of serial ports.
     * @return Comma separated list containing serial "COM" ports
     */
    public String getPorts() {
        return getSerialIO().getSerialPorts();
    }

    public void append64(String base64) {
        try {
            getPrintRaw().append(Base64.decode(base64));
        } catch (IOException e) {
            set(e);
        }
    }

    public void appendHTMLFile(String file) {
        this.file = file;

        try{
            appendHTML(new String(FileUtilities.readRawFile(file), charset.name()));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error appending data", e);
            set(e);
        }
    }

    public void appendHTML(String html) {
        getPrintHTML().append(html);
    }

    /**
     * Gets the first xml node identified by <code>tagName</code>, reads its
     * contents and appends it to the buffer. Assumes XML content is base64
     * formatted.
     *
     * @param file URL reference to the xml file
     * @param xmlTag xml tag to look for
     */
    public void appendXML(String file, String xmlTag) {
        this.file = file;

        try{
            append64(FileUtilities.readXMLFile(file, xmlTag));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error appending data", e);
            set(e);
        }
    }

    /**
     * Appends the entire contents of the specified file to the buffer
     *
     * @param file URL location of the file
     */
    public void appendFile(String file) {
        this.file = file;

        try{
            getPrintRaw().append(FileUtilities.readRawFile(file));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error appending data", e);
            set(e);
        }
    }

    /**
     *
     * @param file URL location of the file
     */
    public void appendImage(String file) {
        this.file = file;

        try{
            readImage();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error appending data", e);
            set(e);
        }
    }

    public void appendPDF(String file) {
        this.file = file;

        try{
            getPrintPS().setPDF(ByteBuffer.wrap(ByteUtilities.readBinaryFile(file)));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error appending data", e);
            set(e);
        }
    }

    public void setLanguage(String lang) {
        this.lang = LanguageType.getType(lang);
    }

    /**
     * Appends a raw image from URL specified in the language format specified.
     * For CPCL and EPL, x and y coordinates should *always* be supplied. If
     * they are not supplied, they will default to position 0,0.
     *
     * @param imageFile URL location of image file
     * @param lang language of printer
     * @param image_x x location to print image
     * @param image_y y location to print image
     */
    public void appendImage(String imageFile, String lang, int image_x, int image_y) {
        this.imageX = image_x;
        this.imageY = image_y;
        appendImage(imageFile, lang);
    }

    /**
     * Appends a raw image from URL specified in the language format specified.
     *
     * @param imageFile URL path to the image to be appended. Can be .PNG, .JPG,
     * .GIF, .BMP (anything that can be converted to a
     * <code>BufferedImage</code>) Cannot be a relative path, since there's no
     * guarantee that the applet is aware of the browser's location.href.
     * @param lang Usually "ESCP", "EPL", "ZPL", etc. Parsed by
     * <code>LanguageType</code> class.
     */
    public void appendImage(String imageFile, String lang) {
        appendImage(imageFile, lang, this.dotDensity);
    }

    /**
     * ESCP only. Appends a raw image from URL specified in the language format
     * specified using the <code>dotDensity</code> specified. Convenience method
     * for
     * <code>appendImage(String imageFile, String lang, int dotDensity)</code>
     * where dotDenity is <code>32</code> "single" or <code>33</code> "double".
     *
     * @param imageFile URL path to the image to be appended. Can be .PNG, .JPG,
     * .GIF, .BMP (anything that can be converted to a
     * <code>BufferedImage</code>) Cannot be a relative path, since there's no
     * guarantee that the applet is aware of the browser's location.href.
     * @param lang Usually "ESCP", "EPL", "ZPL", etc. Parsed by
     * <code>LanguageType</code> class.
     * @param dotDensity Should be either "single", "double" or "triple". Triple
     * being the highest resolution.
     */
    public void appendImage(String imageFile, String lang, String dotDensity) {
        if (dotDensity.equalsIgnoreCase("single")) {
            this.dotDensity = 32;
        } else if (dotDensity.equalsIgnoreCase("double")) {
            this.dotDensity = 33;
        } else if (dotDensity.equalsIgnoreCase("triple")) {
            this.dotDensity = 39;
        } else {
            log.warning("Cannot translate dotDensity value of '"
                                + dotDensity + "'.  Using '" + this.dotDensity + "'.");
        }

        appendImage(imageFile, lang, this.dotDensity);
    }

    /**
     * ESCP only. Appends a raw image from URL specified in the language format
     * specified using the <code>dotDensity</code> specified.
     *
     * @param imageFile URL path to the image to be appended. Can be .PNG, .JPG,
     * .GIF, .BMP (anything that can be converted to a
     * <code>BufferedImage</code>) Cannot be a relative path, since there's no
     * guarantee that the applet is aware of the browser's location.href.
     * @param lang Usually "ESCP", "EPL", "ZPL", etc. Parsed by
     * <code>LanguageType</code> class.
     * @param dotDensity From the <code>ESC *</code> section of the ESC/P
     * programmer's manual. Default = 32
     */
    public void appendImage(String imageFile, String lang, int dotDensity) {
        this.dotDensity = dotDensity;
        setLanguage(lang);

        finishAppendImage(imageFile);
    }

    protected void finishAppendImage(String imageFile) {
        file = imageFile;

        try{
            BufferedImage bi;
            ImageWrapper iw;
            if (ByteUtilities.isBase64Image(imageFile)){
                byte[] imageData = Base64.decode(imageFile.split(",")[1]);
                bi = ImageIO.read(new ByteArrayInputStream(imageData));
            }else{
                bi = ImageIO.read(new URL(imageFile));
            }
            iw = new ImageWrapper(bi, lang);
            iw.setCharset(charset);
            // Image density setting (ESCP only)
            iw.setDotDensity(dotDensity);
            // Image coordinates, (EPL only)
            iw.setxPos(imageX);
            iw.setyPos(imageY);
            getPrintRaw().append(iw.getImageCommand());
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error appending data", e);
            set(e);
        }
    }

    /**
     * Returns the orientation as it has been recently defined. Default is null
     * which will allow the printer configuration to decide.
     *
     * @return orientation of the printer
     */
    public String getOrientation() {
        return this.paperSize.getOrientationDescription();
    }

    public void setOrientation(String orientation) {
        if (this.paperSize == null) {
            log.warning("A paper size must be specified before setting orientation!");
        } else {
            this.paperSize.setOrientation(orientation);
        }
    }

    public void printToFile() {
        printToFile(null);
    }

    public void printToHost(String host) {
        printToHost(host, 9100);
    }

    public void printToHost(String host, String port) {
        try {
            printToHost(host, Integer.parseInt(port));
        } catch (Throwable t) {
            this.set(t);
        }
    }

    public void printToHost(String host, int port) {
        if (!ByteUtilities.isBlank(host) && port > 0) {
            getPrintRaw().setOutputSocket(host, port);
        } else {
            this.set(new NullPrintServiceException("Invalid port or host specified.  "
                    + "Port values must be non-zero positive integers.  "
                    + "Host values must not be empty"));
            this.clear();
            return;
        }
        print();
    }

    public void printToFile(String outputPath) {
        if (!ByteUtilities.isBlank(outputPath)) {
            try {
                getPrintRaw().setOutputPath(outputPath);
            } catch (InvalidFileTypeException e) {
                this.set(e);
                this.clear();
                return;
            }
        } else {
            this.set(new NullPrintServiceException("Blank output path supplied"));
            this.clear();
            return;
        }
        print();
    }

    // Due to applet security, can only be invoked by run() thread
    protected void readImage() {
        try {
            // Use the in-line base64 content as our image
            if (ByteUtilities.isBase64Image(file)) {
                getPrintPS().setImage(Base64.decode(file.split(",")[1]));
            } else {
                getPrintPS().setImage(ImageIO.read(new URL(file)));
            }
        } catch (IOException ex) {
            log.log(Level.WARNING, "Error reading specified image", ex);
        }
    }

    // Use this instead of calling p2d directly.  This will allow 2d graphics
    // to only be used when absolutely needed
    protected PrintPostScript getPrintPS() {
        if (this.printPS == null) {
            this.printPS = new PrintPostScript();
            this.printPS.setPrintParameters(getJobName(), getCopies(), getLogPostScriptFeatures());
        }
        return printPS;
    }

    protected PrintHTML getPrintHTML() {
        if (this.printHTML == null) {
            this.printHTML = new PrintHTML();
            this.printHTML.setPrintParameters(getJobName(), getCopies());
        }
        return printHTML;
    }

    /**
     * Appends raw hexadecimal bytes in the format "x1Bx00", etc.
     *
     * @param s string with hex byte codes such as x1Bx00 ...
     */
    public void appendHex(String s) {
        try {
            getPrintRaw().append(ByteUtilities.hexStringToByteArray(s));
        } catch (NumberFormatException e) {
            this.set(e);
        }
    }


    public void append(String s) {
        try {
            getPrintRaw().append(s.getBytes(charset.name()));
        } catch (UnsupportedEncodingException ex) {
            this.set(ex);
        }
    }

    /**
     * Makes appending the unicode null character possible by appending
     * the equivalent of <code>\x00</code> in JavaScript, which is syntactically
     * invalid in JavaScript (no errors will be thrown, but Strings will be
     * terminated prematurely
     */
    public void appendNull() {
        getPrintRaw().append(new byte[]{'\0'});
    }

    /**
     * Clears the cached raw commands. PrintRaw only.
     */
    public void clear() {
        getPrintRaw().clear();
    }

    /**
     * Performs an asynchronous print and handles the output of exceptions and
     * debugging. Important: print() clears any raw buffers after printing. Use
     * printPersistent() to save the buffer to be used/appended to later.
     */
    public void print() {
        log.info("===== SENDING DATA TO THE PRINTER =====");

        try {
            if (isRawAutoSpooling()) {
                LinkedList<ByteArrayBuilder> pages = ByteUtilities.splitByteArray(
                        getPrintRaw().getByteArray(),
                        endOfDocument.getBytes(charset.name()),
                        documentsPerSpool);

                //FIXME:  Remove this debug line
                log.info("Automatically spooling to " + pages.size() + " separate print job(s)");

                for (ByteArrayBuilder b : pages) {
                    logAndPrint(getPrintRaw(), b.getByteArray());
                }

                if (!reprint) {
                    getPrintRaw().clear();
                }
            } else {
                logAndPrint(getPrintRaw());
            }
        } catch (Exception e) {
            set(e);
        } finally {
            if (this.printRaw != null) {
                getPrintRaw().clear();
            }
        }
    }

    public void init(){
        resetVariables();
        super.init();
    }

    public boolean isActive(){
        return isActive(false);
    }

    protected boolean isActive(boolean applet) {
        return (!applet || isActive());
    }

    public void printHTML() {
        try {
            logAndPrint(getPrintHTML());
        }
        catch(Exception e){
            set(e);
        }
        finally {
            if (this.printRaw != null) {
                getPrintRaw().clear();
            }
        }
    }

    public void printPS() {
        try {
            logAndPrint(getPrintPS());
        }
        catch(Exception e){
            set(e);
        }
        finally {
            if (this.printRaw != null) {
                getPrintRaw().clear();
            }
        }
    }

    public void findPrinters() {
        PrintServiceMatcher.getPrinterArray(true);
    }

    public void findPrinter() {
        findPrinter(null);
    }

    /**
     * Creates the print service by iterating through printers until finding
     * matching printer containing "printerName" in its description
     *
     * @param printer name of printer
     */
    public void findPrinter(String printer) {
        log.info("===== SEARCHING FOR PRINTER =====");

        if (printer == null) {
            PrintFunction.this.setPrintService(PrintServiceLookup.lookupDefaultPrintService());
        } else {
            PrintFunction.this.setPrintService(PrintServiceMatcher.findPrinter(printer));
        }
    }

    /**
     * Uses the JSSC JNI library to retrieve a comma separated list of serial
     * ports from the system, i.e. "COM1,COM2,COM3" or "/dev/tty0,/dev/tty1",
     * etc.
     */
    public void findPorts() {
        log.info("===== SEARCHING FOR SERIAL PORTS =====");
        getSerialIO().fetchSerialPorts();
    }

    public void setSerialBegin(String begin) {
        try {
            getSerialIO().setBegin(begin.getBytes(charset.name()));
        } catch (UnsupportedEncodingException ex) {
            this.set(ex);
        }
    }

    public void setSerialEnd(String end) {
        try {
            getSerialIO().setEnd(end.getBytes(charset.name()));
        } catch (UnsupportedEncodingException ex) {
            this.set(ex);
        }
    }

    public void send(String portName, String data) {
        try {
            if (!getSerialIO().isOpen()) {
                throw new SerialException("A port has not yet been opened.");
            } else if (getSerialIO().getPortName().equals(portName)) {
                getSerialIO().append(data.getBytes(charset.name()));

                try {
                    logCommands(new String(getSerialIO().getInputBuffer().getByteArray(), charset.name()));
                    getSerialIO().send();
                } catch (Throwable t) {
                    this.set(t);
                }

            } else {
                throw new SerialException("Port specified [" + portName + "] "
                        + "differs from previously opened port "
                        + "[" + getSerialIO().getPortName() + "].  Applet currently "
                        + "supports only one open port at a time.  Data not sent.");
            }
        } catch (Throwable t) {
            this.set(t);
        }
    }

    public void setSerialProperties(int baud, int dataBits, String stopBits, int parity, String flowControl) {
        setSerialProperties(Integer.toString(baud), Integer.toString(dataBits),
                stopBits, Integer.toString(parity), flowControl);
    }

    public void setSerialProperties(String baud, String dataBits, String stopBits, String parity, String flowControl) {
        try {
            getSerialIO().setProperties(baud, dataBits, stopBits, parity, flowControl);
        } catch (Throwable t) {
            this.set(t);
        }
    }

    public void closePort(String portName) {
        if (getSerialIO().getPortName().equals(portName)) {
            finishClosePort(portName);
        } else {
            this.set(new SerialException("Port specified [" + portName + "] "
                    + "could not be closed. Please close "
                    + "[" + getSerialIO().getPortName() + "] instead. "
                    + "Applet currently supports only one open port at a time."));
        }
    }

    protected void finishClosePort(String portName){
        log.info("===== CLOSING SERIAL PORT " + portName + " =====");

        try {
            getSerialIO().close();
        } catch (Throwable t) {
            this.set(t);
        }
    }

    public void openPort(String serialPortName) {
        openPort(serialPortName, false);
    }

    public void openPort(String serialPortName, boolean autoSetSerialProperties) {
        openPort(serialPortName, -1, autoSetSerialProperties);
    }

    public void openPort(int serialPortIndex) {
        openPort(serialPortIndex, false);
    }

    public void openPort(int serialPortIndex, boolean autoSetSerialProperties) {
        openPort(null, serialPortIndex, autoSetSerialProperties);
    }

    protected void openPort(String serialPortName, int serialPortIndex, boolean autoSetSerialProperties){
        log.info("===== OPENING SERIAL PORT " + serialPortName + " =====");

        try {
            if (serialPortIndex != -1) {
                getSerialIO().open(serialPortIndex);
            } else {
                getSerialIO().open(serialPortName);
            }
            // Currently a Windows-only feature
            if (autoSetSerialProperties) {
                getSerialIO().autoSetProperties();
            }
        } catch (Throwable t) {
            //notifyBrowser("qzPortNotOpened", getSerialIO().getPortName());
            set(t);
        }
    }

    /**
     * Returns the PrintService's name (the printer name) associated with this
     * applet, if any. Returns null if none is set.
     *
     * @return name of the printer
     */
    public String getPrinter() {
        return ps == null ? null : ps.getName();
        //return ps.getName();
    }

    public SerialIO getSerialIO() {
        try {
            Class.forName("jssc.SerialPort");
            if (this.serialIO == null) {
                this.serialIO = new SerialIO();
            }
            return serialIO;
        } catch (ClassNotFoundException e) {
            // Raise our exception
            this.set(e);
        }
        return null;
    }

    /**
     * Returns the PrintRaw object associated with this applet, if any. Returns
     * null if none is set.
     *
     * @return raw print object
     */
    protected PrintRaw getPrintRaw() {
        if (this.printRaw == null) {
            this.printRaw = new PrintRaw();
            this.printRaw.setPrintParameters(getJobName(), isAlternatePrinting(), getCopies());
        }
        return printRaw;
    }

    protected NetworkUtilities getNetworkUtilities() throws SocketException, ReflectException, UnknownHostException {
        if (this.networkUtilities == null) {
            this.networkUtilities = new NetworkUtilities();
        }
        return this.networkUtilities;
    }

    public String getIP() {
        return this.getIPAddress();
    }

    public void setHostname(String hostname) throws SocketException, ReflectException, UnknownHostException {
        getNetworkUtilities().setHostname(hostname);
    }

    public void setPort(int port) throws SocketException, ReflectException, UnknownHostException {
        getNetworkUtilities().setPort(port);
    }

    /**
     * Returns a comma separated <code>String</code> containing all MAC
     * Addresses found on the system, or <code>null</code> if none are found.
     *
     * @return MAC addresses found on system
     */

    public String getMac() {
        return this.getMacAddress();
    }

    /**
     * Retrieves a <code>String</code> containing a single MAC address. i.e.
     * 0A1B2C3D4E5F. This attempts to get the quickest and most appropriate
     * match for systems with a single adapter by attempting to choose an
     * enabled and non-loopback adapter first if possible.
     * <strong>Note:</strong> If running JRE 1.5, Java won't be able to
     * determine "enabled" or "loopback", so it will attempt to use other methods
     * such as filtering out the 127.0.0.1s, etc.
     * information. Returns <code>null</code> if no adapters are found.
     *
     * @return first MAC address
     */
    public String getMacAddress() {
        try {
            return getNetworkUtilities().getHardwareAddress();
        } catch (Throwable t) {
            return null;
        }
        //return getNetworkHashMap().getLightestNetworkObject().getMacAddress();
    }

     /**
     * Retrieves a <code>String</code> containing a single IP address. i.e.
     * 192.168.1.101 or fe80::81ca:bcae:d6c4:9a16%25 (formatted IPv4 or IPv6)
     * This attempts to get the most appropriate match for
     * systems with a single adapter by attempting to choose an enabled and
     * non-loopback adapter first if possible, however if multiple IPs exist,
     * it will return the first found, regardless of protocol or use.
     * <strong>Note:</strong> If running JRE 1.5, Java won't be able to
     * determine "enabled" or "loopback", so it will attempt to use other methods
     * such as filtering out the 127.0.0.1 addresses, etc.
     * information. Returns <code>null</code> if no adapters are found.
     *
     * @return IP address of system
     */
    public String getIPAddress() {
         //return getNetworkHashMap().getLightestNetworkObject().getInetAddress();
        try {
            return getNetworkUtilities().getInetAddress();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns the PrintService object associated with this applet, if any.
     * Returns null if none is set.
     *
     * @return PrintService object
     */
    protected PrintService getPrintService() {
        return ps;
    }

    public Throwable getError() {
        return getException();
    }

    public Throwable getException() {
        return t;
    }

    public void clearException() {
        t = null;
    }

    public String getVersion() {
        return Constants.VERSION;
    }

    public void setEndOfDocument(String endOfPage) {
        this.endOfDocument = endOfPage;
    }

    public void setPrinter(int index) {
        setPrintService(PrintServiceMatcher.getPrinterList()[index]);
        log.info("Printer set to index: " + index + ",  Name: " + ps.getName());

        //PrinterState state = (PrinterState)this.ps.getAttribute(PrinterState.class);
        //return state == PrinterState.IDLE || state == PrinterState.PROCESSING;
    }

    // Generally called internally only after a printer is found.
    protected void setPrintService(PrintService ps) {
        this.ps = ps;
        if (ps == null) {
            log.warning("Setting null PrintService");
            log.warning("Setting null PrintService");
            return;
        }
        if (getPrintHTML() != null) {
            printHTML.setPrintService(ps);
        }
        if (getPrintPS() != null) {
            printPS.setPrintService(ps);
        }
        if (getPrintRaw() != null) {
            printRaw.setPrintService(ps);
        }
    }

    public void setDocumentsPerSpool(int pagesPer) {
        this.documentsPerSpool = pagesPer;
    }

    public String getJobName() {
        return jobName;
    }

    public void findNetworkInfo() {
        log.info("===== GATHERING NETWORK INFORMATION =====");

        //getNetworkHashMap().clear();
        try {
            // Gather the network information and store in a custom HashMap
            //for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
            //    getNetworkHashMap().put(en.nextElement());
            //}
            getNetworkUtilities().gatherNetworkInfo();
        } catch (IOException e) {
            set(e);
        } catch (ReflectException e) {
            log.log(Level.SEVERE, "getHardwareAddress not supported on Java 1.5", e);
            set(e);
        }

        //getNetworkUtilities().fetchNetworkInfo();
    }

    protected void set(Throwable t) {
        this.t = t;
        LogIt.log(t);
    }

    protected void logCommands(PrintHTML ph) {
        logCommands(ph.get());
    }

    protected void logCommands(PrintRaw pr) {
        logCommands(pr.getOutput());
    }

    protected void logCommands(byte[] commands) {
        try {
            logCommands(new String(commands, charset.name()));
        } catch (UnsupportedEncodingException ex) {
            log.warning("Cannot decode raw bytes for debug output. "
                    + "This could be due to incompatible charset for this JVM "
                    + "or mixed charsets within one byte stream.  Ignore this message"
                    + " if printing seems fine.");
        }
    }

    protected void logCommands(String commands) {
        log.info("\r\n\r\n" + commands + "\r\n\r\n");
    }

    protected void logAndPrint(PrintRaw pr, byte[] data) throws IOException, InterruptedException, PrintException {
        logCommands(data);
        pr.print(data);
    }

    protected void logAndPrint(PrintRaw pr) throws IOException, PrintException, InterruptedException {
        logCommands(pr);
        if (reprint) {
            pr.print();
        } else {
            pr.print();
            pr.clear();
        }
    }

    protected void logAndPrint(PrintPostScript printPS) throws PrinterException {
        logCommands("    <<" + file + ">>");
        printPS.setPaperSize(paperSize);
        if (copies > 0) {
            printPS.setCopies(copies);
        } else {
            printPS.setCopies(1);
        }
        printPS.print();
        psPrint = false;
        paperSize = null;
    }

    protected void logAndPrint(PrintHTML printHTML) throws PrinterException {
        if (file != null) {
            logCommands("    <<" + file + ">>");
        }
        logCommands(printHTML);

        printHTML.print();
        htmlPrint = false;
    }

    /**
     * Sets character encoding for raw printing only
     *
     * @param charset character encoding to use for raw printing
     */
    public void setEncoding(String charset) {
        // Example:  Charset.forName("US-ASCII");
        System.out.println("Default charset encoding: " + Charset.defaultCharset().name());
        try {
            this.charset = Charset.forName(charset);
            getPrintRaw().setCharset(Charset.forName(charset));
            log.info("Current applet charset encoding: " + this.charset.name());
        } catch (IllegalCharsetNameException e) {
            log.log(Level.WARNING, "Could not find specified charset encoding: "
                    + charset + ". Using default.", e);
        }

    }

    public String getEncoding() {
        return this.charset.displayName();
    }

    public Charset getCharset() {
        return this.charset;
    }

    public void setAutoSize(boolean autoSize) {
        if (this.paperSize == null) {
            log.warning("A paper size must be specified before setting auto-size!");
        } else {
            this.paperSize.setAutoSize(autoSize);
        }
    }

    public int getCopies() {
        if (copies > 0) {
            return copies;
        } else {
            return 1;
        }
    }

    public void setCopies(int copies) {
        if (copies > 0) {
            this.copies = copies;
        } else {
            log.log(Level.WARNING, "Copies must be greater than zero", new UnsupportedOperationException("Copies must be greater than zero"));
        }
    }

    protected PaperFormat getPaperSize() {
        return paperSize;
    }

    public void setPaperSize(String width, String height) {
        this.paperSize = PaperFormat.parseSize(width, height);
        log.info("Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }

    public void setPaperSize(float width, float height, String units) {
        this.paperSize = PaperFormat.parseSize("" + width, "" + height, units);
        log.info("Set paper size to " + paperSize.getWidth()
                + paperSize.getUnitDescription() + "x"
                + paperSize.getHeight() + paperSize.getUnitDescription());
    }


    /**
     * Returns the rotation as it has been recently defined
     *
     * @return Rotation value in degrees
     */
    public String getRotation() {
        return "" + getPaperSize().getRotation();
    }

    /**
     * Sets the rotation in degrees of the image being appended (PS only)
     * @param rotation Rotation in degrees
     */
    public void setRotation(String rotation) {
        if (paperSize != null) {
            paperSize.setRotation(Integer.parseInt(rotation));
        } else {
            LogIt.log(Level.WARNING, "Cannot set rotation until after setting paper size using setPaperSize(...)");
        }
    }

    @Override
    public void stop() {
        if (serialIO != null) {
            try {
                serialIO.close();
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Could not close port [" + serialIO.getPortName() + "].", t);
            }
        }
        super.stop();
    }
}
