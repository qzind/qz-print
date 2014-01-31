/*
 * $Id: PDFFile.java,v 1.19 2010-05-23 22:07:05 lujke Exp $
 *
 * Copyright 2004 Sun Microsystems, Inc., 4150 Network Circle,
 * Santa Clara, California 95054, U.S.A. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package com.sun.pdfview;

import java.awt.geom.Rectangle2D;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import com.sun.pdfview.action.*;
import com.sun.pdfview.decode.PDFDecoder;
import com.sun.pdfview.decrypt.*;

/**
 * An encapsulation of a .pdf file.  The methods of this class
 * can parse the contents of a PDF file, but those methods are
 * hidden.  Instead, the public methods of this class allow
 * access to the pages in the PDF file.  Typically, you create
 * a new PDFFile, ask it for the number of pages, and then
 * request one or more PDFPages.
 * @author Mike Wessler
 */
public class PDFFile {

    public final static int             NUL_CHAR = 0;
    public final static int             FF_CHAR = 12;

    private String versionString = "1.1";
    private int majorVersion = 1;
    private int minorVersion = 1;
    /** the end of line character */
    /** the comment text to begin the file to determine it's version */
    private final static String VERSION_COMMENT = "%PDF-";
    /**
     * A ByteBuffer containing the file data
     */
    ByteBuffer fileBuf;
    /**
     * the cross reference table mapping object numbers to locations
     * in the PDF file
     */
    PDFXrefEntry[] xrefEntries;
    /** the root PDFObject, as specified in the PDF file */
    PDFObject root = null;
    /** the Encrypt PDFObject, from the trailer */
    PDFObject encrypt = null;

    /** The Info PDFPbject, from the trailer, for simple metadata */
    PDFObject info = null;

    /** a mapping of page numbers to parsed PDF commands */
    Cache cache;
    /**
     * whether the file is printable or not (trailer -> Encrypt -> P & 0x4)
     */
    private boolean printable = true;
    /**
     * whether the file is saveable or not (trailer -> Encrypt -> P & 0x10)
     */
    private boolean saveable = true;

    /**
     * The default decrypter for streams and strings. By default, no
     * encryption is expected, and thus the IdentityDecrypter is used.
     */
    private PDFDecrypter defaultDecrypter = IdentityDecrypter.getInstance();

    /**
     * The file identifier, as found in a trailer/xref stream dictionary,
     * and as used for encryption
     */
    private PDFObject fileIdentifier = null;

    /**
     * get a PDFFile from a .pdf file.  The file must me a random access file
     * at the moment.  It should really be a file mapping from the nio package.
     * <p>
     * Use the getPage(...) methods to get a page from the PDF file.
     * @param buf the RandomAccessFile containing the PDF.
     * @throws IOException if there's a problem reading from the buffer
     * @throws PDFParseException if the document appears to be malformed, or
     *  its features are unsupported. If the file is encrypted in a manner that
     *  the product or platform does not support then the exception's {@link
     *  PDFParseException#getCause() cause} will be an instance of {@link
     *  UnsupportedEncryptionException}.
     * @throws PDFAuthenticationFailureException if the file is password
     *  protected and requires a password
     */
    public PDFFile(ByteBuffer buf) throws IOException {
	this(buf, null);
    }

    /**
     * get a PDFFile from a .pdf file.  The file must me a random access file
     * at the moment.  It should really be a file mapping from the nio package.
     * <p>
     * Use the getPage(...) methods to get a page from the PDF file.
     * @param buf the RandomAccessFile containing the PDF.
     * @param password the user or owner password
     * @throws IOException if there's a problem reading from the buffer
     * @throws PDFParseException if the document appears to be malformed, or
     *  its features are unsupported. If the file is encrypted in a manner that
     *  the product or platform does not support then the exception's {@link
     *  PDFParseException#getCause() cause} will be an instance of {@link
     *  UnsupportedEncryptionException}.
     * @throws PDFAuthenticationFailureException if the file is password
     *  protected and the supplied password does not decrypt the document
     */
    public PDFFile(ByteBuffer buf, PDFPassword password) throws IOException {
        this.fileBuf = buf;

        cache = new Cache();

        parseFile(password);
    }

    /**
     * Gets whether the owner of the file has given permission to print
     * the file.
     * @return true if it is okay to print the file
     */
    public boolean isPrintable() {
        return printable;
    }

    /**
     * Gets whether the owner of the file has given permission to save
     * a copy of the file.
     * @return true if it is okay to save the file
     */
    public boolean isSaveable() {
        return saveable;
    }

    /**
     * get the root PDFObject of this PDFFile.  You generally shouldn't need
     * this, but we've left it open in case you want to go spelunking.
     */
    public PDFObject getRoot() {
        return root;
    }

    /**
     * return the number of pages in this PDFFile.  The pages will be
     * numbered from 1 to getNumPages(), inclusive.
     */
    public int getNumPages() {
        try {
            return root.getDictRef("Pages").getDictRef("Count").getIntValue();
        } catch (Exception ioe) {
            return 0;
        }
    }

    /**
     * Get metadata (e.g., Author, Title, Creator) from the Info dictionary
     * as a string.
     * @param name the name of the metadata key (e.g., Author)
     * @return the info
     * @throws IOException if the metadata cannot be read
     */
    public String getStringMetadata(String name)
            throws IOException {
        if (info != null) {
            final PDFObject meta = info.getDictRef(name);
            return meta != null ? meta.getTextStringValue() : null;
        } else {
            return null;
        }
    }

    /**
     * Get the keys into the Info metadata, for use with
     * {@link #getStringMetadata(String)}
     * @return the keys present into the Info dictionary
     * @throws IOException if the keys cannot be read
     */
    public Iterator<String> getMetadataKeys()
            throws IOException {
        if (info != null) {
            return info.getDictKeys();
        } else {
            return Collections.<String>emptyList().iterator();
        }
    }


