package qz.ws;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import java.net.BindException;
import java.util.logging.Logger;

/**
 * Created by robert on 9/9/2014.
 */

public class PrintWebSocketServer {

    private static final Logger log = Logger.getLogger(PrintWebSocketServer.class.getName());

    private static final Integer[] ports = new Integer[] { 8181, 8282, 8383, 8484 };


    public static void main(String[] args) {
        runServer();
    }

    public static void runServer() {
        boolean running = false;
        int portIndex = -1;

        while(!running && ++portIndex < ports.length) {
            try {
                Server server = new Server(ports[portIndex]);

                WebSocketHandler wsHandler = new WebSocketHandler() {
                    @Override
                    public void configure(WebSocketServletFactory factory) {
                        factory.register(PrintSocket.class);
                    }
                };
                server.setHandler(wsHandler);
                server.setStopAtShutdown(true);

                server.start();

                running = true;
                log.info("Server started on port " + ports[portIndex]);

                server.join();
            }
            catch (BindException e) {
                log.warning("Port "+ ports[portIndex] +" is already in use");
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}
