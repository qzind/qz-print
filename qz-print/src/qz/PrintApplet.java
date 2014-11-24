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

import netscape.javascript.JSException;
import netscape.javascript.JSObject;
import qz.common.*;
import qz.exception.SerialException;
import qz.printer.PaperFormat;

import javax.print.PrintService;
import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An invisible web applet for use with JavaScript functions to send raw
 * commands to your thermal, receipt, shipping, barcode, card printer and much
 * more.
 *
 * @author A. Tres Finocchiaro
 */
public class PrintApplet extends PrintFunction implements Runnable {

    private static final Logger log = Logger.getLogger(PrintApplet.class.getName());

    private static final AtomicReference<Thread> thisThread = new AtomicReference<Thread>(null);
    private static final long serialVersionUID = 2787955484074291340L;
    private boolean running;
    private long sleep;

    private JSObject window = null;

    private int appendType;
    private boolean startFindingPrinters;
    private boolean doneFindingPrinters;
    private boolean startPrinting;
    private boolean startFindingNetwork;
    private boolean startAppending;
    private boolean startFindingPorts;
    private boolean startSending;
    private boolean startOpeningPort;
    private boolean startClosingPort;

    private boolean autoSetSerialProperties = false;
    private String serialPortName;
    private int serialPortIndex = -1;

    private boolean allowMultiple;
    private String xmlTag;
    private String printer;


    /**
     * Create a privileged thread that will listen for JavaScript events
     *
     * @since 1.1.7
     */
    //@Override
    public void run() {
        // TODO: RKC - Fix the import to make this work!
        // window = JSObject.getWindow(this);
        log.info("QZ-PRINT " + Constants.VERSION);
        log.info("===== JAVASCRIPT LISTENER THREAD STARTED =====");
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                //@Override

                public Object run() throws Exception {

                    startJavaScriptListener();
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            log.log(Level.SEVERE, "Error starting main JavaScript thread.  All else will fail.", e);
            set(e);
        } finally {
            log.info("===== JAVASCRIPT LISTENER THREAD STOPPED =====");
        }
    }

    /**
     * Starts the Applet and runs the JavaScript listener thread
     */
    private void startJavaScriptListener() {
        notifyBrowser("qzReady");
        while (running) {
            try {
                Thread.sleep(sleep);  // Wait 100 milli before running again
                if (startAppending) {
                    try {
                        switch (appendType) {
                            case Constants.APPEND_HTML:
                                super.appendHTMLFile(file);
                            case Constants.APPEND_XML:
                                super.appendXML(file, xmlTag);
                                break;
                            case Constants.APPEND_RAW:
                                super.appendFile(file);
                                break;
                            case Constants.APPEND_IMAGE_PS:
                                super.appendImage(file);
                                break;
                            case Constants.APPEND_IMAGE:
                                super.finishAppendImage(file);
                                break;
                            case Constants.APPEND_PDF:
                                super.appendPDF(file);
                                break;
                            default:
                                break; // Do nothing
                        }
                    } catch (Throwable t) {
                        log.log(Level.SEVERE, "Error appending data", t);
                        set(t);
                    }
                    startAppending = false;
                    setDoneAppending(true);
                }
                if (startFindingPorts) {
                    super.findPorts();

                    startFindingPorts = false;
                    setDoneFindingPorts(true);
                }
                if (startFindingNetwork) {
                    super.findNetworkInfo();

                    startFindingNetwork = false;
                    setDoneFindingNetwork(true);
                }
                if (startOpeningPort) {
                    super.openPort(serialPortName, serialPortIndex, autoSetSerialProperties);

                    startOpeningPort = false;
                    setDoneOpeningPort(true);
                }
                if (startClosingPort) {
                    super.finishClosePort(serialPortName);

                    startClosingPort = false;
                    setDoneClosingPort(true);
                }
                if (startFindingPrinters) {
                    super.findPrinter(printer);

                    startFindingPrinters = false;
                    setDoneFindingPrinters(true);
                }
                // Serial Port Stuff
                if (startSending) {
                    try {
                        startSending = false;
                        logCommands(new String(getSerialIO().getInputBuffer().getByteArray(), charset.name()));
                        getSerialIO().send();
                    } catch (Throwable t) {
                        this.set(t);
                    }
                }
                if (serialIO != null && serialIO.getOutput() != null) {
                    try {
                        notifyBrowser("qzSerialReturned",
                                      new Object[]{serialIO.getPortName(), new String(serialIO.getOutput(), charset.name())});
                    } catch (UnsupportedEncodingException ex) {
                        this.set(ex);
                    }
                    serialIO.clearOutput();
                }
                if (startPrinting) {
                    super.print();

                    startPrinting = false;
                    setDonePrinting(true);
                }
            } catch (InterruptedException e) {
                set(e);
            }
        }
    }

