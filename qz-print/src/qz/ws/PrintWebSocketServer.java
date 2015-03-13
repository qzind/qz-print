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

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import qz.common.TrayManager;

import java.net.BindException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Created by robert on 9/9/2014.
 */

public class PrintWebSocketServer {

    private static final int MAX_MESSAGE_SIZE = Integer.MAX_VALUE;

    private static final Logger log = Logger.getLogger(PrintWebSocketServer.class.getName());

    private static final Integer[] ports = new Integer[] { 8181, 8282, 8383, 8484 };

    private static final TrayManager trayManager = new TrayManager();

    public static void main(String[] args) {
        runServer();
    }

    public static void runServer() {
        final AtomicBoolean running = new AtomicBoolean(false);
        final AtomicInteger portIndex = new AtomicInteger(-1);

        while(!running.get() && portIndex.getAndIncrement() < ports.length) {
            trayManager.setWarningIcon();
            try {
                final Server server = new Server(ports[portIndex.get()]);

                final WebSocketHandler wsHandler = new WebSocketHandler() {
                    @Override
                    public void configure(WebSocketServletFactory factory) {
                        factory.register(PrintSocket.class);
                        factory.getPolicy().setMaxTextMessageSize(MAX_MESSAGE_SIZE);
                    }
                };
                server.setHandler(wsHandler);
                server.setStopAtShutdown(true);
                server.start();

                running.set(true);
                log.info("Server started on port " + ports[portIndex.get()]);
                trayManager.setServer(server, running, portIndex);

                server.join();

                // Don't remove this next line or while loop will seize on restart
                log.info("Shutting down server");
            }
            catch (BindException e) {
                log.warning("Port "+ ports[portIndex.get()] +" is already in use");
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the TrayManager instance for this SocketServer
     * @return The TrayManager instance
     */
    public static TrayManager getTrayManager() {
        return trayManager;
    }
}
