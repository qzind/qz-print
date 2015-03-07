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

package qz.ui;

import java.awt.*;
import java.awt.event.*;
import java.util.logging.*;
import javax.swing.*;
import qz.utils.SystemUtilities;

/**
 * Creates a more robust System Tray menu by leveraging a JPopupMenu.
 * Java currently only supports an AWT PopupMenu, so this class is
 * a work-around for that limitation.
 * 
 * @author A. Tres Finocchiaro
 */
public class PopupTray extends JPopupMenu {
    private final SystemTray systemTray;
    private final TrayIcon trayIcon;
    
    /**
     * Constructs a JPopupMenu attached to the system's SystemTray with
     * the specified imageIcon and toolTipText
     * @param imageIcon The icon to display in the System Tray
     * @param toolTipText The text to display during tray mouse-over
     */
    public PopupTray(ImageIcon imageIcon, String toolTipText) {
        super();
        trayIcon = createTrayIcon();
        systemTray = getSystemTray();
        attachSystemTray(trayIcon);
        setIcon(imageIcon);
        setToolTipText(toolTipText);
    }
    
    /**
     * Constructs a JPopupMenu attached to the system's SystemTray with
     * the specified image and toolTipText
     * @param image The image to display in the System Tray
     * @param toolTipText The text to display during tray mouse-over
     */
    public PopupTray(Image image, String toolTipText) {
        super(toolTipText);
        trayIcon = createTrayIcon();
        systemTray = getSystemTray();
        attachSystemTray(trayIcon);
        setImage(image);
        setToolTipText(toolTipText);
    }
    
    /**
     * Constructs a JPopupMenu attached to the system's SystemTray with
     * the specified toolTipText
     * @param toolTipText The text to display during tray mouse-over
     */
    public PopupTray(String toolTipText) {
        super(toolTipText);
        trayIcon = createTrayIcon();
        systemTray = getSystemTray();
        attachSystemTray(trayIcon);
        setToolTipText(toolTipText);
    }
    
    /**
     * Constructs a JPopupMenu attached to the system's SystemTray with
     * no icon and no toolTipText
     */
    public PopupTray() {
        super();
        trayIcon = createTrayIcon();
        systemTray = getSystemTray();
        attachSystemTray(trayIcon);
    }
    
    /**
     * Sets the TrayIcon image based on the Image specified
     * @param image The image to set the TrayIcon to
     */
    public final void setImage(Image image) {
        if (image != null) {
            trayIcon.setImage(image);
        }
    }
    
    /**
     * Sets the toolTipText for the underlying TrayIcon instance, rather than
     * that of the JPopupMenu
     * @param toolTipText tool text to set
     */
    @Override
    public final void setToolTipText(String toolTipText) {
        trayIcon.setToolTip(toolTipText);
    }
    
    /**
     * Returns the underlying TrayIcon for this object
     * @return The underlying TrayIcon object
     */
    public TrayIcon getTrayIcon() {
        return trayIcon;
    }
    
    /**
     * Returns the width of the underlying TrayIcon
     * @return Width measurement in pixels
     */
    public int getTrayWidth() {
        return (int)trayIcon.getSize().getWidth();
    }
    
    /**
     * Returns the height of the underlying TrayIcon
     * @return Height measurement in pixels
     */
    public int getTrayHeight() {
        return (int)trayIcon.getSize().getHeight();
    }
    
    /**
     * Sets the TrayIcon image based on the ImageIcon specified
     * @param imageIcon The ImageIcon to set the TrayIcon to
     */
    public final void setIcon(ImageIcon imageIcon) {
        if (imageIcon != null && trayIcon != null) {
            trayIcon.setImage(imageIcon.getImage());
        }
    }
    
    /**
     * Creates a custom tray icon with this JPopupMenu attached
     * @return The AWT TrayIcon associated with this JPopupMenu
     */
    private TrayIcon createTrayIcon() {
        // Create an empty tray icon
        TrayIcon newTrayIcon = new TrayIcon(new ImageIcon(new byte[1]).getImage());
        
        // Attach a mouse listener to the tray icon to show a JPopupMenu rather
        // than a PopupMenu
        newTrayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                setInvoker(PopupTray.this);
                setVisible(true);
                /**
                 * Location must be set after setVisible() or it won't
                 * be able to determine its own height.
                 * 
                 * Don't call getHeight() on Apple per Oracle Bug #232610
                 * Don't call getHeight() on Ubuntu per incorrect placement
                 */
               setLocation(e.getX(), e.getY() - 
                       ( SystemUtilities.isWindows() ? getHeight() : 0));
            }
        });
        
        newTrayIcon.setImageAutoSize(true);
        return newTrayIcon;
    }
    
    /**
     * Convenience method for getTrayIcon().displayMessage(...).
     * Displays a pop-up message near the tray icon.  The message will
     * disappear after a time or if the user clicks on it.
     * @param caption the caption displayed above the text, usually in
     * bold; may be <code>null</code>
     * @param text the text displayed for the particular message; may be
     * <code>null</code>
     * @param level The Logger message severity, shouldn't be
     * <code>null</code>
     */
    public void displayMessage(String caption, String text, Level level) {
        trayIcon.displayMessage(caption, text, convert(level));
    }
    
    
    /**
     * Attaches the specified TrayIcon to the SystemTray
     * @param trayIcon The TrayIcon to attach
     */
    private void attachSystemTray(TrayIcon trayIcon) {
        try {
            if (getSystemTray() != null) {
                getSystemTray().add(trayIcon);
            }
        } catch (AWTException e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING,
                "Tray menu could not be added: {0}", e.getLocalizedMessage());
            if (SystemUtilities.isLinux()) {
                Logger.getLogger(getClass().getName()).log(Level.WARNING,
                "Ubuntu users:  If the tray icon does not appear try running the following:\n\n" + 
                        "    $ gsettings set com.canonical.Unity.Panel systray-whitelist \"['all']\"\n\n" +
                        "... then log out and log back in to restart Unity.");
            }
        }
    }
    
    /**
     * Returns the SystemTray, or null if none exists
     * @return The SystemTray, or null if none exists
     */
    private SystemTray getSystemTray() {
        if (SystemTray.isSupported()) {
            if (systemTray == null) {
                return SystemTray.getSystemTray();
            }
            return systemTray;
        } else {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE,
                    "System tray is not supported on this platform");
            return null;
        }
    }

    public static TrayIcon.MessageType convert(Level level)  {
       if (level.equals(Level.SEVERE)) {
           return TrayIcon.MessageType.ERROR;
       } else if (level.equals(Level.WARNING)) {
           return TrayIcon.MessageType.WARNING;
       } else if (level.equals(Level.INFO)) {
           return TrayIcon.MessageType.INFO;
       }
       return TrayIcon.MessageType.NONE;
    }
}
