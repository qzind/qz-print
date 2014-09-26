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
package qz.utils;

import qz.reflection.Reflect;
import qz.reflection.ReflectException;

import java.io.IOException;
import java.net.*;
import java.util.logging.Logger;

/**
 *
 * @author Tres
 */
public class NetworkUtilities {

    private static final Logger log = Logger.getLogger(NetworkUtilities.class.getName());

    private String ipAddress;
    private String macAddress;
    private String hostname = "www.google.com";
    private int port = 80;

    public NetworkUtilities() throws SocketException, ReflectException, UnknownHostException {
        try {
            gatherNetworkInfo();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    
    public void setPort(int port) {
        this.port = port;
    }

    public void gatherNetworkInfo() throws IOException, ReflectException {
        Socket socket = new Socket();
        log.info("Initiating a temporary connection to \"" + hostname + ":" +
                port + "\" to determine main Network Interface");
        SocketAddress endpoint = new InetSocketAddress(hostname, port);
        socket.connect(endpoint);
        InetAddress localAddress = socket.getLocalAddress();
        this.ipAddress = localAddress.getHostAddress();
        socket.close();
        System.out.println(localAddress.getHostAddress());
        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localAddress);
        Reflect r = Reflect.on(networkInterface);
        byte[] b = r.call("getHardwareAddress").get();
        if (b != null && b.length > 0) {
            this.macAddress = ByteUtilities.bytesToHex(b);
        }
    }

    public String getHardwareAddress() {
        return this.macAddress;
    }

    public String getInetAddress() {
        return this.ipAddress;
    }
}
