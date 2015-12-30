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

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joor.Reflect;
import org.joor.ReflectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

/**
 * @author Tres
 */
public class NetworkUtilities {

    private static final Logger log = LoggerFactory.getLogger(NetworkUtilities.class);

    private static NetworkUtilities instance;

    private String ipAddress;
    private String macAddress;


    public static NetworkUtilities getInstance() {
        return getInstance("google.com", 80); //TODO - make sure still works with s protocol
    }

    public static NetworkUtilities getInstance(String hostname, int port) {
        if (instance == null) {
            try {
                instance = new NetworkUtilities(hostname, port);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }

        return instance;
    }

    private NetworkUtilities(String hostname, int port) throws IOException, ReflectException {
        gatherNetworkInfo(hostname, port);
    }

    private void gatherNetworkInfo(String hostname, int port) throws IOException, ReflectException {
        log.info("Initiating a temporary connection to \"{}:{}\" to determine main Network Interface", hostname, port);

        SocketAddress endpoint = new InetSocketAddress(hostname, port);
        Socket socket = new Socket();
        socket.connect(endpoint);

        InetAddress localAddress = socket.getLocalAddress();
        ipAddress = localAddress.getHostAddress();
        socket.close();


        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localAddress);
        Reflect r = Reflect.on(networkInterface);
        byte[] b = r.call("getHardwareAddress").get();
        if (b != null && b.length > 0) {
            macAddress = ByteUtilities.bytesToHex(b);
        }
    }


    public String getHardwareAddress() {
        return macAddress;
    }

    public String getInetAddress() {
        return ipAddress;
    }


    public static JSONObject getNetworkJSON() throws JSONException {
        JSONObject network = new JSONObject();

        NetworkUtilities netUtil = getInstance();
        if (netUtil != null) {
            network.put("ipAddress", netUtil.getInetAddress());
            network.put("macAddress", netUtil.getHardwareAddress());
        } else {
            network.put("error", "Unable to initialize network utilities");
        }

        return network;
    }
}
