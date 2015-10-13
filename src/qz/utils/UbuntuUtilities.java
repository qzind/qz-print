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

package qz.utils;

import qz.ui.IconCache;

import java.awt.*;

/**
 * Utility class for Ubuntu OS specific functions.
 * @author Tres Finocchiaro
 */
public class UbuntuUtilities {

    static Color trayColor;


    /**
     * Attempts to get the SystemTray background color from the Ubuntu OS by
     * first parsing the theme name registered with GTK, and then parsing the
     * <code>dark_bg_color</code> value from the GTK stylesheet.
     * @return The Ubuntu system tray background color, or <code>Color.WHITE</code>
     * if it cannot be determined
     */
    public static Color getTrayColor() {
        if (trayColor == null) {
            trayColor = ColorUtilities.DEFAULT_COLOR;

            // Ubuntu 12.04 LTS
            String themeName = getThemeName("Ambiance");
            String stdout = ShellUtilities.execute(
                    new String[]{
                        "grep", "dark_bg_color",
                        "/usr/share/themes/" + themeName + "/gtk-3.0/gtk.css"
                    },
                    new String[]{
                        "dark_bg_color"
                    });
            if (stdout.isEmpty()) {
                // Ubuntu 14.04 LTS
                stdout = ShellUtilities.execute(
                    // Ubuntu 12.04 - 14.04
                    new String[]{
                        "grep", "dark_bg_color",
                        "/usr/share/themes/" + themeName + "/gtk-3.0/gtk-main.css"
                    },
                    new String[]{
                        "dark_bg_color"
                });
            }
            if (!stdout.isEmpty() && stdout.contains("#")) {
                String[] split = stdout.split("#");
                if (split.length == 2) {
                    trayColor = ColorUtilities.decodeHtmlColorString(split[1]);
                }
            }
        }
        return trayColor;
    }

    /**
     * Attempts to retrieve the Ubuntu theme name, i.e. "Ambience", "Radiance", etc
     * @return the current running theme, or an empty String if it could not be
     * determined.
     */
    public static String getThemeName(String defaultTheme) {
        String themeName = ShellUtilities.execute(
                new String[]{
                    "gconftool-2",
                    "--get",
                    "/desktop/gnome/shell/windows/theme"
                },
                null
        );
        return themeName.isEmpty() ? defaultTheme : themeName;
    }

    /**
     * Replaces the cached tray icons with Ubuntu colorized ones, fixing a Java bug which gives them undesirable
     * transparency
     * @param iconCache The icons which have been cached
     */
    public static void fixTrayIcons(IconCache iconCache) {
        // Execute some shell commands to determine specific Linux OS
        if (SystemUtilities.isUbuntu()) {
            for (IconCache.Icon i : IconCache.getTypes()) {
                if (i.isTrayIcon()) {
                    iconCache.toOpaqueImage(i, getTrayColor());
                }
            }
        }

    }

}
