package qz.ws;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.auth.Certificate;
import qz.common.Constants;
import qz.common.TrayManager;
import qz.printer.PrintOptions;
import qz.printer.PrintOutput;
import qz.printer.PrintServiceMatcher;
import qz.printer.action.PrintProcessor;
import qz.utils.NetworkUtilities;
import qz.utils.PrintingUtilities;

import javax.print.PrintServiceLookup;
import java.awt.print.PrinterAbortException;
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
        log.error("Connection error", error);
        trayManager.displayErrorMessage(error.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        if (message == null || message.isEmpty()) {
            sendError(session, null, "Message is empty");
        }
        if ("ping".equals(message)) { return; } //keep-alive call / no need to process

        String uid = null;
        try {
            log.debug("Message: " + message);
            JSONObject json = new JSONObject(message);
            uid = json.getString("uid");

            //TODO - check certificates / signing

            processMessage(session, json);
        }
        catch(JSONException e) {
            sendError(session, uid, e.getMessage());
            log.error("Bad JSON: {}", e.getMessage());
        }
        catch(Exception e) {
            sendError(session, uid, e.getMessage());
            log.error("Problem processing message", e);
        }
    }

    /**
     * Determine which method was called from web API
     *
     * @param session WebSocket session
     * @param json    JSON received from web API
     */
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
                processPrintRequest(session, UID, params);
                break;

            //TODO
            //case "serial.findPorts": break;
            //case "serial.openPort": break;
            //case "serial.sendData": break;
            //case "serial.closePort": break;

            case "getVersion":
                sendResult(session, UID, Constants.VERSION);
                break;

            default:
                sendError(session, UID, "Invalid function call: " + call);
                break;
        }
    }

    /**
     * Determine print variables and send data to printer
     *
     * @param session WebSocket session
     * @param UID     ID of call from web API
     * @param params  Params of call from web API
     */
    private void processPrintRequest(Session session, String UID, JSONObject params) {
        try {
            PrintOutput output = new PrintOutput(params.optJSONObject("printer"));
            PrintOptions options = new PrintOptions(params.optJSONObject("options"));

            PrintProcessor processor = PrintingUtilities.getPrintProcessor(params.getJSONArray("data"));
            log.debug("Using {} to print", processor.getClass().getName());

            processor.parseData(params.getJSONArray("data"), options);
            processor.print(output, options);
            log.info("Printing complete");

            sendResult(session, UID, null);
        }
        catch(PrinterAbortException e) {
            log.warn("Printing cancelled");
            sendError(session, UID, "Printing cancelled");
        }
        catch(Exception e) {
            log.error("Failed to print", e);

            String err = e.getMessage();
            if (err == null) { err = e.getClass().getSimpleName(); }
            sendError(session, UID, "Printing failed: " + err);
        }
    }


    /**
     * Send JSON reply to web API for call {@code messageUID}
     *
     * @param session     WebSocket session
     * @param messageUID  ID of call from web API
     * @param returnValue Return value of method call, can be {@code null}
     */
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

    /**
     * Send JSON error reply to web API for call {@code messageUID}
     *
     * @param session    WebSocket session
     * @param messageUID ID of call from web API
     * @param errorMsg   Error from method call
     */
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

    /**
     * Raw send method for replies
     *
     * @param session WebSocket session
     * @param reply   JSON Object of reply to web API
     */
    private void send(Session session, JSONObject reply) {
        try {
            session.getRemote().sendString(reply.toString());
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

}
