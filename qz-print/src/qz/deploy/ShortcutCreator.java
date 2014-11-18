
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A helper class for creating cross-platform startup entries in user-space only (not system-wide).
 * For example, on Windows, this is in <code>%USERPROFILE%\Start Menu\Programs\Startup</code>
 *
 *  - Windows uses plain text <code>.url</code> files
 *  - Linux(Ubuntu) uses the plain text <code>.desktop</code> files
 *  - Mac uses AppleScript to set the startup preference via System Settings
 *
 * Usage:
 * <code>
 *  ShortcutCreator sc = new ShortcutCreator("My Java Shortcut");
 *  sc.createStartupShortcut("C:\\Users\\Owner\\Desktop\\MyJavaApp.jar");
 *  sc.createDesktopShortcut("C:\\Users\\Owner\\Desktop\\MyJavaApp.jar");
 * </code>
 *
 * @author A. Tres Finocchiaro
 */
public class ShortcutCreator {

    private static final Logger log = Logger.getLogger(ShortcutCreator.class.getName());

    // The shortcut's file name, i.e. "Print Service Listener"
    private static final String DEFAULT_SHORTCUT_NAME = "QZ-Print Background Service";

    // The OS name, used for deciding which type of shortcut to create
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    // Carriage return character
    private static final String OS_NEWLINE = isWindows() ? "\r\n" : "\n";

    // Windows constants.  Note, the startup folder location changes between XP and Vista
    private static final String DEFAULT_XP_STARTUP = System.getenv("userprofile") + "\\Start Menu\\Programs\\Startup\\";
    private static final String DEFAULT_VISTA_STARTUP = System.getenv("appdata") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\";

    // Windows desktop path
    private static final String WIN_DESKTOP = System.getenv("userprofile") + "\\Desktop\\";

    // Windows icon path (multi-resources files and can be .ico, .dll, .exe format)
    private static final String DEFAULT_WIN_ICON = System.getenv("windir") + "\\system32\\SHELL32.dll";

    // This icon index determines which icon resource to use.  If unsure, use 0.  SHELL32.dll #16 is the 
    // Windows Printer icon.
    private static final int DEFAULT_WIN_ICON_INDEX = 16;

    // Startup folder for Linux(Ubuntu)
    private static final String DEFAULT_LINUX_STARTUP = System.getProperty("user.home") + "/.config/autostart/";

    // Linux(Ubuntu) desktop path
    private static final String LINUX_DESKTOP = System.getProperty("user.home") + "/Desktop/";

    // Linux(Ubuntu) icon path
    private static final String DEFAULT_LINUX_ICON = "printer";
    //private static final String DEFAULT_LINUX_ICON = "/usr/share/app-install/icons/printer.png";

    private static final String MACOS_DESKTOP = System.getenv("userprofile") + "\\Start Menu\\Programs\\Startup\\";

    private final String shortcutName;
    private String windowsIcon;
    private String linuxIcon;

    /**
     * Default constructor
     */
    public ShortcutCreator() {
        shortcutName = DEFAULT_SHORTCUT_NAME;
    }

    /**
     * Create a shortcut with a custom shortcutName
     * @param shortcutName
     */
    public ShortcutCreator(String shortcutName) {
        this.shortcutName = shortcutName;
    }

    /**
     * Creates a startup item for Windows, Mac or Linux. Automatically detects
     * the OS and places the startup item in the user's startup area
     * respectively; to be auto-launched when the user first logs in to
     * the desktop.
     *
     * @param jarPath The path to the jar to launch on startup
     * @return Returns <code>true</code> if the startup item was created
     */
    public boolean createStartupShortcut(String jarPath) {
        if (isWindows()) {
            return createWindowsShortcut(jarPath, (isXP() ? DEFAULT_XP_STARTUP + shortcutName
                    : DEFAULT_VISTA_STARTUP + shortcutName) + ".url");
        } else if (isMac()) {
            return createMacStartup(jarPath);
        } else if (isLinux()) {
            return createLinuxShortcut(jarPath, DEFAULT_LINUX_STARTUP + shortcutName + ".desktop");
        } else {
            log.log(
                    Level.WARNING, "Warning:  This OS is not supported for startup shortcut creation.");
        }
        return false;
    }

