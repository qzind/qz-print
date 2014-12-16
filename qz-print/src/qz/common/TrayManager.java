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

import java.awt.Desktop;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import qz.ui.IconCache;
import qz.ui.JAutoHideSystemTray;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import qz.deploy.ShortcutUtilities;
import qz.utils.SystemUtilities;
import qz.utils.UbuntuUtilities;

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
    JAutoHideSystemTray tray;

    // The name this UI component will use, i.e "QZ Print 1.9.0"
    private final String name;

    // The shortcut and startup helper
    private final ShortcutUtilities shortcutCreator;

    // Action to run when reload is triggered
    private Thread reloadThread;

    // Port that the socket is listening on
    private int port ;

    /**
     * Create a AutoHideJSystemTray with the specified name/text
     */
    public TrayManager() {
        this.name = "QZ Print " + Constants.VERSION;
        this.port = -1;
        // Setup the shortcut name so that the UI components can use it
        shortcutCreator = ShortcutUtilities.getSystemShortcutCreator();
        shortcutCreator.setShortcutName("QZ Print Service");

        // Initialize a custom Swing system tray that hides after a timeout
        tray = new JAutoHideSystemTray(POPUP_TIMEOUT);
        tray.setToolTipText(name);

        // Iterates over all images denoted by IconCache.getTypes() and caches them
        iconCache = new IconCache(tray.getTrayIcon().getSize());
        tray.setImage(iconCache.getImage(IconCache.Icon.DANGER_ICON));

        // Use some native system tricks to fix the tray icon to look proper on Ubuntu
        if (SystemUtilities.isLinux()) {
            UbuntuUtilities.fixTrayIcons(iconCache);
        }

        addMenuItems(tray);
        //tray.displayMessage(name, name + " is running.", TrayIcon.MessageType.INFO);
    }

    /**
     * Stand-alone invocation of TrayManager
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
        advancedMenu.setIcon(iconCache.getIcon(IconCache.Icon.SETTINGS_ICON));
        
        JMenuItem openItem = new JMenuItem("Open file location", iconCache.getIcon(IconCache.Icon.FOLDER_ICON));
        openItem.addActionListener(openListener);
        
        JMenuItem desktopItem = new JMenuItem("Create Desktop shortcut", iconCache.getIcon(IconCache.Icon.DESKTOP_ICON));
        desktopItem.addActionListener(desktopListener);
        
        advancedMenu.add(openItem);
        advancedMenu.add(desktopItem);

        JMenuItem reloadItem = new JMenuItem("Reload", iconCache.getIcon(IconCache.Icon.RELOAD_ICON));
        reloadItem.addActionListener(reloadListener);
        //reloadItem.setEnabled(false);
        

        JMenuItem aboutItem = new JMenuItem("About...", iconCache.getIcon(IconCache.Icon.ABOUT_ICON));
        aboutItem.addActionListener(aboutListener);

        JSeparator separator = new JSeparator();

        JCheckBoxMenuItem startupItem = new JCheckBoxMenuItem("Automatically start");
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
            } catch  (Exception ex) {
                showError("Sorry, unable to open the file browser: " + ex.getLocalizedMessage());
            }
        }
    };

    private final ActionListener desktopListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            shortcutToggle(e, ShortcutUtilities.ToggleType.DESKTOP);
        }
    };
    
    private final ActionListener startupListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            shortcutToggle(e, ShortcutUtilities.ToggleType.STARTUP);
        }
    };

    /**
     * Overrides the default reload action listener
     * @param reloadThread The Thread to call when reload is clicked
     */
    public void setReloadThread(Thread reloadThread) {
        this.reloadThread = reloadThread;
    }

    private ActionListener reloadListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (reloadThread == null) {
                showError("Sorry, Reload has not yet been implimented.");
            } else {
                reloadThread.start();
            }
        }
    };

    private final ActionListener aboutListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            showAbout();
        }
    };

    private final ActionListener exitListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
            if (confirm("Exit " + name + "?")) {
                System.exit(0);
            }
        }
    };
    
    /**
     * Process toggle/checkbox events as they relate to creating shortcuts
     *
     * @param e The ActionEvent passed in from an ActionListener
     * @param toggleType Either ShortcutUtilities.TOGGLE_TYPE_STARTUP or 
     * ShortcutUtilities.TOGGLE_TYPE_DESKTOP
     */
    private void shortcutToggle(ActionEvent e, ShortcutUtilities.ToggleType toggleType) {
        // Assume true incase its a regular JMenuItem    
        boolean checkBoxState = true;
            if (e.getSource() instanceof JCheckBoxMenuItem) {
                checkBoxState = ((JCheckBoxMenuItem)e.getSource()).getState();
            }
            
            if (shortcutCreator.getJarPath() == null) {
                showError("Unable to determine jar path; " + toggleType
                        + " entry cannot succeed.");
                return;
            }

            if (!checkBoxState) {
                // Remove shortcut entry
                if (confirm("Remove " + name + " from " + toggleType + "?")) {
                    if (!shortcutCreator.removeShortcut(toggleType)) {
                        tray.displayMessage(name, "Error removing " +
                                toggleType + " entry", TrayIcon.MessageType.ERROR);
                        checkBoxState = true;   // Set our checkbox back to true
                    } else {
                        tray.displayMessage(name, "Successfully removed " +
                                toggleType + " entry", TrayIcon.MessageType.INFO);
                    }
                } else {
                    checkBoxState = true;   // Set our checkbox back to true
                }
            } else {
                // Add shortcut entry
                if (!shortcutCreator.createShortcut(toggleType)) {
                    tray.displayMessage(name, "Error creating " +
                                toggleType + " entry", TrayIcon.MessageType.ERROR);
                    checkBoxState = false;   // Set our checkbox back to false
                } else {
                    tray.displayMessage(name, "Successfully added " +
                                toggleType + " entry", TrayIcon.MessageType.INFO);
                }
            }
            
            if (e.getSource() instanceof JCheckBoxMenuItem) {
                ((JCheckBoxMenuItem)e.getSource()).setState(checkBoxState);
            }
    }
    

    /**
     * Displays a simple yes/no confirmation dialog and returns true/false
     * respectively
     *
     * @param message The text to display
     * @return true if yes is clicked, false if no is clicked
     */
    private boolean confirm(String message) {
        int i = JOptionPane.showConfirmDialog(tray, message, name, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return i == JOptionPane.YES_OPTION;
    }

    /**
     * Displays a basic error dialog.
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(tray, message,
                name, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Displays a basic about dialog. Utilizes an exploit in the JOptionPane
     * container to layout text as HTML for quick formatting.
     */
    private void showAbout() {
        Object[] info = {
            new JLabel(iconCache.getIcon(IconCache.Icon.LOGO_ICON)),
            "<html><hr><table>"
            + row("Software:", name)
            + row("Port Number:", port < 0 ? "None" : "" + port)
            + row("Publisher:", "http://qzindustries.com")
            + row("Description:<br>&nbsp;", "QZ Print is a print plugin for your web browser, <br>"
            + "used to print barcodes, receipts and more.")
            + "</table></html>"
        };
        JOptionPane.showMessageDialog(tray, info, name, JOptionPane.PLAIN_MESSAGE);
    }

    private String row(String label, String description) {
        return "<tr><td><b>" + label + "</b></td><td>" + description + "</td></tr>";
    }

    /**
     * Sets the WebSocket Server instance for displaying port information and restarting the server
     * @param server The Server instance contain to bind the reload action to
     * @param running Object used to notify PrintSocket to reiterate its main while loop
     * @param portIndex Object used to notify PrintSocket to reset its port array counter
     */
    public void setServer(final Server server, final AtomicBoolean running, final AtomicInteger portIndex) {
        if (server != null && server.getConnectors().length > 0) {
            port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
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
                    } catch (Exception e) {
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
                public void run() { tray.setIcon(iconCache.getIcon(i));}
            }));
        }
    }

    /**
     * Thread safe method for setting the specified status message
     * @param caption The title of the tray message
     * @param text The text body of the tray message
     * @param messageType The message type: TrayIcon.MessageType.INFO, .WARN, .ERROR
     */
    private void displayMessage(final String caption, final String text, final TrayIcon.MessageType messageType) {
        if (tray != null) {
            SwingUtilities.invokeLater(new Thread(new Runnable() {
                @Override
                public void run() {
                    tray.displayMessage(caption, text, messageType);
                }
            }));
        }
    }
}
