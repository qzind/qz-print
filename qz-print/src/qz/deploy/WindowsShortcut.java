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

import qz.common.Constants;
import qz.utils.ShellUtilities;

/**
 *
 * @author Tres Finocchiaro
 */
public class WindowsShortcut extends ShortcutUtilities {
    // Try using ${windows.icon} first, if it exists
    private static String qzIcon = System.getenv("programfiles").replace(" (x86)", "") + "\\" + Constants.ABOUT_TITLE + "\\windows-icon.ico";
    private static String defaultIcon = System.getenv("windir") + "\\system32\\SHELL32.dll";
    private static boolean useQzIcon = fileExists(qzIcon);

    @Override
    public boolean createStartupShortcut() {
        return ShellUtilities.executeRegScript(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\\",
                "add",
                getShortcutName(),
                quoteWrap(fixWhitespaces(getJarPath()))
        );
    }

    @Override
    public String getParentDirectory() {
        return fixWhitespaces(super.getParentDirectory());
    }

    @Override
    public boolean createDesktopShortcut() {
        return createShortcut(System.getenv("userprofile") + "\\Desktop\\");
    }

    @Override
    public boolean removeStartupShortcut() {
        return ShellUtilities.executeRegScript(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\\",
                "delete",
                getShortcutName()
        );
    }

    @Override
    public boolean removeDesktopShortcut() {
        return deleteFile(System.getenv("userprofile") + "\\Desktop\\" +
                getShortcutName() + ".url");
    }
    
    
    @Override
    public boolean hasStartupShortcut() {
        return ShellUtilities.executeRegScript(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\\", 
                "query", 
                getShortcutName()
        );
    }

    @Override
    public boolean hasDesktopShortcut() {
        return fileExists(System.getenv("userprofile") + "\\Desktop\\" +
                getShortcutName() + ".url");
    }
    
    /**
     * Returns the string with Windows formatted escaped double quotes, useful for
     * inserting registry keys
     * @return The supplied string wrapped in double quotes
     */
    private String quoteWrap(String text) {
        return "\\\"" + text + "\\\"";
    }
    
    /**
     * Creates a Windows ".url" shortcut
     *
     * @param folderPath Absolute path to a jar file
     * @return Whether or not the shortcut  was created successfully
     */
    private boolean createShortcut(String folderPath) {
        String workingPath = getParentDirectory(getJarPath());
        String shortcutPath = folderPath + getShortcutName() + ".url";

        // Create the shortcut's parent folder if it does not exist
        return createParentFolder(shortcutPath) && writeArrayToFile(shortcutPath, new String[]{
            "[InternetShortcut]",
            "URL=" + fixURL(getJarPath()),
            workingPath.trim().equals("") ? "" : "WorkingDirectory=" + fixURL(workingPath),
           // SHELL32.DLL:16 is a printer icon on all Windows Operating systems
            "IconIndex=" + (useQzIcon ? 0 : 16),
            "IconFile=" + (useQzIcon ? qzIcon : defaultIcon),
            "HotKey=0"
        });
    }
    
    /**
     * Attempts to correct URL path conversions that occur on old JREs and older
     * Windows versions.  For now, just addresses invalid forward slashes, but 
     * there could be other URLs which will need special consideration.
     * @param filePath The absolute file path to convert
     * @return The converted path
     */
    private static String fixURL(String filePath) {
       return "file:///" + filePath.replace("\\", "/");
    }
    
    /**
     * Attempts to correct URL path conversions that occur on old JREs and older
     * Windows versions.  For now, just addresses %20 spaces, but 
     * there could be other URLs which will need special consideration.
     * @param filePath The absolute file path to convert
     * @return The converted path
     */
    private static String fixWhitespaces(String filePath) {
        return filePath.replace("%20", " ");
    }
}