    /**
     * Creates a startup item for Windows, Mac or Linux. Automatically detects
     * the OS and places the shortcut item in the user's Desktop.
     *
     * @param jarPath The path to the jar to launch on startup
     * @return Returns <code>true</code> if the startup item was created
     */
    public boolean createDesktopShortcut(String jarPath) {
        if (isWindows()) {
            return createWindowsShortcut(jarPath, WIN_DESKTOP + shortcutName + ".url");
    // FIXME // HOW TO CREATE DESKTOP SHORTCUT FOR MAC
    // FIXME    } else if (isMac()) {
    // FIXME        return createMacShortcut(jarPath, MACOS_DESKTOP + shortcutName + ".desktop");
        } else if (isLinux()) {
            return createLinuxShortcut(jarPath, LINUX_DESKTOP + shortcutName + ".desktop");
        } else {
            log.log(
                    Level.WARNING, "Warning:  This OS is not supported for desktop shortcut creation.");
        }
        return false;
    }

    /**
     * Parses the parent directory from an absolute file URL. This will not work
     * with relative paths.<code>
     *    // Good:
     *    getWorkingPath("C:\Folder\MyFile.jar");
     *
     *    // Bad:
     *    getWorkingPath("C:\Folder\SubFolder\..\MyFile.jar");
     * </code>
     *
     * @param filePath Absolute path to a jar file
     * @return The calculated working path value, or an empty string if one
     * could not be determined
     */
    private String getParentDirectory(String filePath) {
        // Working path should always default to the JARs parent folder
        int lastSlash = filePath.lastIndexOf('/');
        lastSlash = lastSlash < 0 ? filePath.lastIndexOf('\\') : lastSlash;
        return lastSlash < 0 ? "" : filePath.substring(0, lastSlash);
    }

    /**
     * Creates a Mac OS auto-start item for the current logged in user
     *
     * @param jarPath Absolute path to a jar file
     * @return Whether or not the startup item was created successfully
     */
    private boolean createMacStartup(String jarPath) {
        Runtime r = Runtime.getRuntime();
        String executable = "osascript";
        String param1 = "-e";
        String param2_create = "tell application \"System Events\" to make login item "
                + "at end with properties {path:\"" + jarPath + "\", "
                + "hidden:true, name:\"" + shortcutName + "\"}";
        String param2_delete = "tell application \"System Events\" to delete every login item "
                + "whos name is \"" + shortcutName + "\"";

        log.log(Level.INFO, "Trying to create startup item {0} using {1} {2} ...", new Object[]{jarPath, executable, param1});

        try {
            // First remove old instances
            Process p = r.exec(new String[]{executable, param1, param2_delete});
            p.waitFor();
            // Add new 
            p = r.exec(new String[]{executable, param1, param2_create});
            p.waitFor();
            return p.exitValue() == 0;
        } catch (InterruptedException ex) {
            log.log(Level.WARNING, "Process was interrupted while creating shortcut: {0}", ex.getLocalizedMessage());
        } catch (IOException ex) {
            log.log(Level.SEVERE, "IO Error while creating shortcut: {0}", ex.getLocalizedMessage());
        }
        return false;
    }

    /**
     * Creates all appropriate parent folders for the file path specified
     *
     * @param filePath
     * @return
     */
    private boolean createParentFolder(String filePath) {
        String parentDirectory = getParentDirectory(filePath);
        File f = new File(parentDirectory);
        try {
            f.mkdirs();
            return f.exists();
        } catch (SecurityException e) {
            log.log(Level.SEVERE, "Error while creating parent directories for: {0} {1}", new Object[]{filePath, e.getLocalizedMessage()});
        }
        return false;
    }

    /**
     * Creates a Linux(Ubuntu) auto-start item for the current logged in user
     *
     * @param jarPath Absolute path to a jar file
     * @return Whether or not the startup item was created successfully
     */
    private boolean createLinuxShortcut(String jarPath, String filePath) {
        String workingPath = getParentDirectory(jarPath);

        log.log(Level.INFO, "Trying to create shortcut {0} to point to {1}...", new Object[]{filePath, jarPath});

        // Create the shortcut's parent folder if it does not exist
        if (!createParentFolder(filePath)) {
            return false;
        }

        return writeArrayToFile(filePath, new String[]{
            "[Desktop Entry]",
            "Type=Application",
            "Name=" + shortcutName,
            "Exec=java -jar " + jarPath,
            workingPath.trim().equals("") ? "" : "Path=" + workingPath,
            //"IconIndex=" + iconIndex,
            "Icon=" + DEFAULT_LINUX_ICON,
            "Terminal=false",
            "Comment=" + shortcutName
        });
    }

