package qz;

import java.io.IOException;
import java.util.logging.Level;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;
import jssc.SerialPortTimeoutException;
import qz.exception.SerialException;

/**
 *
 * @author Tres
 */
public class SerialIO {
    // Serial port attributes obtained from the system
    private int baudRate; // = -1; //SerialPort.BAUDRATE_9600;
    private int dataBits; // = -1; //SerialPort.DATABITS_8;
    private int stopBits; // = -1; //SerialPort.STOPBITS_1;
    private int flowControl; // = -1; //SerialPort.FLOWCONTROL_NONE;
    private int parity; // = -1; //SerialPort.PARITY_NONE;
    
    // Beggining and ending patterns that signify port has responded
    private byte[] begin = { '\u0002' };
    private byte[] end = { '\r' };
    
    // Timeout to wait before giving up on reading the specified amount of bytes
    private int timeout;
    
    // A buffer to hold data returned from the serial port
    private ByteArrayBuilder outputBuffer;
    
    // The informaiton to be sent to the serial port
    private ByteArrayBuilder inputBuffer;
    
    private byte[] output;
    
    private SerialPort port;
    private String serialPorts;
    private String[] portArray;
    private String portName;

    public SerialIO() {
        //port = new SerialPort(portName);
    this.baudRate = SerialPort.BAUDRATE_9600;
    this.dataBits = SerialPort.DATABITS_8;
    this.stopBits = SerialPort.STOPBITS_1;
    this.flowControl = SerialPort.FLOWCONTROL_NONE;
    this.parity = SerialPort.PARITY_NONE;
    setTimeout(1200);
    }

    
    /**
     * Closes the open serial port, if open.  If not, displays a warning message
     * in the console and  continues quietly.  If the port cannot close, a 
     * <code>SerialPortExcpetion</code> will be thrown.
     * @return
     * @throws SerialPortException 
     */
    public boolean close() throws SerialPortException {
        if (port == null || !port.isOpened()) {
            LogIt.log(Level.WARNING, "Serial Port [" + portName + "] does not appear to be open.");
            return false;
        }
        boolean closed = port.closePort();
        if (!closed) {
            LogIt.log(Level.WARNING, "Serial Port [" + portName + "] was not closed properly.");
        } else {
            LogIt.log("Port [" + portName + "] closed successfully.");
        }
        port = null;
        portName = null;
        return closed;
    }
    
    public byte[] getOutput() {
        // TODO:  Honor charset settings from PrintApplet.
        return output;
    }
    
    public void clearOutput() {
        output = null;
    }
    
    private ByteArrayBuilder getOutputBuffer() {
        if (this.outputBuffer == null) {
            this.outputBuffer = new ByteArrayBuilder();
        }
        return this.outputBuffer;
    }
    
    public ByteArrayBuilder getInputBuffer() {
        if (this.inputBuffer == null) {
            this.inputBuffer = new ByteArrayBuilder();
        }
        return this.inputBuffer;
    }
    
    /**
     * Open the specified port name.  i.e. <code>open("COM1");</code> or 
     * <code>open("/dev/tty0");</code>
     * @param portName
     * @return
     * @throws SerialPortException 
     */
    public boolean open(String portName) throws SerialPortException {
        if (port == null) {
            port = new SerialPort(this.portName = portName);
            try {
                port.openPort();
                port.addEventListener(new SerialPortEventListener() {
                    public void serialEvent(SerialPortEvent spe) {
                        SerialIO.this.serialEvent(spe);
                    }
                });
            } catch (SerialPortException e) {   // Catch, null it and throw it
                port = null;
                throw e;
            }
        } else {
            LogIt.log(Level.WARNING, "Serial Port [" + this.portName + "] already appears to be open.");
        }
        return port.isOpened();
    }
    
    
    /**
     * Automatically sets the baud, databits, stopbits, etc based on how the port
     * is already configured in the operating system.
     * @throws SerialPortException
     * @throws IOException
     * @throws SerialException 
     */
    public void autoSetProperties() throws SerialPortException, IOException, SerialException {
       int[] params = SerialUtilities.getSystemAttributes(this.portName);
       port.setParams(params[0], params[1], params[2], params[3]);
       port.setFlowControlMode(params[4]);
    }
    
    public void setProperties(String baud, String dataBits, String stopBits, String parity, String flowControl) throws SerialPortException {
        this.baudRate = SerialUtilities.parseBaudRate(baud);
        this.dataBits = SerialUtilities.parseDataBits(dataBits);
        this.stopBits = SerialUtilities.parseStopBits(stopBits);
        this.parity = SerialUtilities.parseParity(parity);
        this.flowControl = SerialUtilities.parseFlowControl(flowControl);
    }
    
    /**
     * Allow a port to be selected from array of returned ports.
     * @param portID
     * @return
     * @throws SerialPortException
     * @throws SerialException 
     */
    public boolean open(int portID) throws SerialPortException, SerialException {
        if (this.serialPorts == null) {
            this.getSerialPorts();
        }
        if (this.serialPorts.equals("")) {
            throw new SerialException("No ports could be found on this system");
        }
        if (portID > -1 && this.portArray.length > 0 && this.portArray.length > portID) {
            return open(portArray[portID]);
        } else {
            throw new SerialException("Index supplied [" + portID + "] is "
                    + "out of bounds in the following port listing: " + serialPorts);
        }
    }
    
    /**
     * Returns the cached version of the found serial ports on the system.
     * i.e. ["COM1","COM2","COM3"] or ["/dev/tty0","/dev/tty1"]
     * @return 
     */
    public String getSerialPorts() {
        return this.serialPorts;
    }
    
