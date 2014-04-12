/**
 * @author Tres Finocchiaro
 * 
 * Copyright (C) 2013 Tres Finocchiaro, QZ Industries
 *
 * IMPORTANT:  This software is dual-licensed
 * 
 * LGPL 2.1
 * This is free software.  This software and source code are released under 
 * the "LGPL 2.1 License".  A copy of this license should be distributed with 
 * this software. http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * QZ INDUSTRIES SOURCE CODE LICENSE
 * This software and source code *may* instead be distributed under the 
 * "QZ Industries Source Code License", available by request ONLY.  If source 
 * code for this project is to be made proprietary for an individual and/or a
 * commercial entity, written permission via a copy of the "QZ Industries Source
 * Code License" must be obtained first.  If you've obtained a copy of the 
 * proprietary license, the terms and conditions of the license apply only to 
 * the licensee identified in the agreement.  Only THEN may the LGPL 2.1 license
 * be voided.
 * 
 */

/*
 * Backwards compability for older implimentations
 */

package qz;

import java.util.logging.Level;

/**
 * Renamed to PrintApplet version 1.2.0+
 * @author tfino
 */
@Deprecated
public class RawPrintApplet extends PrintApplet {
    @Deprecated
    public RawPrintApplet() {
        super();
        LogIt.log(Level.WARNING, "Since version 1.2.0, use of \"" +
                this.getClass().getCanonicalName() + "\" has been renamed and " +
                "is now deprecated and will be removed in future versions." +
                "  Please use \"" + PrintApplet.class.getCanonicalName() + "\" instead. " +
                "All functionality will remain the same.");
    }
}
