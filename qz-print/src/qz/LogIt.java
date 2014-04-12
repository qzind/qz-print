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

package qz;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.event.PrintJobEvent;

public class LogIt {
    public static boolean disableLogging = false;
    
    

    public static void log(Level lvl, String msg, Throwable t) {
        log(LogIt.class.getName(), lvl, msg, t);
    }

    public static void log(String msg, Throwable t) {
        log(Level.SEVERE, msg, t);
    }
    
    @SuppressWarnings("CallToThreadDumpStack")
    public static void log(Throwable t) {
        if (t instanceof InterruptedException) {
            log(Level.WARNING, "JavaScript listener interrupted, probably due to a browser refresh.");
        } else {
            log("Error", t);
            t.printStackTrace();
        }
    }

    public static void log(String className, Level lvl, String msg) {
        if (!disableLogging) {
            Logger.getLogger(className).log(lvl, msg);
        }
    }
    
    public static void log(String className, Level lvl, String msg, Throwable t) {
        if (!disableLogging) {
            Logger.getLogger(className).log(lvl, msg, t);
        }
    }

    public static void log(Level lvl, String msg) {
        log(LogIt.class.getName(), lvl, msg);
    }

    public static void log(String msg) {
        log(Level.INFO, msg);
    }

    public static void log(PrintJobEvent pje) {
        Level lvl;
        String msg = "Print job ";
        switch (pje.getPrintEventType()) {
            case PrintJobEvent.DATA_TRANSFER_COMPLETE:
                lvl = Level.INFO;
                msg += "data transfer complete.";
                break;
            case PrintJobEvent.NO_MORE_EVENTS:
                lvl = Level.INFO;
                msg += "has no more events.";
                break;
            case PrintJobEvent.JOB_COMPLETE:
                lvl = Level.INFO;
                msg += "job complete.";
                break;
            case PrintJobEvent.REQUIRES_ATTENTION:
                lvl = Level.WARNING;
                msg += "requires attention.";
                break;
            case PrintJobEvent.JOB_CANCELED:
                lvl = Level.WARNING;
                msg += "job canceled.";
                break;
            case PrintJobEvent.JOB_FAILED:
                lvl = Level.SEVERE;
                msg += "job failed.";
                break;
            default:
                return;
        }
        LogIt.log(lvl, msg);
    }

}
