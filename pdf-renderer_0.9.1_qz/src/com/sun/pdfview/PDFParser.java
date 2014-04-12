/*
 * $Id: PDFParser.java,v 1.14 2010-05-23 22:07:05 lujke Exp $
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

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import static java.awt.geom.GeneralPath.WIND_EVEN_ODD;
import static java.awt.geom.GeneralPath.WIND_NON_ZERO;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import com.sun.pdfview.colorspace.PDFColorSpace;
import com.sun.pdfview.colorspace.PatternSpace;
import com.sun.pdfview.decode.PDFDecoder;
import com.sun.pdfview.font.PDFFont;
import com.sun.pdfview.pattern.PDFShader;

/**
 * PDFParser is the class that parses a PDF content stream and
 * produces PDFCmds for a PDFPage.  You should never ever see it run:
 * it gets created by a PDFPage only if needed, and may even run in
 * its own thread.
 *
 * @author Mike Wessler
 */
public class PDFParser extends BaseWatchable {
    /** emit a file of DCT stream data. */
    public final static String  DEBUG_DCTDECODE_DATA = "debugdctdecode";

    // ---- parsing variables

    private Stack<Object> stack;          // stack of Object
    private Stack<ParserState> parserStates;    // stack of RenderState
    // the current render state
    private ParserState state;
    private GeneralPath path;
    private int clip;
    private int loc;
    private boolean resend = false;
    private Tok tok;
    private boolean catchexceptions;   // Indicates state of BX...EX
    /** a weak reference to the page we render into.  For the page
     * to remain available, some other code must retain a strong reference to it.
     */
    private WeakReference pageRef;
    /** the actual command, for use within a singe iteration.  Note that
     * this must be released at the end of each iteration to assure the
     * page can be collected if not in use
     */
    private PDFPage cmds;
    // ---- result variables
    byte[] stream;
    HashMap<String,PDFObject> resources;
    public static int debuglevel = 4000;

    public static void debug(String msg, int level) {
        if (level > debuglevel) {
            System.out.println(escape(msg));
        }
    }

    public static String escape(String msg) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            if (c != '\n' && (c < 32 || c >= 127)) {
                c = '?';
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static void setDebugLevel(int level) {
        debuglevel = level;
    }

    /**
     * Don't call this constructor directly.  Instead, use
     * PDFFile.getPage(int pagenum) to get a PDFPage.  There should
     * never be any reason for a user to create, access, or hold
     * on to a PDFParser.
     */
    public PDFParser(PDFPage cmds, byte[] stream,
            HashMap<String,PDFObject> resources) {
        super();

        this.pageRef = new WeakReference<PDFPage>(cmds);
        this.resources = resources;
        if (resources == null) {
            this.resources = new HashMap<String,PDFObject>();
        }

        this.stream = stream;
    }


    /////////////////////////////////////////////////////////////////
    //  B E G I N   R E A D E R   S E C T I O N
    /////////////////////////////////////////////////////////////////
    /**
     * a token from a PDF Stream
     */
    class Tok {

        /** begin bracket &lt; */
        public static final int BRKB = 11;
        /** end bracket &gt; */
        public static final int BRKE = 10;
        /** begin array [ */
        public static final int ARYB = 9;
        /** end array ] */
        public static final int ARYE = 8;
        /** String (, readString looks for trailing ) */
        public static final int STR = 7;
        /** begin brace { */
        public static final int BRCB = 5;
        /** end brace } */
        public static final int BRCE = 4;
        /** number */
        public static final int NUM = 3;
        /** keyword */
        public static final int CMD = 2;
        /** name (begins with /) */
        public static final int NAME = 1;
        /** unknown token */
        public static final int UNK = 0;
        /** end of stream */
        public static final int EOF = -1;
        /** the string value of a STR, NAME, or CMD token */
        public String name;
        /** the value of a NUM token */
        public double value;
        /** the type of the token */
        public int type;

        /** a printable representation of the token */
        @Override
        public String toString() {
            if (type == NUM) {
                return "NUM: " + value;
            } else if (type == CMD) {
                return "CMD: " + name;
            } else if (type == UNK) {
                return "UNK";
            } else if (type == EOF) {
                return "EOF";
            } else if (type == NAME) {
                return "NAME: " + name;
            } else if (type == CMD) {
                return "CMD: " + name;
            } else if (type == STR) {
                return "STR: (" + name;
            } else if (type == ARYB) {
                return "ARY [";
            } else if (type == ARYE) {
                return "ARY ]";
            } else {
                return "some kind of brace (" + type + ")";
            }
        }
    }

    /**
     * put the current token back so that it is returned again by
     * nextToken().
     */
    private void throwback() {
        resend = true;
    }

    /**
     * get the next token.
     * TODO: this creates a new token each time.  Is this strictly
     * necessary?
     */
    private Tok nextToken() {
        if (resend) {
            resend = false;
            return tok;
        }
        tok = new Tok();
        // skip whitespace
        while (loc < stream.length && PDFFile.isWhiteSpace(stream[loc])) {
            loc++;
        }
        if (loc >= stream.length) {
            tok.type = Tok.EOF;
            return tok;
        }
        int c = stream[loc++];
        // examine the character:
        while (c == '%') {
            // skip comments
            StringBuffer comment = new StringBuffer();
            while (loc < stream.length && c != '\n') {
                comment.append((char) c);
                c = stream[loc++];
            }
            if (loc < stream.length) {
                c = stream[loc++];      // eat the newline
                if (c == '\r') {
                    c = stream[loc++];  // eat a following return
                }
            }
            debug("Read comment: " + comment.toString(), -1);
        }

        if (c == '[') {
            tok.type = Tok.ARYB;
        } else if (c == ']') {
            tok.type = Tok.ARYE;
        } else if (c == '(') {
            // read a string
            tok.type = Tok.STR;
            tok.name = readString();
        } else if (c == '{') {
            tok.type = Tok.BRCB;
        } else if (c == '}') {
            tok.type = Tok.BRCE;
        } else if (c == '<' && stream[loc++] == '<') {
            tok.type = Tok.BRKB;
        } else if (c == '>' && stream[loc++] == '>') {
            tok.type = Tok.BRKE;
        } else if (c == '<') {
            loc--;
            tok.type = Tok.STR;
            tok.name = readByteArray();
        } else if (c == '/') {
            tok.type = Tok.NAME;
            tok.name = readName();
        } else if (c == '.' || c == '-' || (c >= '0' && c <= '9')) {
            loc--;
            tok.type = Tok.NUM;
            tok.value = readNum();
        } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '\'' || c == '"') {
            loc--;
            tok.type = Tok.CMD;
            tok.name = readName();
        } else {
            System.out.println("Encountered character: " + c + " (" + (char) c + ")");
            tok.type = Tok.UNK;
        }
        debug("Read token: " + tok, -1);
        return tok;
    }

