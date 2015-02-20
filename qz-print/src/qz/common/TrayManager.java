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
import qz.ui.GatewayDialog;
import qz.ui.IconCache;
import qz.ui.JAutoHideSystemTray;
import qz.utils.FileUtilities;
import qz.utils.SystemUtilities;
import qz.utils.UbuntuUtilities;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
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
    JAutoHideSystemTray tray;

    private GatewayDialog gw;

    // The name this UI component will use, i.e "QZ Print 1.9.0"
    private final String name;

    // The shortcut and startup helper
    private final ShortcutUtilities shortcutCreator;

    // Action to run when reload is triggered
    private Thread reloadThread;

    // Port that the socket is listening on
    private int port;

    private static final int WINDOW_WIDTH = 720;
    private static final int WINDOW_HEIGHT = 540;
    private static final int WINDOW_BUFFER = 32;

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
        tray = new JAutoHideSystemTray(POPUP_TIMEOUT);
        tray.setToolTipText(name);

        // Iterates over all images denoted by IconCache.getTypes() and caches them
        iconCache = new IconCache(tray.getTrayIcon().getSize());
        tray.setImage(iconCache.getImage(IconCache.Icon.DANGER_ICON));

        // Use some native system tricks to fix the tray icon to look proper on Ubuntu
        if (SystemUtilities.isLinux()) {
            UbuntuUtilities.fixTrayIcons(iconCache);
        }

        // The allow/block dialog
        gw = new GatewayDialog(null, "Action Required", iconCache);

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
        advancedMenu.setIcon(iconCache.getIcon(IconCache.Icon.SETTINGS_ICON));

        JMenuItem savedItem = new JMenuItem("Site Manager", iconCache.getIcon(IconCache.Icon.SAVED_ICON));
        savedItem.addActionListener(savedListener);

        JMenuItem logItem = new JMenuItem("View Logs", iconCache.getIcon(IconCache.Icon.LOG_ICON));
        logItem.addActionListener(logListener);

        JMenuItem openItem = new JMenuItem("Open file location", iconCache.getIcon(IconCache.Icon.FOLDER_ICON));
        openItem.addActionListener(openListener);

        JMenuItem desktopItem = new JMenuItem("Create Desktop shortcut", iconCache.getIcon(IconCache.Icon.DESKTOP_ICON));
        desktopItem.addActionListener(desktopListener);

        advancedMenu.add(savedItem);
        advancedMenu.add(logItem);
        advancedMenu.add(new JSeparator());
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
            showSites();
        }
    };

    private final ActionListener logListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            showLog();
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
            showAbout();
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
        if (gw.prompt("%s wants to access local resources", cert)) {
            printToLog("Allowed " + cert.getCommonName() + " to access local resources", TrayIcon.MessageType.INFO);
            if (gw.isPersistent()) {
                whiteList(cert);
            }
        } else {
            printToLog("Blocked " + cert.getCommonName() + " from accessing local resources", TrayIcon.MessageType.INFO);
            if (gw.isPersistent()) {
                blackList(cert);
            }
        }

        return gw.isApproved();
    }

    public boolean showPrintDialog(Certificate cert, String printer) {
        if (gw.prompt("%s wants to print to " + printer, cert)) {
            printToLog("Allowed " + cert.getCommonName() + " to print to " + printer, TrayIcon.MessageType.INFO);
            if (gw.isPersistent()) {
                whiteList(cert);
            }
        } else {
            printToLog("Blocked " + cert.getCommonName() + " from printing to " + printer, TrayIcon.MessageType.INFO);
            if (gw.isPersistent()) {
                blackList(cert);
            }
        }

        return gw.isApproved();
    }

    /*
    private JOptionPane getOptionPane(JComponent component) {
        if (component == null) { return null; }

        if (component instanceof JOptionPane) {
            return (JOptionPane) component;
        } else {
            return getOptionPane((JComponent) component.getParent());
        }
    }
    */

    /**
     * Show dialog for allowing or blocking a request
     *
     * @return <code>true</code> if the request is allowed, <code>false</code> if blocked
     *
    private boolean showAllowDialog(String message, Certificate cert) {
        if (cert.isBlocked()) { return false; }
        if (cert.isValidQZCert() && cert.isSaved()) { return true; }

        final JButton btnAllow = new JButton("Allow");
        btnAllow.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane pane = getOptionPane((JComponent) e.getSource());
                pane.setValue(btnAllow);
            }
        });

        final JButton btnAlways = new JButton("Always Allow");
        if (!cert.isValidQZCert()) { btnAlways.setEnabled(false); }
        btnAlways.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane pane = getOptionPane((JComponent) e.getSource());
                pane.setValue(btnAlways);
            }
        });

        final JButton btnBlock = new JButton("Block");
        btnBlock.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane pane = getOptionPane((JComponent) e.getSource());
                pane.setValue(btnBlock);
            }
        });

        final JButton btnNever = new JButton("Always Block");
        btnNever.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane pane = getOptionPane((JComponent) e.getSource());
                pane.setValue(btnNever);
            }
        });

        ImageIcon icon = iconCache.getIcon(cert.isValidQZCert() ? IconCache.Icon.VERIFIED_ICON : IconCache.Icon.UNVERIFIED_ICON);

        int opt = JOptionPane.showOptionDialog(tray, message, name, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, icon, new JButton[] {btnAllow, btnAlways, btnBlock, btnNever}, btnAllow);

        switch(opt) {
            case 0: return true; //Allow
            case 1: whiteList(cert); return true; //AlwaysAllow
            case 2: return false; //Block
            case 3: blackList(cert); return false; //AlwaysBlock
            default: return false;
        }
    }*/

    private void whiteList(Certificate cert) {
        FileUtilities.printLineToFile(Constants.ALLOW_FILE, cert.toString());
        displayInfoMessage(String.format(Constants.WHITE_LIST, cert.getOrganization()));
    }

    private void blackList(Certificate cert) {
        FileUtilities.printLineToFile(Constants.BLOCK_FILE, cert.toString());
        displayInfoMessage(String.format(Constants.BLACK_LIST, cert.getOrganization()));
    }

    /**
     * Displays a basic about dialog. Utilizes an exploit in the JOptionPane
     * container to layout text as HTML for quick formatting.
     */
    private void showAbout() {
        Object[] info = {
                new JLabel(iconCache.getIcon(IconCache.Icon.LOGO_ICON)),
                "<html><hr/><table>"
                        + row("Software:", name)
                        + row("Port Number:", port < 0? "None":"" + port)
                        + row("Publisher:", Constants.ABOUT_URL)
                        + row("Description:<br/>&nbsp;", String.format(Constants.ABOUT_DESC, Constants.ABOUT_TITLE))
                        + "</table></html>"
        };
        JOptionPane.showMessageDialog(tray, info, name, JOptionPane.PLAIN_MESSAGE);
    }

    private void showLog() {
        // Get the log text
        BufferedReader br = null;
        StringBuilder log = new StringBuilder();

        try {
            String line;
            br = new BufferedReader(new FileReader(FileUtilities.getFile(Constants.LOG_FILE)));
            while((line = br.readLine()) != null) {
                log.append(line).append("\r\n");
            }
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        finally {
            if (br != null) {
                try { br.close(); }catch(Exception ignore) {}
            }
        }

        // Build the window
        final JFrame logWindow = new JFrame(Constants.ABOUT_TITLE + " - Logs");
        logWindow.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        final JTextArea logText = new JTextArea(log.toString(), 20, 30);
        logText.setEditable(false);
        logText.setLineWrap(true);
        logText.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logText);

        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.X_AXIS));
        btnPanel.add(Box.createHorizontalGlue());

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileUtilities.deleteFile(Constants.LOG_FILE);
                logText.setText("");
            }
        });
        btnPanel.add(clearBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logWindow.dispatchEvent(new WindowEvent(logWindow, WindowEvent.WINDOW_CLOSING));
            }
        });
        btnPanel.add(closeBtn);

        btnPanel.add(Box.createRigidArea(new Dimension(WINDOW_BUFFER, 0)));

        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));
        content.add(scrollPane);
        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));
        content.add(btnPanel);
        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));

        Container container = logWindow.getContentPane();
        container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));

        container.add(content);

        // Actually show the window
        logWindow.setLocationRelativeTo(null);
        logWindow.setVisible(true);
    }

    private void showSites() {
        // Get the site lists
        File allowFile = FileUtilities.getFile(Constants.ALLOW_FILE);
        final Vector<Certificate> allowedCerts = new Vector<Certificate>();
        File blockFile = FileUtilities.getFile(Constants.BLOCK_FILE);
        final Vector<Certificate> blockedCerts = new Vector<Certificate>();

        BufferedReader br = null;

        //Pull from allow file
        try {
            String line;
            br = new BufferedReader(new FileReader(allowFile));
            while((line = br.readLine()) != null) {
                String[] data = line.split("\\t");

                if (data.length == Certificate.saveFields.length) {
                    HashMap<String,String> dataMap = new HashMap<String,String>();
                    for(int i = 0; i < data.length; i++) {
                        dataMap.put(Certificate.saveFields[i], data[i]);
                    }

                    allowedCerts.add(Certificate.loadCertificate(dataMap));
                }
            }
        }
        catch(IOException ioe) {
            ioe.printStackTrace();
        }
        finally {
            if (br != null) {
                try { br.close(); } catch(Exception ignore) {}
            }
        }

        //Pull from block file
        try {
            String line;
            br = new BufferedReader(new FileReader(blockFile));
            while((line = br.readLine()) != null) {
                String[] data = line.split("\\t");

                if (data.length == Certificate.saveFields.length) {
                    HashMap<String,String> dataMap = new HashMap<String,String>();
                    for(int i = 0; i < data.length; i++) {
                        dataMap.put(Certificate.saveFields[i], data[i]);
                    }

                    blockedCerts.add(Certificate.loadCertificate(dataMap));
                }
            }
        }
        catch(IOException ioe) {
            ioe.printStackTrace();
        }
        finally {
            if (br != null) {
                try { br.close(); } catch(Exception ignore) {}
            }
        }

        // Build the window
        final JFrame listWindow = new JFrame(Constants.ABOUT_TITLE + " - Site Manager");
        listWindow.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel labelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));

        final JLabel listLabel = new JLabel(String.format(Constants.WHITE_LIST, "").replaceAll("\\s+", " "));

        labelPanel.add(listLabel);
        labelPanel.add(Box.createHorizontalGlue());

        final Vector<String> allowedList = new Vector<String>();
        for(Certificate cert : allowedCerts) {
            allowedList.add("<html><div style='margin-left: 8px'>" + cert.getOrganization() + " (" + cert.getCommonName() + ")</div></html>");
        }
        final JList<String> allowedSiteList = new JList<String>(allowedList);
        allowedSiteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        allowedSiteList.setLayoutOrientation(JList.VERTICAL);

        final Vector<String> blockedList = new Vector<String>();
        for(Certificate cert : blockedCerts) {
            blockedList.add("<html><div style='margin-left: 8px'>" + cert.getOrganization() + " (" + cert.getCommonName() + ")</div></html>");
        }
        final JList<String> blockedSiteList = new JList<String>(blockedList);
        blockedSiteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blockedSiteList.setLayoutOrientation(JList.VERTICAL);

        JScrollPane allowedListScrollPane = new JScrollPane(allowedSiteList);
        JScrollPane blockedListScrollPane = new JScrollPane(blockedSiteList);

        final JTabbedPane tabPane = new JTabbedPane(SwingConstants.TOP);
        tabPane.addTab("Allowed", allowedListScrollPane);
        tabPane.setMnemonicAt(0, KeyEvent.VK_A);
        tabPane.addTab("Blocked", blockedListScrollPane);
        tabPane.setMnemonicAt(1, KeyEvent.VK_B);

        tabPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                String label = "";
                if (tabPane.getSelectedIndex() == 0) {
                    label = String.format(Constants.WHITE_LIST, "").replaceAll("\\s+", " ");
                } else if (tabPane.getSelectedIndex() == 1) {
                    label = String.format(Constants.BLACK_LIST, "").replaceAll("\\s+", " ");
                }

                listLabel.setText(label);
                allowedSiteList.clearSelection();
                blockedSiteList.clearSelection();
            }
        });

        JPanel detailPanel = new JPanel();
        final JLabel detailLabel = new JLabel();
        detailPanel.add(detailLabel);

        JScrollPane detailScrollPane = new JScrollPane(detailPanel);
        // Ensure the detail pane doesn't resize into the list pane's area
        detailScrollPane.setMinimumSize(new Dimension(WINDOW_WIDTH - (WINDOW_BUFFER * 2), WINDOW_BUFFER * 3));
        detailScrollPane.setPreferredSize(new Dimension(WINDOW_WIDTH - (WINDOW_BUFFER * 2), WINDOW_BUFFER * 3));
        detailScrollPane.setMaximumSize(new Dimension(WINDOW_WIDTH - (WINDOW_BUFFER * 2), WINDOW_BUFFER * 3));

        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new BoxLayout(btnPanel, BoxLayout.X_AXIS));
        btnPanel.add(Box.createHorizontalGlue());

        final JButton deleteBtn = new JButton("Delete");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tabPane.getSelectedIndex() == 0) { // Allowed
                    int index = allowedSiteList.getSelectedIndex();
                    Certificate cert = allowedCerts.get(index);

                    allowedCerts.remove(index);
                    allowedList.remove(index);
                    allowedSiteList.setListData(allowedList);

                    FileUtilities.deleteFromFile(Constants.ALLOW_FILE, cert.toString());
                    printToLog("Removed " + cert.getOrganization() + " (" + cert.getCommonName() + ") from the list of allowed sites", TrayIcon.MessageType.INFO);
                } else if (tabPane.getSelectedIndex() == 1) { // Blocked
                    int index = blockedSiteList.getSelectedIndex();
                    Certificate cert = blockedCerts.get(index);

                    blockedCerts.remove(index);
                    blockedList.remove(index);
                    blockedSiteList.setListData(blockedList);

                    FileUtilities.deleteFromFile(Constants.BLOCK_FILE, cert.toString());
                    printToLog("Removed " + cert.getOrganization() + " (" + cert.getCommonName() + ") from the list of blocked sites", TrayIcon.MessageType.INFO);
                }
            }
        });
        btnPanel.add(deleteBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listWindow.dispatchEvent(new WindowEvent(listWindow, WindowEvent.WINDOW_CLOSING));
            }
        });
        btnPanel.add(closeBtn);

        // Change layout text based on selection values
        allowedSiteList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    if (allowedSiteList.getSelectedIndex() == -1) { // DESELECTION
                        deleteBtn.setEnabled(false);
                        detailLabel.setText("");
                    } else { // SELECTION
                        deleteBtn.setEnabled(true);
                        detailLabel.setText(detailTable(allowedCerts.get(allowedSiteList.getSelectedIndex())));
                    }
                }
            }
        });

        blockedSiteList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    if (blockedSiteList.getSelectedIndex() == -1) { // DESELECTION
                        deleteBtn.setEnabled(false);
                        detailLabel.setText("");
                    } else { // SELECTION
                        deleteBtn.setEnabled(true);
                        detailLabel.setText(detailTable(blockedCerts.get(blockedSiteList.getSelectedIndex())));
                    }
                }
            }
        });

        KeyAdapter delKey = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    deleteBtn.doClick();
                }
            }
        };

        allowedSiteList.addKeyListener(delKey);
        blockedSiteList.addKeyListener(delKey);

        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));
        content.add(labelPanel);
        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));
        content.add(tabPane);
        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));
        content.add(detailScrollPane);
        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));
        content.add(btnPanel);
        content.add(Box.createRigidArea(new Dimension(0, WINDOW_BUFFER)));

        Container container = listWindow.getContentPane();
        container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));

        container.add(Box.createRigidArea(new Dimension(WINDOW_BUFFER, 0)));
        container.add(content);
        container.add(Box.createRigidArea(new Dimension(WINDOW_BUFFER, 0)));

        // Actually show the window
        listWindow.setLocationRelativeTo(null);
        listWindow.setVisible(true);
    }

    private String row(String label, String description) {
        return "<tr><td><b>" + label + "</b></td><td>" + description + "</td></tr>";
    }

    private String detailTable(Certificate cert) {
        return "<html><table>" +
                detailRow("Organization:", cert.getOrganization()) +
                detailRow("Common Name:", cert.getCommonName()) +
                detailRow("Verification:", cert.isValid()? "Verified By QZ Industries":"Unverified website") +
                detailRow("Valid From:", cert.getValidFrom()) +
                detailRow("Valid To:", cert.getValidTo()) +
                "</table></html>";
    }

    private String detailRow(String label, String data) {
        return "<tr><th style='text-align: right;'>" + label + "</th><td style='width: 32px;'></td><td>" + data + "</td></tr>";
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

}
