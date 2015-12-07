/**
 * @author Kyle Berezin
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

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import qz.common.Constants;
import qz.common.TrayManager;
import qz.deploy.DeployUtilities;

/**
 * Created by Kyle on 12/1/2015.
 */

@WebSocket
public class SingleInstanceChecker {

    private static final Logger log = Logger.getLogger(SingleInstanceChecker.class.getName());
    private static final int AUTO_CLOSE = 6 * 1000;
    private static final int TIMEOUT = 3 * 1000;
    private TrayManager trayManager;
    private WebSocketClient client;

    public SingleInstanceChecker(WebSocketClient client, TrayManager trayManager, int port) {
        log.log(Level.INFO, "Checking for a running instance of " + Constants.ABOUT_TITLE + " on port " + port);
        this.client = client;
        autoCloseClient(AUTO_CLOSE);
        this.trayManager = trayManager;
        connectTo("ws://localhost:" + port);
    }

    private void connectTo(String uri) {
        try {
            if (client == null) {
                client = new WebSocketClient();
                client.start();
                client.setConnectTimeout(TIMEOUT);
                client.setAsyncWriteTimeout(TIMEOUT);
                client.setMaxIdleTimeout(TIMEOUT);
                client.setStopTimeout(TIMEOUT);
            }
            URI targetUri = new URI(uri);
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            client.connect(this, targetUri, request);
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not connect to url " + uri + " reason, " + e.getMessage());
            trayManager = null;
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        log.log(Level.WARNING, "Connection closed" + reason);
        trayManager = null;
    }

    @OnWebSocketError
    public void onError(Throwable e) {
        if (!e.getMessage().equals("Connection refused: no further information")) {
            log.log(Level.WARNING, "WebSocket error,  " + e.getMessage());
        }
        trayManager = null;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        try {
            session.getRemote().sendString(Constants.PROBE_REQUEST);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not send data to server " + e.getMessage());
            session.close();
            trayManager = null;
        }
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        session.close();
        if (message.equals(Constants.PROBE_RESPONSE)) {
            log.log(Level.SEVERE, Constants.ABOUT_TITLE + " is already running on " + session.getRemoteAddress().toString());
            trayManager.exit(2);
        }
    }

    private void autoCloseClient(final int millis){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(millis);
                    if (client != null) {
                        if (!(client.isStopped() || client.isStopping())) {
                            client.stop();
                        }
                    }
                } catch (Exception ignore) {
                    log.log(Level.SEVERE, "Couldn't close client after delay");
                }
            }
        });
    }
}