    private void setDonePrinting(boolean donePrinting) {
        this.copies = -1;
        this.notifyBrowser("qzDonePrinting");
    }

    private void setDoneFindingPrinters(boolean doneFindingPrinters) {
        this.doneFindingPrinters = doneFindingPrinters;
        this.notifyBrowser("qzDoneFinding");
    }

    private void setDoneOpeningPort(boolean doneOpeningPort) {
        this.notifyBrowser("qzDoneOpeningPort", getSerialIO() == null ? null : getSerialIO().getPortName());
    }

    private void setDoneClosingPort(boolean doneClosingPort) {
        this.notifyBrowser("qzDoneClosingPort", serialPortName);
    }

    private void setDoneFindingNetwork(boolean doneFindingNetwork) {
        this.notifyBrowser("qzDoneFindingNetwork");
    }

    private void setDoneFindingPorts(boolean doneFindingPorts) {
        this.notifyBrowser("qzDoneFindingPorts");
    }

    private void setDoneAppending(boolean doneAppending) {
        this.notifyBrowser("qzDoneAppending");
    }

    public void setLogPostScriptFeatures(boolean logFeaturesPS) {
        this.logFeaturesPS = logFeaturesPS;
        log.info("Console logging of PostScript printing features set to \"" + logFeaturesPS + "\"");
    }

    public boolean getLogPostScriptFeatures() {
        return this.logFeaturesPS;
    }

    protected void resetVariables() {
        super.resetVariables();

        running = true;
        startPrinting = false;
        startFindingPrinters = false;
        doneFindingPrinters = true;
        startFindingPorts = false;
        startOpeningPort = false;
        startClosingPort = false;
        startSending = false;
        startFindingNetwork = false;
        startAppending = false;
        sleep = getParameter("sleep", 100);
        appendType = 0;
        allowMultiple = false;
        String printer = getParameter("printer", null);
        LogIt.disableLogging = getParameter("disable_logging", false);
        if (printer != null) {
            findPrinter(printer);
        }
    }

    /**
     * Convenience method for calling a JavaScript function with a single
     * <code>String</code> parameter. The functional equivalent of
     * notifyBrowser(String function, new Object[]{String s})
     *
     * @param function JavaScript function to call with message
     * @param s message to be sent
     * @return true if successful
     */
    private boolean notifyBrowser(String function, String s) {
        return notifyBrowser(function, new Object[]{s});
    }

    /**
     * Override for <code>window.call(String, Object[])</code>
	 * Passes an async JavaScript call to <code>window.setTimout()</code> to fix
     * Google Chrome 36.0 (GitHub Bug #33)
     *
     * @param function The JavasScript function to call
     * @param params The parameter or array of parameters to send to the JavaScript
     * function
     */
    public void call(String function, Object[] params) throws JSException {
        String escapedString = function + "(";
        for (Object o : params) {
            if (o instanceof Integer) {
                escapedString += o + ",";
            } else if (o instanceof String) {
                escapedString += "'" + o + "'" + ",";
            }
        }
        if (escapedString.endsWith(",")) {
            escapedString = escapedString.substring(0, escapedString.indexOf(","));
        }

        escapedString += ")";

        Object[] p = new Object[]{escapedString, 0};
        window.call("setTimeout", p);

        log.info("Successfully called JavaScript function \""
                    + function + "(...)\"...");
    }


	/**
	 * Calls JavaScript function (i.e. "qzReady()" from the web browser For a period
	 * of time, will call "jzebraReady()" as well as "qzReady()" but fail silently
	 * on the old "jzebra" prefixed functions. If the "jzebra" equivalent is used,
	 * it will display a deprecation warning.
	 *
	 * @param function The JavasScript function to call
	 * @param params The parameter or array of parameters to send to the JavaScript
	 * function
	 * @return true if successful
	 */
    private boolean notifyBrowser(String function, Object[] params) {
        try {
            String type = (String)window.eval("typeof(" + function + ")");
            // Ubuntu doesn't properly raise exceptions when calling invalid
            // functions, so this is the work-around
            if (!type.equals("function")) {
                throw new Exception("Object \"" + function + "\" does not "
                        + "exist or is not a function.");
            }

            call(function, params);

            log.info("Successfully called JavaScript function \""
                    + function + "(...)\"...");
            if (function.startsWith("jzebra")) {
                log.warning("JavaScript function \"" + function
                        + "(...)\" is deprecated and will be removed in future releases. "
                        + "Please use \"" + function.replaceFirst("jzebra", "qz")
                        + "(...)\" instead.");
            }
            return true;
        } catch (Throwable e) {
            boolean success = false;
            if (function.startsWith("qz")) {
                // Try to call the old jzebra function
                success = notifyBrowser(function.replaceFirst("qz", "jzebra"), params);
            }
            if (function.equals("jebraDoneFinding")) {
                // Try to call yet another deprecated jzebra function
                success = notifyBrowser("jzebraDoneFindingPrinters", params);
            }
            // Warn about the function missing only if it wasn't recovered using the old jzebra name
            if (!success && !function.startsWith("jzebra")) {
                log.warning("Tried calling JavaScript function \""
                        + function + "(...)\" through web browser but it has not "
                        + "been implemented (" + e.getLocalizedMessage() + ")");
            }
            return success;
        }
    }