    /**
     * read a name (sequence of non-PDF-delimiting characters) from the
     * stream.
     */
    private String readName() {
        int start = loc;
        while (loc < stream.length &&
                PDFFile.isRegularCharacter(stream[loc])) {
            loc++;
        }
        return new String(stream, start, loc - start);
    }

    /**
     * read a floating point number from the stream
     */
    private double readNum() {
        int c = stream[loc++];
        boolean neg = c == '-';
        boolean sawdot = c == '.';
        double dotmult = sawdot ? 0.1 : 1;
        double value = (c >= '0' && c <= '9') ? c - '0' : 0;
        while (true) {
            c = stream[loc++];
            if (c == '.') {
                if (sawdot) {
                    loc--;
                    break;
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
                loc--;
                break;
            }
        }
        if (neg) {
            value = -value;
        }
        return value;
    }

    /**
     * <p>read a String from the stream.  Strings begin with a '('
     * character, which has already been read, and end with a balanced ')'
     * character.  A '\' character starts an escape sequence of up
     * to three octal digits.</p>
     *
     * <p>Parenthesis must be enclosed by a balanced set of parenthesis,
     * so a string may enclose balanced parenthesis.</p>
     *
     * @return the string with escape sequences replaced with their
     * values
     */
    private String readString() {
        int parenLevel = 0;
        StringBuffer sb = new StringBuffer();
        while (loc < stream.length) {
            int c = stream[loc++];
            if (c == ')') {
                if (parenLevel-- == 0) {
                    break;
                }
            } else if (c == '(') {
                parenLevel++;
            } else if (c == '\\') {
                // escape sequences
                c = stream[loc++];
                if (c >= '0' && c < '8') {
                    int count = 0;
                    int val = 0;
                    while (c >= '0' && c < '8' && count < 3) {
                        val = val * 8 + c - '0';
                        c = stream[loc++];
                        count++;
                    }
                    loc--;
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
                }
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    /**
     * read a byte array from the stream.  Byte arrays begin with a '<'
     * character, which has already been read, and end with a '>'
     * character.  Each byte in the array is made up of two hex characters,
     * the first being the high-order bit.
     *
     * We translate the byte arrays into char arrays by combining two bytes
     * into a character, and then translate the character array into a string.
     * [JK FIXME this is probably a really bad idea!]
     *
     * @return the byte array
     */
    private String readByteArray() {
        StringBuffer buf = new StringBuffer();

        int count = 0;
        char w = (char) 0;

        // read individual bytes and format into a character array
        while ((loc < stream.length) && (stream[loc] != '>')) {
            char c = (char) stream[loc];
            byte b = (byte) 0;

            if (c >= '0' && c <= '9') {
                b = (byte) (c - '0');
            } else if (c >= 'a' && c <= 'f') {
                b = (byte) (10 + (c - 'a'));
            } else if (c >= 'A' && c <= 'F') {
                b = (byte) (10 + (c - 'A'));
            } else {
                loc++;
                continue;
            }

            // calculate where in the current byte this character goes
            int offset = 1 - (count % 2);
            w |= (0xf & b) << (offset * 4);

            // increment to the next char if we've written four bytes
            if (offset == 0) {
                buf.append(w);
                w = (char) 0;
            }

            count++;
            loc++;
        }

        // ignore trailing '>'
        loc++;

        return buf.toString();
    }

    /////////////////////////////////////////////////////////////////
    //  B E G I N   P A R S E R   S E C T I O N
    /////////////////////////////////////////////////////////////////
    /**
     * Called to prepare for some iterations
     */
    @Override
    public void setup() {
        stack = new Stack<Object>();
        parserStates = new Stack<ParserState>();
        state = new ParserState();
        path = new GeneralPath();
        loc = 0;
        clip = 0;

        //initialize the ParserState
        state.fillCS =
                PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_GRAY);
        state.strokeCS =
                PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_GRAY);
        state.textFormat = new PDFTextFormat();

    // HexDump.printData(stream);
    // System.out.println(dumpStream());
    }

    /**
     * parse the stream.  commands are added to the PDFPage initialized
     * in the constructor as they are encountered.
     * <p>
     * Page numbers in comments refer to the Adobe PDF specification.<br>
     * commands are listed in PDF spec 32000-1:2008 in Table A.1
     *
     * @return <ul><li>Watchable.RUNNING when there are commands to be processed
     *             <li>Watchable.COMPLETED when the page is done and all
     *                 the commands have been processed
     *             <li>Watchable.STOPPED if the page we are rendering into is
     *                 no longer available
     *         </ul>
     */
    public int iterate() throws Exception {
        // make sure the page is still available, and create the reference
        // to it for use within this iteration
        cmds = (PDFPage) pageRef.get();
        if (cmds == null) {
            System.out.println("Page gone.  Stopping");
            return Watchable.STOPPED;
        }

        Object obj = parseObject();

        // if there's nothing left to parse, we're done
        if (obj == null) {
            return Watchable.COMPLETED;
        }

        if (obj instanceof Tok) {
            // it's a command.  figure out what to do.
            // (if not, the token will be "pushed" onto the stack)
            String cmd = ((Tok) obj).name;
            debug("Command: " + cmd + " (stack size is " + stack.size() + ")", 0);
            if (cmd.equals("q")) {
                // push the parser state
                parserStates.push((ParserState) state.clone());

                // push graphics state
                cmds.addPush();
            } else if (cmd.equals("Q")) {
                processQCmd();
            } else if (cmd.equals("cm")) {
                // set transform to array of values
                float[] elts = popFloat(6);
                AffineTransform xform = new AffineTransform(elts);
                cmds.addXform(xform);
            } else if (cmd.equals("w")) {
                // set stroke width
                cmds.addStrokeWidth(popFloat());
            } else if (cmd.equals("J")) {
                // set end cap style
                cmds.addEndCap(popInt());
            } else if (cmd.equals("j")) {
                // set line join style
                cmds.addLineJoin(popInt());
            } else if (cmd.equals("M")) {
                // set miter limit
                cmds.addMiterLimit(popInt());
            } else if (cmd.equals("d")) {
                // set dash style and phase
                float phase = popFloat();
                float[] dashary = popFloatArray();
                cmds.addDash(dashary, phase);
            } else if (cmd.equals("ri")) {
                // TODO: do something with rendering intent (page 197)
            } else if (cmd.equals("i")) {
                popFloat();
            // TODO: do something with flatness tolerance
            } else if (cmd.equals("gs")) {
                // set graphics state to values in a named dictionary
                setGSState(popString());
            } else if (cmd.equals("m")) {
                // path move to
                float y = popFloat();
                float x = popFloat();
                path.moveTo(x, y);
            } else if (cmd.equals("l")) {
                // path line to
                float y = popFloat();
                float x = popFloat();
                path.lineTo(x, y);
            } else if (cmd.equals("c")) {
                // path curve to
                float a[] = popFloat(6);
                path.curveTo(a[0], a[1], a[2], a[3], a[4], a[5]);
            } else if (cmd.equals("v")) {
                // path curve; first control point= start
                float a[] = popFloat(4);
                Point2D cp = path.getCurrentPoint();
                path.curveTo((float) cp.getX(), (float) cp.getY(),
                        a[0], a[1], a[2], a[3]);
            } else if (cmd.equals("y")) {
                // path curve; last control point= end
                float a[] = popFloat(4);
                path.curveTo(a[0], a[1], a[2], a[3], a[2], a[3]);
            } else if (cmd.equals("h")) {
                // path close
                path.closePath();
            } else if (cmd.equals("re")) {
                // path add rectangle
                float a[] = popFloat(4);
                path.moveTo(a[0], a[1]);
                path.lineTo(a[0] + a[2], a[1]);
                path.lineTo(a[0] + a[2], a[1] + a[3]);
                path.lineTo(a[0], a[1] + a[3]);
                path.closePath();
            } else if (cmd.equals("S")) {
                // stroke the path
                cmds.addPath(path, PDFShapeCmd.STROKE | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("s")) {
                // close and stroke the path
                path.closePath();
                cmds.addPath(path, PDFShapeCmd.STROKE | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("f") || cmd.equals("F")) {
                // fill the path (close/not close identical)
                cmds.addPath(path, PDFShapeCmd.FILL | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("f*")) {
                // fill the path using even/odd rule
                path.setWindingRule(WIND_EVEN_ODD);
                cmds.addPath(path, PDFShapeCmd.FILL | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("B")) {
                // fill and stroke the path
                cmds.addPath(path, PDFShapeCmd.BOTH | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("B*")) {
                // fill path using even/odd rule and stroke it
                path.setWindingRule(WIND_EVEN_ODD);
                cmds.addPath(path, PDFShapeCmd.BOTH | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("b")) {
                // close the path, then fill and stroke it
                path.closePath();
                cmds.addPath(path, PDFShapeCmd.BOTH | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("b*")) {
                // close path, fill using even/odd rule, then stroke it
                path.closePath();
                path.setWindingRule(WIND_EVEN_ODD);
                cmds.addPath(path, PDFShapeCmd.BOTH | clip);
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("n")) {
                // clip with the path and discard it
                if (clip != 0) {
                    cmds.addPath(path, clip);
                }
                clip = 0;
                path = new GeneralPath();
            } else if (cmd.equals("W")) {
                // mark this path for clipping!
                clip = PDFShapeCmd.CLIP;
            } else if (cmd.equals("W*")) {
                // mark this path using even/odd rule for clipping
                path.setWindingRule(WIND_EVEN_ODD);
                clip = PDFShapeCmd.CLIP;
            } else if (cmd.equals("sh")) {
                // shade a region that is defined by the shader itself.
                // shading the current space from a dictionary
                // should only be used for limited-dimension shadings
                String gdictname = popString();
                // set up the pen to do a gradient fill according
                // to the dictionary
                PDFObject shobj = findResource(gdictname, "Shading");
                doShader(shobj);


            } else if (cmd.equals("CS")) {
                // set the stroke color space
                state.strokeCS = parseColorSpace(new PDFObject(stack.pop()));
            } else if (cmd.equals("cs")) {
                // set the fill color space
                state.fillCS = parseColorSpace(new PDFObject(stack.pop()));
            } else if (cmd.equals("SC")) {
                // set the stroke color
                int n = state.strokeCS.getNumComponents();
                cmds.addStrokePaint(state.strokeCS.getPaint(popFloat(n)));
            } else if (cmd.equals("SCN")) {
                if (state.strokeCS instanceof PatternSpace) {
                    cmds.addFillPaint(doPattern((PatternSpace) state.strokeCS));
                } else {
                    int n = state.strokeCS.getNumComponents();
                    cmds.addStrokePaint(state.strokeCS.getPaint(popFloat(n)));
                }
            } else if (cmd.equals("sc")) {
                // set the fill color
                int n = state.fillCS.getNumComponents();
                cmds.addFillPaint(state.fillCS.getPaint(popFloat(n)));
            } else if (cmd.equals("scn")) {
                if (state.fillCS instanceof PatternSpace) {
                    cmds.addFillPaint(doPattern((PatternSpace) state.fillCS));
                } else {
                    int n = state.fillCS.getNumComponents();
                    cmds.addFillPaint(state.fillCS.getPaint(popFloat(n)));
                }
            } else if (cmd.equals("G")) {
                // set the stroke color to a Gray value
                state.strokeCS =
                        PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_GRAY);
                cmds.addStrokePaint(state.strokeCS.getPaint(popFloat(1)));
            } else if (cmd.equals("g")) {
                // set the fill color to a Gray value
                state.fillCS =
                        PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_GRAY);
                cmds.addFillPaint(state.fillCS.getPaint(popFloat(1)));
            } else if (cmd.equals("RG")) {
                // set the stroke color to an RGB value
                state.strokeCS =
                        PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_RGB);
                cmds.addStrokePaint(state.strokeCS.getPaint(popFloat(3)));
            } else if (cmd.equals("rg")) {
                // set the fill color to an RGB value
                state.fillCS =
                        PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_RGB);
                cmds.addFillPaint(state.fillCS.getPaint(popFloat(3)));
            } else if (cmd.equals("K")) {
                // set the stroke color to a CMYK value
                state.strokeCS =
                        PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_CMYK);
                cmds.addStrokePaint(state.strokeCS.getPaint(popFloat(4)));
            } else if (cmd.equals("k")) {
                // set the fill color to a CMYK value
                state.fillCS =
                        PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_CMYK);
                cmds.addFillPaint(state.fillCS.getPaint(popFloat(4)));
            } else if (cmd.equals("Do")) {
                // make a do call on the referenced object
                PDFObject xobj = findResource(popString(), "XObject");
                doXObject(xobj);
            } else if (cmd.equals("BT")) {
                processBTCmd();
            } else if (cmd.equals("ET")) {
                // end of text.  noop
                state.textFormat.end();
            } else if (cmd.equals("Tc")) {
                // set character spacing
                state.textFormat.setCharSpacing(popFloat());
            } else if (cmd.equals("Tw")) {
                // set word spacing
                state.textFormat.setWordSpacing(popFloat());
            } else if (cmd.equals("Tz")) {
                // set horizontal scaling
                state.textFormat.setHorizontalScale(popFloat());
            } else if (cmd.equals("TL")) {
                // set leading
                state.textFormat.setLeading(popFloat());
            } else if (cmd.equals("Tf")) {
                // set text font
                float sz = popFloat();
                String fontref = popString();
                state.textFormat.setFont(getFontFrom(fontref), sz);
            } else if (cmd.equals("Tr")) {
                // set text rendering mode
                state.textFormat.setMode(popInt());
            } else if (cmd.equals("Ts")) {
                // set text rise
                state.textFormat.setRise(popFloat());
            } else if (cmd.equals("Td")) {
                // set text matrix location
                float y = popFloat();
                float x = popFloat();
                state.textFormat.carriageReturn(x, y);
            } else if (cmd.equals("TD")) {
                // set leading and matrix:  -y TL x y Td
                float y = popFloat();
                float x = popFloat();
                state.textFormat.setLeading(-y);
                state.textFormat.carriageReturn(x, y);
            } else if (cmd.equals("Tm")) {
                // set text matrix
                state.textFormat.setMatrix(popFloat(6));
            } else if (cmd.equals("T*")) {
                // go to next line
                state.textFormat.carriageReturn();
            } else if (cmd.equals("Tj")) {
                // show text
                state.textFormat.doText(cmds, popString());
            } else if (cmd.equals("\'")) {
                // next line and show text:  T* string Tj
                state.textFormat.carriageReturn();
                state.textFormat.doText(cmds, popString());
            } else if (cmd.equals("\"")) {
                // draw string on new line with char & word spacing:
                // aw Tw ac Tc string '
                String string = popString();
                float ac = popFloat();
                float aw = popFloat();
                state.textFormat.setWordSpacing(aw);
                state.textFormat.setCharSpacing(ac);
                state.textFormat.doText(cmds, string);
            } else if (cmd.equals("TJ")) {
                // show kerned string
                state.textFormat.doText(cmds, popArray());
            } else if (cmd.equals("BI")) {
                // parse inline image
                parseInlineImage();
            } else if (cmd.equals("BX")) {
                catchexceptions = true;     // ignore errors
            } else if (cmd.equals("EX")) {
                catchexceptions = false;    // stop ignoring errors
            } else if (cmd.equals("MP")) {
                // mark point (role= mark role name)
                popString();
            } else if (cmd.equals("DP")) {
                // mark point with dictionary (role, ref)
                // ref is either inline dict or name in "Properties" rsrc
                Object ref = stack.pop();
                popString();
            } else if (cmd.equals("BMC")) {
                // begin marked content (role)
                popString();
            } else if (cmd.equals("BDC")) {
                // begin marked content with dict (role, ref)
                // ref is either inline dict or name in "Properties" rsrc
                Object ref = stack.pop();
                popString();
            } else if (cmd.equals("EMC")) {
                // end marked content
            } else if (cmd.equals("d0")) {
                // character width in type3 fonts
                popFloat(2);
            } else if (cmd.equals("d1")) {
                // character width in type3 fonts
                popFloat(6);
            } else if (cmd.equals("QBT")) {// 'Q' & 'BT' mushed together!
                processQCmd();
                processBTCmd();
            } else {
                if (catchexceptions) {
                    debug("**** WARNING: Unknown command: " + cmd + " **************************", 10);
                } else {
                    throw new PDFParseException("Unknown command: " + cmd);
                }
            }
            if (stack.size() != 0) {
                debug("**** WARNING! Stack not zero! (cmd=" + cmd + ", size=" + stack.size() + ") *************************", 4);
                stack.setSize(0);
            }
        } else {
            stack.push(obj);
        }

        // release or reference to the page object, so that it can be
        // gc'd if it is no longer in use
        cmds = null;

        return Watchable.RUNNING;
    }

    /**
     * abstracted command processing for Q command. Used directly and as
     * part of processing of mushed QBT command.
     */
    private void processQCmd() {
        // pop graphics state ('Q')
        cmds.addPop();
        // pop the parser state
        state = (ParserState) parserStates.pop();
    }

    /**
     * abstracted command processing for BT command. Used directly and as
     * part of processing of mushed QBT command.
     */
    private void processBTCmd() {
        // begin text block:  reset everything.
        state.textFormat.reset();
    }

    /**
     * Cleanup when iteration is done
     */
    @Override
    public void cleanup() {
        if (state != null && state.textFormat != null) {
            state.textFormat.flush();
        }
        if (cmds != null) {
            cmds.finish();
        }

        stack = null;
        parserStates = null;
        state = null;
        path = null;
        cmds = null;
    }
    boolean errorwritten = false;

    public void dumpStreamToError() {
        if (errorwritten) {
            return;
        }
        errorwritten = true;
        try {
            File oops = File.createTempFile("PDFError", ".err");
            FileOutputStream fos = new FileOutputStream(oops);
            fos.write(stream);
            fos.close();
        } catch (IOException ioe) { /* Do nothing */ }
        ;
    }

    public String dumpStream() {
        return escape(new String(stream).replace('\r', '\n'));
    }

    /**
     * take a byte array and write a temporary file with it's data.
     * This is intended to capture data for analysis, like after decoders.
     *
     * @param ary
     * @param name
     */
    public static void emitDataFile (byte [] ary, String name) {
        FileOutputStream ostr;

        try {
            File file = File.createTempFile ("DateFile", name);
            ostr = new FileOutputStream (file);
            System.out.println ("Write: " + file.getPath ());
            ostr.write (ary);
            ostr.close ();
        } catch (IOException ex) {
            // ignore
        }
    }

    /////////////////////////////////////////////////////////////////
    //  H E L P E R S
    /////////////////////////////////////////////////////////////////
    /**
     * get a property from a named dictionary in the resources of this
     * content stream.
     * @param name the name of the property in the dictionary
     * @param inDict the name of the dictionary in the resources
     * @return the value of the property in the dictionary
     */
    private PDFObject findResource(String name, String inDict)
            throws IOException {
        if (inDict != null) {
            PDFObject in = resources.get(inDict);
            if (in == null || in.getType() != PDFObject.DICTIONARY) {
                throw new PDFParseException("No dictionary called " + inDict + " found in the resources");
            }
            return in.getDictRef(name);
        } else {
            return resources.get(name);
        }
    }

    /**
     * Insert a PDF object into the command stream.  The object must
     * either be an Image or a Form, which is a set of PDF commands
     * in a stream.
     * @param obj the object to insert, an Image or a Form.
     */
    private void doXObject(PDFObject obj) throws IOException {
        String type = obj.getDictRef("Subtype").getStringValue();
        if (type == null) {
            type = obj.getDictRef ("S").getStringValue ();
        }
        if (type.equals("Image")) {
            doImage(obj);
        } else if (type.equals("Form")) {
            doForm(obj);
        } else {
            throw new PDFParseException("Unknown XObject subtype: " + type);
        }
    }

    /**
     * Parse image data into a Java BufferedImage and add the image
     * command to the page.
     * @param obj contains the image data, and a dictionary describing
     * the width, height and color space of the image.
     */
    private void doImage(PDFObject obj) throws IOException {
        cmds.addImage(PDFImage.createImage(obj, resources));
    }

    /**
     * Inject a stream of PDF commands onto the page.  Optimized to cache
     * a parsed stream of commands, so that each Form object only needs
     * to be parsed once.
     * @param obj a stream containing the PDF commands, a transformation
     * matrix, bounding box, and resources.
     */
    private void doForm(PDFObject obj) throws IOException {
        // check to see if we've already parsed this sucker
        PDFPage formCmds = (PDFPage) obj.getCache();
        if (formCmds == null) {
            // rats.  parse it.
            AffineTransform at;
            Rectangle2D bbox;
            PDFObject matrix = obj.getDictRef("Matrix");
            if (matrix == null) {
                at = new AffineTransform();
            } else {
                float elts[] = new float[6];
                for (int i = 0; i < elts.length; i++) {
                    elts[i] = ((PDFObject) matrix.getAt(i)).getFloatValue();
                }
                at = new AffineTransform(elts);
            }
            PDFObject bobj = obj.getDictRef("BBox");
            bbox = PDFFile.parseNormalisedRectangle(bobj);
            formCmds = new PDFPage(bbox, 0);
            formCmds.addXform(at);

            HashMap<String,PDFObject> r = new HashMap<String,PDFObject>(resources);
            PDFObject rsrc = obj.getDictRef("Resources");
            if (rsrc != null) {
                r.putAll(rsrc.getDictionary());
            }

            PDFParser form = new PDFParser(formCmds, obj.getStream(), r);
            form.go(true);

            obj.setCache(formCmds);
        }
        cmds.addPush();
        cmds.addCommands(formCmds);
        cmds.addPop();
    }

    /**
     * Set the values into a PatternSpace
     */
    private PDFPaint doPattern(PatternSpace patternSpace) throws IOException {
        float[] components = null;

        String patternName = popString();
        PDFObject pattern = findResource(patternName, "Pattern");

        if (pattern == null) {
            throw new PDFParseException("Unknown pattern : " + patternName);
        }

        if (stack.size() > 0) {
            components = popFloat(stack.size());
        }

        return patternSpace.getPaint(pattern, components, resources);
    }

    /**
     * Parse the next object out of the PDF stream.  This could be a
     * Double, a String, a HashMap (dictionary), Object[] array, or
     * a Tok containing a PDF command.
     */
    private Object parseObject() throws PDFParseException {
        Tok t = nextToken();
        if (t.type == Tok.NUM) {
            return new Double(tok.value);
        } else if (t.type == Tok.STR) {
            return tok.name;
        } else if (t.type == Tok.NAME) {
            return tok.name;
        } else if (t.type == Tok.BRKB) {
            HashMap<String,PDFObject> hm = new HashMap<String,PDFObject>();
            String name = null;
            Object obj;
            while ((obj = parseObject()) != null) {
                if (name == null) {
                    name = (String) obj;
                } else {
                    hm.put(name, new PDFObject(obj));
                    name = null;
                }
            }
            if (tok.type != Tok.BRKE) {
                throw new PDFParseException("Inline dict should have ended with '>>'");
            }
            return hm;
        } else if (t.type == Tok.ARYB) {
            // build an array
            ArrayList<Object> ary = new ArrayList<Object>();
            Object obj;
            while ((obj = parseObject()) != null) {
                ary.add(obj);
            }
            if (tok.type != Tok.ARYE) {
                throw new PDFParseException("Expected ']'");
            }
            return ary.toArray();
        } else if (t.type == Tok.CMD) {
            return t;
        }
        debug("**** WARNING! parseObject unknown token! (t.type=" + t.type + ") *************************", 4);
        return null;
    }

    /**
     * Parse an inline image.  An inline image starts with BI (already
     * read, contains a dictionary until ID, and then image data until
     * EI.
     */
    private void parseInlineImage() throws IOException {
        // build dictionary until ID, then read image until EI
        HashMap<String,PDFObject> hm = new HashMap<String,PDFObject>();
        while (true) {
            Tok t = nextToken();
            if (t.type == Tok.CMD && t.name.equals("ID")) {
                break;
            }
            // it should be a name;
            String name = t.name;
            debug("ParseInlineImage, token: " + name, 1000);
            if (name.equals("BPC")) {
                name = "BitsPerComponent";
            } else if (name.equals("CS")) {
                name = "ColorSpace";
            } else if (name.equals("D")) {
                name = "Decode";
            } else if (name.equals("DP")) {
                name = "DecodeParms";
            } else if (name.equals("F")) {
                name = "Filter";
            } else if (name.equals("H")) {
                name = "Height";
            } else if (name.equals("IM")) {
                name = "ImageMask";
            } else if (name.equals("W")) {
                name = "Width";
            } else if (name.equals("I")) {
                name = "Interpolate";
            }
            Object vobj = parseObject();
            hm.put(name, new PDFObject(vobj));
        }
        if (stream[loc] == '\r') {
            loc++;
        }
        if (stream[loc] == '\n' || stream[loc] == ' ') {
            loc++;
        }


        PDFObject imObj = (PDFObject) hm.get("ImageMask");
        if (imObj != null && imObj.getBooleanValue()) {
        	// [PATCHED by michal.busta@gmail.com] - default value according to PDF spec. is [0, 1]
        	// there is no need to swap array - PDF image should handle this values
            Double[] decode = {new Double(0), new Double(1)};

            PDFObject decodeObj = (PDFObject) hm.get("Decode");
            if (decodeObj != null) {
                decode[0] = new Double(decodeObj.getAt(0).getDoubleValue());
                decode[1] = new Double(decodeObj.getAt(1).getDoubleValue());
            }

            hm.put("Decode", new PDFObject(decode));
        }

        PDFObject obj = new PDFObject(null, PDFObject.DICTIONARY, hm);
        int dstart = loc;

        // now skip data until a whitespace followed by EI
        while (!PDFFile.isWhiteSpace(stream[loc]) ||
                stream[loc + 1] != 'E' ||
                stream[loc + 2] != 'I') {
            loc++;
        }

        // data runs from dstart to loc
        byte[] data = new byte[loc - dstart];
        System.arraycopy(stream, dstart, data, 0, loc - dstart);
        obj.setStream(ByteBuffer.wrap(data));
        loc += 3;
        doImage(obj);
    }

    /**
     * build a shader from a dictionary.
     */
    private void doShader(PDFObject shaderObj) throws IOException {
        PDFShader shader = PDFShader.getShader(shaderObj, resources);

        cmds.addPush();

        Rectangle2D bbox = shader.getBBox();
        if (bbox != null) {
            cmds.addFillPaint(shader.getPaint());
            cmds.addPath(new GeneralPath(bbox), PDFShapeCmd.FILL);
        }

        cmds.addPop();
    }

    /**
     * get a PDFFont from the resources, given the resource name of the
     * font.
     *
     * @param fontref the resource key for the font
     */
    private PDFFont getFontFrom(String fontref) throws IOException {
        PDFObject obj = findResource(fontref, "Font");
        return PDFFont.getFont(obj, resources);
    }

    /**
     * add graphics state commands contained within a dictionary.
     * @param name the resource name of the graphics state dictionary
     */
    private void setGSState(String name) throws IOException {
        // obj must be a string that is a key to the "ExtGState" dict
        PDFObject gsobj = findResource(name, "ExtGState");
        // get LW, LC, LJ, Font, SM, CA, ML, D, RI, FL, BM, ca
        // out of the reference, which is a dictionary
        PDFObject d;
        if ((d = gsobj.getDictRef("LW")) != null) {
            cmds.addStrokeWidth(d.getFloatValue());
        }
        if ((d = gsobj.getDictRef("LC")) != null) {
            cmds.addEndCap(d.getIntValue());
        }
        if ((d = gsobj.getDictRef("LJ")) != null) {
            cmds.addLineJoin(d.getIntValue());
        }
        if ((d = gsobj.getDictRef("Font")) != null) {
            state.textFormat.setFont(getFontFrom(d.getAt(0).getStringValue()),
                    d.getAt(1).getFloatValue());
        }
        if ((d = gsobj.getDictRef("ML")) != null) {
            cmds.addMiterLimit(d.getFloatValue());
        }
        if ((d = gsobj.getDictRef("D")) != null) {
            PDFObject pdash[] = d.getAt(0).getArray();
            float dash[] = new float[pdash.length];
            for (int i = 0; i < pdash.length; i++) {
                dash[i] = pdash[i].getFloatValue();
            }
            cmds.addDash(dash, d.getAt(1).getFloatValue());
        }
        if ((d = gsobj.getDictRef("CA")) != null) {
            cmds.addStrokeAlpha(d.getFloatValue());
        }
        if ((d = gsobj.getDictRef("ca")) != null) {
            cmds.addFillAlpha(d.getFloatValue());
        }
    // others: BM=blend mode
    }

    /**
     * generate a PDFColorSpace description based on a PDFObject.  The
     * object could be a standard name, or the name of a resource in
     * the ColorSpace dictionary, or a color space name with a defining
     * dictionary or stream.
     */
    private PDFColorSpace parseColorSpace(PDFObject csobj) throws IOException {
        if (csobj == null) {
            return state.fillCS;
        }

        return PDFColorSpace.getColorSpace(csobj, resources);
    }

    /**
     * pop a single float value off the stack.
     * @return the float value of the top of the stack
     * @throws PDFParseException if the value on the top of the stack
     * isn't a number
     */
    private float popFloat() throws PDFParseException {
        Object obj = stack.pop();
        if (obj instanceof Double) {
            return ((Double) obj).floatValue();
        } else {
            throw new PDFParseException("Expected a number here.");
        }
    }

    /**
     * pop an array of float values off the stack.  This is equivalent
     * to filling an array from end to front by popping values off the
     * stack.
     * @param count the number of numbers to pop off the stack
     * @return an array of length <tt>count</tt>
     * @throws PDFParseException if any of the values popped off the
     * stack are not numbers.
     */
    private float[] popFloat(int count) throws PDFParseException {
        float[] ary = new float[count];
        for (int i = count - 1; i >= 0; i--) {
            ary[i] = popFloat();
        }
        return ary;
    }

    /**
     * pop a single integer value off the stack.
     * @return the integer value of the top of the stack
     * @throws PDFParseException if the top of the stack isn't a number.
     */
    private int popInt() throws PDFParseException {
        Object obj = stack.pop();
        if (obj instanceof Double) {
            return ((Double) obj).intValue();
        } else {
            throw new PDFParseException("Expected a number here.");
        }
    }

    /**
     * pop an array of integer values off the stack.  This is equivalent
     * to filling an array from end to front by popping values off the
     * stack.
     * @param count the number of numbers to pop off the stack
     * @return an array of length <tt>count</tt>
     * @throws PDFParseException if any of the values popped off the
     * stack are not numbers.
     */
    private float[] popFloatArray() throws PDFParseException {
        Object obj = stack.pop();
        if (!(obj instanceof Object[])) {
            throw new PDFParseException("Expected an [array] here.");
        }
        Object[] source = (Object[]) obj;
        float[] ary = new float[source.length];
        for (int i = 0; i < ary.length; i++) {
            if (source[i] instanceof Double) {
                ary[i] = ((Double) source[i]).floatValue();
            } else {
                throw new PDFParseException("This array doesn't consist only of floats.");
            }
        }
        return ary;
    }

    /**
     * pop a String off the stack.
     * @return the String from the top of the stack
     * @throws PDFParseException if the top of the stack is not a NAME
     * or STR.
     */
    private String popString() throws PDFParseException {
        Object obj = stack.pop();
        if (!(obj instanceof String)) {
            throw new PDFParseException("Expected string here: " + obj.toString());
        } else {
            return (String) obj;
        }
    }

    /**
     * pop a PDFObject off the stack.
     * @return the PDFObject from the top of the stack
     * @throws PDFParseException if the top of the stack does not contain
     * a PDFObject.
     */
    private PDFObject popObject() throws PDFParseException {
        Object obj = stack.pop();
        if (!(obj instanceof PDFObject)) {
            throw new PDFParseException("Expected a reference here: " + obj.toString());
        }
        return (PDFObject) obj;
    }

    /**
     * pop an array off the stack
     * @return the array of objects that is the top element of the stack
     * @throws PDFParseException if the top element of the stack does not
     * contain an array.
     */
    private Object[] popArray() throws PDFParseException {
        Object obj = stack.pop();
        if (!(obj instanceof Object[])) {
            throw new PDFParseException("Expected an [array] here: " + obj.toString());
        }
        return (Object[]) obj;
    }

    /**
     * A class to store state needed whiel rendering.  This includes the
     * stroke and fill color spaces, as well as the text formatting
     * parameters.
     */
    class ParserState implements Cloneable {

        /** the fill color space */
        PDFColorSpace fillCS;
        /** the stroke color space */
        PDFColorSpace strokeCS;
        /** the text paramters */
        PDFTextFormat textFormat;

        /**
         * Clone the render state.
         */
        @Override
        public Object clone() {
            ParserState newState = new ParserState();

            // no need to clone color spaces, since they are immutable
            newState.fillCS = fillCS;
            newState.strokeCS = strokeCS;

            // we do need to clone the textFormat
            newState.textFormat = (PDFTextFormat) textFormat.clone();

            return newState;
        }
    }
}
