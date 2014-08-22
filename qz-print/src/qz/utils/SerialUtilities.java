/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package qz.utils;

import jssc.SerialPort;
import qz.common.LogIt;
import qz.exception.SerialException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

/**
 *
 * @author Tres
 */
public class SerialUtilities {

    private static final Logger log = Logger.getLogger(SerialUtilities.class.getName());

    private final static int WINDOWS = 1;
    private final static int LINUX = 2;
    private final static int OSX = 3;
    
    public static String winCmd = "%windir%\\System32\\reg.exe "
            + "query \"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Ports\" |find \"?\"";
    
    public static int[] getSystemAttributes(String portName) throws IOException, SerialException {
        switch(getOS()) {
            case WINDOWS: return getWindowsAttributes(portName);
            case LINUX: LogIt.log("Parsing Linux Serial Port attributes has not yet been implemented"); break;
            case OSX: LogIt.log("Parsing OSX Serial Port attributes has not yet been implemented"); break;
            default: //Do nothing
        }
        return null;
    }
    
    /**
     * Calls REG.EXE to obtain the port settings.  These should be returned in the
     * format "COM10:    REG_SZ    9600,n,8,1"
     * @param portName name of windows port
     * @return windows port attributes
     * @throws IOException 
     */
    public static int[] getWindowsAttributes(String portName) throws IOException, SerialException {
        String[] command = { "cmd.exe", "/c", winCmd.replace("?", portName)};
        Process p = Runtime.getRuntime().exec(command);
        String output = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
        log.info("Found windows registry settings: " + output);
        String[] split = output.split("REG_SZ");
        if (split.length > 0) {
            int[] attr = {SerialPort.BAUDRATE_9600, 
                SerialPort.DATABITS_8, 
                SerialPort.STOPBITS_1, 
                SerialPort.PARITY_NONE,
                SerialPort.FLOWCONTROL_NONE};
            String settings = split[split.length -1];
            String[] _attr = settings.split(",");
            for (int i = 0; i < _attr.length; i++) {
                String val = _attr[i];
                switch (i) {
                    case 0: attr[0] = parseBaudRate(val); break;
                    case 1: attr[3] = parseParity(val); break;
                    case 2: attr[1] = parseDataBits(val); break;
                    case 3: attr[2] = parseStopBits(val); break;
                    case 4: attr[4] = parseFlowControl(val); break;
                    default: // Do nothing
                }
            }
            for (int i : attr) {
                if (i == -1) {
                    throw new SerialException("Cannot parse system provided "
                            + "serial attributes: " + output);
                }
            }
            return attr;
        }
        throw new SerialException("Cannot parse system provided "
                            + "serial attributes: " + output);
    }
    
    /**
     * Checks the value of <code>System.getProperty("os.name");</code> and 
     * returns either SerialUtilities.WINDOWS, LINUX, or OSX.  Solaris, AIX, OS/2,
     * etc are not currently handled and will return -1, which will cause an error.
     * BSD defaults to OSX, which may need to be changed.
     * @return operating system constant
     */
    public static int getOS() {
        String os = System.getProperty("os.name", "Windows 7").toLowerCase();
        if (os.startsWith("windows")) {
            log.info("OS Detected: Windows");
            return WINDOWS;
        } else if (os.startsWith("linux")) {
            log.info("OS Detected: Linux");
            return LINUX;
        } else if (os.startsWith("mac os") || os.startsWith("freebsd")) {
            log.info("OS Detected: OS X");
            return OSX;
        } else {
            log.info("Unknown OS Detected.");
            return -1;
        }
    }
    
