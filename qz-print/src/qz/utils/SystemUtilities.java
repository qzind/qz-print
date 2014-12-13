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

/**
 * Utility class for OS detection functions.
 * @author Tres Finocchiaro
 */
public class SystemUtilities {
    // Name of the os, i.e. "Windows XP", "Mac OS X"
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static String linuxVersion;
    
     /**
     * Returns a lowercase version of the Operating system name identified by
     * <code>System.getProperty("os.name");</code>.
     *
     * @return
     */
    public static String getOS() {
        return OS_NAME;
    }

    
    /**
     * Determine if the current Operating System is Windows
     *
     * @return <code>true</code> if Windows, <code>false</code> otherwise
     */
    public static boolean isWindows() {
        return (OS_NAME.contains("win"));
    }

    /**
     * Determine if the current Operating System is Mac OS
     *
     * @return <code>true</code> if Mac OS, <code>false</code> otherwise
     */
    public static boolean isMac() {
        return (OS_NAME.contains("mac"));
    }

    /**
     * Determine if the current Operating System is Linux
     *
     * @return <code>true</code> if Linux, <code>false</code> otherwise
     */
    public static boolean isLinux() {
        return (OS_NAME.contains("linux"));
    }

    /**
     * Determine if the current Operating System is Unix
     *
     * @return <code>true</code> if Unix, <code>false</code> otherwise
     */
    public static boolean isUnix() {
        return (OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.indexOf("aix") > 0);
    }

    /**
     * Determine if the current Operating System is Solaris
     *
     * @return <code>true</code> if Solaris, <code>false</code> otherwise
     */
    public static boolean isSolaris() {
        return (OS_NAME.contains("sunos"));
    }

    /**
     * Returns whether the output of <code>uname -a</code> shell command contains "Ubuntu"
     * @return true if this OS is Ubuntu
     */
    public static boolean isUbuntu() {
        String linuxVersion = getLinuxVersion();
        return linuxVersion != null && getLinuxVersion().contains("Ubuntu");
    }

    /**
     * Returns the output of <code>uname -a</code> shell command, useful for parsing the Linux Version
     * @return the output of <code>uname -a</code>, of null if not running Linux
     */
    public static String getLinuxVersion() {
        if (isLinux() && linuxVersion == null) {
            linuxVersion = ShellUtilities.execute(
                    new String[]{"uname", "-a"},
                    null
            );
        }
        return linuxVersion;
    }
    
}
