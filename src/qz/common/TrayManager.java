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

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.auth.Certificate;
import qz.deploy.DeployUtilities;
import qz.deploy.LinuxCertificate;
import qz.deploy.WindowsDeploy;
import qz.ui.*;
import qz.utils.*;
import qz.ws.PrintSocketServer;
import qz.ws.SingleInstanceChecker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the icons and actions associated with the TrayIcon
 *
 * @author Tres Finocchiaro
 */
public class TrayManager {

    private static final Logger log = LoggerFactory.getLogger(TrayManager.class);

    // The cached icons
    private final IconCache iconCache;

    // Custom swing pop-up menu
    private AutoHidePopupTray tray;

    private ConfirmDialog confirmDialog;
    private GatewayDialog gatewayDialog;
    private AboutDialog aboutDialog;
    private LogDialog logDialog;
    private SiteManagerDialog sitesDialog;

    // Need a class reference to this so we can set it from the request dialog window
    private JCheckBoxMenuItem anonymousItem;

    // The name this UI component will use, i.e "QZ Print 1.9.0"
    private final String name;

    // The shortcut and startup helper
    private final DeployUtilities shortcutCreator;

    // Action to run when reload is triggered
    private Thread reloadThread;

    /**
     * Create a AutoHideJSystemTray with the specified name/text
     */
    public TrayManager() {
        name = Constants.ABOUT_TITLE + " " + Constants.VERSION;

        // Setup the shortcut name so that the UI components can use it
        shortcutCreator = DeployUtilities.getSystemShortcutCreator();
        shortcutCreator.setShortcutName(Constants.ABOUT_TITLE);

        // Initialize a custom Swing system tray that hides after a timeout
        int POPUP_TIMEOUT = 2000;
        tray = new AutoHidePopupTray(POPUP_TIMEOUT);
        tray.setToolTipText(name);

        // Iterates over all images denoted by IconCache.getTypes() and caches them
        iconCache = new IconCache(tray.getIconSize());
        tray.setImage(iconCache.getImage(IconCache.Icon.DANGER_ICON));

        // Linux specific tasks
        if (SystemUtilities.isLinux()) {
            // Fix the tray icon to look proper on Ubuntu
            UbuntuUtilities.fixTrayIcons(iconCache);
            // Install cert into user's nssdb for Chrome, etc
            LinuxCertificate.installCertificate();
        } else if (SystemUtilities.isWindows()) {
            // Configure IE intranet zone via registry to allow websockets
            WindowsDeploy.configureIntranetZone();
            WindowsDeploy.configureEdgeLoopback();
        }

        // The allow/block dialog
        gatewayDialog = new GatewayDialog(null, "Action Required", iconCache);

        // The ok/cancel dialog
        confirmDialog = new ConfirmDialog(null, "Please Confirm", iconCache);

        addMenuItems(tray);
        //tray.displayMessage(name, name + " is running.", Level.INFO);

        if (tray.getTrayIcon() != null) {
            tray.getTrayIcon().addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        tray.setVisible(false);
                        aboutListener.actionPerformed(new ActionEvent(e.getSource(), e.getID(), null));
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {}

                @Override
                public void mouseReleased(MouseEvent e) {}

                @Override
                public void mouseEntered(MouseEvent e) {}

                @Override
                public void mouseExited(MouseEvent e) {}
            });
        }
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

        anonymousItem = new JCheckBoxMenuItem("Block Anonymous Requests");
        anonymousItem.setMnemonic(KeyEvent.VK_K);
        anonymousItem.setState(Certificate.UNSIGNED.isBlocked());
        anonymousItem.addActionListener(anonymousListener);

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
        advancedMenu.add(anonymousItem);
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
        aboutDialog = new AboutDialog(aboutItem, iconCache, name);
        aboutDialog.addPanelButton(sitesItem);
        aboutDialog.addPanelButton(logItem);
        aboutDialog.addPanelButton(openItem);

        if (SystemUtilities.isMac()) {
            MacUtilities.registerAboutDialog(aboutDialog);
            MacUtilities.registerQuitHandler(this);
        }

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
                // To get Macs to open the package's contents rather than launching it, we -R the package's auth folder to
                // select it in finder. Thus we are opening auth's parent folder rather than the package.
                if (SystemUtilities.isMac()) {
                    ShellUtilities.execute(new String[] {"open", "-R", shortcutCreator.getJarPath()});
                } else {
                    Desktop d = Desktop.getDesktop();
                    d.open(new File(shortcutCreator.getParentDirectory()));
                }
            }
            catch(Exception ex) {
                if (!SystemUtilities.isLinux() || !ShellUtilities.execute(new String[] {"xdg-open", shortcutCreator.getParentDirectory()})) {
                    showErrorDialog("Sorry, unable to open the file browser: " + ex.getLocalizedMessage());
                }
            }
        }
    };

    private final ActionListener desktopListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            shortcutToggle(e, DeployUtilities.ToggleType.DESKTOP);
        }
    };

    private final ActionListener savedListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            sitesDialog.setVisible(true);
        }
    };

    private final ActionListener anonymousListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            boolean checkBoxState = true;
            if (e.getSource() instanceof JCheckBoxMenuItem) {
                checkBoxState = ((JCheckBoxMenuItem)e.getSource()).getState();
            }

            log.debug("Block unsigned: {}", checkBoxState);

            if (checkBoxState) {
                blackList(Certificate.UNSIGNED);
            } else {
                FileUtilities.deleteFromFile(Constants.BLOCK_FILE, Certificate.UNSIGNED.data());
            }
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
            shortcutToggle(e, DeployUtilities.ToggleType.STARTUP);
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
            aboutDialog.setVisible(true);
        }
    };

    private final ActionListener exitListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (confirmDialog.prompt("Exit " + name + "?")) { exit(0); }
        }
    };

    public void exit(int returnCode) {
        System.exit(returnCode);
    }

    /**
     * Process toggle/checkbox events as they relate to creating shortcuts
     *
     * @param e          The ActionEvent passed in from an ActionListener
     * @param toggleType Either ShortcutUtilities.TOGGLE_TYPE_STARTUP or
     *                   ShortcutUtilities.TOGGLE_TYPE_DESKTOP
     */
    private void shortcutToggle(ActionEvent e, DeployUtilities.ToggleType toggleType) {
        // Assume true in case its a regular JMenuItem
        boolean checkBoxState = true;
        if (e.getSource() instanceof JCheckBoxMenuItem) {
            checkBoxState = ((JCheckBoxMenuItem)e.getSource()).getState();
        }

        if (shortcutCreator.getJarPath() == null) {
            showErrorDialog("Unable to determine jar path; " + toggleType + " entry cannot succeed.");
            return;
        }

        if (!checkBoxState) {
            // Remove shortcut entry
            if (confirmDialog.prompt("Remove " + name + " from " + toggleType + "?")) {
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
            ((JCheckBoxMenuItem)e.getSource()).setState(checkBoxState);
        }
    }

    /**
     * Displays a basic error dialog.
     */
    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(tray, message, name, JOptionPane.ERROR_MESSAGE);
    }

    public boolean showGatewayDialog(final Certificate cert) {
        if (cert == null) {
            displayErrorMessage("Invalid certificate");
            return false;
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        gatewayDialog.prompt("%s wants to access local resources", cert);
                    }
                });
            }
            catch(Exception ignore) {}

            if (gatewayDialog.isApproved()) {
                log.info("Allowed {} to access local resources", cert.getCommonName());
                if (gatewayDialog.isPersistent()) {
                    whiteList(cert);
                }
            } else {
                log.info("Blocked {} from accessing local resources", cert.getCommonName());
                if (gatewayDialog.isPersistent()) {
                    blackList(cert);
                }
            }
        }

        return gatewayDialog.isApproved();
    }

    public boolean showPrintDialog(final Certificate cert, final String printer) {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    gatewayDialog.prompt("%s wants to print to " + printer, cert);
                }
            });
        }
        catch(Exception ignore) {}

        if (gatewayDialog.isApproved()) {
            log.info("Allowed {} to print to {}", cert.getCommonName(), printer);
            if (gatewayDialog.isPersistent()) {
                whiteList(cert);
            }
        } else {
            log.info("Blocked {} from printing to {}", cert.getCommonName(), printer);
            if (gatewayDialog.isPersistent()) {
                if (Certificate.UNSIGNED.equals(cert)) {
                    anonymousItem.doClick(); // if always block anonymous requests -> flag menu item
                } else {
                    blackList(cert);
                }
            }
        }

        return gatewayDialog.isApproved();
    }

    private void whiteList(Certificate cert) {
        FileUtilities.printLineToFile(Constants.ALLOW_FILE, cert.data());
        displayInfoMessage(String.format(Constants.WHITE_LIST, "\"" + cert.getOrganization() + "\""));
    }

    private void blackList(Certificate cert) {
        FileUtilities.printLineToFile(Constants.BLOCK_FILE, cert.data());
        displayInfoMessage(String.format(Constants.BLACK_LIST, cert.getOrganization()));
    }

    /**
     * Sets the WebSocket Server instance for displaying port information and restarting the server
     *
     * @param server            The Server instance contain to bind the reload action to
     * @param running           Object used to notify PrintSocket to reiterate its main while loop
     * @param securePortIndex   Object used to notify PrintSocket to reset its port array counter
     * @param insecurePortIndex Object used to notify PrintSocket to reset its port array counter
     */
    public void setServer(final Server server, final AtomicBoolean running, final AtomicInteger securePortIndex, final AtomicInteger insecurePortIndex) {
        if (server != null && server.getConnectors().length > 0) {
            singleInstanceCheck(PrintSocketServer.INSECURE_PORTS, insecurePortIndex.get());

            displayInfoMessage("Server started on port(s) " + TrayManager.getPorts(server));
            aboutDialog.setServer(server);
            setDefaultIcon();

            setReloadThread(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        setDangerIcon();
                        server.stop();
                        running.set(false);
                        securePortIndex.set(-1);
                        insecurePortIndex.set(-1);
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
     * Returns a String representation of the ports assigned to the specified Server
     */
    public static String getPorts(Server server) {
        String ports = "";
        for(Connector c : server.getConnectors()) {
            ports = ports + ((ServerConnector)c).getLocalPort() + ", ";
        }

        return ports.replaceAll(", $", "");
    }

    /** Thread safe method for setting the warning status message */
    public void displayInfoMessage(String text) {
        displayMessage(name, text, TrayIcon.MessageType.INFO);
    }

    /** Thread safe method for setting the default icon */
    public void setDefaultIcon() {
        setIcon(IconCache.Icon.DEFAULT_ICON);
    }

    /** Thread safe method for setting the error status message */
    public void displayErrorMessage(String text) {
        displayMessage(name, text, TrayIcon.MessageType.ERROR);
    }

    /** Thread safe method for setting the danger icon */
    public void setDangerIcon() {
        setIcon(IconCache.Icon.DANGER_ICON);
    }

    /** Thread safe method for setting the warning status message */
    public void displayWarningMessage(String text) {
        displayMessage(name, text, TrayIcon.MessageType.WARNING);
    }

    /** Thread safe method for setting the warning icon */
    public void setWarningIcon() {
        setIcon(IconCache.Icon.WARNING_ICON);
    }

    /** Thread safe method for setting the specified icon */
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
     * @param caption The title of the tray message
     * @param text    The text body of the tray message
     * @param level   The message type: Level.INFO, .WARN, .SEVERE
     */
    private void displayMessage(final String caption, final String text, final TrayIcon.MessageType level) {
        if (tray != null) {
            SwingUtilities.invokeLater(new Thread(new Runnable() {
                @Override
                public void run() {
                    tray.displayMessage(caption, text, level);
                    //trayLogger.log(level, "Tray Message: " + text);
                }
            }));
        }
    }

    /* TODO - replace with log4j file appender
    public void addLogHandler(Logger logger) {
        if (logHandler == null) {
            try {
                logHandler = createLogHandler();
            }
            catch(IOException e) {
                logger.log(Level.WARNING, String.format("Could not write to log file %s. Log history be limited to the console only.", Constants.LOG_FILE), e);
            }
        }

        if (logHandler != null) {
            logger.addHandler(logHandler);
        }
    }

    public FileHandler createLogHandler() throws IOException {
        String logFile = SystemUtilities.getDataDirectory() + File.separator + Constants.LOG_FILE + "%g.log";

        FileHandler logHandler = new FileHandler(logFile, Constants.LOG_SIZE, Constants.LOG_ROTATIONS, true);
        logHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return String.format("[%s] %tY-%<tm-%<td %<tH:%<tM:%<tS - %s\r\n", logRecord.getLevel().toString(), new Date(), logRecord.getMessage());
            }
        });
        return logHandler;
    }
    */

    public void singleInstanceCheck(Integer[] insecurePorts, Integer insecurePortIndex) {
        for(int port : insecurePorts) {
            if (port != insecurePorts[insecurePortIndex]) {
                new SingleInstanceChecker(this, port);
            }
        }
    }

}
