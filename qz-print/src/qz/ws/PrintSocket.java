package qz.ws;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.extensions.Frame;


/**
 * Created by robert on 9/9/2014.
 */
@WebSocket
public class PrintSocket {

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

    @OnWebSocketMessage
    public void onMessage(Session session, String text) {
        System.out.println("Server message: " + text);
    }

}
