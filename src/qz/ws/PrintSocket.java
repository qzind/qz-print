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
import qz.PrintFunction;
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
@WebSocket
public class PrintSocket {

    private final Logger log = Logger.getLogger(PrintSocket.class.getName());

    // We are going to pass this for all unsigned requests
    // This way, paid or not, users will have to Allow/Deny all unsigned requests, encouraging security
    public static final Certificate UNSIGNED;

    static {
        HashMap<String,String> map = new HashMap<String,String>();
        map.put("fingerprint", "UNSIGNED REQUEST");
        map.put("commonName", "An anonymous request");
        map.put("organization", "Unknown");
        map.put("validFrom", "0000-00-00 00:00:00");
        map.put("validTo", "0000-00-00 00:00:00");
        map.put("valid", "false");
        UNSIGNED = Certificate.loadCertificate(map);
    }

    // Each connection to the websocket has its own instance of QZ to avoid conflicting print buffers
    private static HashMap<Integer,PrintFunction> connections = new HashMap<Integer,PrintFunction>();
    private static HashMap<Integer,Certificate> certificates = new HashMap<Integer,Certificate>();
    private static AtomicBoolean isAsking = new AtomicBoolean(false);

    private final List<String> restrictedMethodNames = Arrays.asList("run", "stop", "start", "call", "init", "destroy", "paint");

    // List of methods that will cause the print dialog to pop-up
    private final List<String> printingMethods = Arrays.asList("print", "printHTML", "printPS", "printToFile", "printToHost");
    // List of methods that will cause the gateway dialog to pop-up
    private final List<String> privilegedMethods = Arrays.asList("findNetworkInfo", "closePort", "findPrinter", "findPrinters",
            "findPorts", "openPort", "send", "setSerialProperties", "setSerialBegin", "setSerialEnd", "getSerialIO");

    private final TrayManager trayManager = PrintWebSocketServer.getTrayManager();

    private static JSONArray methods = null;

    private static Throwable lastError = null;

    @OnWebSocketConnect
    public void onConnect(Session session) {
        log.info("Server connect: " + session.getRemoteAddress());
        trayManager.displayInfoMessage("Client connected");
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        // Remove the QZ instance associated with the disconnected client
        Integer port = session.getRemoteAddress().getPort();
        if (connections.get(port) != null) {
            connections.get(port).stop();
            connections.remove(port);
        }
        if (certificates.get(port) != null) {
            certificates.remove(port);
        }

        log.info("WebSocket close: " + statusCode + " - " + reason);
        trayManager.displayInfoMessage("Client disconnected");
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        log.severe("Server error: " + error.getMessage());
        trayManager.displayErrorMessage("Server error: " + error.getMessage());
    }

    @OnWebSocketFrame
    public void onFrame(Session session, Frame frame) {
        log.info("Server frame: " + frame.toString());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String json) {
        if (json == null) {
            sendError(session, "Invalid Message");
        } else if (!"ping".equals(json)) {
            try {
                log.info("Request: " + json);

                int start = json.indexOf("{");
                if (start == -1) { start = 0; }
                String signature = json.substring(0, start);
                json = json.substring(start);

                Certificate cert = certificates.get(session.getRemoteAddress().getPort());

                if (cert != null) {
                    if (cert.isSignatureValid(signature, json)) {
                        processMessage(session, new JSONObject(json), cert);
                    } else {
                        processMessage(session, new JSONObject(json), UNSIGNED);
                    }
                } else {
                    // No certificate - likely a setup call, pass null
                    processMessage(session, new JSONObject(json), null);
                }
            }
            catch(JSONException e) {
                sendError(session, "Invalid JSON");
                e.printStackTrace();
            }
        }
    }

