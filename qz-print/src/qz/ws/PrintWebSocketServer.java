package qz.ws;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

/**
 * Created by robert on 9/9/2014.
 */


public class PrintWebSocketServer {

    public static void main(String[] args) {
        runServer();
    }

    public static void runServer() {
        try {
            Server server = new Server(8181);
            WebSocketHandler wsHandler = new WebSocketHandler() {
                @Override
                public void configure(WebSocketServletFactory factory) {
                    factory.register(PrintSocket.class);
                }
            };
            server.setHandler(wsHandler);
            server.setStopAtShutdown(true);
            server.start();
            System.out.println("Server started");
            server.join();
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}