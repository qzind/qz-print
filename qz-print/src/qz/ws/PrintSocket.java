package qz.ws;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.extensions.Frame;
import qz.PrintApplet;


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

    @OnWebSocketMessage
    public void onMessage(Session session, String text) {
        if (qz == null) qz = new PrintApplet();
        System.out.println("Server message: " + text);

        // Look for space. Text before the space is the command. The rest is data.
        int space = text.indexOf(' ');
        if (space > 0) {
            String command = text.substring(0, space);
            if ("qzReady".equals(command)) {
                qz.isActive();
            } else if ("notReady".equals(command)) {
                // !qz.isActive();
            } else if ("isLoaded".equals(command)) {
                qz.isActive();
            } else if ("qzDonePrinting".equals(command)) {

            } else if ("useDefaultPrinter".equals(command)) {

            } else if ("printToFile".equals(command)) {

            } else if ("printToHost".equals(command)) {

            } else if ("findPrinter(name)".equals(command)) {

            } else if ("findPrinters".equals(command)) {

            } else if ("printEPL".equals(command)) {

            } else if ("printESCP".equals(command)) {

            } else if ("printZPL".equals(command)) {

            } else if ("printEPCL".equals(command)) {

            } else if ("appendEPCL".equals(command)) {

            } else if ("print64".equals(command)) {

            } else if ("printPages".equals(command)) {

            } else if ("printXML".equals(command)) {

            } else if ("printHex".equals(command)) {

            } else if ("printFile".equals(command)) {

            } else if ("printImage(scaleImage)".equals(command)) {

            } else if ("printPDF".equals(command)) {

            } else if ("printHTML".equals(command)) {

            } else if ("listNetworkInfo".equals(command)) {

            } else if ("printHTML5Page".equals(command)) {

            } else if ("logFeatures".equals(command)) {

            } else if ("useAlternatePrinting".equals(command)) {

            } else if ("listSerialPorts".equals(command)) {

            } else if ("".equals(command)) {

            } else if ("closeSerialPort".equals(command)) {

            } else if ("sendSerialData".equals(command)) {

            }
        }
    }

}
