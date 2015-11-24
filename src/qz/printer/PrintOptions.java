package qz.printer;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.OrientationRequested;
import java.awt.print.PageFormat;

public class PrintOptions {

    private static final Logger log = LoggerFactory.getLogger(PrintOptions.class);

    private Pixel psOptions = new Pixel();
    private Raw rawOptions = new Raw();


    /**
     * Parses the provided JSON Object into relevant Pixel and Raw options
     */
    public PrintOptions(JSONObject configOpts) {
        if (configOpts == null) { configOpts = new JSONObject(); }

        //check for raw options
        if (!configOpts.isNull("altPrinting")) {
            try { rawOptions.altPrinting = configOpts.getBoolean("altPrinting"); }
            catch(JSONException e) { warn("boolean", "altPrinting", configOpts.opt("altPrinting")); }
        }
        if (!configOpts.isNull("encoding")) {
            rawOptions.encoding = configOpts.optString("encoding", null);
        }
        if (!configOpts.isNull("endOfDoc")) {
            rawOptions.endOfDoc = configOpts.optString("endOfDoc", null);
        }
        if (!configOpts.isNull("language")) {
            rawOptions.language = configOpts.optString("language", null);
        }
        if (!configOpts.isNull("perSpool")) {
            try { rawOptions.perSpool = configOpts.getInt("perSpool"); }
            catch(JSONException e) { log.warn("integer", "perSpool", configOpts.opt("perSpool")); }
        }

        //check for pixel options
        if (!configOpts.isNull("colorType")) {
            try {
                psOptions.colorType = ColorType.valueOf(configOpts.optString("colorType").toUpperCase());
            }
            catch(IllegalArgumentException e) {
                warn("valid value", "colorType", configOpts.opt("colorType"));
            }
        }
        if (!configOpts.isNull("copies")) {
            try { psOptions.copies = configOpts.getInt("copies"); }
            catch(JSONException e) { warn("integer", "copies", configOpts.opt("copies")); }
            if (psOptions.copies < 1) {
                log.warn("Cannot have less than one copy");
                psOptions.copies = 1;
            }
        }
        if (!configOpts.isNull("density")) {
            try { psOptions.density = configOpts.getInt("density"); }
            catch(JSONException e) { warn("integer", "density", configOpts.opt("density")); }
        }
        if (!configOpts.isNull("duplex")) {
            try { psOptions.duplex = configOpts.getBoolean("duplex"); }
            catch(JSONException e) { warn("boolean", "duplex", configOpts.opt("duplex")); }
        }
        if (!configOpts.isNull("margins")) {
            Margins m = new Margins();
            JSONObject subMargins = configOpts.optJSONObject("margins");
            if (subMargins != null) {
                //each individually
                if (!subMargins.isNull("top")) {
                    try { m.top = subMargins.getDouble("top"); }
                    catch(JSONException e) { warn("double", "margins.top", subMargins.opt("top")); }
                }
                if (!subMargins.isNull("right")) {
                    try { m.right = subMargins.getDouble("right"); }
                    catch(JSONException e) { warn("double", "margins.right", subMargins.opt("right")); }
                }
                if (!subMargins.isNull("bottom")) {
                    try { m.bottom = subMargins.getDouble("bottom"); }
                    catch(JSONException e) { warn("double", "margins.bottom", subMargins.opt("bottom")); }
                }
                if (!subMargins.isNull("left")) {
                    try { m.left = subMargins.getDouble("left"); }
                    catch(JSONException e) { warn("double", "margins.left", subMargins.opt("left")); }
                }
            } else {
                try { m.setAll(configOpts.getDouble("margins")); }
                catch(JSONException e) { warn("double", "margins", configOpts.opt("margins")); }
            }

            psOptions.margins = m;
        }
        if (!configOpts.isNull("orientation")) {
            try {
                psOptions.orientation = Orientation.valueOf(configOpts.optString("orientation").replaceAll("\\-", "_").toUpperCase());
            }
            catch(IllegalArgumentException e) {
                warn("valid value", "orientation", configOpts.opt("orientation"));
            }
        }
        if (!configOpts.isNull("paperThickness")) {
            try { psOptions.paperThickness = configOpts.getDouble("paperThickness"); }
            catch(JSONException e) { warn("double", "paperThickness", configOpts.opt("paperThickness")); }
        }
        if (!configOpts.isNull("printerTray")) {
            psOptions.printerTray = configOpts.optString("printerTray", null);
        }
        if (!configOpts.isNull("rotation")) {
            try { psOptions.rotation = configOpts.getDouble("rotation"); }
            catch(JSONException e) { warn("double", "rotation", configOpts.opt("rotation")); }
        }
        if (!configOpts.isNull("size")) {
            Size s = new Size();
            JSONObject subSize = configOpts.optJSONObject("size");
            if (subSize != null) {
                if (!subSize.isNull("width")) {
                    try { s.width = subSize.getDouble("width"); }
                    catch(JSONException e) { warn("double", "size.width", subSize.opt("width")); }
                }
                if (!subSize.isNull("height")) {
                    try { s.height = subSize.getDouble("height"); }
                    catch(JSONException e) { warn("double", "size.height", subSize.opt("height")); }
                }

                if (!subSize.isNull("scaleImage")) {
                    try { s.fitImage = subSize.getBoolean("scaleImage"); }
                    catch(JSONException e) { warn("boolean", "size.scaleImage", subSize.opt("scaleImage")); }
                }

                psOptions.size = s;
            } else {
                warn("JSONObject", "size", configOpts.opt("size"));
            }
        }
        if (!configOpts.isNull("units")) {
            switch(configOpts.optString("units")) {
                case "mm":
                    psOptions.units = Unit.MM; break;
                case "cm":
                    psOptions.units = Unit.CM; break;
                case "in":
                    psOptions.units = Unit.INCH; break;
                default:
                    warn("valid value", "size.units", configOpts.opt("units")); break;
            }
        }
    }

