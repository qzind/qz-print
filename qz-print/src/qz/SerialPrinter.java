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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import javax.print.PrintException;
import javax.print.PrintService;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;
import jssc.SerialPortTimeoutException;

/**
 * SerialPrinter implements the Printer class and provides functions for sending
 * and processing received serial data.
 * 
 * @author Thomas Hart II
 */
public class SerialPrinter implements Printer {

    // Serial port attributes obtained from the system
    private int baudRate;
    private int dataBits;
    private int stopBits;
    private int flowControl;
    private int parity;
    private boolean serialPortsFound = false;
    
    // Beginning and ending patterns that signify port has responded
    private byte[] begin = { '\u0002' };
    private byte[] end = { '\r' };
    
    // Timeout to wait before giving up on reading the specified amount of bytes
    private int timeout;
    
    // A buffer to hold data returned from the serial port
    private ByteArrayBuilder outputBuffer;
    
    // The informaiton to be sent to the serial port
    private ByteArrayBuilder inputBuffer;
    
    private byte[] output;
    
    private SerialPort port = null;
    private String serialPorts;
    private String[] portArray;
    private String portName;
    private final Applet applet;
    private final BrowserTools btools;
    private final boolean ready;

    /**
     * Initialize the SerialPrinter variables and grab a reference to the applet
     * so that javascript callback functions can be called.
     * 
     * @param applet 
     */
    public SerialPrinter(Applet applet) {
        //port = new SerialPort(portName);
        this.baudRate = SerialPort.BAUDRATE_9600;
        this.dataBits = SerialPort.DATABITS_8;
        this.stopBits = SerialPort.STOPBITS_1;
        this.flowControl = SerialPort.FLOWCONTROL_NONE;
        this.parity = SerialPort.PARITY_NONE;
        this.applet = applet;
        this.btools = new BrowserTools(applet);
        this.ready = true;
        setTimeout(1200);
    }
    
    public String getName() {
        return "Serial Printer";
    }

    public void printRaw(ByteArrayBuilder data) throws PrintException {
        LogIt.log(Level.WARNING, "Serial Printer does not support raw printing.");
    }

    public void printAlternate(ByteArrayBuilder data) throws PrintException {
        LogIt.log(Level.WARNING, "Serial Printer does not support alternate printing.");
    }

    public boolean ready() {
        return ready;
    }

    public void setPrintService(PrintService ps) {
        LogIt.log(Level.WARNING, "Serial Printer does not require a print service.");
    }

    public PrintService getPrintService() {
        LogIt.log(Level.WARNING, "Serial Printer does not require a print service.");
        return null;
    }

    public String getType() {
        return "Serial";
    }

    public void setName(String name) {
        
    }

    public void setJobTitle(String jobTitle) {
        
    }
    
