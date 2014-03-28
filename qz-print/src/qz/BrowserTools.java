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
package qz;

import java.applet.Applet;
import java.util.logging.Level;
import netscape.javascript.JSException;
import netscape.javascript.JSObject;

/**
 * The ScriptListener will provide an interface for receiving and sending 
 * messages to and from JavaScript.
 * 
 * @author Thomas Hart
 */
public class BrowserTools {
    
    private final JSObject window;
    
    public BrowserTools(Applet applet) {
        window = JSObject.getWindow(applet);
    }

     /**
     * Calls JavaScript function (i.e. "qzReady()" from the web browser For a
     * period of time, will call "jzebraReady()" as well as "qzReady()" but fail
     * silently on the old "jzebra" prefixed functions. If the "jzebra"
     * equivalent is used, it will display a deprecation warning.
     *
     * @param function The JavasScript function to call
     * @param o The parameter or array of parameters to send to the JavaScript
     * function
     * @return
     */
    public boolean notifyBrowser(String function, Object[] o) {
        try {
            String type = (String)window.eval("typeof(" + function + ")");
            // Ubuntu doesn't properly raise exceptions when calling invalid
            // functions, so this is the work-around
            if (!type.equals("function")) {
                throw new JSException("Object \"" + function + "\" does not "
                        + "exist or is not a function.");
            }
            
            window.call(function, o);
            
            LogIt.log(Level.INFO, "Succesfully called JavaScript function \""
                    + function + "(...)\"...");
            
            // Check for deprecated function use and log an appropriate warning
            if (function.startsWith("jzebra")) {
                LogIt.log(Level.WARNING, "JavaScript function \"" + function
                        + "(...)\" is deprecated and will be removed in future releases. "
                        + "Please use \"" + function.replaceFirst("jzebra", "qz")
                        + "(...)\" instead.");
            }
            /*else if (function.equals("qzDoneAppending") || 
                     function.equals("qzDoneFinding") || 
                     function.equals("qzDoneFindingNetwork")) {
                LogIt.log(Level.WARNING, "JavaScript function \"" + function + "(...)\" is deprecated and will be removed in future releases.");
            }*/
            
            return true;
        } catch (JSException e) { 
            boolean success = false;
            if (function.startsWith("qz")) {
                // Try to call the old jzebra function
                success = notifyBrowser(function.replaceFirst("qz", "jzebra"), o);
            }
            if (function.equals("jzebraDoneFinding")) {
                // Try to call yet another deprecated jzebra function
                success = notifyBrowser("jzebraDoneFindingPrinters", o);
            }
            // Warn about the function missing only if it wasn't recovered using the old jzebra name
            // or it's a deprecated javascript function from the qz set
            if (!success && !function.startsWith("jzebra") &&
                !function.equals("qzDoneAppending") && 
                !function.equals("qzDoneFinding") && 
                !function.equals("qzDoneFindingNetwork")) {
                LogIt.log(Level.WARNING, "Tried calling JavaScript function \""
                        + function + "(...)\" through web browser but it has not "
                        + "been implemented (" + e.getLocalizedMessage() + ")");
                LogIt.log(Level.WARNING, e.toString());
            }
            return success;
        }
    }

    /**
     * Convenience method for calling a JavaScript function with a single
     * <code>String</code> parameter. The functional equivalent of
     * notifyBrowser(String function, new Object[]{String s})
     *
     * @param function
     * @param s
     * @return
     */
    public boolean notifyBrowser(String function, String s) {
        return notifyBrowser(function, new Object[]{s});
    }
    
    /**
     * Convenience method for calling a JavaScript function with no parameters.
     * The functional equivalent of notifyBrowser(String function, new
     * Object[]{null})
     * @param function
     * @return 
     */
    public boolean notifyBrowser(String function) {
        return notifyBrowser(function, new Object[]{null});
    }

}