    /**
     * Helper method for parse warnings
     *
     * @param expectedType Expected entry type
     * @param name         Option name
     * @param actualValue  Invalid value passed
     */
    private static void warn(String expectedType, String name, Object actualValue) {
        log.warn("Cannot read {} as an {} for {}, using default", actualValue, expectedType, name);
    }


    public Raw getRawOptions() {
        return rawOptions;
    }

    public Pixel getPixelOptions() {
        return psOptions;
    }


    // Option groups //

    /** Raw printing options */
    public class Raw {
        private boolean altPrinting = false;    //Alternate printing for linux systems
        private String encoding = null;         //Text encoding / charset
        private String endOfDoc = null;         //End of document character
        private String language = null;         //Printer language
        private int perSpool = 1;               //Pages per spool


        public boolean isAltPrinting() {
            return altPrinting;
        }

        public String getEncoding() {
            return encoding;
        }

        public String getEndOfDoc() {
            return endOfDoc;
        }

        public String getLanguage() {
            return language;
        }

        public int getPerSpool() {
            return perSpool;
        }
    }

    /** Pixel printing options */
    public class Pixel {
        private ColorType colorType = ColorType.COLOR;  //Color / black&white
        private int copies = 1;                         //Job copies
        private int density = 72;                       //Pixel density (DPI or DPMM)
        private boolean duplex = false;                 //Double/single sided
        private Margins margins = new Margins();        //Page margins
        private Orientation orientation = null;         //Page orientation
        private double paperThickness = -1;             //Paper thickness
        private String printerTray = null;              //Printer tray to use
        private double rotation = 0;                    //Image rotation
        private Size size = null;                       //Paper size
        private Unit units = Unit.INCH;                 //Units for density, margins, size


        public ColorType getColorType() {
            return colorType;
        }

        public int getCopies() {
            return copies;
        }

        public int getDensity() {
            return density;
        }

        public boolean isDuplex() {
            return duplex;
        }

        public Margins getMargins() {
            return margins;
        }

        public Orientation getOrientation() {
            return orientation;
        }

        public double getPaperThickness() {
            return paperThickness;
        }

        public String getPrinterTray() {
            return printerTray;
        }

        public double getRotation() {
            return rotation;
        }

        public Size getSize() {
            return size;
        }

        public Unit getUnits() {
            return units;
        }
    }

    // Sub options //

    /** Pixel page size options */
    public class Size {
        private double width = -1;          //Page width
        private double height = -1;         //Page height
        private boolean fitImage = false;   //Adjust paper size for best image fit


        public double getWidth() {
            return width;
        }

        public double getHeight() {
            return height;
        }

        public boolean isFitImage() {
            return fitImage;
        }
    }

    /** Pixel page margins options */
    public class Margins {
        private double top = 0;             //Top page margin
        private double right = 0;           //Right page margin
        private double bottom = 0;          //Bottom page margin
        private double left = 0;            //Left page margin

        private void setAll(double margin) {
            top = margin;
            right = margin;
            bottom = margin;
            left = margin;
        }


        public double top() {
            return top;
        }

        public double right() {
            return right;
        }

        public double bottom() {
            return bottom;
        }

        public double left() {
            return left;
        }
    }

    /** Pixel dimension values */
    public enum Unit {
        INCH(1.0f),
        CM(2.54f),
        MM(25.4f);

        private final float toInch; //multiplicand to convert to inches

        Unit(float toIN) {
            toInch = toIN;
        }

        public float asInch() {
            return toInch;
        }
    }

    /** Pixel page orientation option */
    public enum Orientation {
        PORTRAIT(OrientationRequested.PORTRAIT, PageFormat.PORTRAIT),
        REVERSE_PORTRAIT(OrientationRequested.PORTRAIT, PageFormat.PORTRAIT),
        LANDSCAPE(OrientationRequested.LANDSCAPE, PageFormat.LANDSCAPE),
        REVERSE_LANDSCAPE(OrientationRequested.REVERSE_LANDSCAPE, PageFormat.REVERSE_LANDSCAPE);

        private final OrientationRequested asAttribute; //OrientationRequested const
        private final int asFormat; //PageFormat const

        Orientation(OrientationRequested asAttribute, int asFormat) {
            this.asAttribute = asAttribute;
            this.asFormat = asFormat;
        }


        public OrientationRequested getAsAttribute() {
            return asAttribute;
        }

        public int getAsFormat() {
            return asFormat;
        }
    }

    /** Pixel page color option */
    public enum ColorType {
        COLOR(Chromaticity.COLOR),
        GREYSCALE(Chromaticity.MONOCHROME),
        BLACKWHITE(Chromaticity.MONOCHROME);

        private Chromaticity chromatic;

        ColorType(Chromaticity chromatic) {
            this.chromatic = chromatic;
        }


        public Chromaticity getChromatic() {
            return chromatic;
        }
    }

}
