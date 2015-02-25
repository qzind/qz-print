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

package qz.deploy;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.logging.Level;
import static qz.deploy.ShortcutUtilities.log;

/**
 *
 * @author Tres Finocchiaro
 */
public class LinuxShortcut extends ShortcutUtilities {
    @Override
    public boolean createStartupShortcut() {
        return createShortcut(System.getProperty("user.home") + "/.config/autostart/");
    }

    @Override
    public boolean createDesktopShortcut() {
        return createShortcut(System.getProperty("user.home") + "/Desktop/");
    }

    @Override
    public boolean removeStartupShortcut() {
        return deleteFile(System.getProperty("user.home") + 
                "/.config/autostart/" + getShortcutName() + ".desktop");
    }

    @Override
    public boolean removeDesktopShortcut() {
        return deleteFile(System.getProperty("user.home") + "/Desktop/" + 
                getShortcutName() + ".desktop");
    }
    
    
    @Override
    public boolean hasStartupShortcut() {
        return fileExists(System.getProperty("user.home") + "/.config/autostart/" +
                getShortcutName() + ".desktop");
    }

    @Override
    public boolean hasDesktopShortcut() {
        return fileExists(System.getProperty("user.home") + "/Desktop/" + 
                getShortcutName() + ".desktop");
    }
    
    @Override
    public String getJarPath() {  
        String jarPath = super.getJarPath();
        try {
            jarPath = URLDecoder.decode(jarPath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Error decoding URL: {0}{1}", 
                    new Object[]{jarPath, e.getLocalizedMessage()});
        }
        return jarPath;
    }
    
    /**
     * Creates a Linux ".desktop" shortcut
     *
     * @param jarPath Absolute path to a jar file
     * @return Whether or not the shortcut was created successfully
     */
    private boolean createShortcut(String folderPath) {
        String workingPath = getParentDirectory();
        String shortcutPath = folderPath + getShortcutName() + ".desktop";

        // Create the shortcut's parent folder if it does not exist
        return createParentFolder(shortcutPath) && writeArrayToFile(shortcutPath, new String[]{
            "[Desktop Entry]",
            "Type=Application",
            "Name=" + getShortcutName(),
            "Exec=java -jar \"" + getJarPath() + "\"",
            workingPath.trim().equals("") ? "" : "Path=" + workingPath,
            //"IconIndex=" + iconIndex,
            "Icon=printer",
            "Terminal=false",
            "Comment=" + getShortcutName()
        });
    }
}
