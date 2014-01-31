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

/**
 * Enum for print languages, such as ZPL, EPL, etc.
 *
 * @author tfino
 */
public enum LanguageType {

    ZPLII, ZPL, EPL2, EPL, CPCL, ESCP, ESCP2, UNKNOWN;

    LanguageType() {
    }

    public static LanguageType getType(String s) {
        for (LanguageType lt : LanguageType.values()) {
            if (s.equalsIgnoreCase(lt.name())) {
                return lt;
            }
        }
        if (s.equalsIgnoreCase("ZEBRA")) {
            return ZPLII;
        } else if (s.equalsIgnoreCase("ZPL2")) {
            return ZPLII;
        } else if (s.equalsIgnoreCase("EPLII")) {
            return EPL2;
        } else if (s.equalsIgnoreCase("ESC")) {
            return ESCP;
        } else if (s.equalsIgnoreCase("ESC/P")) {
            return ESCP;
        } else if (s.equalsIgnoreCase("ESC/POS")) {
            return ESCP;
        } else if (s.equalsIgnoreCase("ESC\\P")) {
            return ESCP;
        } else if (s.equalsIgnoreCase("ESC/P2")) {
            return ESCP2;
        } else if (s.equalsIgnoreCase("EPSON")) {
            return ESCP;
        }
        return UNKNOWN;
    }

    /**
     * Returns whether or not the specified <code>LanguageType</code> flips 
     * inverts the black and white pixels before sending to the printer.
     * @param languageType
     * @return 
     */
    public static boolean requiresImageOutputInverted(LanguageType languageType) {
        switch (languageType) {
            case EPL:
            case EPL2:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns whether or not this <code>LanguageType</code> flips 
     * inverts the black and white pixels before sending to the printer.
     * @return 
     */
    public boolean requiresImageOutputInverted() {
        return LanguageType.requiresImageOutputInverted(this);
    }

    /**
     * Returns whether or not the specified <code>LanguageType</code> requires 
     * the image width to be validated prior to processing output.  This
     * is required for image formats that normally require the image width to
     * be a multiple of 8
     * @param languageType
     * @return 
     */
    public static boolean requiresImageWidthValidated(LanguageType languageType) {
        switch (languageType) {
            case CPCL:
            case EPL:
            case EPL2:
                return true;
            default:
                return false;
        }
    }

    public boolean requiresImageWidthValidated() {
        return LanguageType.requiresImageWidthValidated(this);
    }
}