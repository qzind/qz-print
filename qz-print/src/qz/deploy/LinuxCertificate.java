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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Properties;

/**
 *
 * @author Tres Finocchiaro
 */
public class LinuxCertificate {
     // System logger
    static final Logger log = Logger.getLogger(LinuxCertificate.class.getName());

    private static String nssdb = "sql:" + System.getenv("HOME") + "/.pki/nssdb";
    private static String certutil = "certutil";

    private static String getCertificatePath() {
	// We assume that if the keystore is "qz-tray.jks", the cert must be "qz-tray.crt" 
	Properties sslProperties = ShortcutUtilities.loadSSLProperties();
        if (sslProperties != null) {
            return sslProperties.getProperty("wss.keystore").replaceAll("\\.jks$", ".crt");
        }
        return null;
    }

    public static void installCertificate() {
        String certPath = getCertificatePath();
        String errMsg = "";
        boolean success = false;
        if (certPath != null) {
            success = ShellUtilities.execute(new String[] {
                certutil, "-d", nssdb, "-A", "-t", "TC", "-n",Constants.ABOUT_COMPANY, "-i", certPath
            });

            if (!success) {
                errMsg += "Error executing " + certutil + 
                    ".  Ensure it is installed properly with write access to " + nssdb + ".";
            }
        } else {
            errMsg += "Unable to determine path to certificate.";
        }
        if (!success) {
            log.log(Level.WARNING, "{0} Secure websockets will not function on certain browsers.", errMsg);
        }
    }
}