    /**
     * Parses the suppled String data bits parameter and returns the SerialPorts's
     * DATABITS_X <code>int</code> value that corresponds with the provided 
     * String.  Returns -1 if the data bits value can't be parsed.
     * 
     * @param s data bits value
     * @return  number of data bits
     */
    public static int parseDataBits(String s) {
        s = s.trim();
        if (s.equals("")) {
            log.severe("Cannot parse empty data bits value.");
        } else if (s.equals("5")) {
            log.info("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_5;
        } else if (s.equals("6")) {
            log.info("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_6; 
        } else if (s.equals("7")) {
            log.info("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_7; 
        } else if (s.equals("8")) {
            log.info("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_8; 
        } else {
            log.severe("Data bits value of " + s + " not supported");
        }
        return -1;
    }
    
      /**
     * Parses the supplied String flow control parameter and returns the SerialPorts's
     * FLOWCONTROL_TYPE_DIRECTION <code>int</code> value that corresponds with the provided 
     * String.  Returns -1 if the flow control value can't be parsed.
     * TODO: (TEST THIS) By default flow control is assumed to be in the "out" 
     * direction.  This could be backwards on Windows, as the registry does not 
     * specify direction.
     * 
     * @param s number of stop bits
     * @return  number of stop bits
     */
    public static int parseStopBits(String s) {
        s = s.trim();
        if (s.equals("1") || s.equals("")) {
            log.info("Parsed serial setting: " + s + "=STOPBITS_" + s);
            return SerialPort.STOPBITS_1;
        } else if (s.equals("2")) {
            log.info("Parsed serial setting: " + s + "=STOPBITS_" + s);
            return SerialPort.STOPBITS_2;
        } else if (s.equals("1.5") || s.equals("1_5")) {
            log.info("Parsed serial setting: " + s + "=STOPBITS_" + s);
            return SerialPort.STOPBITS_1_5;
        } else {
            log.severe("Stop bits value of " + s + " could not be parsed");
        }
        return -1;
    }
    
    /**
     * Parses the suppled String flow control parameter and returns the SerialPorts's
     * FLOWCONTROL_TYPE_DIRECTION <code>int</code> value that corresponds with the provided 
     * String.  Returns -1 if the flow control value can't be parsed.
     * TODO: (TEST THIS) By default flow control is assumed to be in the "out" 
     * direction.  This could be backwards on Windows, as the registry does not 
     * specify direction.
     * 
     * @param s
     * @return 
     */
    public static int parseFlowControl(String s) {
        s = s.trim();
        if (s.equals("n") || s.equals("none") || s.equals("")) {
            log.info("Parsed serial setting: " + s + "=FLOWCONTROL_NONE");
            return SerialPort.FLOWCONTROL_NONE;
        } else if (s.equals("x") || s.equals("xonxoff") || s.equals("xonxoff_out")) {
            log.info("Parsed serial setting: " + s + "=FLOWCONTROL_XONXOFF_OUT");
            return SerialPort.FLOWCONTROL_XONXOFF_OUT;
        } else if (s.equals("xonxoff_in")) {
            log.info("Parsed serial setting: " + s + "=FLOWCONTROL_XONXOFF_IN");
            return SerialPort.FLOWCONTROL_XONXOFF_IN;
        } else if (s.equals("p") || s.equals("rtscts") || s.equals("rtscts_out")) {
            log.info("Parsed serial setting: " + s + "=FLOWCONTROL_RTSCTS_OUT");
            return SerialPort.FLOWCONTROL_RTSCTS_OUT;
        } else if (s.equals("rtscts_in")) {
            log.info("Parsed serial setting: " + s + "=FLOWCONTROL_RTSCTS_IN");
            return SerialPort.FLOWCONTROL_RTSCTS_IN;
        } else {
            log.severe("Flow control value of " + s + " could not be parsed");
        }
        return -1;
    }
    
    /**
     * Parses the suppled String data bits parameter and returns the SerialPorts's
     * DATABITS_X <code>int</code> value that corresponds with the provided 
     * String.  Returns -1 if the data bits value can't be parsed.
     * 
     * @param s
     * @return 
     */
    public static int parseParity(String s) {
        s = s.trim().toLowerCase();
        if (s.startsWith("n") || s.equals("")) {
            log.info("Parsed serial setting: " + s + "=PARITY_NONE");
            return SerialPort.PARITY_NONE;
        } else if (s.startsWith("e")) {
            log.info("Parsed serial setting: " + s + "=PARITY_EVEN");
            return SerialPort.PARITY_EVEN;
        } else if (s.equals("o")) {
            log.info("Parsed serial setting: " + s + "=PARITY_ODD");
            return SerialPort.PARITY_ODD;
        } else if (s.equals("m")) {
            log.info("Parsed serial setting: " + s + "=PARITY_MARK");
            return SerialPort.PARITY_MARK; 
        } else if (s.equals("s")) {
            log.info("Parsed serial setting: " + s + "=PARITY_SPACE");
            return SerialPort.PARITY_SPACE; 
        } else {
            log.severe("Data bits value of " + s + " not supported");
        }
        return -1;
    }
    

    /**
     * Parses the supplied String baud rate parameter and returns the SerialPorts's
     * BAUDRATE_XXX <code>int</code> value that corresponds with the provided 
     * String.  Returns -1 if the baud rate value can't be parsed.
     * 
     * @param s
     * @return 
     */
    public static int parseBaudRate(String s) {
        int baud = -1;
        try {
            baud = Integer.decode(s.trim());
        } catch (Exception ex) {
            log.severe("Cannot parse baud rate value. " + ex.getMessage());
            return -1;
        }

        switch(baud) {
            // valid baud rates
            case 110:
            case 300:
            case 600:
            case 1200:
            case 4800:
            case 9600:
            case 14400:
            case 19200:
            case 38400:
            case 57600:
            case 115200:
            case 128000:
            case 256000:
                log.info(String.format("Parsed serial setting: %d=BAUDRATE_%d", baud, baud));
                break;
            default:
                log.severe("Baud rate of " + s + " not supported");
                baud = -1;
        }

        return baud;
    }
    
}