    /**
     * Convenience method for calling a JavaScript function with no parameters.
     * The functional equivalent of notifyBrowser(String function, new
     * Object[]{null})
     */
    private boolean notifyBrowser(String function) {
        return notifyBrowser(function, new Object[]{null});
    }

    /**
     * Overrides getParameter() to allow all upper or all lowercase parameter
     * names
     *
     * @param name name of parameter to retrieve
     * @param defaultVal default value if parameter not found
     * @return value of parameter
     */
    private String getParameter(String name, String defaultVal) {
        if (name != null) {
            try {
                String retVal = super.getParameter(name);
                retVal = isBlank(retVal) ? super.getParameter(name.toUpperCase()) : retVal;
                return isBlank(retVal) ? defaultVal : retVal;
            } catch (NullPointerException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    /**
     * Same as <code>getParameter(String, String)</code> except for a
     * <code>long</code> type.
     *
     * @param name name of parameter
     * @param defaultVal default value if not found
     * @return value of parameter
     */
    private long getParameter(String name, long defaultVal) {
        return Long.parseLong(getParameter(name, "" + defaultVal));
    }

    private boolean getParameter(String name, boolean defaultVal) {
        return Boolean.parseBoolean(getParameter(name, Boolean.toString(defaultVal)));
    }

    /**
     * Returns true if given String is empty or null
     *
     * @param s string to be checked
     * @return true if null or blank
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().equals("");
    }

    public void appendHTMLFile(String url) {
        this.appendType = Constants.APPEND_HTML;
        this.appendFromThread(url, appendType);
        //throw new UnsupportedOperationException("Sorry, not yet supported.");
    }

    /**
     * Gets the first xml node identified by <code>tagName</code>, reads its
     * contents and appends it to the buffer. Assumes XML content is base64
     * formatted.
     *
     * @param url URL reference to the xml file
     * @param xmlTag xml tag to look for
     */
    public void appendXML(String url, String xmlTag) {
        appendFromThread(url, Constants.APPEND_XML);
        this.xmlTag = xmlTag;
    }

    /**
     * Appends the entire contents of the specified file to the buffer
     *
     * @param url URL location of the file
     */
    public void appendFile(String url) {
        appendFromThread(url, Constants.APPEND_RAW);
    }

    /**
     *
     * @param url URL location of the file
     */
    public void appendImage(String url) {
        appendFromThread(url, Constants.APPEND_IMAGE_PS);
    }

    public void appendPDF(String url) {
        appendFromThread(url, Constants.APPEND_PDF);
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
        setLanguage(lang);
        appendFromThread(imageFile, Constants.APPEND_IMAGE);
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
        appendFromThread(imageFile, Constants.APPEND_IMAGE);
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
        setLanguage(lang);
        appendFromThread(imageFile, Constants.APPEND_IMAGE);
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
     * Appends a file of the specified type
     *
     * @param file URL location of file to be printed
     * @param appendType how to append the file
     */
    private void appendFromThread(String file, int appendType) {
        this.startAppending = true;
        this.appendType = appendType;
        this.file = file;
    }

    // Use this instead of calling p2d directly.  This will allow 2d graphics
    // to only be used when absolutely needed
    protected PrintPostScript getPrintPS() {
        if (this.printPS == null) {
            this.printPS = new PrintPostScript();
            this.printPS.setPrintParameters(this);
        }
        return printPS;
    }

    protected PrintHTML getPrintHTML() {
        if (this.printHTML == null) {
            this.printHTML = new PrintHTML();
            this.printHTML.setPrintParameters(this);
        }
        return printHTML;
    }

    /**
     * Performs an asyncronous print and handles the output of exceptions and
     * debugging. Important: print() clears any raw buffers after printing. Use
     * printPersistent() to save the buffer to be used/appended to later.
     */
    public void print() {
        startPrinting = true;
        reprint = false;
    }

    public void printHTML() {
        htmlPrint = true;
        print();
    }

    public void printPS() {
        psPrint = true;
        print();
    }

    /**
     * Get our main thread ready, but don't start it until <code>start()</code>
     * has been called.
     */
    @Override
    public void init() {
        if (!allowMultiple && thisThread.get() != null && thisThread.get().isAlive()) {
            log.warning("init() called, but applet already "
                    + "seems to be running.  Ignoring.");
            return;
        }
        if (allowMultiple && thisThread.get() != null && thisThread.get().isAlive()) {
            log.info("init() called, but applet already "
                    + "seems to be running.  Allowing.");
        }
        resetVariables();
        thisThread.set(new Thread(this));
        super.init();
    }

    public boolean isActive(){
        return isActive(true);
    }

    /**
     * No need to paint, the applet is invisible
     */
    @Override
        public void paint(Graphics g) {
        // Do nothing
    }

    /**
     * Start our main thread
     */
    @Override
        public void start() {
        try {
            thisThread.get().start();
        } catch (JSException e) {
            set(e);
            log.severe("Error setting applet object in JavaScript using LiveConnect.  "
                    + "This is usually caused by Java Security Settings.  In Windows, enable the Java "
                    + "Console and hit 5 to show verbose messages.");
        } catch (Exception e) {
            set(e);
        }
        super.start();
    }

    @Override
        public void stop() {
        running = false;
        thisThread.set(null);
        if (serialIO != null) {
            try {
                serialIO.close();
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Could not close port [" + serialIO.getPortName() + "].", t);
            }
        }
        super.stop();
    }

    @Override
        public void destroy() {
        this.stop();
        super.destroy();
    }

    /**
     * Creates the print service by iterating through printers until finding
     * matching printer containing "printerName" in its description
     *
     * @param printer name of printer
     */
    public void findPrinter(String printer) {
        this.startFindingPrinters = true;
        this.doneFindingPrinters = false;
        this.printer = printer;
    }

    /**
     * Uses the JSSC JNI library to retrieve a comma separated list of serial
     * ports from the system, i.e. "COM1,COM2,COM3" or "/dev/tty0,/dev/tty1",
     * etc.
     */
    public void findPorts() {
        this.startFindingPorts = true;
    }

    public void closePort(String portName) {
        if (getSerialIO().getPortName().equals(portName)) {
            this.startClosingPort = true;
        } else {
            this.set(new SerialException("Port specified [" + portName + "] "
                    + "could not be closed. Please close "
                    + "[" + getSerialIO().getPortName() + "] instead. "
                    + "Applet currently supports only one open port at a time."));
        }
    }

    public void openPort(String serialPortName) {
        this.openPort(serialPortName, false);
    }

    public void openPort(String serialPortName, boolean autoSetSerialProperties) {
        this.serialPortIndex = -1;
        this.serialPortName = serialPortName;
        this.startOpeningPort = true;
        this.autoSetSerialProperties = autoSetSerialProperties;
    }

    public void openPort(int serialPortIndex) {
        this.openPort(serialPortIndex, false);
    }

    public void openPort(int serialPortIndex, boolean autoSetSerialProperties) {
        this.serialPortName = null;
        this.serialPortIndex = serialPortIndex;
        this.startOpeningPort = true;
    }

    public boolean isDoneFinding() {
        return doneFindingPrinters;
    }

    public SerialIO getSerialIO() {
        try {
            Class.forName("jssc.SerialPort");
            if (this.serialIO == null) {
                this.serialIO = new SerialIO();
            }
            return serialIO;
        } catch (ClassNotFoundException e) {
            // Stop whatever is happening
            this.startFindingPorts = false;
            this.startSending = false;
            this.startOpeningPort = false;
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
            this.printRaw.setPrintParameters(this);
        }
        return printRaw;
    }

    // Generally called internally only after a printer is found.
    protected void setPrintService(PrintService ps) {
        this.ps = ps;
        if (ps == null) {
            log.warning("Setting null PrintService");
            log.warning("Setting null PrintService");
            return;
        }
        if (printHTML != null) {
            printHTML.setPrintService(ps);
        }
        if (printPS != null) {
            printPS.setPrintService(ps);
        }
        if (printRaw != null) {
            printRaw.setPrintService(ps);
        }
    }

    public void findNetworkInfo() {
        this.startFindingNetwork = true;
    }

    public void setPaperSize(float width, float height) {
        this.paperSize = new PaperFormat(width, height);
        log.info("Set paper size to " + paperSize.getWidth()
                         + paperSize.getUnitDescription() + "x"
                         + paperSize.getHeight() + paperSize.getUnitDescription());
    }


}
