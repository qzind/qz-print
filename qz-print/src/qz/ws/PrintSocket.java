package qz.ws;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.joor.Reflect;
import org.joor.ReflectException;
import qz.PrintApplet;
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
    private final Logger log = Logger.getLogger(PrintApplet.class.getName());

    private final List<String> restrictedMethodNames = Arrays.asList("run", "stop", "start", "call", "init", "destroy", "paint");

    private static JSONArray methods = null;

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
        } else {
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
                                jMethod.put("parameters", method.getParameterCount());

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

            try {
                Method [] methods = PrintFunction.class.getMethods();
                Method method = null;
                for (Method m : methods) {
//                    log.info(name + ":" + params + "  -  " + mm.getName() + ":" + mm.getParameterTypes().length);
                    if (m.getName().equals(name) && parts.length() == m.getParameterTypes().length) {
                        method = m;
                        break;
                    }
                }

                Object result;     // default for void

                if (method != null) { // We found one that will work. Now call it
                    String selectedPrinter = qz.getPrinter();

                    // Create array of objects based on number of parameters and their types
                    Object [] params = new Object[parts.length()];
                    // We must get the parameter object types correct based on what the method wants
                    for (int i = 0; i < parts.length(); i++) {
                        params[i] = convertType(parts.getString(i), method.getParameterTypes()[i]);
                    }

                    // Using jOOR to call method since primitives are involved
                    // Invoke the method with all the parameters
                    log.info("Calling: "+ name + Arrays.toString(params));
                    result = Reflect.on(qz).call(name, params).get();

                    if (result instanceof PrintFunction) {
                        result = "void";    // set since the return value is void
                    }

                    // Send new return value for getPrinter when selected printer changes
                    if ((selectedPrinter == null && qz.getPrinter() != null) || (selectedPrinter != null && !selectedPrinter.equals(qz.getPrinter()))){
                        log.info("Selected New Printer");
                        sendResponse(session, "{\"method\":\"getPrinter\",\"params\":[],\"callback\":\"setupMethods\",\"init\":true,\"result\":\""+ qz.getPrinter() +"\"}");
                    }

                } else {
                    message.put("error", "Method not found");
                    sendResponse(session, message);
                    return;
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
        System.out.println(data + " to type " + type);
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