    /**
     * Creates a Windows auto-start item for the current logged in user
     *
     * @param jarPath Absolute path to a jar file
     * @return Whether or not the startup item was created successfully
     */
    private boolean createWindowsShortcut(String jarPath, String filePath) {
        String workingPath = getParentDirectory(jarPath);

        // Create the shortcut's parent folder if it does not exist
        if (!createParentFolder(filePath)) {
            return false;
        }

        log.log(Level.INFO, "Trying to create shortcut {0} to point to {1}...", new Object[]{filePath, jarPath});
        return writeArrayToFile(filePath, new String[]{
            "[InternetShortcut]",
            "URL=" + fixWindowsPath(jarPath),
            workingPath.trim().equals("") ? "" : "WorkingDirectory=" + fixWindowsPath(workingPath),
            "IconIndex=" + DEFAULT_WIN_ICON_INDEX,
            "IconFile=" + DEFAULT_WIN_ICON,
            "HotKey=0"
        });
    }

    /**
     * Writes the contents of <code>String[] array</code> to the specified
     * <code>filePath</code> used for creating launcher shortcuts for both
     * Windows and Linux taking OS-specific newlines into account
     *
     * @param filePath Absolute file path to be written to
     * @param array Array of lines to be written
     * @return Whether or not the write was successful
     */
    private static boolean writeArrayToFile(String filePath, String[] array) {
        BufferedWriter writer = null;
        boolean returnVal = false;
        try {
            writer = new BufferedWriter(new FileWriter(new File(filePath)));
            for (String line : array) {
                writer.write(line + OS_NEWLINE);
            }
            returnVal = true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not write file: {0}", e.getLocalizedMessage());
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
                setExecutable(filePath);
            } catch (IOException e) {
            }
        }
        return returnVal;
    }

    /**
     * Sets the executable permission flag for a file.  This only works on Linux/Unix.
     * @param filePath
     * @return <code>true</code> if successful, <code>false</code> otherwise
     */
    private static boolean setExecutable(String filePath) {
        if (!isWindows()) {
            try {
                File f = new File(filePath);
                f.setExecutable(true);
                return true;
            } catch (SecurityException e) {
                log.log(Level.SEVERE, "Unable to set file as executable: {0} {1}", new Object[] {filePath, e.getLocalizedMessage()});
            }
        } else {
            return true;
        }
        return false;
    }
    
    /**
     * Attempts to correct URI path conversions that occur on old JREs and older
     * Windows versions.  For now, just addresses invalid forward slashes, but there could be many
     * others snags.
     * @param filePath The absolute file path to convert
     * @return The converted path without the %20 URI gibberish
     */
    private static String fixWindowsPath(String filePath) {
       return "file:///" + filePath.replace("\\", "/");
    }

    /**
     * Determine if the current Operating System is Windows XP
     *
     * @return <code>true</code> if Windows XP, <code>false</code> otherwise
     */
    private static boolean isXP() {
        return isWindows() && (OS_NAME.indexOf(" xp") >= 0);
    }

    /**
     * Determine if the current Operating System is Windows
     *
     * @return <code>true</code> if Windows, <code>false</code> otherwise
     */
    private static boolean isWindows() {
        return (OS_NAME.indexOf("win") >= 0);
    }

    /**
     * Determine if the current Operating System is Mac OS
     *
     * @return <code>true</code> if Mac OS, <code>false</code> otherwise
     */
    private static boolean isMac() {
        return (OS_NAME.indexOf("mac") >= 0);
    }

    /**
     * Determine if the current Operating System is Linux
     *
     * @return <code>true</code> if Linux, <code>false</code> otherwise
     */
    private static boolean isLinux() {
        return (OS_NAME.indexOf("linux") >= 0);
    }

    /**
     * Determine if the current Operating System is Unix
     *
     * @return <code>true</code> if Unix, <code>false</code> otherwise
     */
    private static boolean isUnix() {
        return (OS_NAME.indexOf("nix") >= 0 || OS_NAME.indexOf("nux") >= 0 || OS_NAME.indexOf("aix") > 0);
    }

    /**
     * Determine if the current Operating System is Solaris
     *
     * @return <code>true</code> if Solaris, <code>false</code> otherwise
     */
    private static boolean isSolaris() {
        return (OS_NAME.indexOf("sunos") >= 0);
    }
}
