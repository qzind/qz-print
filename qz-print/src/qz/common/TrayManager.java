/**
 * @author Tres Finocchiaro
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

package qz.common;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import qz.auth.Certificate;
import qz.deploy.ShortcutUtilities;
import qz.ui.*;
import qz.utils.FileUtilities;
import qz.utils.SystemUtilities;
import qz.utils.UbuntuUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the icons and actions associated with the TrayIcon
 *
 * @author Tres Finocchiaro
 */
public class TrayManager {
    // The cached icons
    private final IconCache iconCache;

    // Time in millis before the popup menu disappears
    final int POPUP_TIMEOUT = 2000;

    // Custom swing pop-up menu
    AutoHidePopupTray tray;

    private GatewayDialog gatewayDialog;
    private AboutDialog aboutDialog;
    private LogDialog logDialog;
    private SiteManagerDialog sitesDialog;

    // The name this UI component will use, i.e "QZ Print 1.9.0"
    private final String name;

    // The shortcut and startup helper
    private final ShortcutUtilities shortcutCreator;

    // Action to run when reload is triggered
    private Thread reloadThread;

    // Port that the socket is listening on
    private int port;

    /**
     * Create a AutoHideJSystemTray with the specified name/text
     */
    public TrayManager() {
        this.name = Constants.ABOUT_TITLE + " " + Constants.VERSION;
        this.port = -1;
        // Setup the shortcut name so that the UI components can use it
        shortcutCreator = ShortcutUtilities.getSystemShortcutCreator();
        shortcutCreator.setShortcutName(Constants.ABOUT_TITLE + " Service");

        // Initialize a custom Swing system tray that hides after a timeout
        tray = new AutoHidePopupTray(POPUP_TIMEOUT);
        tray.setToolTipText(name);

        // Iterates over all images denoted by IconCache.getTypes() and caches them
        iconCache = new IconCache(tray.getTrayIcon().getSize());
        tray.setImage(iconCache.getImage(IconCache.Icon.DANGER_ICON));

        // Use some native system tricks to fix the tray icon to look proper on Ubuntu
        if (SystemUtilities.isLinux()) {
            UbuntuUtilities.fixTrayIcons(iconCache);
        }

        // The allow/block dialog
        gatewayDialog = new GatewayDialog(null, "Action Required", iconCache);

        addMenuItems(tray);
        //tray.displayMessage(name, name + " is running.", TrayIcon.MessageType.INFO);

        tray.getTrayIcon().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //TODO - show pending dialog
                System.out.println("Tray message clicked");
            }
        });
    }

    /**
     * Stand-alone invocation of TrayManager
     *
     * @param args arguments to pass to main
     */
    public static void main(String args[]) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new TrayManager();
            }
        });
    }

    /**
     * Builds the swing pop-up menu with the specified items
     */
    private void addMenuItems(JPopupMenu popup) {
        JMenu advancedMenu = new JMenu("Advanced");
        advancedMenu.setMnemonic(KeyEvent.VK_A);
        advancedMenu.setIcon(iconCache.getIcon(IconCache.Icon.SETTINGS_ICON));

        JMenuItem sitesItem = new JMenuItem("Site Manager...", iconCache.getIcon(IconCache.Icon.SAVED_ICON));
        sitesItem.setMnemonic(KeyEvent.VK_M);
        sitesItem.addActionListener(savedListener);
        sitesDialog = new SiteManagerDialog(sitesItem, iconCache);

        JMenuItem logItem = new JMenuItem("View Logs...", iconCache.getIcon(IconCache.Icon.LOG_ICON));
        logItem.setMnemonic(KeyEvent.VK_L);
        logItem.addActionListener(logListener);
        logDialog = new LogDialog(logItem, iconCache);

        JMenuItem openItem = new JMenuItem("Open file location", iconCache.getIcon(IconCache.Icon.FOLDER_ICON));
        openItem.setMnemonic(KeyEvent.VK_O);
        openItem.addActionListener(openListener);

        JMenuItem desktopItem = new JMenuItem("Create Desktop shortcut", iconCache.getIcon(IconCache.Icon.DESKTOP_ICON));
        desktopItem.setMnemonic(KeyEvent.VK_D);
        desktopItem.addActionListener(desktopListener);

        advancedMenu.add(sitesItem);
        advancedMenu.add(logItem);
        advancedMenu.add(new JSeparator());
        advancedMenu.add(openItem);
        advancedMenu.add(desktopItem);


        JMenuItem reloadItem = new JMenuItem("Reload", iconCache.getIcon(IconCache.Icon.RELOAD_ICON));
        reloadItem.setMnemonic(KeyEvent.VK_R);
        reloadItem.addActionListener(reloadListener);

        JMenuItem aboutItem = new JMenuItem("About...", iconCache.getIcon(IconCache.Icon.ABOUT_ICON));
        aboutItem.setMnemonic(KeyEvent.VK_B);
        aboutItem.addActionListener(aboutListener);
        aboutDialog = new AboutDialog(aboutItem, iconCache, name, port);

        JSeparator separator = new JSeparator();

        JCheckBoxMenuItem startupItem = new JCheckBoxMenuItem("Automatically start");
        startupItem.setMnemonic(KeyEvent.VK_S);
        startupItem.setState(shortcutCreator.hasStartupShortcut());
        startupItem.addActionListener(startupListener);

        JMenuItem exitItem = new JMenuItem("Exit", iconCache.getIcon(IconCache.Icon.EXIT_ICON));
        exitItem.addActionListener(exitListener);

        popup.add(advancedMenu);
        popup.add(reloadItem);
        popup.add(aboutItem);
        popup.add(startupItem);
        popup.add(separator);
        popup.add(exitItem);
    }

    private final ActionListener openListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            try {
                Desktop d = Desktop.getDesktop();
                d.browse(new File(shortcutCreator.getParentDirectory()).toURI());
            }
            catch(Exception ex) {
                showErrorDialog("Sorry, unable to open the file browser: " + ex.getLocalizedMessage());
            }
        }
    };

    private final ActionListener desktopListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            shortcutToggle(e, ShortcutUtilities.ToggleType.DESKTOP);
        }
    };

    private final ActionListener savedListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            ArrayList<Certificate> allowList = readCertificates(FileUtilities.getFile(Constants.ALLOW_FILE));
            ArrayList<Certificate> blockList = readCertificates(FileUtilities.getFile(Constants.BLOCK_FILE));

            // TODO:  Auto-refresh the reading of block/allow similar to logs
            sitesDialog.setAllowList(allowList);
            sitesDialog.setBlockList(blockList);
            sitesDialog.setVisible(true);
        }
    };

    private final ActionListener logListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            logDialog.setVisible(true);
        }
    };

    private final ActionListener startupListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            shortcutToggle(e, ShortcutUtilities.ToggleType.STARTUP);
        }
    };

    /**
     * Sets the default reload action (in this case, <code>Thread.start()</code>) to be fired
     *
     * @param reloadThread The Thread to call when reload is clicked
     */
    public void setReloadThread(Thread reloadThread) {
        this.reloadThread = reloadThread;
    }

    private ActionListener reloadListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (reloadThread == null) {
                showErrorDialog("Sorry, Reload has not yet been implemented.");
            } else {
                reloadThread.start();
            }
        }
    };

    private final ActionListener aboutListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            aboutDialog.setPort(port);
            aboutDialog.setVisible(true);
        }
    };

    private final ActionListener exitListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (showConfirmDialog("Exit " + name + "?")) {
                System.exit(0);
            }
        }
    };

    /**
     * Process toggle/checkbox events as they relate to creating shortcuts
     *
     * @param e          The ActionEvent passed in from an ActionListener
     * @param toggleType Either ShortcutUtilities.TOGGLE_TYPE_STARTUP or
     *                   ShortcutUtilities.TOGGLE_TYPE_DESKTOP
     */
    private void shortcutToggle(ActionEvent e, ShortcutUtilities.ToggleType toggleType) {
        // Assume true in case its a regular JMenuItem
        boolean checkBoxState = true;
        if (e.getSource() instanceof JCheckBoxMenuItem) {
            checkBoxState = ((JCheckBoxMenuItem) e.getSource()).getState();
        }

        if (shortcutCreator.getJarPath() == null) {
            showErrorDialog("Unable to determine jar path; " + toggleType + " entry cannot succeed.");
            return;
        }

        if (!checkBoxState) {
            // Remove shortcut entry
            if (showConfirmDialog("Remove " + name + " from " + toggleType + "?")) {
                if (!shortcutCreator.removeShortcut(toggleType)) {
                    tray.displayMessage(name, "Error removing " + toggleType + " entry", TrayIcon.MessageType.ERROR);
                    checkBoxState = true;   // Set our checkbox back to true
                } else {
                    tray.displayMessage(name, "Successfully removed " + toggleType + " entry", TrayIcon.MessageType.INFO);
                }
            } else {
                checkBoxState = true;   // Set our checkbox back to true
            }
        } else {
            // Add shortcut entry
            if (!shortcutCreator.createShortcut(toggleType)) {
                tray.displayMessage(name, "Error creating " + toggleType + " entry", TrayIcon.MessageType.ERROR);
                checkBoxState = false;   // Set our checkbox back to false
            } else {
                tray.displayMessage(name, "Successfully added " + toggleType + " entry", TrayIcon.MessageType.INFO);
            }
        }

        if (e.getSource() instanceof JCheckBoxMenuItem) {
            ((JCheckBoxMenuItem) e.getSource()).setState(checkBoxState);
        }
    }

    /**
     * Displays a simple yes/no confirmation dialog and returns true/false
     * respectively
     *
     * @param message The text to display
     * @return true if yes is clicked, false if no is clicked
     */
    private boolean showConfirmDialog(String message) {
        int i = JOptionPane.showConfirmDialog(tray, message, name, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return i == JOptionPane.YES_OPTION;
    }

    /**
     * Displays a basic error dialog.
     */
    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(tray, message, name, JOptionPane.ERROR_MESSAGE);
    }

    public boolean showGatewayDialog(Certificate cert) {
        if (gatewayDialog.prompt("%s wants to access local resources", cert)) {
            printToLog("Allowed " + cert.getCommonName() + " to access local resources", TrayIcon.MessageType.INFO);
            if (gatewayDialog.isPersistent()) {
                whiteList(cert);
            }
        } else {
            printToLog("Blocked " + cert.getCommonName() + " from accessing local resources", TrayIcon.MessageType.INFO);
            if (gatewayDialog.isPersistent()) {
                blackList(cert);
            }
        }

        return gatewayDialog.isApproved();
    }

    public boolean showPrintDialog(Certificate cert, String printer) {
        if (gatewayDialog.prompt("%s wants to print to " + printer, cert)) {
            printToLog("Allowed " + cert.getCommonName() + " to print to " + printer, TrayIcon.MessageType.INFO);
            if (gatewayDialog.isPersistent()) {
                whiteList(cert);
            }
        } else {
            printToLog("Blocked " + cert.getCommonName() + " from printing to " + printer, TrayIcon.MessageType.INFO);
            if (gatewayDialog.isPersistent()) {
                blackList(cert);
            }
        }

        return gatewayDialog.isApproved();
    }

    private void whiteList(Certificate cert) {
        FileUtilities.printLineToFile(Constants.ALLOW_FILE, cert.data());
        displayInfoMessage(String.format(Constants.WHITE_LIST, cert.getOrganization()));
    }

    private void blackList(Certificate cert) {
        FileUtilities.printLineToFile(Constants.BLOCK_FILE, cert.data());
        displayInfoMessage(String.format(Constants.BLACK_LIST, cert.getOrganization()));
    }

    private String row(String label, String description) {
        return "<tr><td><b>" + label + "</b></td><td>" + description + "</td></tr>";
    }

    /**
     * Sets the WebSocket Server instance for displaying port information and restarting the server
     *
     * @param server    The Server instance contain to bind the reload action to
     * @param running   Object used to notify PrintSocket to reiterate its main while loop
     * @param portIndex Object used to notify PrintSocket to reset its port array counter
     */
    public void setServer(final Server server, final AtomicBoolean running, final AtomicInteger portIndex) {
        if (server != null && server.getConnectors().length > 0) {
            port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
            displayInfoMessage("Server started on port " + port);
            setDefaultIcon();

            setReloadThread(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        setDangerIcon();
                        setPort(-1);
                        server.stop();
                        running.set(false);
                        portIndex.set(-1);
                    }
                    catch(Exception e) {
                        displayErrorMessage("Error stopping print socket: " + e.getLocalizedMessage());
                    }
                }
            }));
        } else {
            displayErrorMessage("Invalid server");
        }
    }

    /**
     * Sets the port number in use for display purposes only
     *
     * @param port The port to display in the About dialog
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Thread safe method for setting the warning status message
     */
    public void displayInfoMessage(String text) {
        displayMessage(name, text, TrayIcon.MessageType.INFO);
    }

    /**
     * Thread safe method for setting the default icon
     */
    public void setDefaultIcon() {
        setIcon(IconCache.Icon.DEFAULT_ICON);
    }

    /**
     * Thread safe method for setting the error status message
     */
    public void displayErrorMessage(String text) {
        displayMessage(name, text, TrayIcon.MessageType.ERROR);
    }

    /**
     * Thread safe method for setting the danger icon
     */
    public void setDangerIcon() {
        setIcon(IconCache.Icon.DANGER_ICON);
    }

    /**
     * Thread safe method for setting the warning status message
     */
    public void displayWarningMessage(String text) {
        displayMessage(name, text, TrayIcon.MessageType.WARNING);
    }


    /**
     * Thread safe method for setting the warning icon
     */
    public void setWarningIcon() {
        setIcon(IconCache.Icon.WARNING_ICON);
    }

    /**
     * Thread safe method for setting the specified icon
     */
    private void setIcon(final IconCache.Icon i) {
        if (tray != null) {
            SwingUtilities.invokeLater(new Thread(new Runnable() {
                @Override
                public void run() {
                    tray.setIcon(iconCache.getIcon(i));
                }
            }));
        }
    }

    /**
     * Thread safe method for setting the specified status message
     *
     * @param caption     The title of the tray message
     * @param text        The text body of the tray message
     * @param messageType The message type: TrayIcon.MessageType.INFO, .WARN, .ERROR
     */
    private void displayMessage(final String caption, final String text, final TrayIcon.MessageType messageType) {
        if (tray != null) {
            SwingUtilities.invokeLater(new Thread(new Runnable() {
                @Override
                public void run() {
                    tray.displayMessage(caption, text, messageType);
                    printToLog(text, messageType);
                }
            }));
        }
    }

    public void printToLog(String message, TrayIcon.MessageType type) {
        FileUtilities.printLineToFile(Constants.LOG_FILE, String.format("[%s] %tY-%<tm-%<td %<tH:%<tM:%<tS - %s", type, new Date(), message));
    }


    public ArrayList<Certificate> readCertificates(File file) {
        ArrayList<Certificate> certList = new ArrayList<Certificate>();
        BufferedReader br = null;
        try {

            String line;
            br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                String[] data = line.split("\\t");

                if (data.length == Certificate.saveFields.length) {
                    HashMap<String, String> dataMap = new HashMap<String, String>();
                    for (int i = 0; i < data.length; i++) {
                        dataMap.put(Certificate.saveFields[i], data[i]);
                    }
                    certList.add(Certificate.loadCertificate(dataMap));
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignore) {
                }
            }
        }
        return certList;
    }

}
