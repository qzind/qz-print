package qz.ws;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.auth.Certificate;
import qz.common.TrayManager;
import qz.printer.PrintOptions;
import qz.printer.PrintServiceMatcher;
import qz.printer.Printing;
import qz.utils.NetworkUtilities;

import javax.print.PrintServiceLookup;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;


@WebSocket
public class PrintSocketClient {

    private static final Logger log = LoggerFactory.getLogger(PrintSocketClient.class);

    private final TrayManager trayManager = PrintSocketServer.getTrayManager();


    private static HashMap<Integer,Certificate> certificates = new HashMap<>(); //port -> public certificate
    private static AtomicBoolean dialogOpen = new AtomicBoolean(false);


    @OnWebSocketConnect
    public void onConnect(Session session) {
        log.info("Connection opened:" + session.getRemoteAddress());
        trayManager.displayInfoMessage("Client connected");
    }

    @OnWebSocketClose
    public void onClose(Session session, int closeCode, String reason) {
        log.info("Connection closed: " + closeCode + " - " + reason);
        trayManager.displayInfoMessage("Client disconnected");
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        log.warn("Connection error: " + error.getMessage());
        trayManager.displayErrorMessage(error.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        if (message == null || message.isEmpty()) {
            sendError(session, "Message is empty");
        }
        if ("ping".equals(message)) { return; } //keep-alive call / no need to process

        try {
            log.info("Message: " + message);
            JSONObject json = new JSONObject(message);

            //TODO - check certificates / signing

            processMessage(session, json);
        }
        catch(JSONException e) {
            sendError(session, e.getMessage());
            log.error("Bad JSON: {}", e.getMessage());
        }
        catch(Exception e) {
            sendError(session, e.getMessage());
            log.error("Problem processing message", e);
        }
    }

    private void processMessage(Session session, JSONObject json) throws JSONException {
        String UID = json.getString("uid");
        String call = json.getString("call");
        JSONObject params = json.optJSONObject("params");
        if (params == null) { params = new JSONObject(); }

        //TODO - gateway dialog

        switch(call) {
            case "websocket.getNetworkInfo":
                sendResult(session, UID, NetworkUtilities.getNetworkJSON());
                break;
            case "printers.getDefault":
                sendResult(session, UID, PrintServiceLookup.lookupDefaultPrintService().getName());
                break;
            case "printers.find":
                if (params.has("query")) {
                    sendResult(session, UID, PrintServiceMatcher.getPrinterJSON(params.getString("query")));
                } else {
                    sendResult(session, UID, PrintServiceMatcher.getPrintersJSON());
                }
                break;

            case "print":
                PrintOptions options = new PrintOptions(json.getJSONObject("params").getJSONObject("options"));
                sendResult(session, UID, null);
                break;

            //TODO
            //case "serial.findPorts": break;
            //case "serial.openPort": break;
            //case "serial.sendData": break;
            //case "serial.closePort": break;

            default:
                sendError(session, UID, "Invalid function call: " + call);
                break;
        }
    }


    private void sendResult(Session session, String messageUID, Object returnValue) {
        try {
            JSONObject reply = new JSONObject();
            reply.put("uid", messageUID);
            reply.put("result", returnValue);
            send(session, reply);
        }
        catch(JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendError(Session session, String errorMsg) {
        sendError(session, null, errorMsg);
    }

    private void sendError(Session session, String messageUID, String errorMsg) {
        try {
            JSONObject reply = new JSONObject();
            reply.putOpt("uid", messageUID);
            reply.put("error", errorMsg);
            send(session, reply);
        }
        catch(JSONException e) {
            e.printStackTrace();
        }
    }

    private void send(Session session, JSONObject reply) {
        try {
            session.getRemote().sendString(reply.toString());
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

}
