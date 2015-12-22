package qz.communication;

import qz.utils.UsbUtilities;

import javax.usb.UsbDevice;
import javax.usb.UsbException;
import javax.usb.UsbInterface;
import javax.usb.UsbPipe;

public class UsbIO {

    private UsbDevice device;
    private UsbInterface iface;


    public UsbIO(short vendorId, short productId) throws UsbException {
        this(UsbUtilities.findDevice(vendorId, productId));
    }

    public UsbIO(UsbDevice device) {
        this.device = device;
    }

    public void open(byte ifc) throws UsbException {
        iface = device.getActiveUsbConfiguration().getUsbInterface(ifc);
        iface.claim();
    }

    public boolean isOpen() {
        return iface.isClaimed();
    }

    public byte[] readData(byte endpoint, int responseSize) throws UsbException {
        byte[] response = new byte[responseSize];
        exchangeData(endpoint, response);
        return response;
    }

    public void sendData(byte endpoint, byte[] data) throws UsbException {
        exchangeData(endpoint, data);
    }

    /**
     * Data will be sent to or received from the open usb device, depending on the {@code endpoint} used.
     *
     * @param endpoint Endpoint on the usb device interface to pass data across
     * @param data     Byte array of data to send, or to be written from a receive
     */
    private void exchangeData(byte endpoint, byte[] data) throws UsbException {
        UsbPipe pipe = iface.getUsbEndpoint(endpoint).getUsbPipe();
        pipe.open();

        try {
            pipe.syncSubmit(data);
        }
        finally {
            pipe.close();
        }
    }

    public void close() throws UsbException {
        iface.release();
    }

}
