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

import javax.print.PrintException;
import javax.print.PrintService;

/**
 * Printer is an abstract class that defines the common functionality of all
 * types of printers
 * 
 * @author Thomas Hart II
 */
public interface Printer {

    /**
     * getName returns a String representation of the printer's name
     * 
     * @return The printer's name
     */
    public abstract String getName();
    
    /**
     * This function sends raw data to a Raw type printer
     * 
     * @param data The raw data to send
     * @throws javax.print.PrintException
     */ 
    public abstract void printRaw(ByteArrayBuilder data) throws PrintException;

    /**
     * This function sends raw data to a Raw type printer using an alternate 
     * approach designed to work with *nix style printing (CUPS)
     * 
     * @param data The raw data to send
     * @throws javax.print.PrintException
     */
    public void printAlternate(ByteArrayBuilder data) throws PrintException;
    
    /**
     * Returns a boolean value based on whether the printer is ready to accept 
     * a job
     * 
     * @return Boolean, true if printer is ready
    */ 
    public abstract boolean ready();
    
    /**
     * Sets the PrintService associated with this Printer
     * 
     * @param ps
     */
    public abstract void setPrintService(PrintService ps);
    
    /**
     * Returns the PrintService associated with this Printer
     * 
     * @return 
     */
    public abstract PrintService getPrintService();
    
    /**
     * Returns a String with the type of printer
     * Possibilities: RAW, PS, FILE, DEBUG
     * 
     * @return The printer type
     */
    public abstract String getType();
    
    /**
     * Sets the name variable of the printer
     *
     * @param name The printer's name
     */
    public abstract void setName(String name);
    
    /**
     * Sets the title of the job.
     * 
     * @param jobTitle The new title
     */
    public abstract void setJobTitle(String jobTitle);

    
    
}
