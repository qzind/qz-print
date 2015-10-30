/**
 * @author Robert Casto
 * <p/>
 * Copyright (C) 2013 Tres Finocchiaro, QZ Industries
 * <p/>
 * IMPORTANT: This software is dual-licensed
 * <p/>
 * LGPL 2.1 This is free software. This software and source code are released under the "LGPL 2.1 License". A copy of
 * this license should be distributed with this software. http://www.gnu.org/licenses/lgpl-2.1.html
 * <p/>
 * QZ INDUSTRIES SOURCE CODE LICENSE This software and source code *may* instead be distributed under the "QZ Industries
 * Source Code License", available by request ONLY. If source code for this project is to be made proprietary for an
 * individual and/or a commercial entity, written permission via a copy of the "QZ Industries Source Code License" must
 * be obtained first. If you've obtained a copy of the proprietary license, the terms and conditions of the license
 * apply only to the licensee identified in the agreement. Only THEN may the LGPL 2.1 license be voided.
 */

package qz.ws;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.common.Constants;
import qz.common.TrayManager;
import qz.deploy.DeployUtilities;

import javax.swing.*;
import java.net.BindException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by robert on 9/9/2014.
 */

public class PrintSocketServer {

    private static final Logger log = LoggerFactory.getLogger(PrintSocketServer.class);

    private static final int MAX_MESSAGE_SIZE = Integer.MAX_VALUE;
    private static final Integer[] SECURE_PORTS = new Integer[] {8181, 8282, 8383, 8484};
    private static final Integer[] INSECURE_PORTS = new Integer[] {8182, 8283, 8384, 8485};


    private static TrayManager trayManager;


    public static void main(String[] args) {
        for (String s : args) {
            // Print version information and exit
            if ("-v".equals(s) || "--version".equals(s)) {
                System.out.println(Constants.VERSION);
                System.exit(0);
            }
        }
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    trayManager = new TrayManager();
                }
            });
            runServer();
        }
        catch(Exception e) {
            log.error("Could not start tray manager", e);
        }

        log.warn("The web socket server is no longer running");
    }

    public static void runServer() {
        final AtomicBoolean running = new AtomicBoolean(false);
        final AtomicInteger securePortIndex = new AtomicInteger(0);
        final AtomicInteger insecurePortIndex = new AtomicInteger(0);

        Properties sslProperties = DeployUtilities.loadSSLProperties();

        while(!running.get() && securePortIndex.get() < SECURE_PORTS.length && insecurePortIndex.get() < INSECURE_PORTS.length) {
            Server server = new Server(INSECURE_PORTS[insecurePortIndex.get()]);

            if (sslProperties != null) {
                // Bind the secure socket on the proper port number (i.e. 9341), add it as an additional connector
                SslContextFactory sslContextFactory = new SslContextFactory();
                sslContextFactory.setKeyStorePath(sslProperties.getProperty("wss.keystore"));
                sslContextFactory.setKeyStorePassword(sslProperties.getProperty("wss.storepass"));

                SslConnectionFactory sslConnection = new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString());
                HttpConnectionFactory httpConnection = new HttpConnectionFactory(new HttpConfiguration());

                ServerConnector connector = new ServerConnector(server, sslConnection, httpConnection);
                connector.setHost("localhost");
                connector.setPort(SECURE_PORTS[securePortIndex.get()]);
                server.addConnector(connector);
            } else {
                log.warn("Could not start secure WebSocket");
            }

            try {
                final WebSocketHandler wsHandler = new WebSocketHandler() {
                    @Override
                    public void configure(WebSocketServletFactory factory) {
                        factory.register(PrintSocketClient.class);
                        factory.getPolicy().setMaxTextMessageSize(MAX_MESSAGE_SIZE);
                    }
                };
                server.setHandler(wsHandler);
                server.setStopAtShutdown(true);
                server.start();

                running.set(true);
                trayManager.setServer(server, running, securePortIndex, insecurePortIndex);
                log.info("Server started on port(s) " + TrayManager.getPorts(server));

                server.join();

                // Don't remove this next line or while loop will seize on restart
                //TODO - find out why
                log.info("Shutting down server");
            }
            catch(BindException | MultiException e) {
                //order of getConnectors is the order we added them -> insecure first
                if (server.getConnectors()[0].isFailed()) {
                    insecurePortIndex.incrementAndGet();
                }
                if (server.getConnectors().length > 1 && server.getConnectors()[1].isFailed()) {
                    securePortIndex.incrementAndGet();
                }

                //explicitly stop the server, because if only 1 port has an exception the other will still be opened
                try{ server.stop(); }catch(Exception ignore){ ignore.printStackTrace(); }
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get the TrayManager instance for this SocketServer
     *
     * @return The TrayManager instance
     */
    public static TrayManager getTrayManager() {
        return trayManager;
    }
}
