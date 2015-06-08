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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.logging.Level;

import static qz.utils.UbuntuUtilities.log;

/**
 * Utility class for managing all <code>Runtime.exec(...)</code> functions.
 * @author Tres Finocchiaro
 */
public class ShellUtilities {

    /**
     * Executes a synchronous shell command and returns true if the
     * <code>Process.exitValue()</code> is <code>0</code>.
     * @param commandArray array of command pieces to supply to the shell environment to e executed as a single command
     * @return <code>true</code> if <code>Process.exitValue()</code> is <code>0</code>, otherwise
     * <code>false</code>.
     */
    public static boolean execute(String[] commandArray) {
        log.log(Level.INFO, "Executing: {0}", Arrays.toString(commandArray));
        try {
            // Create and execute our new process
            Process p = Runtime.getRuntime().exec(commandArray);
            p.waitFor();
            return p.exitValue() == 0;
        } catch (InterruptedException ex) {
            log.log(Level.WARNING, "InterruptedException waiting for a return value: {0}{1}",
                    new Object[]{Arrays.toString(commandArray), ex.getLocalizedMessage()});
        } catch (IOException ex) {
            log.log(Level.SEVERE, "IOException executing: {0}{1}",
                    new Object[]{Arrays.toString(commandArray), ex.getLocalizedMessage()});
        }
        return false;
    }

    /**
     * Executes a synchronous shell command and return the result.
     *
     * @param commandArray array of shell commands to execute
     * @param searchFor array of return values to look for, case sensitivity
     * matters
     * @return The first matching string value
     */
    public static String execute(String[] commandArray, String[] searchFor) {
        return execute(commandArray, searchFor, true);
    }

    /**
     * Executes a synchronous shell command and return the result.
     *
     * @param commandArray array of shell commands to execute
     * @param searchFor array of return values to look for, or <code>null</code>
     * to return the first line of standard output
     * @param caseSensitive whether or not to perform case-sensitive search
     * @return The first matching an element of <code>searchFor</code>, unless
     * <code>searchFor</code> is null ,then the first line of standard output
     */
    public static String execute(String[] commandArray, String[] searchFor, boolean caseSensitive) {
        log.log(Level.INFO, "Executing: {0}", Arrays.toString(commandArray));
        try {
            // Create and execute our new process
            Process p = Runtime.getRuntime().exec(commandArray);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String s;
            while ((s = stdInput.readLine()) != null) {
                if (searchFor == null) {
                    return s.trim();
                }
                for (String search : searchFor) {
                    if (caseSensitive) {
                        if (s.contains(search.trim())) {
                            return s.trim();
                        }
                    } else {
                        if (s.toLowerCase().contains(search.toLowerCase().trim())) {
                            return s.trim();
                        }
                    }
                }
            }
        } catch (IOException ex) {
            log.log(Level.SEVERE, "IOException executing: {0}{1}",
                    new Object[]{Arrays.toString(commandArray), ex.getLocalizedMessage()});
        }
        return "";
    }

    /**
     * Checks that the currently running OS is Apple and executes a native
     * AppleScript macro against the OS. Returns true if the
     * <code>Process.exitValue()</code> is <code>0</code>.
     *
     * @param scriptBody AppleScript to execute
     * @return true if the <code>Process.exitValue()</code> is
     * <code>0</code>.
     */
    public static boolean executeAppleScript(String scriptBody) {
        if (!SystemUtilities.isMac()) {
            log.log(Level.SEVERE, "AppleScript can only be invoked from Apple OS");
            return false;
        }
        return execute(new String[]{"osascript", "-e", scriptBody});
    }

   /**
     * Checks that the currently running OS is Apple and executes a native
     * AppleScript macro against the OS. Returns true if the
     * supplied searchValues are found within the standard output.
     *
     * @param scriptBody AppleScript text to execute
     * @param searchValue1 first value to search for
     * @param searchValue2 second value to search for
     * @return true if the supplied searchValues are found within the standard
     * output.
     */
    public static boolean executeAppleScript(String scriptBody, String searchValue1, String searchValue2) {
        if (!SystemUtilities.isMac()) {
            log.log(Level.SEVERE, "AppleScript can only be invoked from Apple OS");
            return false;
        }
        // Empty string returned by execute(...) means the values weren't found
        return !execute(new String[]{"osascript", "-e", scriptBody},
                new String[] {searchValue1, searchValue2}).isEmpty();
    }

    /**
     * Executes a native Registry delete/query command against the OS
     * @param keyPath The path to the containing registry key
     * @param function "delete", or "query"
     * @param value the registry value to add, delete or query
     * @return true if the return code is zero
     */
    public static boolean executeRegScript(String keyPath, String function, String value) {
        return executeRegScript(keyPath, function, value, null);
    }

    /**
     * Executes a native Registry add/delete/query command against the OS
     * @param keyPath The path to the containing registry key
     * @param function "add", "delete", or "query"
     * @param value the registry value to add, delete or query
     * @param data the registry data to add when using the "add" function
     * @return true if the return code is zero
     */
    public static boolean executeRegScript(String keyPath, String function, String value, String data) {
        if (!SystemUtilities.isWindows()) {
            log.log(Level.SEVERE, "Reg commands can only be invoked from Windows");
            return false;
        }
        String reg = System.getenv("windir") + "\\system32\\reg.exe";
        if (function.equals("delete")) {
            return execute(new String[]{
                reg, function, keyPath, "/v", value, "/f"
            });
        } else if (function.equals("add")) {
            return execute(new String[]{
                reg, function, keyPath, "/v", value, "/d", data, "/f"
            });
        } else if (function.equals("query")) {
            return execute(new String[]{
                reg, function, keyPath, "/v", value
            });
        } else {
            log.log(Level.SEVERE, "Reg operation {0} not supported.", function);
            return false;
        }
    }
}
