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
package qz.utils;

import qz.common.Base64;
import qz.common.ByteArrayBuilder;
import qz.common.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * Place for all raw static byte conversion functions. Especially useful for
 * converting typed Hex (<code>String</code> base 16) to <code>byte[]</code>,
 * <code>byte[]</code> to Hex (<code>String</code> base 16), <code>byte[]</code>
 * to <code>int[]</code> conversions, etc.
 *
 * @author Tres Finocchiaro
 */
public class ByteUtilities {

    private static final Logger log = Logger.getLogger(ByteUtilities.class.getName());

    /**
     * Converts typed Hex (<code>String</code> base 16) to <code>byte[]</code>.
     * This is especially useful for special characters that are appended via
     * JavaScript, specifically the "\0" or <code>NUL</code> character, which
     * will early terminate a JavaScript <code>String</code>.
     *
     * @param s Base 16 String to covert to byte array
     * @return byte array
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
                    //data[i] = Byte.parseByte(split[i], 16);
                    Integer signedByte = Integer.parseInt(split[i], 16);
                    data[i] = (byte) (signedByte & 0xFF);
                }
            } else if (s.length() == 2) {
                data = new byte[]{Byte.parseByte(s)};
            }
        }
        return data;
    }

    public static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, true);
    }

    public static String bytesToHex(byte[] bytes, boolean upperCase) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = Constants.HEXES_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = Constants.HEXES_ARRAY[v & 0x0F];
        }
        if (upperCase) {
            return new String(hexChars);
        }
        return new String(hexChars).toLowerCase();
    }

    /**
     * Iterates through byte array finding matches of a sublist of bytes.
     * Returns an array of positions. TODO: Make this natively Iterable.
     *
     * @param array byte array to search
     * @param sublist sublist to search for
     * @return indicies to found sublists
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
     * @param src array to split
     * @param pattern pattern to determine where split should occur
     * @param count number of arrays to split data into
     * @return array of byte arrays
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

    // TODO: RKC - Verify method is not needed
    public static byte[] intArrayToByteArray(int ints[]) {
        byte[] bytes = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            bytes[i] = (byte) ints[i];
        }
        return bytes;
    }

    // TODO: RKC - Verify method is not needed
    /**
     * Base 2 array to Base 10 array converter: Takes a binary array (using a
     * boolean array for storage) and converts every 8 rows to a full byte,
     * keeping the value in decimal (<code>int</code>). Returns an array of
     * integers (<code>int[]</code>). These integers will likely be converted to
     * a byte array (<code>byte[]</code>) later.
     *
     * @param black boolean values to convert
     * @return number array in base 10
     */
    public static int[] binaryArrayToIntArray(boolean[] black) {
        int[] hex = new int[black.length / 8];
        // Convert every eight zero's to a full byte, in decimal
        for (int i = 0; i < hex.length; i++) {
            for (int k = 0; k < 8; k++) {
                hex[i] += (black[8 * i + k] ? 1 : 0) << 7 - k;
            }
        }
        return hex;
    }

    /**
     * Converts an integer array (<code>int[]</code>) to a String representation
     * of a hexadecimal array.
     * 
     * @param raw numbers to be converted to hex
     * @return hex string representation
     */
    public static String getHexString(int[] raw) {
        if (raw == null) {
            return null;
        }
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final int i : raw) {
            hex.append(Constants.HEXES.charAt((i & 0xF0) >> 4)).append(Constants.HEXES.charAt((i & 0x0F)));
        }
        return hex.toString();
    }

    public static boolean isBlank(Object o) {
        if (o instanceof byte[]) {
            return ((byte[]) o).length < 1;
        } else if (o instanceof String) {
            return "".equals(o);
        } else {
            log.warning("Unchecked blank comparison.");
            return o == null;
        }
    }

    public static boolean isBase64Image(String path) {
        return path.startsWith("data:image/") && path.contains(";base64,");
    }

    private static boolean isBase64PDF(String path) {
        return path.startsWith("data:application/pdf;base64,");
    }

    /**
     * Reads a binary file (i.e. PDF) from URL to a ByteBuffer. This is later
     * appended to the applet, but needs a renderer capable of printing it to
     * PostScript
     * @param file URL string to location of file
     * @return byte array containing file content
     * @throws IOException
     */
    public static byte[] readBinaryFile(String file) throws IOException {
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
                out = new ByteArrayOutputStream(Constants.OUTPUT_STREAM_SIZE);
            }

            byte[] buffer = new byte[Constants.BYTE_BUFFER_SIZE];
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
}
