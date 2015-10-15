package qz.printer;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintOptions {

    private static final Logger log = LoggerFactory.getLogger(PrintOptions.class);

    private Postscript psOptions = new Postscript();
    private Raw rawOptions = new Raw();


    public PrintOptions(JSONObject options) {
        //check for raw options
        if (!options.isNull("perSpool")) {
            try { rawOptions.perSpool = options.getInt("perSpool"); }
            catch(JSONException e) { log.warn("integer", "perSpool", options.opt("perSpool")); }
        }
        if (!options.isNull("language")) {
            rawOptions.language = options.optString("language");
        }
        if (!options.isNull("encoding")) {
            rawOptions.encoding = options.optString("encoding");
        }
        if (!options.isNull("endOfDoc")) {
            rawOptions.endOfDoc = options.optString("endOfDoc");
        }
        if (!options.isNull("printerTray")) {
            rawOptions.printerTray = options.optString("printerTray");
        }
        if (!options.isNull("altPrinting")) {
            try { rawOptions.altPrinting = options.getBoolean("altPrinting"); }
            catch(JSONException e) { warn("boolean", "altPrinting", options.opt("altPrinting")); }
        }


        //check for postscript options
        if (!options.isNull("size")) {
            Size s = new Size();
            JSONObject subSize = options.optJSONObject("size");
            if (subSize != null) {
                try { s.width = subSize.getDouble("width"); }
                catch(JSONException e) { warn("double", "size.width", subSize.opt("width")); }
                try { s.height = subSize.getDouble("height"); }
                catch(JSONException e) { warn("double", "size.height", subSize.opt("height")); }

                s.units = subSize.optString("units");

                try { s.auto = subSize.getBoolean("auto"); }
                catch(JSONException e) { warn("boolean", "size.auto", subSize.opt("auto")); }

                psOptions.size = s;
            } else {
                warn("JSONObject", "size", options.opt("size"));
            }
        }
        if (!options.isNull("margins")) {
            Margins m = new Margins();
            JSONObject subMargins = options.optJSONObject("margins");
            if (subMargins != null) {
                //each individually
                try { m.top = subMargins.getDouble("top"); }
                catch(JSONException e) { warn("double", "margins.top", subMargins.opt("top")); }
                try { m.right = subMargins.getDouble("right"); }
                catch(JSONException e) { warn("double", "margins.right", subMargins.opt("right")); }
                try { m.bottom = subMargins.getDouble("bottom"); }
                catch(JSONException e) { warn("double", "margins.bottom", subMargins.opt("bottom")); }
                try { m.left = subMargins.getDouble("left"); }
                catch(JSONException e) { warn("double", "margins.left", subMargins.opt("left")); }

                psOptions.margins = m;
            } else {
                try { m.setAll(options.getDouble("margins")); }
                catch(JSONException e) { warn("double", "margins", options.opt("margins")); }
            }
        }
        if (!options.isNull("duplex")) {
            try { psOptions.duplex = options.getBoolean("duplex"); }
            catch(JSONException e) { warn("boolean", "duplex", options.opt("duplex")); }
        }
        if (!options.isNull("orientation")) {
            try {
                psOptions.orientation = Orientation.valueOf(options.optString("orientation").toUpperCase());
            }
            catch(IllegalArgumentException e) {
                warn("valid value", "orientation", options.opt("orientation"));
            }
        }
        if (!options.isNull("rotation")) {
            try { psOptions.rotation = options.getDouble("rotation"); }
            catch(JSONException e) { warn("double", "rotation", options.opt("rotation")); }
        }
        if (!options.isNull("paperThickness")) {
            try { psOptions.paperThickness = options.getDouble("paperThickness"); }
            catch(JSONException e) { warn("double", "paperThickness", options.opt("paperThickness")); }
        }
        if (!options.isNull("colorType")) {
            try {
                psOptions.colorType = ColorType.valueOf(options.optString("colorType").toUpperCase());
            }
            catch(IllegalArgumentException e) {
                warn("valid value", "colorType", options.opt("colorType"));
            }
        }
        if (!options.isNull("copies")) {
            try { psOptions.copies = options.getInt("copies"); }
            catch(JSONException e) { warn("integer", "copies", options.opt("copies")); }
        }
    }

    private static void warn(String expectedType, String name, Object actualValue) {
        log.warn("Cannot read {} as an {} for {}", actualValue, expectedType, name);
    }


    public Raw getRawOptions() {
        return rawOptions;
    }

    public Postscript getPSOptions() {
        return psOptions;
    }


    // Option groups //

    public class Raw {
        int perSpool = 1;
        String language = null;
        String encoding = null;
        String endOfDoc = null;
        String printerTray = null;
        boolean altPrinting = false;
    }

    public class Postscript {
        Size size = null;
        Margins margins = new Margins();
        boolean duplex = false;
        Orientation orientation = Orientation.DEFAULT;
        double rotation = 0;
        double paperThickness = -1;
        ColorType colorType = ColorType.COLOR;
        int copies = 1;
    }

    // Sub options //

    private class Size {
        double width = -1;
        double height = -1;
        String units = "in";
        boolean auto = false;
    }

    private class Margins {
        double top = 0;
        double right = 0;
        double bottom = 0;
        double left = 0;

        void setAll(double margin) {
            top = margin;
            right = margin;
            bottom = margin;
            left = margin;
        }
    }

    private enum Orientation {
        DEFAULT, PORTRAIT, LANDSCAPE, LANDSCAPE_REVERSE
    }

    private enum ColorType {
        COLOR, GREYSCALE, BLACKWHITE
    }

}
