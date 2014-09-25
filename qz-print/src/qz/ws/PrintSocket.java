package qz.ws;

import com.sun.deploy.util.StringUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import org.joor.Reflect;
import qz.PrintApplet;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by robert on 9/9/2014.
 */
@WebSocket
public class PrintSocket {

    private PrintApplet qz = null;

    @OnWebSocketConnect
    public void onConnect(Session session) {
        System.out.println("Server connect: " + session.getRemoteAddress());
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        System.out.println("Server close: " + statusCode + " - " + reason);
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        System.out.println("Server error: " + error.getMessage());
    }

    @OnWebSocketFrame
    public void onFrame(Session session, Frame frame) {
        System.out.println("Server frame: " + frame.toString());
    }

    private static Set<String> methods = null;

    @OnWebSocketMessage
    public String onMessage(Session session, String text) {
        if (text == null) return "ERROR:Invalid Message";
        if (qz == null) { qz = new PrintApplet(); qz.init(); qz.start(); }
        System.out.println("Server message: " + text);

        // Using Reflection, call correct method on PrintApplet.
        // Except for listMessages which is not part of PrintApplet
        if (text.startsWith("listMessages")) {
            if (methods == null) { methods = new TreeSet<String>(); }
            try {
                Class c = PrintApplet.class;
                Method[] m = c.getDeclaredMethods();
                for (Method method : m) {
                    if (method.getModifiers() == 1 &&
                        method.getDeclaringClass() == PrintApplet.class) {
                        String name = method.getName();
                        if (!name.equals("run") &&               // Some methods must not be included
                            !name.equals("stop") &&
                            !name.equals("start") &&
                            !name.equals("call") &&
                            !name.equals("init") &&
                            !name.equals("destroy") &&
                            !name.equals("paint")) {
                            methods.add(method.getName() + "," +
                                        method.getReturnType() + "," +
                                        method.getParameterTypes().length);
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (methods == null) return "ERROR:Unable to list messages";
            return StringUtils.join(methods, "\t");
        } else {        // Figure out which method is being called and call it returning any values
            String [] parts = text.split("\\t");
            String name = parts[0];
            int params = Integer.valueOf(parts.length-1);
            try {
                Method [] m = PrintApplet.class.getMethods();
                Method method = null;
                for (Method mm : m) {
                    System.out.println(name + ":" + params + "  -  " + mm.getName() + ":" + mm.getParameterTypes().length);
                    if (mm.getName().equals(name) &&
                        params == mm.getParameterTypes().length) {
                            method = mm;
                    }
                }
                Object result = "";     // default for void
                if (method != null) { // We found one that will work. Now call it
                    // Create array of objects based on number of parameters and their types
                    Object [] obj = new Object[params];
                    // We must get the parameter object types correct based on what the method wants
                    for (int x = 1; x < params + 1; x++) {
                        obj[x-1] = covertType(parts[x], method.getParameterTypes()[x-1]);
                    }

                    // Using jOOR to call method since primitives are involved
                    // Invoke the method with all the parameters
                    switch(params) {
                        case 0:
                            result = Reflect.on(qz).call(name).get();
//                            result = String.valueOf(method.invoke(qz));
                            break;
                        case 1:
                            result = Reflect.on(qz).call(name, obj[0]).get();
//                            result = String.valueOf(method.invoke(qz, obj[0]));
                            break;
                        case 2:
                            result = Reflect.on(qz).call(name, obj[0], obj[1]).get();
//                            result = String.valueOf(method.invoke(qz, obj[0], obj[1]));
                            break;
                        case 3:
                            result = Reflect.on(qz).call(name, obj[0], obj[1], obj[2]).get();
//                            result = String.valueOf(method.invoke(qz, obj[0], obj[1], obj[2]));
                            break;
                        case 4:
                            result = Reflect.on(qz).call(name, obj[0], obj[1], obj[2], obj[3]).get();
//                            result = String.valueOf(method.invoke(qz, obj[0], obj[1], obj[2], obj[3]));
                            break;
                        case 5:
                            result = Reflect.on(qz).call(name, obj[0], obj[1], obj[2], obj[3], obj[4]).get();
//                            result = String.valueOf(method.invoke(qz, obj[0], obj[1], obj[2], obj[3], obj[5]));
                            break;
                        default:
                            result = "ERROR:Invalid parameters";
                    }
                } else {
                    return "ERROR:Message not found";
                }
                return "RESULT:" + result;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return "ERROR:Unknown Message";
    }

    private Object covertType(String data, Object type) {
        System.out.println(data + " to type " + type);
        if (type instanceof String) return data;
        if (type instanceof Integer) return Integer.decode(data);
        if (type instanceof Float) return Float.parseFloat(data);
        if (type instanceof Double) return Double.parseDouble(data);
        String name = String.valueOf(type);
        if ("int".equals(name)) return Integer.decode(data);
        if ("float".equals(name)) return Float.parseFloat(data);
        if ("double".equals(name)) return Double.parseDouble(data);
        return data;
    }

    public static void main(String[] args) {
        PrintSocket ps = new PrintSocket();
        try { Thread.sleep(2000); } catch (Exception ignore) {}
        System.out.println(ps.onMessage(null, "listMessages"));
//        try { Thread.sleep(2000); } catch (Exception ignore) {}
//        System.out.println(ps.onMessage(null, "getPrinters"));
        try { Thread.sleep(2000); } catch (Exception ignore) {}
        System.out.println(ps.onMessage(null, "setPrinter\t1"));
        System.exit(0);
    }
}
