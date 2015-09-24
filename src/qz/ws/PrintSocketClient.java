package qz.ws;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import qz.auth.Certificate;
import qz.common.TrayManager;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;


@WebSocket
public class PrintSocketClient {

    private static final Logger log = Logger.getLogger(PrintSocketClient.class.getName());

    private final TrayManager trayManager = PrintSocketServer.getTrayManager();

    private static HashMap<Integer,Certificate> certificates = new HashMap<>();
    private static AtomicBoolean dialogOpen = new AtomicBoolean(false);


    @OnWebSocketConnect
    public void onConnect(Session session) {
        log.info("New connection:" + session.getRemoteAddress());
        trayManager.displayInfoMessage("Client connected");
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        log.info("Connection closed: " + statusCode + " - " + reason);
        trayManager.displayInfoMessage("Client disconnected");
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        log.warning("Connection error: " + error.getMessage());
        trayManager.displayErrorMessage(error.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        log.info("Message: " + message);
    }

}
