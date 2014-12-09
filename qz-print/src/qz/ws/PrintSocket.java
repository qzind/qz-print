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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by robert on 9/9/2014.
 */
@WebSocket
public class PrintSocket {

    private static PrintFunction qz = null;
    private final Logger log = Logger.getLogger(PrintSocket.class.getName());

    private final List<String> restrictedMethodNames = Arrays.asList("run", "stop", "start", "call", "init", "destroy", "paint");

    private static JSONArray methods = null;

    private static Throwable lastError = null;

    @OnWebSocketConnect
    public void onConnect(Session session) {
        log.info("Server connect: " + session.getRemoteAddress());
    }

    //TODO - is idle timeout going to cause issues ??      - yes
    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        log.info("WebSocket close: " + statusCode + " - " + reason);
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        log.severe("Server error: " + error.getMessage());
    }

    @OnWebSocketFrame
    public void onFrame(Session session, Frame frame) {
        log.info("Server frame: " + frame.toString());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String json) {
        if (json == null) {
            sendResponse(session, "{'error': 'Invalid Message'}");
        } else if (!"ping".equals(json)){
            try {
                log.info("Request: "+ json);
                processMessage(session, new JSONObject(json));
            }
            catch(JSONException e) {
                sendResponse(session, "{'error': 'Invalid JSON'}");
                e.printStackTrace();
            }
        }
    }

    private void processMessage(Session session, JSONObject message) throws JSONException {
        if (qz == null) { qz = new PrintFunction(); qz.init(); qz.start(); }
        log.info("Server message: " + message);

        // Using Reflection, call correct method on PrintApplet.
        // Except for listMessages which is not part of PrintApplet
        if (message.getString("method").equals("listMessages")) {
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

            return;
        } else {        // Figure out which method is being called and call it returning any values
            JSONArray parts = message.optJSONArray("params");
            if (parts == null) parts = new JSONArray();
            String name = message.getString("method");
            Vector<Method> possibleMethods = new Vector<Method>();

            try {
                Method [] methods = PrintFunction.class.getMethods();
                for (Method m : methods) {
//                    log.info(name + ":" + params + "  -  " + mm.getName() + ":" + mm.getParameterTypes().length);
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
                    for (Method method : possibleMethods) { // We found methods that may work. Now call them
                        try {
                            // Create array of objects based on number of parameters and their types
                            Object[] params = new Object[parts.length()];
                            // We must get the parameter object types correct based on what the method wants
                            for (int i = 0; i < parts.length(); i++) {
                                params[i] = convertType(parts.getString(i), method.getParameterTypes()[i]);
                            }

                            // Using jOOR to call method since primitives are involved
                            // Invoke the method with all the parameters
                            log.info("Calling: " + name + Arrays.toString(params));
                            result = Reflect.on(qz).call(name, params).get();


                            if (result instanceof PrintFunction) {
                                result = "void";    // set since the return value is void
                            }

                            if ("openPort".equals(name)){
                                result = (qz.getSerialIO() == null ? null : qz.getSerialIO().getPortName());

                                // Watch serial port for any received data so we can send it to the browser
                                if (qz.getSerialIO() != null && qz.getSerialIO().isOpen()) {
                                    new Thread(){
                                        public void run(){
                                            while(qz.getSerialIO() != null) {
                                                if (qz.getSerialIO().getOutput() != null) {
                                                    try {
                                                        JSONObject portMsg = new JSONObject();
                                                        portMsg.put("init", false);
                                                        portMsg.put("callback", "qzSerialReturned");
                                                        portMsg.put("result", "[\""+ qz.getSerialIO().getPortName() +"\",\""+ new String(qz.getSerialIO().getOutput(), qz.getCharset()) +"\"]");

                                                        sendResponse(session, portMsg);
                                                        qz.getSerialIO().clearOutput();
                                                    }
                                                    catch (JSONException e){
                                                        log.warning("Issue sending data received from serial port - "+ e.getMessage());
                                                    }
                                                }
                                            }
                                        }

                                        private Session session;
                                        private PrintFunction qz;
                                        public Thread setup(Session session, PrintFunction qz){
                                            this.session = session;
                                            this.qz = qz;
                                            return this;
                                        }
                                    }.setup(session, qz).start();
                                }
                            }
                            if ("closePort".equals(name)){
                                result = params[0];
                            }
                            if ("send".equals(name)){
                                String data = new String(qz.getSerialIO().getOutput() == null? "".getBytes():qz.getSerialIO().getOutput(), qz.getCharset());
                                qz.getSerialIO().clearOutput();

                                result = (qz.getSerialIO() == null ? null : "[\""+ qz.getSerialIO().getPortName() +"\",\""+ data +"\"]");
                            }

                            // Send new return value for getPrinter when selected printer changes
                            if ("findPrinter".equals(name)) {
                                log.info("Selected New Printer");
                                sendNewMethod(session, "getPrinter", qz.getPrinter());
                                sendNewMethod(session, "getPrinters", qz.getPrinters().replaceAll("\\\\", "%5C")); //escape all backslashes
                            }

                            // Pass method results to simulate APPLET's synchronous calls
                            if ("findPorts".equals(name)) {
                                sendNewMethod(session, "getPorts", qz.getPorts());
                            }

                            if ("setLogPostScriptFeatures".equals(name)) {
                                sendNewMethod(session, "getLogPostScriptFeatures", qz.getLogPostScriptFeatures());
                            }

                            if ("useAlternatePrinting".equals(name)) {
                                sendNewMethod(session, "isAlternatePrinting", qz.isAlternatePrinting());
                            }

                            if (qz.getException() != lastError){
                                sendNewMethod(session, "getException", qz.getException()==null? null:qz.getException().getLocalizedMessage());
                                lastError = qz.getException();
                            }

                            break; //method worked, don't try others
                        } catch (Exception e) {
                            log.warning("Method "+ method.getName() +" failed: '"+ e.getMessage() +"', will try overloaded method if one exists");
                            e.printStackTrace();
                        }
                    }
                }

                message.put("result", result);
                sendResponse(session, message);
                return;

            } catch (ReflectException ex) {
                ex.printStackTrace();
                message.put("error", ex.getMessage());
            }
        }

        if (message.get("error") != null) {
            message.put("error", "Unknown Message");
        }
        sendResponse(session, message);
    }

    private void sendNewMethod(Session session, String methodName, Object result) {
        sendResponse(session, "{\"method\":\"" + methodName + "\",\"params\":[],\"callback\":\"setupMethods\",\"init\":true,\"result\":\"" + result + "\"}");
    }

    private void sendResponse(Session session, JSONObject message){
        sendResponse(session, message.toString());
    }

    private void sendResponse(Session session, String jsonMsg) {
        try {
            log.info("Response: "+ jsonMsg);
            session.getRemote().sendString(jsonMsg);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Object convertType(String data, Object type) {
        log.info("CONVERTING " + data + " --> " + type);

        if (type instanceof String) return data;
        if (type instanceof Integer) return Integer.decode(data);
        if (type instanceof Float) return Float.parseFloat(data);
        if (type instanceof Double) return Double.parseDouble(data);
        if (type instanceof Boolean) return Boolean.parseBoolean(data);

        String name = String.valueOf(type);
        if ("int".equals(name)) return Integer.decode(data);
        if ("float".equals(name)) return Float.parseFloat(data);
        if ("double".equals(name)) return Double.parseDouble(data);
        if ("boolean".equals(name)) return Boolean.parseBoolean(data);

        return data;
    }

}
