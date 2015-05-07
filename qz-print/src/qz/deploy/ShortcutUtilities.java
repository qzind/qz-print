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
import qz.utils.SystemUtilities;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for creating, querying and removing startup shortcuts and
 * desktop shortcuts.
 *
 * @author Tres Finocchiaro
 */
public abstract class ShortcutUtilities {

    // System logger
    static final Logger log = Logger.getLogger(ShortcutUtilities.class.getName());

    // Default shortcut name to create
    static private final String DEFAULT_SHORTCUT_NAME = "Java Shortcut";

    // Newline character, which changes between Unix and Windows
    static private final String NEWLINE = SystemUtilities.isWindows() ? "\r\n" : "\n";

    private String jarPath;
    private String shortcutName;

    /**
     * Creates a startup item for the current OS. Automatically detects the OS
     * and places the startup item in the user's startup area respectively; to
     * be auto-launched when the user first logs in to the desktop.
     *
     * @return Returns <code>true</code> if the startup item was created
     */
    public abstract boolean createStartupShortcut();

    /**
     * Test whether or not a startup shortcut for the specified shortcutName
     * exists on this system
     * @return true if a startup shortcut exists on this system, false otherwise
     */
    public abstract boolean hasStartupShortcut();

     /**
     * Test whether or not a desktop shortcut for the specified shortcutName
     * exists on this system
     * @return true if a desktop shortcut exists on this system, false otherwise
     */
    public abstract boolean hasDesktopShortcut();

    /**
     * Creates a startup for the current OS. Automatically detects the OS and
     * places the shortcut item on the user's Desktop.
     *
     * @return Returns <code>true</code> if the startup item was created
     */
    public abstract boolean createDesktopShortcut();

    /**
     * Removes a startup item for the current OS. Automatically detects the OS
     * and removes the startup item from the user's startup area respectively.
     *
     * @return Returns <code>true</code> if the startup item was removed
     */
    public abstract boolean removeStartupShortcut();

    /**
     * Removes a desktop shortcut for the current OS. Automatically detects the
     * OS and removes the shortcut from the current user's Desktop.
     *
     * @return Returns <code>true</code> if the Desktop shortcut item was
     * removed
     */
    public abstract boolean removeDesktopShortcut();

    /**
     * Single function to be used to dynamically create various shortcut types
     * @param toggleType ToggleType.STARTUP or ToggleType.DESKTOP
     * @return Whether or not the shortcut creation was successful
     */
    public boolean createShortcut(ToggleType toggleType) {
        switch (toggleType) {
            case STARTUP:
                return !hasShortcut(ToggleType.STARTUP) ? createStartupShortcut() : true;
            case DESKTOP:
                return !hasShortcut(ToggleType.DESKTOP) ? createDesktopShortcut() : true;
            default:
                log.log(Level.WARNING, "Sorry, creating {0} shortcuts are not yet supported", toggleType);
                return false;
        }
    }

    /**
     * Single function to be used to dynamically check if a shortcut already exists
     * @param toggleType Shortcut type, i.e. <code>ToggleType.STARTUP</code> or <code>ToggleType.DESKTOP</code>
     * @return Whether or not the shortcut already exists
     */
    public boolean hasShortcut(ToggleType toggleType) {
        boolean hasShortcut = false;
        switch (toggleType) {
            case STARTUP:
                hasShortcut = hasStartupShortcut();
                break;
            case DESKTOP:
                hasShortcut = hasDesktopShortcut();
                break;
            default:
                log.log(Level.WARNING, "Sorry, checking for {0} shortcuts are not yet supported", toggleType);
        }

        if (hasShortcut) {
            log.log(Level.INFO, "The {0} shortcut for {1} ({2}) exists",
                    new Object[]{ toggleType, getShortcutName(), getJarPath() });
        }

        return hasShortcut;
    }