    private void processMessage(Session session, JSONObject message, Certificate certificate) throws JSONException {
        Integer port = session.getRemoteAddress().getPort();
        if (connections.get(port) == null) { connections.put(port, new PrintFunction()); connections.get(port).init(); connections.get(port).start(); }
        PrintFunction qz = connections.get(port);

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
                if (methods == null) {
                    methods = new JSONArray();

                    try {
                        Class c = PrintFunction.class;
                        Method[] m = c.getDeclaredMethods();
                        for(Method method : m) {
                            if (method.getModifiers() == Modifier.PUBLIC) {
                                String name = method.getName();

                                // Add only if not in restricted method names list
                                if (!restrictedMethodNames.contains(name)) {
                                    JSONObject jMethod = new JSONObject();
                                    jMethod.put("name", name);
                                    jMethod.put("returns", method.getReturnType());
                                    jMethod.put("parameters", method.getParameterTypes().length);

                                    methods.put(jMethod);
                                }
                            }
                        }
                    }
                    catch(Exception ex) {
                        ex.printStackTrace();
                        message.put("error", ex.getMessage());
                    }
                }

                message.put("result", methods);
                sendResponse(session, message);
            } else {
                //Send blocked callback to web page
                message.put("callback", "requestBlocked");
                message.put("init", "false");
                sendResponse(session, message);
            }

            isAsking.set(false);