    /**
     * Used internally to track down PDFObject references.  You should never
     * need to call this.
     * <p>
     * Since this is the only public method for tracking down PDF objects,
     * it is synchronized.  This means that the PDFFile can only hunt down
     * one object at a time, preventing the file's location from getting
     * messed around.
     * <p>
     * This call stores the current buffer position before any changes are made
     * and restores it afterwards, so callers need not know that the position
     * has changed.
     *
     */
    public synchronized PDFObject dereference(PDFXref ref, PDFDecrypter decrypter)
            throws IOException {
        int id = ref.getObjectNumber();

        // make sure the id is valid and has been read
        if (id >= xrefEntries.length || id < 0) {
            return PDFObject.nullObj;
        }

        // if there is an entry, make sure that it can resolve to the
        // requested generation number and that it's not a free entry; if
        // so, we should return the null object
        final PDFXrefEntry entry = xrefEntries[id];
        if (entry == null || !entry.resolves(ref)) {
            return PDFObject.nullObj;
        }

        // check to see if this is already dereferenced
        PDFObject obj = entry.getObject();
        if (obj != null) {
            return obj;
        }

        switch (entry.getType()) {
            case OBJ_IN_BODY:

                int loc = entry.getOffset();
                if (loc < 0) {
                    return PDFObject.nullObj;
                }

                // store the current position in the buffer
                int startPos = fileBuf.position();

                // move to where this object is
                fileBuf.position(loc);

                // read the object and cache the reference
                obj= readObject(fileBuf, ref.getObjectNumber(), ref.getGeneration(), decrypter);
                if (obj == null) {
                    obj = PDFObject.nullObj;
                }

                entry.setObject(obj);

                // reset to the previous position
                fileBuf.position(startPos);

                return obj;

            case OBJ_IN_STREAM:

                final PDFObject stream =
                        dereference(entry.getStream(), getDefaultDecrypter());
                if (stream == null || stream.getType() != PDFObject.STREAM || !"ObjStm".equals(stream.getDictRef("Type").getStringValue())) {
                    throw new PDFParseException(entry.getStream().getObjectNumber() +
                            " is not an object stream, but was referenced in " +
                            "the xref stream as one");
                }

                final ByteBuffer streamBuf = stream.getStreamBuffer();

                final PDFXrefEntry streamSourceEntry = xrefEntries[entry.getStream().getObjectNumber()];
                int[] offsets = streamSourceEntry.getObjectIndexOffsets();
                if (offsets == null) {
                    offsets = new int[stream.getDictionary().get("N").getIntValue()];
                    int first = stream.getDictionary().get("First").getIntValue();
                    for (int i = 0; i < offsets.length; ++i) {
                        // we don't need the object number
                        final PDFObject objNum =
                                readObject(streamBuf, -1, -1,
                                        IdentityDecrypter.getInstance());
                        // add in the initial offset represented by First here
                        offsets[i] = first +
                                readObject(streamBuf, -1, -1,
                                        IdentityDecrypter.getInstance()).
                                        getIntValue();
                    }
                    streamSourceEntry.setObjectIndexOffsets(offsets);
                }

                if (entry.getOffset() < 0 || entry.getOffset() >= offsets.length) {
                    throw new PDFParseException("Xref references index that does not exist in stream");
                }

                streamBuf.position(offsets[entry.getOffset()]);
                // According to the PDF spec:
                //  "Any strings that are inside streams such as content streams
                //  and compressed object streams, which themselves are
                //  encrypted"
                // So, we figure out whether the containing stream was
                // encrypted or not; unfortunately, we don't have this
                // cached anywhere. If the stream was encrypted, we make
                // sure we don't attempt to decrypt any strings within.
                obj= readObject(streamBuf, ref.getObjectNumber(), ref.getGeneration(),
                        PDFDecoder.isEncrypted(stream) ?
                                IdentityDecrypter.getInstance() :
                                getDefaultDecrypter());
                if (obj == null) {
                    obj = PDFObject.nullObj;
                }

                entry.setObject(obj);
                return obj;

            case FREE:
                // this case should in practice be covered by the
                // call to entry.resolves() above
                return PDFObject.nullObj;

            default:
                throw new UnsupportedOperationException(
                        "Don't know how to handle xref type " +
                                entry.getType());
        }

    }

    /**
     * Is the argument a white space character according to the PDF spec?.
     * ISO Spec 32000-1:2008 - Table 1
     */
    public static boolean isWhiteSpace(int c) {
        switch (c) {
            case NUL_CHAR:  // Null (NULL)
            case '\t':      // Horizontal Tab (HT)
            case '\n':      // Line Feed (LF)
            case FF_CHAR:   // Form Feed (FF)
            case '\r':      // Carriage Return (CR)
            case ' ':       // Space (SP)
                return true;
            default:
                return false;
        }
    }

    /**
     * Is the argument a delimiter according to the PDF spec?<p>
     *
     * ISO 32000-1:2008 - Table 2
     *
     * @param c the character to test
     */
    public static boolean isDelimiter(int c) {
        switch (c) {
            case '(':   // LEFT PARENTHESIS
            case ')':   // RIGHT PARENTHESIS
            case '<':   // LESS-THAN-SIGN
            case '>':   // GREATER-THAN-SIGN
            case '[':   // LEFT SQUARE BRACKET
            case ']':   // RIGHT SQUARE BRACKET
            case '{':   // LEFT CURLY BRACKET
            case '}':   // RIGHT CURLY BRACKET
            case '/':   // SOLIDUS
            case '%':   // PERCENT SIGN
                return true;
            default:
                return false;
        }
    }

    /**
     * return true if the character is neither a whitespace or a delimiter.
     *
     * @param c the character to test
     * @return boolean
     */
    public static boolean isRegularCharacter (int c) {
        return !(isWhiteSpace(c) || isDelimiter(c));
    }

    /**
     * read the next object from the file
     * @param buf the buffer to read from
     * @param objNum the object number of the object containing the object
 *  being read; negative only if the object number is unavailable (e.g., if
 *  reading from the trailer, or reading at the top level, in which
 *  case we can expect to be reading an object description)
     * @param objGen the object generation of the object containing the object
*  being read; negative only if the objNum is unavailable
     * @param decrypter the decrypter to use
     */
    private PDFObject readObject(
            ByteBuffer buf, int objNum, int objGen, PDFDecrypter decrypter) throws IOException {
	return readObject(buf, objNum, objGen, false, decrypter);
    }