    public String getPortName() {
        return this.portName;
    }

    /**
     * Caches a comma delimited list of ports found on this system.  Also caches
     * the array so that it can be referenced by index when opening the port
     * later.
     * @return 
     */
    public String fetchSerialPorts() {
        StringBuilder sb = new StringBuilder();
        this.portArray = SerialPortList.getPortNames();
        for (int i = 0; i < this.portArray.length; i++) {
            sb.append(this.portArray[i]).append(i < this.portArray.length - 1 ? "," : "");
        }
        return (this.serialPorts = sb.toString());
    }
    
    /**
     * Timeout in milliseconds for the port.readBytes() function.
     * @return 
     */
    public int getTimeout() {
        return this.timeout;
    }
    
    /**
     * Timeout in milliseconds for the port.readBytes() function.
     * Default is 1200 (1.2 seconds)
     * @param timeout 
     */
    public final void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void serialEvent(SerialPortEvent event) {
        try {
            // Receive data
            if (event.isRXCHAR()) {
                getOutputBuffer().append(port.readBytes(event.getEventValue(), timeout));
                
                int[] beginPos = ByteUtilities.indicesOfSublist(getOutputBuffer().getByteArray(), begin);
                int[] endPos = ByteUtilities.indicesOfSublist(getOutputBuffer().getByteArray(), end);
                if (beginPos.length > 0 && endPos.length > 0) {
                    int _begin = beginPos[beginPos.length -1];
                    int _end  = endPos[endPos.length -1];
                    // TODO:  Use specified charset in PrintApplet.
                    LogIt.log(new String(getOutputBuffer().getByteArray(), _begin, _end - _begin));
                    output = new byte[_end - _begin];
                    System.arraycopy(getOutputBuffer().getByteArray(), _begin, output, 0, _end - _begin);
                    getOutputBuffer().clear();
                }
                
            }
        } catch (SerialPortException e) {
            LogIt.log(Level.SEVERE, "Exception occured while reading data from port.", e);
        } catch (SerialPortTimeoutException e) {
            LogIt.log(Level.WARNING, "Timeout occured waiting for port to respond.  Timeout value: " + timeout, e);
        }
            /*if (event.isERR()) {
                // TODO:  Make this exception visible from PrintApplet.
                LogIt.log(Level.WARNING, "Error occured reading data from port "
                        + "[" + event.getPortName() + "].  You may want to close "
                        + "and reopen communications for this port.");
            }
                            recievedCmds += new String(serialPort.readBytes(len), pa.getCharset().name());
                            //LogIt.log("Debug:  Recieved serial bytes:");
                            //for (byte b : recievedCmds.getBytes()) {
                            //    LogIt.log(Byte.toString(b));
                            //}
                            
                            int stx = recievedCmds.lastIndexOf(beginCmd);
                            int cr = recievedCmds.lastIndexOf(endCmd);
                            
                            //LogIt.log("Debug:  STX_POS:" + stx + ", CR_POS:" + cr);
                            
                            if (stx >= 0 && cr > stx) {
                                recievedCmds = recievedCmds.substring(stx+1, cr);
                                //LogIt.log("Response from " + portLabel + " (" + len + " bytes)" + "\n" + recievedCmds);
                                pa.notifyBrowser("jzebraSerialReturned", new Object[]{portLabel, recievedCmds});
                                recievedCmds = "";
                            } 
                            
                            //if (recievedCmds.startsWith("\u0002") && recievedCmds.endsWith("\u0013")) {
                            //    LogIt.log("Response from " + portLabel + " (" + len + " bytes)" + "\n" + recievedCmds);
                            //    pa.notifyBrowser("jzebraSerialReturned", new Object[]{portLabel, recievedCmds});
                            //    recievedCmds = "";
                            //}
                        } catch (Throwable t) {
                            pa.notifyBrowser("jzebraException", t.getMessage());
                        }*/
            
    }
    
    /**
     * Return whether or not the serial port is open
     * @return 
     */
    public boolean isOpen() {
        return port == null ? false : port.isOpened();
    }
    
    /**
     * Applies the port parameters and writes the buffered data to the serial port
     * @throws SerialPortException 
     */
    public void send() throws SerialPortException {
        port.setParams(baudRate, dataBits, stopBits, parity);
        port.setFlowControlMode(flowControl);
        LogIt.log("Sending data to [" + portName + "]:\r\n\r\n" + new String(getInputBuffer().getByteArray()) + "\r\n\r\n");
        port.writeBytes(getInputBuffer().getByteArray());
        getInputBuffer().clear();
    }
    
    public void append(byte[] bytes) {
        getInputBuffer().append(bytes);
    }
    
    
    
    public byte[] getBegin() {
        return begin;
    }

    public void setBegin(byte[] begin) {
        this.begin = begin;
    }

    public byte[] getEnd() {
        return end;
    }

    public void setEnd(byte[] end) {
        this.end = end;
    }
    
    /**
     * Writes the specified byte array to the serial port
     * @param bytes
     * @throws SerialPortException
     *
    public void write(byte[] bytes) throws SerialPortException {
        port.writeBytes(bytes);
    }
    
    /**
     * Writes the ByteArrayBuilder's backed byte array to the serial port
     * @param b
     * @throws SerialPortException 
     *
     public void write(ByteArrayBuilder b) throws SerialPortException {
       port.writeBytes(b.getByteArray());
    }*/
    
}
