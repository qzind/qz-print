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
package qz.printer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Enum for print languages, such as ZPL, EPL, etc.
 *
 * @author tfino
 */
public enum LanguageType {

    ZPLII(false, false, "ZEBRA", "ZPL2"),
    ZPL(false, false),
    EPL2(true, true, "EPLII"),
    EPL(true, true),
    CPCL(false, true),
    ESCP(false, false, "ESC", "ESC/P", "ESC/POS", "ESC\\P", "EPSON"),
    ESCP2(false, false, "ESC/P2"),
    UNKNOWN(false, false);


    private boolean imgOutputInvert = false;
    private boolean imgWidthValidated = false;
    private List<String> altNames;

    LanguageType(boolean imgOutputInvert, boolean imgWidthValidated, String... altNames) {
        this.imgOutputInvert = imgOutputInvert;
        this.imgWidthValidated = imgWidthValidated;

        this.altNames = new ArrayList<>();
        Collections.addAll(this.altNames, altNames);
    }

    public static LanguageType getType(String type) {
        for(LanguageType lang : LanguageType.values()) {
            if (type.equalsIgnoreCase(lang.name()) || lang.altNames.contains(type)) {
                return lang;
            }
        }

        return UNKNOWN;
    }


    /**
     * Returns whether or not this {@code LanguageType}
     * inverts the black and white pixels before sending to the printer.
     *
     * @return {@code true} if language type flips black and white pixels
     */
    public boolean requiresImageOutputInverted() {
        return imgOutputInvert;
    }

    /**
     * Returns whether or not the specified {@code LanguageType} requires
     * the image width to be validated prior to processing output.  This
     * is required for image formats that normally require the image width to
     * be a multiple of 8
     *
     * @return {@code true} if the printer requires image width validation
     */
    public boolean requiresImageWidthValidated() {
        return imgWidthValidated;
    }
}