    /**
     * Single function to be used to dynamically remove various shortcut types
     * @param toggleType ToggleType.STARTUP or ToggleType.DESKTOP
     * @return Whether or not the shortcut removal was successful
     */
    public boolean removeShortcut(ToggleType toggleType) {
        switch (toggleType) {
            case STARTUP:
                return hasShortcut(ToggleType.STARTUP) ? removeStartupShortcut() : true;
            case DESKTOP:
                return hasShortcut(ToggleType.DESKTOP) ? removeDesktopShortcut() : true;
            default:
                log.log(Level.WARNING, "Sorry, removing {0} shortcuts are not yet supported", toggleType);
                return false;
        }
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
    static final String getParentDirectory(String filePath) {
        // Working path should always default to the JARs parent folder
        int lastSlash = filePath.lastIndexOf('/');
        lastSlash = lastSlash < 0 ? filePath.lastIndexOf('\\') : lastSlash;
        return lastSlash < 0 ? "" : filePath.substring(0, lastSlash);
    }

    public String getParentDirectory() {
        return getParentDirectory(getJarPath());
    }


    public void setShortcutName(String shortcutName) {
        if (shortcutName != null) {
            this.shortcutName = shortcutName;
        }
    }

    public String getShortcutName() {
        return shortcutName == null ? DEFAULT_SHORTCUT_NAME : shortcutName;
    }

    /**
     * Detects the OS and creates the appropriate shortcut creator
     * @return The appropriate shortcut creator for the currently running OS
     */
    public static ShortcutUtilities getSystemShortcutCreator() {
        if (SystemUtilities.isWindows()) {
            return new WindowsShortcut();
        } else if (SystemUtilities.isMac()) {
            return new MacShortcut();
        } else if (SystemUtilities.isLinux()) {
            return new LinuxShortcut();
        } else {
            throw new UnsupportedOperationException("Shortcut creation for this OS is not yet supported");
        }
    }

    /**
     * Creates all appropriate parent folders for the file path specified
     *
     * @param filePath The file in which to create parent directories for
     * @return Whether or not the parent folder creation was successful
     */
    static final boolean createParentFolder(String filePath) {
        String parentDirectory = getParentDirectory(filePath);
        File f = new File(parentDirectory);
        try {
            f.mkdirs();
            return f.exists();
        } catch (SecurityException e) {
            log.log(Level.SEVERE, "Error while creating parent directories for: {0} {1}",
                    new Object[]{filePath, e.getLocalizedMessage()});
        }
        return false;
    }

    /**
     * Returns whether or not a file exists
     * @param filePath The full path to a file
     * @return True if the specified filePath exists.  False otherwise.
     */
    static final boolean fileExists(String filePath) {
        try {
            return new File(filePath).exists();
        } catch (SecurityException e) {
            log.log(Level.WARNING, "SecurityException while checking for file {0}{1}",
                    new Object[]{filePath, e.getLocalizedMessage()});
            return false;
        }
    }

    /**
     * Deletes the specified file
     *
     * @param filePath The full file path to be deleted
     * @return Whether or not the file deletion was successful
     */
    static final boolean deleteFile(String filePath) {
        File f = new File(filePath);
        try {
            return f.delete();
        } catch (SecurityException e) {
            log.log(Level.SEVERE, "Error while deleting: {0} {1}",
                    new Object[]{filePath, e.getLocalizedMessage()});
        }
        return false;
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
    static final boolean writeArrayToFile(String filePath, String[] array) {
        log.log(Level.INFO, "Writing array contents to file: {0}: \n{1}",
                new Object[]{filePath, Arrays.toString(array)});
        BufferedWriter writer = null;
        boolean returnVal = false;
        try {
            writer = new BufferedWriter(new FileWriter(new File(filePath)));
            for (String line : array) {
                writer.write(line + NEWLINE);
            }
            returnVal = true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not write file: {0}{1}",
                    new Object[]{filePath, e.getLocalizedMessage()});
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
     * Sets the executable permission flag for a file. This only works on
     * Linux/Unix.
     *
     * @param filePath The full file path to set the execute flag on
     * @return <code>true</code> if successful, <code>false</code> otherwise
     */
    static final boolean setExecutable(String filePath) {
        if (!SystemUtilities.isWindows()) {
            try {
                File f = new File(filePath);
                f.setExecutable(true);
                return true;
            } catch (SecurityException e) {
                log.log(Level.SEVERE, "Unable to set file as executable: {0} {1}",
                        new Object[]{filePath, e.getLocalizedMessage()});
            }
        } else {
            return true;
        }
        return false;
    }

    /**
     * Gets the path to qz-tray.properties
     * @return
     */
    public static final String detectPropertiesPath() {
        // Use supplied path from IDE or command line
        // i.e  -DsslPropertiesFile=C:\qz-tray.properties
        String override = System.getProperty("sslPropertiesFile");
        if (override != null) {
            return override;
        }

        String jarPath = detectJarPath();
        String propFile = Constants.PROPS_FILE + ".properties";

        // Use relative path based on qz-tray.jar, fix %20
        // TODO:  Find a better way to fix the unicode chars?
        return fixWhitespaces(getParentDirectory(jarPath) + File.separator + propFile);
    }

    /**
     * Determines the currently running Jar's absolute path on the local filesystem
     * @return A String value representing the absolute path to the currently running
     * jar
     */
    public static final String detectJarPath() {
        try {
            return new File(ShortcutUtilities.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath()).getCanonicalPath();
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Unable to determine Jar path", ex);
        }
        return null;
    }

    /**
     * Returns the jar which we will create a shortcut for
     * @return The path to the jar path which has been set
     */
    public String getJarPath() {
        if (jarPath == null) {
            jarPath = detectJarPath();
        }
        return jarPath;
    }

    /**
     * Set the jar path for which we will create a shortcut for
     * @param jarPath The full file path of the jar file
     */
    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }

    /**
     * Small Enum for differentiating "desktop" and "startup"
     */
    public enum ToggleType {
        STARTUP, DESKTOP;
        /**
         * Returns the English description of this object
         * @return The string "startup" or "desktop"
         */
        @Override
        public String toString() {
            return getName();
        }

        /**
         * Returns the English description of this object
         * @return The string "startup" or "desktop"
         */
        public String getName() {
            return this.name() == null ? null : this.name().toLowerCase();
        }
    }
      
    /**
     * Attempts to correct URL path conversions that occur on old JREs and older
     * Windows versions.  For now, just addresses %20 spaces, but 
     * there could be other URLs which will need special consideration.
     * @param filePath The absolute file path to convert
     * @return The converted path
     */
    public static String fixWhitespaces(String filePath) {
        return filePath.replace("%20", " ");
    }
}
