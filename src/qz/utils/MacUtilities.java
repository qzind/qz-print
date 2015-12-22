/**
 * @author Tres Finocchiaro
 *
 * Copyright (C) 2015 Tres Finocchiaro, QZ Industries
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

package qz.utils;

import com.apple.OSXAdapter;
import qz.common.TrayManager;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for MacOS specific functions.
 * @author Tres Finocchiaro
 */
public class MacUtilities {
    private static Dialog aboutDialog;
    private static TrayManager trayManager;

    public static void showAboutDialog() {
        if (aboutDialog != null) { aboutDialog.setVisible(true); }
    }
    
    public static void showExitPrompt() {
    	if (trayManager != null) { trayManager.exit(0); }
    }

    /**
     * Adds a listener to register the Apple "About" dialog to call setVisible() on the specified Dialog
     * @param aboutDialog
     */
    public static void registerAboutDialog(Dialog aboutDialog) {
        MacUtilities.aboutDialog = aboutDialog;
        try {
            OSXAdapter.setAboutHandler(MacUtilities.class, MacUtilities.class.getDeclaredMethod("showAboutDialog"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Adds a listener to register the Apple "Quit" to call trayManager.exit(0)
     * @param trayManager
     */
    public static void registerQuitHandler(TrayManager trayManager) {
    	MacUtilities.trayManager = trayManager;
        MacUtilities.aboutDialog = aboutDialog;
        try {
            OSXAdapter.setQuitHandler(MacUtilities.class, MacUtilities.class.getDeclaredMethod("showExitPrompt"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