    /**
     * findPorts starts the process of finding the list of serial ports.
     */
    public void findPorts() {
        
        LogIt.log("Serial Printer now finding ports.");
        
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                fetchPortList();
                return null;
            }
        });
 
    }

    /**
     * Return a comma delimited String of all available serial ports
     * 
     * @return The list of ports
     */
    public String getPorts() {
        return serialPorts;
    }

    /**
     * openPort creates a port reference and opens it.
     * 
     * @param portName The name of the port to open
     * @return A boolean representing whether or not opening the port succeeded.
     */
    public boolean openPort(String portName) {
        if(!serialPortsFound) {
            findPorts();
        }
        if (port == null) {
            port = new SerialPort(this.portName = portName);
            
            // Use a privileged action to open the port
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    try {
                        port.openPort();
                    } catch (SerialPortException ex) {
                        port = null;
                        LogIt.log(Level.SEVERE, "Could not open serial port.", ex);
                    }
                    return null;
                }
            });
            
            // Add a listener to the port to check for incoming data
            try {
                port.addEventListener(new SerialPortEventListener() {
                    public void serialEvent(SerialPortEvent spe) {
                        serialEventListener(spe);
                    }
                });
            } catch (SerialPortException ex) {
                LogIt.log(Level.SEVERE, "Could not add listener to serial port.", ex);
            }
            
            this.portName = portName;
            LogIt.log("Opened Serial Port " + this.portName);
        } else {
            LogIt.log(Level.WARNING, "Serial Port [" + this.portName + "] already appears to be open.");
        }
        this.btools.notifyBrowser("qzDoneOpeningPort", portName);
        return port.isOpened();
    }
    
    /**
     * closePort closes the currently open port. A port name is provided but is
     * only used in the log. Since only one port can be open at a time, 
     * closePort does not require you to specify the correct port.
     * 
     * @param portName The name of the port to close. Only used in log.
     * @return A boolean representing whether the close routine was successful.
     */
    public boolean closePort(String portName) {
        return closePort(portName, true);
    }

    /**
     * closePort closes the currently open port. A port name is provided but is
     * only used in the log. Since only one port can be open at a time, 
     * closePort does not require you to specify the correct port.
     * 
     * @param portName The name of the port to close. Only used in log.
     * @param warnClosed Warn the user if the port is already closed
     * @return A boolean representing whether the close routine was successful.
     */
    public boolean closePort(String portName, boolean warnClosed) {
        if (port == null || !port.isOpened()) {
            if (warnClosed) {
                LogIt.log(Level.WARNING, "Serial Port [" + portName + "] does not appear to be open.");
            }
            return false;
        }
        
        boolean closed = false;
        try {
            closed = port.closePort();
        } catch (SerialPortException ex) {
            LogIt.log(Level.SEVERE, "Could not close serial port.", ex);
        }
        
        if (!closed) {
            LogIt.log(Level.WARNING, "Serial Port [" + portName + "] was not closed properly.");
        } else {
            LogIt.log("Port [" + portName + "] closed successfully.");
        }
        btools.notifyBrowser("qzDoneClosingPort", portName);
        port = null;
        this.portName = null;
        return closed;
    }

    /**
     * Set the character to mark the beginning of returned serial data.
     * 
     * @param serialBegin The beginning character.
     */
    public void setSerialBegin(ByteArrayBuilder serialBegin) {
        this.begin = serialBegin.getByteArray();
    }

    /**
     * Set the character to mark the ending of returned serial data.
     * 
     * @param serialEnd The ending character.
     */
    public void setSerialEnd(ByteArrayBuilder serialEnd) {
        this.end = serialEnd.getByteArray();
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
        this.baudRate = SerialUtilities.parseBaudRate(baud);
        this.dataBits = SerialUtilities.parseDataBits(dataBits);
        this.stopBits = SerialUtilities.parseStopBits(stopBits);
        this.parity = SerialUtilities.parseParity(parity);
        this.flowControl = SerialUtilities.parseFlowControl(flowControl);
    }

    /**
     * Send serial data to the opened port.
     * 
     * @param serialData A string of the data to send.
     */
    public void send(String serialData) {
        if(port != null) {
            inputBuffer = getInputBuffer();
            inputBuffer.append(serialData.getBytes());
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                public Object run() {
                    try {
                        port.setParams(baudRate, dataBits, stopBits, parity);
                        port.setFlowControlMode(flowControl);
                        LogIt.log("Sending data to [" + portName + "]:\r\n\r\n" + new String(getInputBuffer().getByteArray()) + "\r\n\r\n");
                        port.writeBytes(getInputBuffer().getByteArray());
                        getInputBuffer().clear();
                    } catch (SerialPortException ex) {
                        LogIt.log(Level.SEVERE, "Could not send data to serial port.", ex);
                    }
                    return null;
                }
            });
        }
        else {
            LogIt.log(Level.SEVERE, "No serial port is open.");
        }
    }

    /**
     * Get any returned serial data.
     * 
     * @return The returned data
     */
    public String getReturnData() {
        if(output != null) {
            String returnData = new String(output);
            output = null;
            return returnData;
        }
        else {
            return null;
        }
    }
    
    /**
     * Timeout in milliseconds for the port.readBytes() function.
     * Default is 1200 (1.2 seconds)
     * @param timeout 
     */
    private void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    /**
     * Fetch a list of available serial ports and set the serialPorts variable.
     */
    public void fetchPortList() {
        try {
            StringBuilder sb = new StringBuilder();
            portArray = SerialPortList.getPortNames();
            for (int i = 0; i < portArray.length; i++) {
                sb.append(portArray[i]).append(i < portArray.length - 1 ? "," : "");
            }
            serialPorts = sb.toString();
            serialPortsFound = true;
            LogIt.log("Found Serial Ports: " + serialPorts);
        }
        catch (NullPointerException ex) {
            LogIt.log(Level.SEVERE, "Null pointer.", ex);
        }
        catch (NoClassDefFoundError ex) {
            LogIt.log(Level.SEVERE, "Problem communicating with the JSSC class.", ex);
        }
    }

    /**
     * A listener that is attached to the serial port when data is sent to
     * monitor for any returned data.
     * 
     * @param event A reference to the serial port returned data event
     */
    public void serialEventListener(SerialPortEvent event) {
        try {
            // Receive data
            if (event.isRXCHAR()) {
                getOutputBuffer().append(port.readBytes(event.getEventValue(), timeout));
                
                int[] beginPos = ByteUtilities.indicesOfSublist(getOutputBuffer().getByteArray(), begin);
                int[] endPos = ByteUtilities.indicesOfSublist(getOutputBuffer().getByteArray(), end);
                if (beginPos.length > 0 && endPos.length > 0) {
                    int _begin = beginPos[beginPos.length -1];
                    int _end  = endPos[endPos.length -1];
                    output = new byte[_end - _begin];
                    System.arraycopy(getOutputBuffer().getByteArray(), _begin, output, 0, _end - _begin);
                    getOutputBuffer().clear();
                }
                
                if(output != null) {
                    LogIt.log("Received Serial Data: " + new String(output));
                    btools.notifyBrowser("qzSerialReturned", new String(output));
                }
                else {
                    LogIt.log(Level.WARNING, "Received serial data but it was null. Please check the begin and end characters.");
                }
            }
        } catch (SerialPortException e) {
            LogIt.log(Level.SEVERE, "Exception occured while reading data from port.", e);
        } catch (SerialPortTimeoutException e) {
            LogIt.log(Level.WARNING, "Timeout occured waiting for port to respond.  Timeout value: " + timeout, e);
        }
        
    }
    
    /**
     * Grab a reference to or create a ByteArrayBuilder to use as an
     * input buffer
     * 
     * @return The inputBuffer ByteArrayBuilder
     */
    public ByteArrayBuilder getInputBuffer() {
        if (this.inputBuffer == null) {
            this.inputBuffer = new ByteArrayBuilder();
        }
        return this.inputBuffer;
    }
    
    private ByteArrayBuilder getOutputBuffer() {
        if (this.outputBuffer == null) {
            this.outputBuffer = new ByteArrayBuilder();
        }
        return this.outputBuffer;
    }
}
