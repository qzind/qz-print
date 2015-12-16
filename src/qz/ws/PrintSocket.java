/**
 * @author Robert Casto
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

package qz.ws;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.joor.Reflect;
import org.joor.ReflectException;

import qz.auth.Certificate;
import qz.common.TrayManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Created by robert on 9/9/2014.
 */
@Deprecated //Re-written under PrintSocketClient - no need for Reflective calls
public class PrintSocket {

    private final Logger log = Logger.getLogger(PrintSocket.class.getName());


    // Each connection to the websocket has its own instance of QZ to avoid conflicting print buffers
    //private static HashMap<Integer,PrintFunction> connections = new HashMap<Integer,PrintFunction>();
    private static HashMap<Integer,Certificate> certificates = new HashMap<Integer,Certificate>();
    private static AtomicBoolean isAsking = new AtomicBoolean(false);

    private final List<String> privilegedMethods = Arrays.asList("findNetworkInfo", "closePort", "findPrinter", "findPrinters",
                                                                 "findPorts", "openPort", "send", "setSerialProperties", "setSerialBegin", "setSerialEnd", "getSerialIO");

    private final TrayManager trayManager = PrintSocketServer.getTrayManager();


    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        // Remove the QZ instance associated with the disconnected client
        Integer port = session.getRemoteAddress().getPort();
        //if (connections.get(port) != null) {
        //    connections.get(port).stop();
        //    connections.remove(port);
        //}
        if (certificates.get(port) != null) {
            certificates.remove(port);
        }

        log.info("WebSocket close: " + statusCode + " - " + reason);
        trayManager.displayFineMessage("Client disconnected");
    }


    @OnWebSocketMessage
    public void onMessage(Session session, String json) {

        int start = json.indexOf("{");
        if (start == -1) { start = 0; }
        String signature = json.substring(0, start);
        json = json.substring(start);

        Certificate cert = certificates.get(session.getRemoteAddress().getPort());

        if (cert != null) {
            if (cert.isSignatureValid(signature, json)) {

            }
        }

    }

    private void processMessage(Session session, JSONObject message, Certificate certificate) throws JSONException {
        Integer port = session.getRemoteAddress().getPort();
        //if (connections.get(port) == null) { connections.put(port, new PrintFunction()); connections.get(port).init(); connections.get(port).start(); }
        //PrintFunction qz = connections.get(port);

        log.info("Server message: " + message);

        // Using Reflection, call correct method on PrintApplet.
        // Except for listMessages which is not part of PrintApplet
        if ("listMessages".equals(message.getString("method"))) {
            JSONArray param = message.optJSONArray("params");
            String certString = null;
            if (param != null) { certString = param.getString(0); }

            try {
                //Certificate will be null for this call, so we will build it here
                certificate = new Certificate(certString);
                certificates.put(port, certificate);
            }
            catch(Exception ignore) {}

            //Dialog blocks UI, so each request should wait until no longer blocked
            while(isAsking.get()) {
                try { Thread.sleep(1000); }catch(Exception ignore) {}
            }

            isAsking.set(true);

            if (trayManager.showGatewayDialog(certificate)) {

            }
        } else {        // Figure out which method is being called and call it returning any values
            String name = message.optString("method");
            boolean blocked = false;

            if (privilegedMethods.contains(name) && !trayManager.showGatewayDialog(certificate)) {
                blocked = true; //required successful gateway dialog, but failed
            }

        }
    }


}
