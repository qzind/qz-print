package qz.utils;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import qz.printer.PrintOptions;
import qz.printer.action.PrintHTML;
import qz.printer.action.PrintImage;
import qz.printer.action.PrintPDF;
import qz.printer.action.PrintProcessor;
import qz.printer.action.PrintRaw;

public class PrintingUtilities {

    private PrintingUtilities() {}


    //FIXME - needs re-thought, [file] type can be used as sole data for all print types ..

    public static PrintProcessor getPrintProcessor(JSONArray printData, PrintOptions.Raw rawPrintOptions) throws JSONException {
        if (rawPrintOptions != null && !rawPrintOptions.isDefault()) {
            //return new PrintRaw();
        }

        for(int i = 0; i < printData.length(); i++) {
            String type;

            JSONObject data = printData.optJSONObject(i);
            if (data == null) {
                type = "raw";
            } else {
                type = data.getString("type").toLowerCase();
            }

            switch(type) {
                case "raw": case "hex": case "xml":
                    //return new PrintRaw();
                case "html":
                    return new PrintHTML();
                case "pdf":
                    return new PrintPDF();
                case "image": case "base64": case "file":
                default:
                    break; //only postscript if no other option is found
            }
        }

        return new PrintImage();
    }

}
