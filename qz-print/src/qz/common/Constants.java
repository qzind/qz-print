package qz.common;

import java.awt.*;

/**
 * Created by robert on 7/9/2014.
 */
public class Constants {
    public static final String HEXES = "0123456789ABCDEF";
    public static final char[] HEXES_ARRAY = HEXES.toCharArray();
    public static final int OUTPUT_STREAM_SIZE = 20480;
    public static final int BYTE_BUFFER_SIZE = 512;
    public static final int APPEND_XML = 1;
    public static final String VERSION = "1.8.5";
    public static final int APPEND_RAW = 2;
    public static final int APPEND_IMAGE = 3;
    public static final int APPEND_IMAGE_PS = 4;
    public static final int APPEND_PDF = 8;
    public static final int APPEND_HTML = 16;

    /* QZ-Tray Constants */
    public static final String BLOCK_FILE = "blocked";
    public static final String ALLOW_FILE = "allowed";
    public static final String TEMP_FILE = "temp";
    public static final String LOG_FILE = "debug";
    public static final String DATA_DIR = "qz";

    public static final int BORDER_PADDING = 10;

    public static final String ABOUT_TITLE = "QZ Tray";
    public static final String ABOUT_URL = "http://qzindustries.com";
    public static final String ABOUT_COMPANY = "QZ Industries, LLC";
    public static final String ABOUT_DESC = "%s is a plugin for your web browser, <br/> used to print barcodes, receipts and more.";

    public static final String TRUSTED_PUBLISHER = String.format("Verified by %s", Constants.ABOUT_COMPANY);
    public static final String UNTRUSTED_PUBLISHER = "Untrusted website";


    public static final String WHITE_LIST = "Sites permanently allowed %s to access local resources";
    public static final String BLACK_LIST = "Sites permanently blocked %s from accessing local resources";

    public static final int EXPIRY_WARN = 30;   // days
    public static final Color WARNING_COLOR = Color.RED;
    public static final Color TRUSTED_COLOR = Color.BLUE;
}