    /**
     * read the next object with a special catch for numbers
     * @param buf the buffer to read from
     * @param objNum the object number of the object containing the object
 *  being read; negative only if the object number is unavailable (e.g., if
 *  reading from the trailer, or reading at the top level, in which
 *  case we can expect to be reading an object description)
     * @param objGen the object generation of the object containing the object
*  being read; negative only if the objNum is unavailable
     * @param numscan if true, don't bother trying to see if a number is
*  an object reference (used when already in the middle of testing for
*  an object reference, and not otherwise)
     * @param decrypter the decrypter to use
     */
    private PDFObject readObject(
            ByteBuffer buf, int objNum, int objGen,
            boolean numscan, PDFDecrypter decrypter) throws IOException {
        // skip whitespace
        int c;
        PDFObject obj = null;
        while (obj == null) {
            c = nextNonWhitespaceChar(buf);
            // check character for special punctuation:
            if (c == '<') {
                // could be start of <hex data>, or start of <<dictionary>>
                c = buf.get();
                if (c == '<') {
                    // it's a dictionary
		    obj= readDictionary(buf, objNum, objGen, decrypter);
                } else {
                    buf.position(buf.position() - 1);
		    obj= readHexString(buf, objNum, objGen, decrypter);
                }
            } else if (c == '(') {
		obj= readLiteralString(buf, objNum, objGen, decrypter);
            } else if (c == '[') {
                // it's an array
		obj= readArray(buf, objNum, objGen, decrypter);
            } else if (c == '/') {
                // it's a name
                obj = readName(buf);
            } else if (c == '%') {
                // it's a comment
                readLine(buf);
            } else if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') {
                // it's a number
                obj = readNumber(buf, (char) c);
                if (!numscan) {
                    // It could be the start of a reference.
                    // Check to see if there's another number, then "R".
                    //
                    // We can't use mark/reset, since this could be called
                    // from dereference, which already is using a mark
                    int startPos = buf.position();

		    PDFObject testnum= readObject(buf, -1, -1, true, decrypter);
                    if (testnum != null &&
                            testnum.getType() == PDFObject.NUMBER) {
			PDFObject testR= readObject(buf, -1, -1, true, decrypter);
                        if (testR != null &&
                                testR.getType() == PDFObject.KEYWORD &&
                                testR.getStringValue().equals("R")) {
                            // yup.  it's a reference.
                            PDFXref xref = new PDFXref(obj.getIntValue(),
                                    testnum.getIntValue());
                            // Create a placeholder that will be dereferenced
                            // as needed
                            obj = new PDFObject(this, xref);
                        } else if (testR != null &&
                                testR.getType() == PDFObject.KEYWORD &&
                                testR.getStringValue().equals("obj")) {
                            // it's an object description
			    obj= readObjectDescription(
                                    buf, obj.getIntValue(),
                                    testnum.getIntValue(),
                                    decrypter);
                        } else {
                            buf.position(startPos);
                        }
                    } else {
                        buf.position(startPos);
                    }
                }
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                // it's a keyword
                obj = readKeyword(buf, (char) c);
            } else {
                // it's probably a closing character.
                // throwback
                buf.position(buf.position() - 1);
                break;
            }
        }
        return obj;
    }

    /**
     * Get the next non-white space character
     * @param buf the buffer to read from
     * @return the next non-whitespace character
     */
    private int nextNonWhitespaceChar(ByteBuffer buf) {
        int c;
        while (isWhiteSpace(c = buf.get())) {
            // nothing
        }
        return c;
    }

    /**
     * Consume all sequential whitespace from the current buffer position,
     * leaving the buffer positioned at non-whitespace
     * @param buf the buffer to read from
     */
    private void consumeWhitespace(ByteBuffer buf) {
        nextNonWhitespaceChar(buf);
        buf.position(buf.position() - 1);
    }

    /**
     * requires the next few characters (after whitespace) to match the
     * argument.
     * @param buf the buffer to read from
     * @param match the next few characters after any whitespace that
     * must be in the file
     * @return true if the next characters match; false otherwise.
     */
    private boolean nextItemIs(ByteBuffer buf, String match) throws IOException {
        // skip whitespace
        int c = nextNonWhitespaceChar(buf);
        for (int i = 0; i < match.length(); i++) {
            if (i > 0) {
                c = buf.get();
            }
            if (c != match.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * process a version string, to determine the major and minor versions
     * of the file.
     *
     * @param versionString
     */
    private void processVersion(String versionString) {
        try {
            StringTokenizer tokens = new StringTokenizer(versionString, ".");
            majorVersion = Integer.parseInt(tokens.nextToken());
            minorVersion = Integer.parseInt(tokens.nextToken());
            this.versionString = versionString;
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * return the major version of the PDF header.
     *
     * @return int
     */
    public int getMajorVersion() {
        return majorVersion;
    }

    /**
     * return the minor version of the PDF header.
     *
     * @return int
     */
    public int getMinorVersion() {
        return minorVersion;
    }

    /**
     * return the version string from the PDF header.
     *
     * @return String
     */
    public String getVersionString() {
        return versionString;
    }

    /**
     * read an entire &lt;&lt; dictionary &gt;&gt;.  The initial
     * &lt;&lt; has already been read.
     * @param buf the buffer to read from
     * @param objNum the object number of the object containing the dictionary
     *  being read; negative only if the object number is unavailable, which
     *  should only happen if we're reading a dictionary placed directly
     *  in the trailer
     * @param objGen the object generation of the object containing the object
     *  being read; negative only if the objNum is unavailable
     * @param decrypter the decrypter to use
     * @return the Dictionary as a PDFObject.
     */
    private PDFObject readDictionary(
            ByteBuffer buf, int objNum, int objGen, PDFDecrypter decrypter) throws IOException {
        HashMap<String,PDFObject> hm = new HashMap<String,PDFObject>();
        // we've already read the <<.  Now get /Name obj pairs until >>
        PDFObject name;
	while ((name= readObject(buf, objNum, objGen, decrypter))!=null) {
            // make sure first item is a NAME
            if (name.getType() != PDFObject.NAME) {
                throw new PDFParseException("First item in dictionary must be a /Name.  (Was " + name + ")");
            }
	    PDFObject value= readObject(buf, objNum, objGen, decrypter);
            if (value != null) {
                hm.put(name.getStringValue(), value);
            }
        }
        //	System.out.println("End of dictionary at location "+raf.getFilePointer());
        if (!nextItemIs(buf, ">>")) {
            throw new PDFParseException("End of dictionary wasn't '>>'");
        }
        //	System.out.println("Dictionary closed at location "+raf.getFilePointer());
        return new PDFObject(this, PDFObject.DICTIONARY, hm);
    }

    /**
     * read a character, and return its value as if it were a hexidecimal
     * digit.
     * @return a number between 0 and 15 whose value matches the next
     * hexidecimal character.  Returns -1 if the next character isn't in
     * [0-9a-fA-F]
     * @param buf the buffer to read from
     */
    private int readHexDigit(ByteBuffer buf) throws IOException {
        int a = nextNonWhitespaceChar(buf);
        switch (a) {
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                a -= '0';
                break;
            case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                a -= 'a' - 10;
                break;
            case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
                a -= 'A' - 10;
                break;
            default:
                a = -1;
                break;
        }
        return a;
    }

    /**
     * return the 8-bit value represented by the next two hex characters.
     * If the next two characters don't represent a hex value, return -1
     * and reset the read head.  If there is only one hex character,
     * return its value as if there were an implicit 0 after it.
     * @param buf
     */
    private int readHexPair(ByteBuffer buf) throws IOException {
        int first = readHexDigit(buf);
        if (first < 0) {
            buf.position(buf.position() - 1);
            return -1;
        }
        int second = readHexDigit(buf);
        if (second < 0) {
            buf.position(buf.position() - 1);
            return (first << 4);
        } else {
            return (first << 4) + second;
        }
    }

    /**
     * read a < hex string >.  The initial < has already been read.
     * @param buf the buffer to read from
     * @param objNum the object number of the object containing the dictionary
 *  being read; negative only if the object number is unavailable, which
 *  should only happen if we're reading a string placed directly
 *  in the trailer
     * @param objGen the object generation of the object containing the object
*  being read; negative only if the objNum is unavailable
     * @param decrypter the decrypter to use
     */
    private PDFObject readHexString(
            ByteBuffer buf, int objNum, int objGen, PDFDecrypter decrypter) throws IOException {
        // we've already read the <. Now get the hex bytes until >
        int val;
        StringBuffer sb = new StringBuffer();
        while ((val = readHexPair(buf)) >= 0) {
            sb.append((char) val);
        }
        if (buf.get() != '>') {
            throw new PDFParseException("Bad character in Hex String");
        }
        return new PDFObject(this, PDFObject.STRING,
                decrypter.decryptString(objNum, objGen, sb.toString()));
    }

    /**
     * <p>read a ( character string ).  The initial ( has already been read.
     * Read until a *balanced* ) appears.</p>
     *
     * <p>Section 3.2.3 of PDF Refernce version 1.7 defines the format of
     * String objects. Regarding literal strings:</p>
     *
     * <blockquote>Within a literal string, the backslash (\) is used as an
     * escape character for various purposes, such as to include newline
     * characters, nonprinting ASCII characters, unbalanced parentheses, or
     * the backslash character itself in the string. The character
     * immediately following the backslash determines its precise
     * interpretation (see Table 3.2). If the character following the
     * backslash is not one of those shown in the table, the backslash
     * is ignored.</blockquote>
     *
     * * <p>This only reads 8 bit basic character 'strings' so as to avoid a
     * text string interpretation when one is not desired (e.g., for byte
     * strings, as used by the decryption mechanism). For an interpretation of
     * a string returned from this method, where the object type is defined
     * as a 'text string' as per Section 3.8.1, Table 3.31 "PDF Data Types",
     * {@link PDFStringUtil#asTextString} ()} or
     * {@link PDFObject#getTextStringValue()} must be employed.</p>
     *
     * @param buf the buffer to read from
     * @param objNum the object number of the object containing the dictionary
 *  being read; negative only if the object number is unavailable, which
 *  should only happen if we're reading a dictionary placed directly
 *  in the trailer
     * @param objGen the object generation of the object containing the object
*  being read; negative only if the objNum is unavailable
     * @param decrypter the decrypter to use
     */
    private PDFObject readLiteralString(
            ByteBuffer buf, int objNum, int objGen, PDFDecrypter decrypter) throws IOException {
        int c;

        // we've already read the (.  now get the characters until a
        // *balanced* ) appears.  Translate \r \n \t \b \f \( \) \\ \ddd
        // if a cr/lf follows a backslash, ignore the cr/lf
        int parencount = 1;
        StringBuffer sb = new StringBuffer();

        while (parencount > 0) {
            c = buf.get() & 0xFF;
            // process unescaped parenthesis
            if (c == '(') {
                parencount++;
            } else if (c == ')') {
                parencount--;
                if (parencount == 0) {
                    c = -1;
                    break;
                }
            } else if (c == '\\') {

                // From the spec:
                // Within a literal string, the backslash (\) is used as an
                // escape character for various purposes, such as to include
                // newline characters, nonprinting ASCII characters,
                // unbalanced parentheses, or the backslash character itself
                // in the string. The character immediately following the
                // backslash determines its precise interpretation (see
                // Table 3.2). If the character following the backslash is not
                // one of those shown in the table, the backslash is ignored.
                //
                // summary of rules:
                //
                // \n \r \t \b \f 2-char sequences are used to represent their
                //  1-char counterparts
                //
                // \( and \) are used to escape parenthesis
                //
                // \\ for a literal backslash
                //
                // \ddd (1-3 octal digits) for a character code
                //
                //  \<EOL> is used to put formatting newlines into the
                //  file, but aren't actually part of the string; EOL may be
                //  CR, LF or CRLF
                //
                // any other sequence should see the backslash ignored

                // grab the next character to see what we're dealing with
                c = buf.get() & 0xFF;
                if (c >= '0' && c < '8') {
                    // \ddd form - one to three OCTAL digits
                    int count = 0;
                    int val = 0;
                    while (c >= '0' && c < '8' && count < 3) {
                        val = val * 8 + c - '0';
                        c = buf.get() & 0xFF;
                        count++;
                    }
                    // we'll have read one character too many
                    buf.position(buf.position() - 1);
                    c = val;
                } else if (c == 'n') {
                    c = '\n';
                } else if (c == 'r') {
                    c = '\r';
                } else if (c == 't') {
                    c = '\t';
                } else if (c == 'b') {
                    c = '\b';
                } else if (c == 'f') {
                    c = '\f';
                } else if (c == '\r') {
                    // escaped CR to be ignored; look for a following LF
                    c = buf.get() & 0xFF;
                    if (c != '\n') {
                        // not an LF, we'll consume this character on
                        // the next iteration
                        buf.position(buf.position() - 1);
                    }
                    c = -1;
                } else if (c == '\n') {
                    // escaped LF to be ignored
                    c = -1;
                }
                // any other c should be used as is, as it's either
                // one of ()\ in which case it should be used literally,
                // or the backslash should just be ignored
            }
            if (c >= 0) {
                sb.append((char) c);
            }
        }
        return new PDFObject(this, PDFObject.STRING,
                decrypter.decryptString(objNum, objGen, sb.toString()));
    }

    /**
     * Read a line of text.  This follows the semantics of readLine() in
     * DataInput -- it reads character by character until a '\n' is
     * encountered.  If a '\r' is encountered, it is discarded.
     * @param buf the buffer to read from
     */
    private String readLine(ByteBuffer buf) {
        StringBuffer sb = new StringBuffer();

        while (buf.remaining() > 0) {
            char c = (char) buf.get();

            if (c == '\r') {
                if (buf.remaining() > 0) {
                    char n = (char) buf.get(buf.position());
                    if (n == '\n') {
                        buf.get();
                    }
                }
                break;
            } else if (c == '\n') {
                break;
            }

            sb.append(c);
        }

        return sb.toString();
    }

    /**
     * read an [ array ].  The initial [ has already been read.  PDFObjects
     * are read until ].
     * @param buf the buffer to read from
     * @param objNum the object number of the object containing the dictionary
 *  being read; negative only if the object number is unavailable, which
 *  should only happen if we're reading an array placed directly
 *  in the trailer
     * @param objGen the object generation of the object containing the object
*  being read; negative only if the objNum is unavailable
     * @param decrypter the decrypter to use
     */
    private PDFObject readArray(
            ByteBuffer buf, int objNum, int objGen, PDFDecrypter decrypter) throws IOException {
        // we've already read the [.  Now read objects until ]
        ArrayList<PDFObject> ary = new ArrayList<PDFObject>();
        PDFObject obj;
	while((obj= readObject(buf, objNum, objGen, decrypter))!=null) {
            ary.add(obj);
        }
        if (buf.get() != ']') {
            throw new PDFParseException("Array should end with ']'");
        }
        PDFObject[] objlist = new PDFObject[ary.size()];
        for (int i = 0; i < objlist.length; i++) {
            objlist[i] = (PDFObject) ary.get(i);
        }
        return new PDFObject(this, PDFObject.ARRAY, objlist);
    }

    /**
     * read a /name.  The / has already been read.
     * @param buf the buffer to read from
     */
    private PDFObject readName(ByteBuffer buf) throws IOException {
        // we've already read the / that begins the name.
        // all we have to check for is #hh hex notations.
        StringBuffer sb = new StringBuffer();
        int c;
        while (isRegularCharacter(c = buf.get())) {
            if (c < '!' && c > '~') {
                break;      // out-of-range, should have been hex
            }
            // H.3.2.4 indicates version 1.1 did not do hex escapes
            if (c == '#' && (majorVersion != 1 && minorVersion != 1)) {
                int hex = readHexPair(buf);
                if (hex >= 0) {
                    c = hex;
                } else {
                    throw new PDFParseException("Bad #hex in /Name");
                }
            }
            sb.append((char) c);
        }
        buf.position(buf.position() - 1);
        return new PDFObject(this, PDFObject.NAME, sb.toString());
    }

    /**
     * read a number.  The initial digit or . or - is passed in as the
     * argument.
     */
    private PDFObject readNumber(ByteBuffer buf, char start) throws IOException {
        // we've read the first digit (it's passed in as the argument)
        boolean neg = start == '-';
        boolean sawdot = start == '.';
        double dotmult = sawdot ? 0.1 : 1;
        double value = (start >= '0' && start <= '9') ? start - '0' : 0;
        while (true) {
            int c = buf.get();
            if (c == '.') {
                if (sawdot) {
                    throw new PDFParseException("Can't have two '.' in a number");
                }
                sawdot = true;
                dotmult = 0.1;
            } else if (c >= '0' && c <= '9') {
                int val = c - '0';
                if (sawdot) {
                    value += val * dotmult;
                    dotmult *= 0.1;
                } else {
                    value = value * 10 + val;
                }
            } else {
                buf.position(buf.position() - 1);
                break;
            }
        }
        if (neg) {
            value = -value;
        }
        return new PDFObject(this, PDFObject.NUMBER, new Double(value));
    }

    /**
     * read a bare keyword.  The initial character is passed in as the
     * argument.
     */
    private PDFObject readKeyword(ByteBuffer buf, char start) throws IOException {
        // we've read the first character (it's passed in as the argument)
        StringBuffer sb = new StringBuffer(String.valueOf(start));
        int c;
        while (isRegularCharacter(c = buf.get())) {
            sb.append((char) c);
        }
        buf.position(buf.position() - 1);
        return new PDFObject(this, PDFObject.KEYWORD, sb.toString());
    }

    /**
     * read an entire PDFObject.  The intro line, which looks something
     * like "4 0 obj" has already been read.
     * @param buf the buffer to read from
     * @param objNum the object number of the object being read, being
 *  the first number in the intro line (4 in "4 0 obj")
     * @param objGen the object generation of the object being read, being
*  the second number in the intro line (0 in "4 0 obj").
     * @param decrypter the decrypter to use
     */
    private PDFObject readObjectDescription(
            ByteBuffer buf, int objNum, int objGen, PDFDecrypter decrypter) throws IOException {
        // we've already read the 4 0 obj bit.  Next thing up is the object.
        // object descriptions end with the keyword endobj
        long debugpos = buf.position();
	PDFObject obj= readObject(buf, objNum, objGen, decrypter);
        // see if it's a dictionary.  If so, this could be a stream.
	PDFObject endkey= readObject(buf, objNum, objGen, decrypter);
        if (endkey.getType() != PDFObject.KEYWORD) {
            throw new PDFParseException("Expected 'stream' or 'endobj'");
        }
        if (obj.getType() == PDFObject.DICTIONARY && endkey.getStringValue().equals("stream")) {
            // skip until we see \n
            readLine(buf);
            ByteBuffer data = readStream(buf, obj);
            if (data == null) {
                data = ByteBuffer.allocate(0);
            }
            obj.setStream(data);
	    endkey= readObject(buf, objNum, objGen, decrypter);
        }
        // at this point, obj is the object, keyword should be "endobj"
        String endcheck = endkey.getStringValue();
        if (endcheck == null || !endcheck.equals("endobj")) {
            System.out.println("WARNING: object at " + debugpos + " didn't end with 'endobj'");
        //throw new PDFParseException("Object musst end with 'endobj'");
        }
        obj.setObjectId(objNum, objGen);
        return obj;
    }

    /**
     * read the stream portion of a PDFObject.  Calls decodeStream to
     * un-filter the stream as necessary.
     *
     * @param buf the buffer to read from
     * @param dict the dictionary associated with this stream.
     * @return a ByteBuffer with the encoded stream data
     */
    private ByteBuffer readStream(ByteBuffer buf, PDFObject dict) throws IOException {
        // pointer is at the start of a stream.  read the stream and
        // decode, based on the entries in the dictionary
        PDFObject lengthObj = dict.getDictRef("Length");
        int length = -1;
        if (lengthObj != null) {
            length = lengthObj.getIntValue();
        }
        if (length < 0) {
            throw new PDFParseException("Unknown length for stream");
        }

        // slice the data
        int start = buf.position();
        ByteBuffer streamBuf = buf.slice();
        streamBuf.limit(length);

        // move the current position to the end of the data
        buf.position(buf.position() + length);
        int ending = buf.position();

        if (!nextItemIs(buf, "endstream")) {
            System.out.println("read " + length + " chars from " + start + " to " +
                    ending);
            throw new PDFParseException("Stream ended inappropriately");
        }

        return streamBuf;
    // now decode stream
    // return PDFDecoder.decodeStream(dict, streamBuf);
    }

    /**
     * read the cross reference table from a PDF file.  When this method
     * is called, the file pointer must point to the start of an xref table
     * (i.e., to the start of the "xref" keyword) or an xref stream object.
     * Reads the xref entries and populate xrefEntries. Also reads the
     * trailer/xref stream dictionary to set root, fileIdentifier and encryption
     * parameters. If /Prev entries are present, proceeds to read previous
     * trailers and xrefs, too.
     * @param password the password to use for decryption
     */
    private void readTrailersAndXrefs(PDFPassword password)
            throws
            IOException,
            PDFAuthenticationFailureException,
            EncryptionUnsupportedByProductException,
            EncryptionUnsupportedByPlatformException {
        // the table of xrefs


        // read a bunch of nested trailer tables
        boolean furtherCrossrefsToRead = true;
        while (furtherCrossrefsToRead) {

            PDFObject header =
                    readObject(fileBuf, -1, -1, IdentityDecrypter.getInstance());

            if (header.getType() == PDFObject.KEYWORD &&
                    "xref".equals(header.getStringValue())) {
                furtherCrossrefsToRead = readCrossrefTableAndTrailer(password);
            } else if (isXrefStream(header)) {
                furtherCrossrefsToRead = readCrossrefStream(header, true);
            } else {
                throw new PDFParseException(
                        "Expected xref table or xref stream, but found " +
                                header);
            }

        }

        // make sure we found a root
        if (root == null) {
            throw new PDFParseException("No /Root key found in trailer dictionary");
        }

        if (root.getDictRef("Version") != null) {
            processVersion(root.getDictRef("Version").getStringValue());
        }

        // check what permissions are relevant
        if (encrypt != null) {
            defaultDecrypter =
                    PDFDecrypterFactory.createDecryptor(
                            encrypt,
                            fileIdentifier,
                            password);
            PDFObject permissions = encrypt.getDictRef("P");
            if (permissions!=null && !defaultDecrypter.isOwnerAuthorised()) {
                int perms= permissions != null ? permissions.getIntValue() : 0;
                if (permissions!=null) {
                    printable = (perms & 4) != 0;
                    saveable = (perms & 16) != 0;
                }
            }
        }

        // dereference the root object
        root.dereference();
    }

    /**
     * Identify whether a given PDFObject identifies itself as a crossreference
     * stream
     * @param header the object to test
     * @return whether the object is an xref stream
     * @throws IOException if there's a problem reading the header
     */
    private boolean isXrefStream(PDFObject header) throws IOException {
        return header.getType() == PDFObject.STREAM &&
                "XRef".equals(header.getDictRef("Type").getStringValue());
    }

    /**
     * Read entries from a xref table, and its trailer dictionary, which
     * is expected to follow it
     * @param password the password
     * @return whether a previous crossref table/stream should be read; the
     * buffer will have been positioned at its start point
     * @throws IOException in case of a bad format, or IO problems
     */
    private boolean readCrossrefTableAndTrailer(PDFPassword password) throws IOException {

        // we're positioned at the start of a cross reference table
        PDFObject headerObject;
        while (true) {
            // read until the word "trailer"

            headerObject = readObject(fileBuf, -1, -1, IdentityDecrypter.getInstance());
            if (headerObject.getType() != PDFObject.NUMBER) {
                // we must be out of the cross-ref table!
                break;
            }

            // each subsection will start with
            //   <first obj number>
            //   <number of entries in subsection>
            // read them now:
            int objNumStart = headerObject.getIntValue();

            // read the size of the reference table
            PDFObject sizeObj =
                    readObject(fileBuf, -1, -1, IdentityDecrypter.getInstance());
            if (sizeObj.getType() != PDFObject.NUMBER) {
                throw new PDFParseException("Expected number for length of xref table");
            }
            int numEntries = sizeObj.getIntValue();

            final int lastObjNum = objNumStart + numEntries;
            ensureXrefEntriesCapacity(lastObjNum + 1);
            

            consumeWhitespace(fileBuf);

            // read entry lines

            final byte[] refline = new byte[20];
            for (int objNum = objNumStart; objNum < lastObjNum; objNum++) {
                // each reference line is 20 bytes long
                fileBuf.get(refline);

                // if xrefEntries already contains an entry for this
                // object number then we've earlier read a xref
                // for this object number from a later incremental
                // upgrade
                if (xrefEntries[objNum] == null) {
                    PDFXrefEntry entry;
                    final byte entryType = refline[17];
                    if (entryType == 'n') {
                        // active entry
                        int offset = Integer.parseInt(new String(refline, 0, 10));
                        int generation = Integer.parseInt(new String(refline, 11, 5));
                        final PDFXref ref = new PDFXref(objNum, generation);
                        entry = PDFXrefEntry.toBodyObject(generation, offset);
                    } else if (entryType == 'f') {
                        // freed entry
                        entry = PDFXrefEntry.forFreedObject();
                    } else {
                        throw new PDFParseException("Unknown xref entry type: "
                                + entryType);
                    }
                    xrefEntries[objNum] = entry;
                }
            }
        }

        // at this point, the "trailer" word (not EOL) has been read, hopefully!
        if (headerObject.getType() != PDFObject.KEYWORD ||
                !"trailer".equals(headerObject.getStringValue())) {
            throw new PDFParseException(
                    "Expected to find trailer immediately after xref table, " +
                            "but found " + headerObject + " instead");
        }

        PDFObject trailerdict = readObject(fileBuf, -1, -1, IdentityDecrypter.getInstance());
        if (trailerdict.getType() != PDFObject.DICTIONARY) {
            throw new PDFParseException("Expected dictionary after \"trailer\"");
        }

        return processTrailerDict(trailerdict, false, true);


    }

    /**
     * Process a trailer or xref stream dictionary, recording root, info,
     * encrypt and fileIdentifier members as appropriate. If a Prev entry
     * is found, and followPrev is true, the buffer position is set to the
     * location of a further xref table/stream to read
     * @param trailerdict the trailer/xref-stream dictionary
     * @param xrefStreamSource if the trailer comes from an xref stream, as
     *  opposed to an xref table
     * @param followPrev whether Prev entries should be followed
     * @return whether followPrev was set and a Prev entry was found, indicating
     * that the buffer is now positioned to have another xref stream/table read
     * @throws IOException if the file is badly formed, or in case of IO
     * difficulties
     */
    private boolean processTrailerDict(
            PDFObject trailerdict,
            boolean xrefStreamSource,
            boolean followPrev) throws IOException {

        // read the root object location
        if (root == null) {
            root = trailerdict.getDictRef("Root");
            if (root != null) {
                root.setObjectId(PDFObject.OBJ_NUM_TRAILER,
                        PDFObject.OBJ_NUM_TRAILER);
            }
        }

        if (fileIdentifier == null) {
            fileIdentifier = trailerdict.getDictRef("ID");
        }

        // read the encryption information
        if (encrypt == null) {
            encrypt = trailerdict.getDictRef("Encrypt");
            if (encrypt != null) {
                encrypt.setObjectId(PDFObject.OBJ_NUM_TRAILER,
                        PDFObject.OBJ_NUM_TRAILER);
            }
        }


        if (info == null) {
            info = trailerdict.getDictRef("Info");
            if (info != null) {
                if (!info.isIndirect()) {
                    throw new PDFParseException(
                            "Info in trailer must be an indirect reference");
                }
                info.setObjectId(PDFObject.OBJ_NUM_TRAILER,
                        PDFObject.OBJ_NUM_TRAILER);
            }
        }

        if (!xrefStreamSource) {
            PDFObject xrefStm = trailerdict.getDictRef("XRefStm");
            if (xrefStm != null) {
                // this is a hybrid reference file, read the
                // cross-reference stream before any Prevs
                fileBuf.position(xrefStm.getIntValue());
                readCrossrefStream(null, false);
            }
        }

        PDFObject prevloc = null;
        if (followPrev) {
            // read the location of the previous xref table
            prevloc = trailerdict.getDictRef("Prev");
            if (prevloc != null) {
                fileBuf.position(prevloc.getIntValue());
            }
        }

        return prevloc != null;
    }

    /**
     * Read a Cross Reference Stream from the document
     * @param xrefStream the xrefStream; if <code>null</code>, the stream
     *  will be read from the current fileBuf position
     * @param followPrev if the Prev entry from the dictionary should be
     *  followed to read a previous xref stream
     * @return whether a Prev reference has been found and should be followed,
     *  in which case fileBuf will have been positioned to the start of the
     *  prev xref table/stream
     * @throws IOException if the PDF is poorly formed
     */
    private boolean readCrossrefStream(PDFObject xrefStream, boolean followPrev) throws IOException {

        // the xref stream will have an object number but since there's no
        // decryption involved, it doesn't matter
        if (xrefStream == null) {
            xrefStream = readObject(fileBuf, -1, -1, IdentityDecrypter.getInstance());
            if (!isXrefStream(xrefStream)) {
                throw new PDFParseException("Object found at offset for cross" +
                        " reference stream is not a cross reference stream");
            }
        }


        final int size = xrefStream.getDictRef("Size").getIntValue();
        ensureXrefEntriesCapacity(size);
        final PDFObject[] wObjs = xrefStream.getDictRef("W").getArray();
        final int[] fieldLengths = new int[3];
        int entryLength = 0;
        for (int i = 0; i < 3; ++i) {
            fieldLengths[i] = wObjs[i].getIntValue();
            entryLength += fieldLengths[i];
        }

        final PDFObject[] index;
        final PDFObject indexObj = xrefStream.getDictRef("Index");
        if (indexObj != null) {
            index = indexObj.getArray();
        } else {
            index = new PDFObject[] {
                    new PDFObject(0),
                    new PDFObject(size)
            };             
        }

        final ByteBuffer table = xrefStream.getStreamBuffer();
        for (int i = 0; i < index.length; i += 2) {
            final int start = index[i].getIntValue();
            final int end = start + index[i + 1].getIntValue();
            for (int objNum = start; objNum < end; ++objNum) {
                if (xrefEntries[objNum] == null) {
                    PDFXrefEntry.Type type;
                    if (fieldLengths[0] == 0) {
                        type = PDFXrefEntry.Type.OBJ_IN_BODY;
                    } else {
                        type = PDFXrefEntry.Type.forTypeField(
                                readInt(table, fieldLengths[0]));
                    }
                    int field2  = readInt(table, fieldLengths[1]);
                    // note that this is supposed to default to 0 if field 3
                    // length is 0 for type 1 entries, and that will work just fine
                    int field3  = readInt(table, fieldLengths[2]);
                    xrefEntries[objNum] =
                            type.makeXrefStreamEntry(field2, field3);
                } else {
                    table.position(table.position() + entryLength);
                }
            }
        }
        
        return processTrailerDict(xrefStream, true, followPrev);

    }

    /**
     * Read an numBytes-bytes big-endian unsigned int from a table
     * @param table the table to read from
     * @param numBytes the number of bytes to read
     * @return the integer read; 0 if numBytes is 0
     */
    private int readInt(ByteBuffer table, int numBytes) {
        int val = 0;
        while (numBytes-- > 0) {
            final int b = table.get() & 0xFF;
            val = (val << 8) | b;
        }
        return val;
    }

    /**
     * Ensure that the xrefEntries table will support a given number of
     * objects from 0-size. If we were to read the Size entry from a
     * cross reference table before processing cross reference tables
     * then we could immediately set it to the correct size, but until we
     * do so, we'll just have to resize every now and then, though for
     * most documents, no resizes should be required.
     * @param size the required size of the xref table (i.e., the maximum
     *  object number plus 1)
     */
    private void ensureXrefEntriesCapacity(int size) {
        if (xrefEntries == null || xrefEntries.length < size) {
            final PDFXrefEntry[] newXrefEntries = new PDFXrefEntry[size];
            if (xrefEntries != null) {
                System.arraycopy(xrefEntries, 0, newXrefEntries, 0, xrefEntries.length);
            }
            xrefEntries = newXrefEntries;
        }

    }

    /**
     * build the PDFFile reference table.  Nothing in the PDFFile actually
     * gets parsed, despite the name of this function.  Things only get
     * read and parsed when they're needed.
     * @param password
     */
    private void parseFile(PDFPassword password) throws IOException {
        // start at the begining of the file
        fileBuf.rewind();
        String versionLine = readLine(fileBuf);
        if (versionLine.startsWith(VERSION_COMMENT)) {
            processVersion(versionLine.substring(VERSION_COMMENT.length()));
        }
        fileBuf.rewind();

        fileBuf.position(fileBuf.limit() - 1);
        if (!backscan(fileBuf, "startxref")) {
            throw new PDFParseException("This may not be a PDF File");
        }
        int postStartXrefPos = fileBuf.position();


        // ensure that we've got at least one piece of whitespace here, which
        // should be a carriage return
        if (!isWhiteSpace(fileBuf.get())) {
            throw new PDFParseException("Found suspicious startxref without " +
                    "trialing whitespace");
        }

        final StringBuilder xrefBuf = new StringBuilder();
        char c = (char) nextNonWhitespaceChar(fileBuf);
        while (c >= '0' && c <= '9')  {
            xrefBuf.append(c);
            c = (char) fileBuf.get();
        }

        int xrefpos = Integer.parseInt(xrefBuf.toString());
        fileBuf.position(xrefpos);

        try {
            readTrailersAndXrefs(password);
        } catch (UnsupportedEncryptionException e) {
            throw new PDFParseException(e.getMessage(), e);
        }
    }

    /**
     * Scans backwards from the current buffer position, looking for
     * the given scan token, which must exist in its entirety before
     * the current buffer position. When successful, the buffer position is
     * at the point immediately after the token. If not found, the buffer
     * position will be at 0.
     * @param buf the buffer to scan, positioned appropriately
     * @param scanToken the token to scan for
     * @return whether the token was found
     */
    private boolean backscan(ByteBuffer buf, String scanToken) {

        byte[] scanbuf = new byte[32];
        if (scanToken.length() * 2 > scanbuf.length) {
            // should be fine, though less than optimal, for current usages
            throw new IllegalArgumentException("scanToken is too long - " +
                    "adjust buffer length");
        }

        int scanPos = buf.position() - scanbuf.length;
        if (scanPos < 0) {
            // use a shorter scanbuf to do a single, most likely failing, scan
            scanbuf = new byte[buf.position()];
            scanPos = 0;
        }

        while (scanPos >= 0) {
            buf.position(scanPos);
            buf.get(scanbuf);

            // find startxref in scan
            String scans = new String(scanbuf);
            int loc = scans.lastIndexOf(scanToken);
            if (loc > 0) {
                buf.position(scanPos + loc + scanToken.length());
                return true;
            }

            int newScanPos = scanPos - scanbuf.length + scanToken.length() - 1;
            if (newScanPos < 0) {
                scanPos = scanPos == 0 ? -1 : newScanPos;
            } else {
                scanPos = newScanPos;
            }
        }

        return false;
    }

    /**
     * Gets the outline tree as a tree of OutlineNode, which is a subclass
     * of DefaultMutableTreeNode.  If there is no outline tree, this method
     * returns null.
     */
    public OutlineNode getOutline() throws IOException {
        // find the outlines entry in the root object
        PDFObject oroot = root.getDictRef("Outlines");
        OutlineNode work = null;
        OutlineNode outline = null;
        if (oroot != null) {
            // find the first child of the outline root
            PDFObject scan = oroot.getDictRef("First");
            outline = work = new OutlineNode("<top>");

            // scan each sibling in turn
            while (scan != null) {
                // add the new node with it's name
                String title = scan.getDictRef("Title").getTextStringValue();
                OutlineNode build = new OutlineNode(title);
                work.add(build);

                // find the action
                PDFAction action = null;

                PDFObject actionObj = scan.getDictRef("A");
                if (actionObj != null) {
                    action = PDFAction.getAction(actionObj, getRoot());
                } else {
                    // try to create an action from a destination
                    PDFObject destObj = scan.getDictRef("Dest");
                    if (destObj != null) {
                        try {
                            PDFDestination dest =
                                    PDFDestination.getDestination(destObj, getRoot());

                            action = new GoToAction(dest);
                        } catch (IOException ioe) {
                            // oh well
                        }
                    }
                }

                // did we find an action?  If so, add it
                if (action != null) {
                    build.setAction(action);
                }

                // find the first child of this node
                PDFObject kid = scan.getDictRef("First");
                if (kid != null) {
                    work = build;
                    scan = kid;
                } else {
                    // no child.  Process the next sibling
                    PDFObject next = scan.getDictRef("Next");
                    while (next == null) {
                        scan = scan.getDictRef("Parent");
                        next = scan.getDictRef("Next");
                        work = (OutlineNode) work.getParent();
                        if (work == null) {
                            break;
                        }
                    }
                    scan = next;
                }
            }
        }

        return outline;
    }

    /**
     * Gets the page number (starting from 1) of the page represented by
     * a particular PDFObject.  The PDFObject must be a Page dictionary or
     * a destination description (or an action).
     * @return a number between 1 and the number of pages indicating the
     * page number, or 0 if the PDFObject is not in the page tree.
     */
    public int getPageNumber(PDFObject page) throws IOException {
        if (page.getType() == PDFObject.ARRAY) {
            page = page.getAt(0);
        }

        // now we've got a page.  Make sure.
        PDFObject typeObj = page.getDictRef("Type");
        if (typeObj == null || !typeObj.getStringValue().equals("Page")) {
            return 0;
        }

        int count = 0;
        while (true) {
            PDFObject parent = page.getDictRef("Parent");
            if (parent == null) {
                break;
            }
            PDFObject kids[] = parent.getDictRef("Kids").getArray();
            for (int i = 0; i < kids.length; i++) {
                if (kids[i].equals(page)) {
                    break;
                } else {
                    PDFObject kcount = kids[i].getDictRef("Count");
                    if (kcount != null) {
                        count += kcount.getIntValue();
                    } else {
                        count += 1;
                    }
                }
            }
            page = parent;
        }
        return count;
    }

    /**
     * Get the page commands for a given page in a separate thread.
     *
     * @param pagenum the number of the page to get commands for
     */
    public PDFPage getPage(int pagenum) {
        return getPage(pagenum, false);
    }

    /**
     * Get the page commands for a given page.
     *
     * @param pagenum the number of the page to get commands for
     * @param wait if true, do not exit until the page is complete.
     */
    public PDFPage getPage(int pagenum, boolean wait) {
        Integer key = new Integer(pagenum);
        HashMap<String,PDFObject> resources = null;
        PDFObject pageObj = null;
        boolean needread = false;

        PDFPage page = cache.getPage(key);
        PDFParser parser = cache.getPageParser(key);
        if (page == null) {
            try {
                // hunt down the page!
                resources = new HashMap<String,PDFObject>();

                PDFObject topPagesObj = root.getDictRef("Pages");
                pageObj = findPage(topPagesObj, 0, pagenum, resources);

                if (pageObj == null) {
                    return null;
                }

                page = createPage(pagenum, pageObj);

                byte[] stream = getContents(pageObj);
                parser = new PDFParser(page, stream, resources);

                cache.addPage(key, page, parser);
            } catch (IOException ioe) {
                System.out.println("GetPage inner loop:");
                ioe.printStackTrace();
                return null;
            }
        }

        if (parser != null && !parser.isFinished()) {
            parser.go(wait);
        }

        return page;
    }

    /**
     * Stop the rendering of a particular image on this page
     */
    public void stop(int pageNum) {
        PDFParser parser = cache.getPageParser(new Integer(pageNum));
        if (parser != null) {
            // stop it
            parser.stop();
        }
    }

    /**
     * get the stream representing the content of a particular page.
     *
     * @param pageObj the page object to get the contents of
     * @return a concatenation of any content streams for the requested
     * page.
     */
    private byte[] getContents(PDFObject pageObj) throws IOException {
        // concatenate all the streams
        PDFObject contentsObj = pageObj.getDictRef("Contents");
        if (contentsObj == null) {
            throw new IOException("No page contents!");
        }

        PDFObject contents[] = contentsObj.getArray();

        // see if we have only one stream (the easy case)
        if (contents.length == 1) {
            return contents[0].getStream();
        }

        // first get the total length of all the streams
        int len = 0;
        for (int i = 0; i < contents.length; i++) {
            byte[] data = contents[i].getStream();
            if (data == null) {
                throw new PDFParseException("No stream on content " + i +
                        ": " + contents[i]);
            }
            len += data.length;
        }

        // now assemble them all into one object
        byte[] stream = new byte[len];
        len = 0;
        for (int i = 0; i < contents.length; i++) {
            byte data[] = contents[i].getStream();
            System.arraycopy(data, 0, stream, len, data.length);
            len += data.length;
        }

        return stream;
    }

    /**
     * Create a PDF Page object by finding the relevant inherited
     * properties
     *
     * @param pageObj the PDF object for the page to be created
     */
    private PDFPage createPage(int pagenum, PDFObject pageObj)
            throws IOException {
        int rotation = 0;
        Rectangle2D mediabox = null; // second choice, if no crop
        Rectangle2D cropbox = null;  // first choice

        PDFObject mediaboxObj = getInheritedValue(pageObj, "MediaBox");
        if (mediaboxObj != null) {
            mediabox = parseNormalisedRectangle(mediaboxObj);
        }

        PDFObject cropboxObj = getInheritedValue(pageObj, "CropBox");
        if (cropboxObj != null) {
            cropbox = parseNormalisedRectangle(cropboxObj);
        }

        PDFObject rotateObj = getInheritedValue(pageObj, "Rotate");
        if (rotateObj != null) {
            rotation = rotateObj.getIntValue();
        }

        Rectangle2D bbox = ((cropbox == null) ? mediabox : cropbox);

        return new PDFPage(pagenum, bbox, rotation, cache);
    }

    /**
     * Get the PDFObject representing the content of a particular page. Note
     * that the number of the page need not have anything to do with the
     * label on that page.  If there are two blank pages, and then roman
     * numerals for the page number, then passing in 6 will get page (iv).
     *
     * @param pagedict the top of the pages tree
     * @param start the page number of the first page in this dictionary
     * @param getPage the number of the page to find; NOT the page's label.
     * @param resources a HashMap that will be filled with any resource
     *                  definitions encountered on the search for the page
     */
    private PDFObject findPage(PDFObject pagedict, int start, int getPage,
            Map<String,PDFObject> resources) throws IOException {
        PDFObject rsrcObj = pagedict.getDictRef("Resources");
        if (rsrcObj != null) {
            resources.putAll(rsrcObj.getDictionary());
        }

        PDFObject typeObj = pagedict.getDictRef("Type");
        if (typeObj != null && typeObj.getStringValue().equals("Page")) {
            // we found our page!
            return pagedict;
        }

        // find the first child for which (start + count) > getPage
        PDFObject kidsObj = pagedict.getDictRef("Kids");
        if (kidsObj != null) {
            PDFObject[] kids = kidsObj.getArray();
            for (int i = 0; i < kids.length; i++) {
                int count = 1;
                // BUG: some PDFs (T1Format.pdf) don't have the Type tag.
                // use the Count tag to indicate a Pages dictionary instead.
                PDFObject countItem = kids[i].getDictRef("Count");
                //                if (kids[i].getDictRef("Type").getStringValue().equals("Pages")) {
                if (countItem != null) {
                    count = countItem.getIntValue();
                }

                if (start + count >= getPage) {
                    return findPage(kids[i], start, getPage, resources);
                }

                start += count;
            }
        }

        return null;
    }

    /**
     * Find a property value in a page that may be inherited.  If the value
     * is not defined in the page itself, follow the page's "parent" links
     * until the value is found or the top of the tree is reached.
     *
     * @param pageObj the object representing the page
     * @param propName the name of the property we are looking for
     */
    private PDFObject getInheritedValue(PDFObject pageObj, String propName)
            throws IOException {
        // see if we have the property
        PDFObject propObj = pageObj.getDictRef(propName);
        if (propObj != null) {
            return propObj;
        }

        // recursively see if any of our parent have it
        PDFObject parentObj = pageObj.getDictRef("Parent");
        if (parentObj != null) {
            return getInheritedValue(parentObj, propName);
        }

        // no luck
        return null;
    }

    public static Rectangle2D parseNormalisedRectangle(PDFObject obj)
            throws IOException {

        if (obj != null) {
            if (obj.getType() == PDFObject.ARRAY) {
                PDFObject bounds[] = obj.getArray();
                if (bounds.length == 4) {
                    final double x0 = bounds[0].getDoubleValue();
                    final double y0 = bounds[1].getDoubleValue();
                    final double x1 = bounds[2].getDoubleValue();
                    final double y1 = bounds[3].getDoubleValue();

                    final double minX;
                    final double maxY;
                    final double maxX;
                    final double minY;

                    if (x0 < x1) {
                        minX = x0;
                        maxX = x1;
                    } else {
                        minX = x1;
                        maxX = x0;
                    }
                    if (y0 < y1) {
                        minY = y0;
                        maxY = y1;
                    } else {
                        minY = y1;
                        maxY = y0;
                    }

                    return new Rectangle2D.Double(minX, minY, Math.abs(maxX - minX), Math.abs(maxY - minY));

                } else {
                    throw new PDFParseException("Rectangle definition didn't have 4 elements");
                }
            } else {
                throw new PDFParseException("Rectangle definition not an array");
            }
        } else {
            throw new PDFParseException("Rectangle not present");
        }

    }

    /**
     * Get the default decrypter for the document
     * @return the default decrypter; never null, even for documents that
     *  aren't encrypted
     */
    public PDFDecrypter getDefaultDecrypter() {
        return defaultDecrypter;
    }
}