            return;
        } else {        // Figure out which method is being called and call it returning any values
            String name = message.optString("method");
            boolean blocked = false;

            if (printingMethods.contains(name) && !trayManager.showPrintDialog(certificate, qz.getPrinter())) {
                blocked = true; //required successful print dialog, but failed
            }
            if (privilegedMethods.contains(name) && !trayManager.showGatewayDialog(certificate)) {
                blocked = true; //required successful gateway dialog, but failed
            }

            if (!blocked) {
                JSONArray parts = message.optJSONArray("params");
                if (parts == null) { parts = new JSONArray(); }
                Vector<Method> possibleMethods = new Vector<Method>();

                try {
                    Method[] methods = PrintFunction.class.getMethods();
                    for(Method m : methods) {
                        if (m.getName().equals(name) && parts.length() == m.getParameterTypes().length) {
                            possibleMethods.add(m);
                        }
                    }

                    Object result = null;     // default for void

                    if (possibleMethods.size() == 0) {
                        message.put("error", "No methods found");
                        sendResponse(session, message);
                        return;
                    } else {
                        for(Method method : possibleMethods) { // We found methods that may work. Now call them
                            try {
                                // Create array of objects based on number of parameters and their types
                                Object[] params = new Object[parts.length()];
                                // We must get the parameter object types correct based on what the method wants
                                for(int i = 0; i < parts.length(); i++) {
                                    params[i] = convertType(parts.getString(i), method.getParameterTypes()[i]);
                                }

                                // Using jOOR to call method since primitives are involved
                                // Invoke the method with all the parameters
                                log.info("Calling: " + name + Arrays.toString(params));
                                result = Reflect.on(qz).call(name, params).get();


                                if (result instanceof PrintFunction) {
                                    result = "void";    // set since the return value is void
                                }

                                if ("openPort".equals(name)) {
                                    result = (qz.getSerialIO() == null? null:qz.getSerialIO().getPortName());

                                    // Watch serial port for any received data so we can send it to the browser
                                    if (qz.getSerialIO() != null && qz.getSerialIO().isOpen()) {
                                        qz.getSerialIO().clearOutput();

                                        new Thread() {
                                            public void run() {
                                                while(qz.getSerialIO() != null) {
                                                    if (qz.getSerialIO().getOutput() != null) {
                                                        try {
                                                            JSONObject portMsg = new JSONObject();
                                                            portMsg.put("init", false);
                                                            portMsg.put("callback", "qzSerialReturned");
                                                            JSONArray res = new JSONArray();
                                                            res.put(qz.getSerialIO().getPortName());
                                                            res.put(new String(qz.getSerialIO().getOutput(), qz.getCharset()));
                                                            portMsg.put("result", res);

                                                            sendResponse(session, portMsg);
                                                            qz.getSerialIO().clearOutput();
                                                        }
                                                        catch(JSONException e) {
                                                            log.warning("Issue sending data received from serial port - " + e.getMessage());
                                                        }
                                                    }
                                                }
                                            }

                                            private Session session;
                                            private PrintFunction qz;

                                            public Thread setup(Session session, PrintFunction qz) {
                                                this.session = session;
                                                this.qz = qz;
                                                return this;
                                            }
                                        }.setup(session, qz).start();
                                    }
                                }
                                if ("closePort".equals(name)) {
                                    result = params[0];
                                }
                                if ("send".equals(name)) {
                                    String data = new String(qz.getSerialIO().getOutput() == null? "".getBytes():qz.getSerialIO().getOutput(), qz.getCharset());
                                    qz.getSerialIO().clearOutput();

                                    result = (qz.getSerialIO() == null? null:"[\"" + qz.getSerialIO().getPortName() + "\",\"" + data + "\"]");
                                }

                                // Send new return value for getPrinter when selected printer changes
                                if ("findPrinter".equals(name)) {
                                    log.info("Selected New Printer");
                                    sendNewMethod(session, "getPrinter", qz.getPrinter());
                                }

                                if ("findPrinter".equals(name) || "findPrinters".equals(name)) {
                                    sendNewMethod(session, "getPrinters", qz.getPrinters()); //escape all backslashes
                                }

                                // Pass method results to simulate APPLET's synchronous calls
                                if ("findPorts".equals(name)) {
                                    sendNewMethod(session, "getPorts", qz.getPorts());
                                }

                                if ("findNetworkInfo".equals(name)) {
                                    sendNewMethod(session, "getIP", qz.getIP());
                                    sendNewMethod(session, "getMac", qz.getMac());
                                }

                                if ("setLogPostScriptFeatures".equals(name)) {
                                    sendNewMethod(session, "getLogPostScriptFeatures", qz.getLogPostScriptFeatures());
                                }

                                if ("useAlternatePrinting".equals(name)) {
                                    sendNewMethod(session, "isAlternatePrinting", qz.isAlternatePrinting());
                                }

                                if (qz.getException() != lastError) {
                                    String eMsg = (qz.getException() == null? null:qz.getException().getLocalizedMessage());

                                    sendNewMethod(session, "getException", eMsg);
                                    lastError = qz.getException();

                                    if (eMsg != null) {
                                        trayManager.displayErrorMessage(eMsg);
                                    }
                                }

                                break; //method worked, don't try others
                            }
                            catch(Exception e) {
                                log.warning("Method " + method.getName() + " failed: '" + e.getMessage() + "', will try overloaded method if one exists");
                                e.printStackTrace();
                            }
                        }
                    }

                    message.put("result", result);
                    sendResponse(session, message);
                    return;

                }
                catch(ReflectException ex) {
                    ex.printStackTrace();
                    message.put("error", ex.getMessage());
                }
            } else {
                // Didn't print request, clear the buffer for the next request
                qz.clear();
                //Send blocked callback to web page
                message.put("callback", "requestBlocked");
                message.put("init", "false");
                sendResponse(session, message);
            }
        }

        if (message.opt("error") != null) {
            message.put("error", "Unknown Message");
        }
        sendResponse(session, message);
    }

    private void sendNewMethod(Session session, String methodName, Object result) {
        if (result instanceof String) {
            //escape special characters
            result = ((String)result).replaceAll("\\\\", "%5C").replaceAll("\"", "%22");
        }

        sendResponse(session, "{\"method\":\"" + methodName + "\",\"params\":[],\"callback\":\"setupMethods\",\"init\":true,\"result\":\"" + result + "\"}");
    }

    private void sendError(Session session, String error) {
        sendResponse(session, "{\"error\": \"" + error + "\"}");
    }

    private void sendResponse(Session session, JSONObject message) {
        sendResponse(session, message.toString());
    }

    private void sendResponse(Session session, String jsonMsg) {
        try {
            log.info("Response: " + jsonMsg);
            session.getRemote().sendString(jsonMsg);
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    private Object convertType(String data, Object type) {
        log.info("CONVERTING " + data + " --> " + type);

        if (type instanceof String) { return data; }
        if (type instanceof Integer) { return Integer.decode(data); }
        if (type instanceof Float) { return Float.parseFloat(data); }
        if (type instanceof Double) { return Double.parseDouble(data); }
        if (type instanceof Boolean) { return Boolean.parseBoolean(data); }

        String name = String.valueOf(type);
        if ("int".equals(name)) { return Integer.decode(data); }
        if ("float".equals(name)) { return Float.parseFloat(data); }
        if ("double".equals(name)) { return Double.parseDouble(data); }
        if ("boolean".equals(name)) { return Boolean.parseBoolean(data); }

        return data;
    }

}
