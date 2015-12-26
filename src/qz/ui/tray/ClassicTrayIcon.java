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

package qz.ui.tray;

import org.jdesktop.swinghelper.tray.JXTrayIcon;
import qz.utils.MacUtilities;
import qz.utils.ShellUtilities;
import qz.utils.SystemUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.peer.TrayIconPeer;
import java.lang.reflect.Field;

/**
 * Wraps a Swing JPopupMenu into an AWT PopupMenu
 * 
 * @author Tres Finocchiaro
 */
public class ClassicTrayIcon extends JXTrayIcon {
    public ClassicTrayIcon(Image image) {
        super(image);
    }

    @Override
    public void setJPopupMenu(JPopupMenu source) {
        final PopupMenu popup = new PopupMenu();
        setPopupMenu(popup);
        wrapAll(popup, source.getComponents());
    }

    /**
     * Convert an array of Swing menu components to its AWT equivalent
     * @param menu PopupMenu to receive new components
     * @param components Array of components to recurse over
     */
    private static void wrapAll(Menu menu, Component[] components) {
        for (Component c : components) {
            MenuItem item = AWTMenuWrapper.wrap(c);
            menu.add(item);
            if (item instanceof Menu) {
                wrapAll((Menu)item, ((JMenu)c).getMenuComponents());
            }
        }
    }
}
