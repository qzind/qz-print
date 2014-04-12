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

import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.util.logging.Level;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.OrientationRequested;

/**
 * Represents paper size in inches or millimeters with built in parsers.  Default
 * is 8.5 x 11.0 inches (US)
 * @author tfino
 */
public class PaperFormat {
    private static final int REVERSE_PORTRAIT = 9238;
    
    private float width = 8.5f;
    private float height = 11.0f;
    
    private Float autoWidth;
    private Float autoHeight;
    
    private boolean autoSize;
    
    private int orientation = PageFormat.PORTRAIT;

    private int units = MediaSize.INCH;

    public PaperFormat() {
    }

    /*public PaperSize(String width, String height) {
    setSize(width, height);
    }*/
    public PaperFormat(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /*public PaperSize(String width, String height, String units) {
    setSize(width, height, units);
    }*/
    public PaperFormat(float width, float height, int units) {
        this.units = units;
        this.width = width;
        this.height = height;
    }

    /*public Float[] getSize() {
        return size;
    }*/
    
    public void setAutoSize(BufferedImage b) {
        if (b != null) {
            setAutoSize(b.getWidth(), b.getHeight(), this);
            autoSize = true;
        } else {
            LogIt.log(Level.WARNING, "Image specified is empty.");
        }
    }
    
    public static int parseOrientation(String orientation) {
        if (orientation.equalsIgnoreCase("landscape")) {
           return PageFormat.LANDSCAPE;
        } else if (orientation.equalsIgnoreCase("portrait")) {
            return PageFormat.PORTRAIT;
        } else if (orientation.equalsIgnoreCase("reverse-landscape") || orientation.equalsIgnoreCase("reverse_landscape") || orientation.equalsIgnoreCase("reverse landscape")) {
            return PageFormat.REVERSE_LANDSCAPE;
        } else if (orientation.equalsIgnoreCase("reverse-portrait") || orientation.equalsIgnoreCase("reverse_portrait") || orientation.equalsIgnoreCase("reverse portrait")) {
            return PaperFormat.REVERSE_PORTRAIT;
        } else {
            LogIt.log(Level.WARNING, "Orientation specified \"" + orientation + "\" not recognized.");
            return -1;
        }
    }
   
    public String getOrientationDescription() {
        return getOrientationDescription(this.orientation);
    }
    
    public static String getOrientationDescription(int orientation) {
        switch (orientation) {
            case PaperFormat.REVERSE_PORTRAIT: return "reverse-portrait";
            case PageFormat.LANDSCAPE: return "landscape";
            case PageFormat.REVERSE_LANDSCAPE: return "reverse-landscape";
            case PageFormat.PORTRAIT: return "portrait"; // move down
            default: return "unknown"; 
        }
    }
    
   public void setOrientation(String s) {
       this.orientation = parseOrientation(s);
   }
   
   public void setOrientation(int orientation) {
       this.orientation = orientation;
   }
    
    public int getOrientation() {
        if (orientation == PaperFormat.REVERSE_PORTRAIT) {
            return PageFormat.PORTRAIT;
        }
        return orientation;
    }
    
    public OrientationRequested getOrientationRequested() {
        switch (orientation) {
            case PageFormat.LANDSCAPE: return OrientationRequested.LANDSCAPE;
            case PageFormat.REVERSE_LANDSCAPE: return OrientationRequested.REVERSE_LANDSCAPE;
            case PaperFormat.REVERSE_PORTRAIT: return OrientationRequested.REVERSE_PORTRAIT;
            case PageFormat.PORTRAIT: // move down
            default: return OrientationRequested.PORTRAIT; 
        }
    }

    /**
     * Automatically calculates the best <code>PaperSize</code> based on the supplied 
     * image dimensions 
     * @param imageWidth
     * @param imageHeight
     * @param p
     * @return 
     */
    public static void setAutoSize(int imageWidth, int imageHeight, PaperFormat p) {
        // swap image dimensions
        if (p.isLandscape()) {
            int temp = imageWidth;
            imageWidth = imageHeight;
            imageHeight = temp;
        }
        
        float imageRatio = (float)imageWidth/(float)imageHeight;
        float paperRatio = p.getWidth()/p.getHeight();
        float wRatio = (float)imageWidth/p.getWidth();
        float hRatio = (float)imageHeight/p.getHeight();
        
        if (imageRatio >= paperRatio) {
            // use width to recalculate height
            p.setAutoWidth(p.getWidth());
            p.setAutoHeight((float)imageHeight / wRatio);
        } else {
            // use height to recalculate width
            p.setAutoHeight(p.getHeight());
            p.setAutoWidth((float)imageWidth / hRatio);
        }
        p.setAutoSize(true);
    }
    
    public boolean isPortrait() {
        return this.getOrientation() == PaperFormat.REVERSE_PORTRAIT || this.getOrientation() == PageFormat.PORTRAIT;
    }
    
    public boolean isLandscape() {
        return this.getOrientation() == PageFormat.LANDSCAPE || this.getOrientation() == PageFormat.REVERSE_LANDSCAPE;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }
    
    /*public final void setAutoSize(float width, float height) {
        this.autoWidth = width;
        this.autoHeight = height;
    }*/
    
    @Override
    public String toString() {
       return "Size: " + width + getUnitDescription() + " x " + height + getUnitDescription() + 
               ", AutoSize: " + getAutoWidth() + getUnitDescription() + " x " + getAutoHeight() + 
               getUnitDescription() + " (orientation is \"" + getOrientationDescription() + 
               "\", auto size is " + (autoSize ? "enabled)" : "disabled)");
    }

    
    public boolean isAutoSize() {
        return autoSize;
    }

    public void setAutoSize(boolean autoSize) {
        this.autoSize = autoSize;
    }
    
    public final void setAutoWidth(float width) {
        this.autoWidth = width;
    }
    
    public final void setAutoHeight(float height) {
        this.autoHeight = height;
    }
   
    public final float getAutoWidth() {
        return autoWidth == null || !autoSize ? width : autoWidth;
    }
    
    public final float getAutoHeight() {
        return autoHeight == null || !autoSize ? height : autoHeight;
    }

    /*public final void setSize(float width, float height) {
        size = new Float[]{width, height};
    }*/

    public final void setUnits(int units) {
        this.units = units;
    }

    public String getUnitDescription() {
        switch (units) {
            case MediaSize.INCH:
                return "in";
            case MediaSize.MM:
                return "mm";
        }
        return null;
    }

    public int getUnits() {
        return units;
    }

    /**
     * Parses paper size (such as 8.5in x 11.0 in) and sets it
     * @param width
     * @param height 
     */
    public static PaperFormat parseSize(String width, String height) throws NumberFormatException {
        if (width.toLowerCase().endsWith("in") && height.toLowerCase().endsWith("in")) {
            return parseSize(width.split("in")[0], height.split("in")[0], "in");
        } else if (width.toLowerCase().endsWith("mm") && height.toLowerCase().endsWith("mm")) {
            return parseSize(width.split("mm")[0], height.split("mm")[0], "mm");
        } else {
            return parseSize(width, height, null);
        }
    }

    /**
     * Sets paper size and units, example:  8.5, 11.0, mm.
     * @param width
     * @param height
     * @param unit 
     */
    public static PaperFormat parseSize(String width, String height, String units) throws NumberFormatException {
        if (width == null || height == null) {
            throw new NumberFormatException("Cannot parse float from null value");
        }
        Float w = Float.parseFloat(width.trim());
        Float h = Float.parseFloat(height.trim());

        int unitz = parseUnits(units);
        if (unitz != -1) {
            return new PaperFormat(w, h, unitz);
        } else {
            return new PaperFormat(w, h);
        }

    }

    public static int parseUnits(String units) {
        if (units == null) {
            return -1;
        } else if (units.equalsIgnoreCase("in") || units.equalsIgnoreCase("inch") || units.equalsIgnoreCase("standard")) {
            return MediaSize.INCH;
        } else if (units.equalsIgnoreCase("mm") || units.equalsIgnoreCase("metric")) {
            return MediaSize.MM;
        } else {
            return -1;
        }
    }
}
