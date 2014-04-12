/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package qz;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import jssc.SerialPort;
import qz.exception.SerialException;

/**
 *
 * @author Tres
 */
public class SerialUtilities {
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
     * @param portName
     * @return
     * @throws IOException 
     */
    public static int[] getWindowsAttributes(String portName) throws IOException, SerialException {
        String[] command = { "cmd.exe", "/c", winCmd.replace("?", portName)};
        Process p = Runtime.getRuntime().exec(command);
        String output = new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
        LogIt.log("Found windows registry settings: " + output);
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
                    case 0: attr[0] = SerialUtilities.parseBaudRate(val); break;
                    case 1: attr[3] = SerialUtilities.parseParity(val); break;
                    case 2: attr[1] = SerialUtilities.parseDataBits(val); break;
                    case 3: attr[2] = SerialUtilities.parseStopBits(val); break;
                    case 4: attr[4] = SerialUtilities.parseFlowControl(val); break;
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
     * @return 
     */
    public static int getOS() {
        String os = System.getProperty("os.name", "Windows 7").toLowerCase();
        if (os.startsWith("windows")) {
            LogIt.log("OS Detected: Windows");
            return WINDOWS;
        } else if (os.startsWith("linux")) {
            LogIt.log("OS Detected: Linux");
            return LINUX;
        } else if (os.startsWith("mac os") || os.startsWith("freebsd")) {
            LogIt.log("OS Detected: OS X");
            return OSX;
        } else {
            LogIt.log("Unknown OS Detected.");
            return -1;
        }
    }
    
    /**
     * Parses the suppled String data bits parameter and returns the SerialPorts's
     * DATABITS_X <code>int</code> value that corresponds with the provided 
     * String.  Returns -1 if the data bits value can't be parsed.
     * 
     * @param s
     * @return 
     */
    public static int parseDataBits(String s) {
        s = s.trim();
        if (s.equals("")) {
            LogIt.log(Level.SEVERE, "Canot parse empty data bits value.");
        } else if (s.equals("5")) {
            LogIt.log("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_5;
        } else if (s.equals("6")) {
            LogIt.log("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_6; 
        } else if (s.equals("7")) {
            LogIt.log("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_7; 
        } else if (s.equals("8")) {
            LogIt.log("Parsed serial setting: " + s + "=DATABITS_" + s);
            return SerialPort.DATABITS_8; 
        } else {
            LogIt.log(Level.SEVERE, "Data bits value of " + s + " not supported");
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
    public static int parseStopBits(String s) {
        s = s.trim();
        if (s.equals("1") || s.equals("")) {
            LogIt.log("Parsed serial setting: " + s + "=STOPBITS_" + s);
            return SerialPort.STOPBITS_1;
        } else if (s.equals("2")) {
            LogIt.log("Parsed serial setting: " + s + "=STOPBITS_" + s);
            return SerialPort.STOPBITS_2;
        } else if (s.equals("1.5") || s.equals("1_5")) {
            LogIt.log("Parsed serial setting: " + s + "=STOPBITS_" + s);
            return SerialPort.STOPBITS_1_5;
        } else {
            LogIt.log(Level.SEVERE, "Stop bits value of " + s + " could not be parsed");
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
            LogIt.log("Parsed serial setting: " + s + "=FLOWCONTROL_NONE");
            return SerialPort.FLOWCONTROL_NONE;
        } else if (s.equals("x") || s.equals("xonxoff") || s.equals("xonxoff_out")) {
            LogIt.log("Parsed serial setting: " + s + "=FLOWCONTROL_XONXOFF_OUT");
            return SerialPort.FLOWCONTROL_XONXOFF_OUT;
        } else if (s.equals("xonxoff_in")) {
            LogIt.log("Parsed serial setting: " + s + "=FLOWCONTROL_XONXOFF_IN");
            return SerialPort.FLOWCONTROL_XONXOFF_IN;
        } else if (s.equals("p") || s.equals("rtscts") || s.equals("rtscts_out")) {
            LogIt.log("Parsed serial setting: " + s + "=FLOWCONTROL_RTSCTS_OUT");
            return SerialPort.FLOWCONTROL_RTSCTS_OUT;
        } else if (s.equals("rtscts_in")) {
            LogIt.log("Parsed serial setting: " + s + "=FLOWCONTROL_RTSCTS_IN");
            return SerialPort.FLOWCONTROL_RTSCTS_IN;
        } else {
            LogIt.log(Level.SEVERE, "Flow control value of " + s + " could not be parsed");
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
            LogIt.log("Parsed serial setting: " + s + "=PARITY_NONE");
            return SerialPort.PARITY_NONE;
        } else if (s.startsWith("e")) {
            LogIt.log("Parsed serial setting: " + s + "=PARITY_EVEN");
            return SerialPort.PARITY_EVEN;
        } else if (s.equals("o")) {
            LogIt.log("Parsed serial setting: " + s + "=PARITY_ODD");
            return SerialPort.PARITY_ODD;
        } else if (s.equals("m")) {
            LogIt.log("Parsed serial setting: " + s + "=PARITY_MARK");
            return SerialPort.PARITY_MARK; 
        } else if (s.equals("s")) {
            LogIt.log("Parsed serial setting: " + s + "=PARITY_SPACE");
            return SerialPort.PARITY_SPACE; 
        } else {
            LogIt.log(Level.SEVERE, "Data bits value of " + s + " not supported");
        }
        return -1;
    }
    

    /**
     * Parses the suppled String baud rate parameter and returns the SerialPorts's
     * BAUDRATE_XXX <code>int</code> value that corresponds with the provided 
     * String.  Returns -1 if the baud rate value can't be parsed.
     * 
     * @param s
     * @return 
     */
    public static int parseBaudRate(String s) {
        s = s.trim();
        if (s.equals("")) {
            LogIt.log(Level.SEVERE, "Canot parse empty baud rate value.");
        } else if (s.equals("110")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_110;
        } else if (s.equals("300")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_300; 
        } else if (s.equals("600")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_600; 
        } else if (s.equals("1200")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_1200; 
        } else if (s.equals("4800")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_4800; 
        } else if (s.equals("9600")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_9600; 
        } else if (s.equals("14400")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_14400; 
        } else if (s.equals("19200")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_19200; 
        } else if (s.equals("38400")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_38400; 
        } else if (s.equals("57600")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_57600; 
        } else if (s.equals("115200")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_115200; 
        } else if (s.equals("128000")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_128000; 
        } else if (s.equals("256000")) {
            LogIt.log("Parsed serial setting: " + s + "=BAUDRATE_" + s);
            return SerialPort.BAUDRATE_256000; 
        } else {
            LogIt.log(Level.SEVERE, "Baud rate of " + s + " not supported");
        }
        return -1;
    }
    
}
