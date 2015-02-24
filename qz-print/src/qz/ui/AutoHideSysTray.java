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

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Pop-up Menu which auto-hides after a certain timeout
 * @author A. Tres Finocchiaro
 */
public class AutoHideSysTray extends SysTray {
    // The timeout in milliseconds before this item is hidden
    private final int timeout;
    
    // Attached to all menu items, needed for detecting mouse hover
    private final MouseListener globalMouseListener;
    
    // Stores the current mouse hover status
    private boolean mouseOver;
    
    // Notifies the "hide listener thread" to terminate
    private boolean threadActive;
            
    /**
     * Default constructor
     * @param timeout The amount of time before this menu hides itself
     */
    public AutoHideSysTray(final int timeout) {
        super();
        this.mouseOver = false;
        this.threadActive = true;
        this.timeout = timeout;
        this.globalMouseListener = createGlobalMouseListener();
        setSystemLookAndFeel();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        setMouseOver(true);
        if (visible) {
            createHideListenerThread(timeout);
        } else {
            stopHideListenerThreads();
        }
    }
    
    /**
     * Sets a flag indicating whether or not the mouse is currently over the menu
     * @param mouseOver whether or not the mouse is currently over the menu
     */
    private void setMouseOver(boolean mouseOver) {
        this.mouseOver = mouseOver;
    }
    
    /**
     * Returns whether or not the mouse is currently over the menu
     * @return whether or not the mouse is currently over the menu
     */
    private boolean getMouseOver() {
        return mouseOver;
    }
    
   
    /**
     * Adds a specified JMenuItem and also attaches a mouse listener which 
     * is used to detect focus
     * @param menuItem The MenuItem to add
     * @return The MenuItem argument
     */
     @Override
    public JMenuItem add(JMenuItem menuItem) {
        if (menuItem != null) { menuItem.addMouseListener(globalMouseListener); }
        recurseSubMenuItems(menuItem);
        return super.add(menuItem);
    }
    
    public void recurseSubMenuItems(Component c) {
        if (c instanceof JMenu) {
            for (Component component : ((JMenu)c).getMenuComponents()) {
                component.addMouseListener(globalMouseListener);
                recurseSubMenuItems(component);
            }
        }
    }

    /**
     * Adds a specified Component and also attaches a mouse listener which 
     * is used to detect focus
     * @param comp The Component to add
     * @return The Component argument
     */
    @Override
    public Component add(Component comp) {
        if (comp != null) { comp.addMouseListener(globalMouseListener); }
        return super.add(comp);
    }
    
    
    /**
     * Creates a single mouse listener which all JMenuItems will share
     * @return A new MouseListener object
     */
    private MouseListener createGlobalMouseListener() {
        return new MouseListener() {
            public void mouseClicked(MouseEvent e) {}
            public void mousePressed(MouseEvent e) {}
            public void mouseReleased(MouseEvent e) {}
            public void mouseEntered(MouseEvent e) { setMouseOver(true); }
            public void mouseExited(MouseEvent e) { setMouseOver(false); }
        };
    }
    
    /**
     * Tells all internal "hide listener threads" to stop
     */
    private void stopHideListenerThreads() {
        threadActive = false;
    }
    
    
    
    /**
     * Creates a new thread which hides this component after a specified timeout value
     * Self-aware of mouse position, this will only be hidden if the mouse isn't
     * currently over the component.
     * 
     * Since mouse position outside of a pop-up is impossible to determine on a 
     * system tray, we use the JMenuItem's enter/exit to set the hide flag instead
     * @param timeout The timeout in milliseconds before this component is hidden 
     */
    private void createHideListenerThread(final int timeout) {
        threadActive = true;
        new Thread(new Runnable() {
            final int STEP = 100;
            int counter = 0;
            @Override
            public void run() {
                while (threadActive) {
                    try { Thread.sleep(STEP); }
                    catch (InterruptedException e) {}
                    if (!threadActive) {
                        break;
                    }
                    if (!getMouseOver()) {
                        counter++;
                    } else {
                        counter = 0;
                    }
                    if (counter * STEP > timeout) {
                        SwingUtilities.invokeLater(new Thread(new Runnable() {
                            public void run() { setVisible(false); }
                        }));
                    }
                }
            }
        }).start();
    }
    
    /**
     * Attempts to set the Java Look & Feel to that which matches the Operating
     * System
     */
    private void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Logger.getLogger(getClass().getName()).log(Level.WARNING,
                    "Error getting the default look and feel");
        }
    }
}
