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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.logging.Level;

/**
 * Place for all raw static byte conversion functions. Expecially useful for
 * converting typed Hex (<code>String</code> base 16) to <code>byte[]</code>,
 * <code>byte[]</code> to Hex (<code>String</code> base 16), <code>byte[]</code>
 * to <code>int[]</code> conversions, etc.
 *
 * @author Tres Finocchiaro
 */
public class ByteUtilities {

    /**
     * Converts typed Hex (<code>String</code> base 16) to <code>byte[]</code>.
     * This is expecially useful for special characters that are appended via
     * JavaScript, specifically the "\0" or <code>NUL</code> character, which
     * will early terminate a JavaScript <code>String</code>.
     *
     * @param s
     * @return
     * @throws NumberFormatException
     */
    public static byte[] hexStringToByteArray(String s) throws NumberFormatException {
        byte[] data = new byte[0];
        if (s != null && s.length() > 0) {
            String[] split;
            if (s.length() > 2) {
                if (s.length() >= 3 && s.contains("x")) {
                    s = s.startsWith("x") ? s.substring(1) : s;
                    s = s.endsWith("x") ? s.substring(0, s.length() - 1) : s;
                    split = s.split("x");
                } else {
                    split = s.split("(?<=\\G..)");
                }

                data = new byte[split.length];
                for (int i = 0; i < split.length; i++) {
                    data[i] = Byte.parseByte(split[i], 16);
                }
            } else if (s.length() == 2) {
                data = new byte[]{Byte.parseByte(s)};
            }
        }
        return data;
    }

    final protected static char[] HEXES_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEXES_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEXES_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Iterates through byte array finding matches of a sublist of bytes.
     * Returns an array of positions. TODO: Make this natively Iterable.
     *
     * @param array
     * @param target
     * @return
     */
    public static int[] indicesOfSublist(byte[] array, byte[] sublist) {
        LinkedList<Integer> indexes = new LinkedList<Integer>();

        if (array == null || sublist == null || array.length == 0
                || sublist.length == 0 || sublist.length > array.length) {
            return new int[0];
        }

        // Find instances of byte list
        outer:
        for (int i = 0; i < array.length - sublist.length + 1; i++) {
            for (int j = 0; j < sublist.length; j++) {
                if (array[i + j] != sublist[j]) {
                    continue outer;
                }
            }
            indexes.add(i);
        }

        // Convert to primitive type
        int[] int_array = new int[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
            int_array[i] = indexes.get(i);
        }

        return int_array;
    }

    /**
     * When supplied a <code>src</code> <code>byte[]</code> array,
     * <code>pattern</code> <code>byte[]</code> array and <code>count</code>,
     * this returns a <code>LinkedList</code> of <code>byte[]</code> split at
     * the nth instance of the supplied <code>pattern</code>. (nth being the
     * supplied <code>count</code> parameter)
     *
     * This is useful for large print batches that need to be split up (for
     * example) after the P1 or ^XO command has been issued.
     *
     * TODO: A rewrite of this would be a proper <code>Iteratable</code>
     * interface paired with an <code>Iterator</code> that does this
     * automatically and would eliminate the need for a
     * <code>indicesOfSublist()</code> function.
     *
     * @param src
     * @param pattern
     * @param count
     * @return
     */
    public static LinkedList<ByteArrayBuilder> splitByteArray(byte[] src, byte[] pattern, int count)
            throws NullPointerException, IndexOutOfBoundsException, ArrayStoreException {
        LinkedList<ByteArrayBuilder> byteArrayList = new LinkedList<ByteArrayBuilder>();
        int[] split = indicesOfSublist(src, pattern);
        count = count < 1 ? 1 : count;
        int _count = 1;
        int prev = 0;
        ByteArrayBuilder b = new ByteArrayBuilder();
        for (int i : split) {
            byte[] temp = new byte[i - prev + pattern.length];
            System.arraycopy(src, prev, temp, 0, temp.length);
            b.append(temp);
            if (_count < count) {
                _count++;
            } else {
                byteArrayList.add(b);
                b = new ByteArrayBuilder();
                _count = 1;
            }
            prev = i + pattern.length;
        }
        if (!byteArrayList.contains(b)) {
            byteArrayList.add(b);
        }
        return byteArrayList;
    }

    public static byte[] intArrayToByteArray(int ints[]) {
        byte[] bytes = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            bytes[i] = (byte) ints[i];
        }
        return bytes;
    }

    /**
     * Base 2 array to Base 10 array converter: Takes a binary array (using a
     * boolean array for storage) and converts every 8 rows to a full byte,
     * keeping the value in decimal (<code>int</code>). Returns an array of
     * integers (<code>int[]</code>). These integers will likely be converted to
     * a byte array (<code>byte[]</code>) later.
     *
     * @param black
     * @return
     */
    public static int[] binaryArrayToIntArray(boolean[] black) {
        int[] hex = new int[(int) (black.length / 8)];
        // Convert every eight zero's to a full byte, in decimal
        for (int i = 0; i < hex.length; i++) {
            for (int k = 0; k < 8; k++) {
                hex[i] += (black[8 * i + k] ? 1 : 0) << 7 - k;
            }
        }
        return hex;
    }

    private static final String HEXES = "0123456789ABCDEF";

    /*
     * Converts an integer array (<code>int[]</code>) to a String representation
     * of a hexadecimal array.
     * 
     * @param raw
     * @return 
     */
    public static String getHexString(int[] raw) {
        if (raw == null) {
            return null;
        }
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final int i : raw) {
            hex.append(HEXES.charAt((i & 0xF0) >> 4)).append(HEXES.charAt((i & 0x0F)));
        }
        return hex.toString();
    }

    public static boolean isBlank(Object o) {
        if (o instanceof byte[]) {
            return ((byte[]) o).length < 1;
        } else if (o instanceof String) {
            return ((String) o) == null || ((String) o).equals("");
        } else {
            LogIt.log(Level.WARNING, "Uchecked blank comparison.");
            return o == null;
        }
    }
    
    public static boolean isBase64Image(String path) {
        return path.startsWith("data:image/") && path.contains(";base64,");
    }

    public static boolean isBase64PDF(String path) {
        return path.startsWith("data:application/pdf;base64,");
    }
    
    /**
     * Reads a binary file (i.e. PDF) from URL to a ByteBuffer. This is later
     * appended to the applet, but needs a renderer capable of printing it to
     * PostScript
     * @param file
     * @return
     * @throws IOException
     * @throws MalformedURLException 
     */
    public static byte[] readBinaryFile(String file) throws IOException, MalformedURLException {
        if (isBase64PDF(file)) {
            return Base64.decode(file.split(",")[1]);
        } else {
            URLConnection con = new URL(file).openConnection();
            InputStream in = con.getInputStream();
            int size = con.getContentLength();

            ByteArrayOutputStream out;
            if (size != -1) {
                out = new ByteArrayOutputStream(size);
            } else {
                 // Pick some appropriate size
                out = new ByteArrayOutputStream(20480);
            }

            byte[] buffer = new byte[512];
            while (true) {
                int len = in.read(buffer);
                if (len == -1) {
                    break;
                }
                out.write(buffer, 0, len);
            }
            in.close();
            out.close();

            return out.toByteArray();
        }
    }



    /**
     * Alternative to getHex(). Not used. Converts a <code>byte[]</code> to a
     * Hexadecimal (base 16) String representation. i.e. { 0x1B, 0x00 } would
     * look like this: "1B00"
     *
     * @param b
     * @return
     * @throws Exception
     */
    /*public static String getHexString(byte[] b) throws Exception {
     String result = "";
     for (int i = 0; i < b.length; i++) {
     result +=
     Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
     }
     return result;
     }*/
}

/*if (offset > 0 && end > 0 && end > offset

    
 ) { 
 //LogIt.log("Start: " + offset + ": " + orig[offset] + ", End: " + end + ": " + orig[end]);
 byte[] printBytes = new byte[end - offset];
 int counter = 0;
 for (int i = offset; i < end; i++) {
 printBytes[counter++] = orig[i];
 }
 doc = new SimpleDoc(printBytes, docFlavor.get(), docAttr.get());
 }

    
 else {
 doc = new SimpleDoc(orig, docFlavor.get(), docAttr.get());
 }*/
