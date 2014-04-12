/*
 * $Id: TestType1CFont.java,v 1.5 2009-08-07 23:18:32 tomoke Exp $
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
package test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.geom.*;

import com.sun.pdfview.font.*;

public class TestType1CFont extends JPanel implements KeyListener {

    int encoding[] = new int[256];  // glyph at position i has code encoding[i]
    int glyphnames[];   // glyph at position i has name SID glyphnames[i]
    String names[];  // extra names for this font (others in FontSupport)
    //    String safenames[];  // names without high-bit characters
    HashMap<String,GlyphData> charset = new HashMap<String,GlyphData>();
    ArrayList<String> charnames = new ArrayList<String>();
    int charcounter = -1;
    byte[] data;
    int pos;
    byte[] subrs[];
    float[] stack = new float[100];
    int stackptr = 0;
    AffineTransform at = new AffineTransform(0.001f, 0, 0, 0.001f, 0, 0);
    int num;
    float fnum;
    int type;
    static int CMD = 0;
    static int NUM = 1;
    static int FLT = 2;

    public boolean isRequestFocusEnabled() {
        return true;
    }

    public TestType1CFont(InputStream is) throws IOException {
        super();
        setPreferredSize(new Dimension(800, 800));
        addKeyListener(this);
        BufferedInputStream bis = new BufferedInputStream(is);
        int count = 0;
        ArrayList<byte[]> al = new ArrayList<byte[]>();
        byte b[] = new byte[32000];
        int len;
        while ((len = bis.read(b, 0, b.length)) >= 0) {
            byte[] c = new byte[len];
            System.arraycopy(b, 0, c, 0, len);
            al.add(c);
            count += len;
            b = new byte[32000];
        }
        data = new byte[count];
        len = 0;
        for (int i = 0; i < al.size(); i++) {
            byte from[] = al.get(i);
            System.arraycopy(from, 0, data, len, from.length);
            len += from.length;
        }
        pos = 0;
        //	printData();
        parse();
    // TODO: free up (set to null) unused structures (data, subrs, stack)
    }
    GlyphData showing;
    String showname;
    Font gfont = new Font("Sans-serif", Font.PLAIN, 24).deriveFont(AffineTransform.getScaleInstance(1, -1));
    Color fillColor = new Color(0xe0, 0xff, 0xff);

    public void keyTyped(KeyEvent evt) {
    }

    public void keyReleased(KeyEvent evt) {
    }

    public void keyPressed(KeyEvent evt) {
        if (evt.getKeyCode() == evt.VK_RIGHT) {
            charcounter++;
            if (charcounter >= charnames.size()) {
                charcounter = 0;
            }
            showing = readGlyph((String) charnames.get(charcounter));
        } else if (evt.getKeyCode() == evt.VK_LEFT) {
            charcounter--;
            if (charcounter < 0) {
                charcounter = charnames.size() - 1;
            }
            showing = readGlyph((String) charnames.get(charcounter));
        } else {
            char c = evt.getKeyChar();
            //	System.out.println("Got char: "+name);
            showing = readGlyph(FontSupport.stdNames[FontSupport.standardEncoding[(int) c & 0xff]]);
        }
        repaint();
    }

    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.white);
        g2.fillRect(0, 0, getWidth(), getHeight());
        AffineTransform at = new AffineTransform(0.5, 0, 0, -0.5, 30, getHeight() * 3 / 4);
        g2.transform(at);
        g2.setColor(Color.black);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        //	System.out.println("Showing="+showing);
        if (showing != null) {
            showing.draw(g2);
        }
    }

    private void printData() {
        char[] parts = new char[17];
        int partsloc = 0;
        for (int i = 0; i < data.length; i++) {
            int d = ((int) data[i]) & 0xff;
            if (d == 0) {
                parts[partsloc++] = '.';
            } else if (d < 32 || d >= 127) {
                parts[partsloc++] = '?';
            } else {
                parts[partsloc++] = (char) d;
            }
            if (d < 16) {
                System.out.print("0" + Integer.toHexString(d));
            } else {
                System.out.print(Integer.toHexString(d));
            }
            if ((i & 15) == 15) {
                System.out.println("      " + new String(parts));
                partsloc = 0;
            } else if ((i & 7) == 7) {
                System.out.print("  ");
                parts[partsloc++] = ' ';
            } else if ((i & 1) == 1) {
                System.out.print(" ");
            }
        }
        System.out.println();
    }

    private int readNext(boolean charstring) {
        num = (int) (data[pos++]) & 0xff;
        if (num == 30 && !charstring) { // goofy floatingpoint rep
            readFNum();
            return type = FLT;
        } else if (num == 28) {
            num = (((int) data[pos]) << 8) + (((int) data[pos + 1]) & 0xff);
            pos += 2;
            return type = NUM;
        } else if (num == 29 && !charstring) {
            num = (((int) data[pos] & 0xff) << 24) |
                    (((int) data[pos + 1] & 0xff) << 16) |
                    (((int) data[pos + 2] & 0xff) << 8) |
                    (((int) data[pos + 3] & 0xff));
            pos += 4;
            return type = NUM;
        } else if (num == 12) {  // two-byte command
            num = 1000 + ((int) (data[pos++]) & 0xff);
            return type = CMD;
        } else if (num < 32) {
            return type = CMD;
        } else if (num < 247) {
            num -= 139;
            return type = NUM;
        } else if (num < 251) {
            num = (num - 247) * 256 + (((int) data[pos++]) & 0xff) + 108;
            return type = NUM;
        } else if (num < 255) {
            num = -(num - 251) * 256 - (((int) data[pos++]) & 0xff) - 108;
            return type = NUM;
        } else if (!charstring) { // dict shouldn't have a 255 code
            printData();
            throw new RuntimeException("Got a 255 code while reading dict");
        } else { // num was 255
            fnum = ((((int) data[pos] & 0xff) << 24) |
                    (((int) data[pos + 1] & 0xff) << 16) |
                    (((int) data[pos + 2] & 0xff) << 8) |
                    (((int) data[pos + 3] & 0xff))) / 65536f;
            pos += 4;
            return type = FLT;
        }
    }

    public void readFNum() {
        // work in nybbles: 0-9=0-9, a=. b=E, c=E-, d=rsvd e=neg f=end
        float f = 0;
        boolean neg = false;
        int exp = 0;
        int eval = 0;
        float mul = 1;
        byte work = data[pos++];
        while (true) {
            if (work == (byte) 0xdd) {
                work = data[pos++];
            }
            int nyb = (work >> 4) & 0xf;
            work = (byte) ((work << 4) | 0xd);
            if (nyb < 10) {
                if (exp != 0) {         // working on the exponent
                    eval = eval * 10 + nyb;
                } else if (mul == 1) {  // working on an int
                    f = f * 10 + nyb;
                } else {              // working on decimal part
                    f += nyb * mul;
                    mul /= 10f;
                }
            } else if (nyb == 0xa) {    // decimal
                mul = 0.1f;
            } else if (nyb == 0xb) {    // E+
                exp = 1;
            } else if (nyb == 0xc) {    // E-
                exp = -1;
            } else if (nyb == 0xe) {      // neg
                neg = true;
            } else {
                break;
            }
        }
        fnum = (neg ? -1 : 1) * f * (float) Math.pow(10, eval * exp);
    }

    private int readInt(int len) {
        int n = 0;
        for (int i = 0; i < len; i++) {
            n = (n << 8) | (((int) data[pos++]) & 0xff);
        }
        return n;
    }

    private int readByte() {
        return ((int) data[pos++]) & 0xff;
    }


    // DICT structure:
    // operand operator operand operator ...

    // INDEX structure:
    // count(2) offsize [offset offset ... offset] data
    // offset array has count+1 entries
    // data starts at 3+(count+1)*offsize
    // offset for data is offset+2+(count+1)*offsize
    public int getIndexSize(int loc) {
//	System.out.println("Getting size of index at "+loc);
        int hold = pos;
        pos = loc;
        int count = readInt(2);
        if (count == 0) {
            return 2;
        }
        int encsz = readByte();
        // pos is now at the first offset.  last offset is at count*encsz
        pos += count * encsz;
        int end = readInt(encsz);
        pos = hold;
        return 2 + (count + 1) * encsz + end;
    }

    class Range {

        private int start;
        private int len;

        public Range(int start, int len) {
            this.start = start;
            this.len = len;
        }

        public final int getStart() {
            return start;
        }

        public final int getLen() {
            return len;
        }

        public final int getEnd() {
            return start + len;
        }
    }

    public Range getIndexEntry(int index, int id) {
        int hold = pos;
        pos = index;
        int count = readInt(2);
        int encsz = readByte();
        pos += encsz * id;
        int from = readInt(encsz);
        Range r = new Range(from + 2 + index + encsz * (count + 1), readInt(encsz) - from);
        pos = hold;
        return r;
    }

    // Top DICT: NAME    CODE   DEFAULT
    // charstringtype    12 6    2
    // fontmatrix        12 7    0.001 0 0 0.001
    // charset           15      - (offset)  names of glyphs (ref to name idx)
    // encoding          16      - (offset)  array of codes
    // CharStrings       17      - (offset)
    // Private           18      - (size, offset)

    // glyph at position i in CharStrings has name charset[i]
    // and code encoding[i]
    int charstringtype = 2;
    float temps[] = new float[32];
    int charsetbase = 0;
    int encodingbase = 0;
    int charstringbase = 0;
    int privatebase = 0;
    int privatesize = 0;
    int gsubrbase = 0;
    int lsubrbase = 0;
    int gsubrsoffset = 0;
    int lsubrsoffset = 0;
    int defaultWidthX = 0;
    int nominalWidthX = 0;
    int nglyphs = 1;

    private void readDict(Range r) {
//	System.out.println("reading dictionary from "+r.getStart()+" to "+r.getEnd());
        pos = r.getStart();
        while (pos < r.getEnd()) {
            int cmd = readCommand(false);
            if (cmd == 1006) { // charstringtype, default=2
                charstringtype = (int) stack[0];
            } else if (cmd == 1007) { // fontmatrix
                if (stackptr == 4) {
                    at = new AffineTransform((float) stack[0], (float) stack[1],
                            (float) stack[2], (float) stack[3],
                            0, 0);
                } else {
                    at = new AffineTransform((float) stack[0], (float) stack[1],
                            (float) stack[2], (float) stack[3],
                            (float) stack[4], (float) stack[5]);
                }
            } else if (cmd == 15) { // charset
                charsetbase = (int) stack[0];
            } else if (cmd == 16) { // encoding
                encodingbase = (int) stack[0];
            } else if (cmd == 17) { // charstrings
                charstringbase = (int) stack[0];
            } else if (cmd == 18) { // private
                privatesize = (int) stack[0];
                privatebase = (int) stack[1];
            } else if (cmd == 19) { // subrs (in Private dict)
                lsubrbase = (int) stack[0];
                lsubrsoffset = calcoffset(lsubrbase);
            } else if (cmd == 20) { // defaultWidthX (in Private dict)
                defaultWidthX = (int) stack[0];
            } else if (cmd == 21) { // nominalWidthX (in Private dict)
                nominalWidthX = (int) stack[0];
            }
            stackptr = 0;
        }
    }

    private int readCommand(boolean charstring) {
        while (true) {
            int type = readNext(charstring);
            if (type == CMD) {
                System.out.print("CMD= " + num + ", args=");
                for (int i = 0; i < stackptr; i++) {
                    System.out.print(" " + stack[i]);
                }
                System.out.println();
                return num;
            } else {
                stack[stackptr++] = (type == NUM) ? (float) num : (float) fnum;
            }
        }
    }

    private void readEncodingData(int base) {
        if (base == 0) {  // this is the StandardEncoding
            System.out.println("**** STANDARD ENCODING!");
        // TODO: copy standard encoding
        } else if (base == 1) {  // this is the expert encoding
            System.out.println("**** EXPERT ENCODING!");
        // TODO: copy ExpertEncoding
        } else {
            pos = base;
            int encodingtype = readByte();
            if ((encodingtype & 127) == 0) {
                System.out.println("**** Type 0 Encoding:");

                int ncodes = readByte();
                for (int i = 1; i < ncodes + 1; i++) {
                    encoding[i] = readByte();
                    System.out.println("Encoding[" + i + "] = " + encoding[i]);
                }
            } else if ((encodingtype & 127) == 1) {
                System.out.println("**** Type 1 Encoding:");

                int nranges = readByte();
                int pos = 0;
                for (int i = 0; i < nranges; i++) {
                    int start = readByte();
                    int more = readByte();
                    for (int j = start; j < start + more + 1; j++) {
                        System.out.println("Encoding[" + pos + "] = " + j);
                        encoding[pos++] = j;
                    }
                }
            } else {
                System.out.println("Bad encoding type: " + encodingtype);
            }
        // TODO: now check for supplemental encoding data
        }
    }

    private void readGlyphNames(int base) {
        if (base == 0) {
            System.out.println("**** Identity glyphNames");

            glyphnames = new int[229];
            for (int i = 0; i < glyphnames.length; i++) {
                glyphnames[i] = i;
            }
            return;
        } else if (base == 1) {
            System.out.println("**** Type1CExpertCharset glyphNames");
            glyphnames = FontSupport.type1CExpertCharset;
            return;
        } else if (base == 2) {
            System.out.println("**** Type1CExpertSubCharset glyphNames");
            glyphnames = FontSupport.type1CExpertSubCharset;
            return;
        }

        System.out.print("**** Custom glyphNames Type ");

        // nglyphs has already been set.
        glyphnames = new int[nglyphs];
        glyphnames[0] = 0;
        pos = base;
        int type = readByte();
        if (type == 0) {
            System.out.println("0");

            for (int i = 1; i < nglyphs; i++) {
                glyphnames[i] = readInt(2);
                System.out.println("glyphnames[" + i + "] = " + glyphnames[i]);
            }
        } else if (type == 1) {
            System.out.println("1");

            int n = 1;
            while (n < nglyphs) {
                int sid = readInt(2);
                int range = readByte() + 1;
                for (int i = 0; i < range; i++) {
                    System.out.println("glyphnames[" + n + "] = " + sid);
                    glyphnames[n++] = sid++;
                }
            }
        } else if (type == 2) {
            System.out.println("2");

            int n = 1;
            while (n < nglyphs) {
                int sid = readInt(2);
                int range = readInt(2) + 1;
                for (int i = 0; i < range; i++) {
                    System.out.println("glyphnames[" + n + "] = " + sid);
                    glyphnames[n++] = sid++;
                }
            }
        }
    }

    private void readNames(int base) {
        pos = base;
        int nextra = readInt(2);
        names = new String[nextra];
        //	safenames= new String[nextra];
        for (int i = 0; i < nextra; i++) {
            Range r = getIndexEntry(base, i);
            names[i] = new String(data, r.getStart(), r.getLen());
            System.out.println("Read name: " + i + " from " + r.getStart() + " to " + r.getEnd() + ": " + safe(names[i]));
        }
    }

    private void parse() {
        int majorVersion = readByte();
        int minorVersion = readByte();
        int hdrsz = readByte();
        int offsize = readByte();
        // jump over rest of header: base of font names index
        int fnames = hdrsz;
        // offset in the file of the array of font dicts
        int topdicts = fnames + getIndexSize(fnames);
        // offset in the file of local names
        int names = topdicts + getIndexSize(topdicts);
        // offset in the file of the array of global subroutines
        gsubrbase = names + getIndexSize(names);
        gsubrsoffset = calcoffset(gsubrbase);
        // read extra names
        readNames(names);
        // does this file have more than one font?
        pos = topdicts;
        if (readInt(2) != 1) {
            printData();
            throw new RuntimeException("More than one font in this file!");
        }
        // read first dict
        System.out.println("TOPDICT[0]:");
        readDict(getIndexEntry(topdicts, 0));
        // read the private dictionary
        System.out.println("PRIVATE DICT:");
        readDict(new Range(privatebase, privatesize));
        // calculate the number of glyphs
        pos = charstringbase;
        nglyphs = readInt(2);
        // now get the glyph names
        System.out.println("GLYPHNAMES:");
        readGlyphNames(charsetbase);
        // now figure out the encoding
        System.out.println("ENCODING:");
        readEncodingData(encodingbase);
        // now get the glyphs
        System.out.println("GLYPHS:");
        readGlyphs(charstringbase);
    }

    private String safe(String src) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c >= 32 && c < 128) {
                sb.append(c);
            } else {
                sb.append("<" + (int) c + ">");
            }
        }
        return sb.toString();
    }

    class GlyphPoint {

        float x, y;
        boolean curvecontrol;
        GeneralPath gp;

        public GlyphPoint(float x, float y, boolean curvectrl) {
            this.x = x;
            this.y = y;
            this.curvecontrol = curvectrl;
            gp = new GeneralPath();
            if (curvectrl) {
                gp.moveTo(x - 4, y - 4);
                gp.lineTo(x + 4, y + 4);
                gp.moveTo(x - 4, y + 4);
                gp.lineTo(x + 4, y - 4);
            } else {
                gp.moveTo(x - 4, y - 4);
                gp.lineTo(x - 4, y + 4);
                gp.lineTo(x + 4, y + 4);
                gp.lineTo(x + 4, y - 4);
                gp.closePath();
            }
        }
    }

    class GlyphData {

        GeneralPath gp;
        GeneralPath advp;
        ArrayList<GlyphPoint> points;
        float x, y;
        float advance;
        String name;

        public GlyphData() {
            gp = new GeneralPath();
            //	    advp= new GeneralPath();
            points = new ArrayList<GlyphPoint>  ();
            x = y = 0;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void lineTo(float x, float y) {
            gp.lineTo(x, y);
            this.x = x;
            this.y = y;
            points.add(new GlyphPoint(x, y, false));
        }

        public void moveTo(float x, float y) {
            gp.moveTo(x, y);
            this.x = x;
            this.y = y;
            points.add(new GlyphPoint(x, y, false));
        }

        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            gp.curveTo(x1, y1, x2, y2, x3, y3);
            this.x = x3;
            this.y = y3;
            points.add(new GlyphPoint(x1, y1, true));
            points.add(new GlyphPoint(x2, y2, true));
            points.add(new GlyphPoint(x3, y3, false));
        }

        public void closePath() {
            gp.closePath();
        }

        public void addGlyph(GlyphData gv, float x, float y) {
            AffineTransform at = AffineTransform.getTranslateInstance(x, y);
            PathIterator pi = gv.gp.getPathIterator(at);

            float[] coords = new float[6];

            while (!pi.isDone()) {
                int type = pi.currentSegment(coords);

                switch (type) {
                    case PathIterator.SEG_MOVETO:
                        moveTo(coords[0], coords[1]);
                        break;
                    case PathIterator.SEG_LINETO:
                        lineTo(coords[0], coords[1]);
                        break;
                    case PathIterator.SEG_CUBICTO:
                        curveTo(coords[0], coords[1], coords[2], coords[3],
                                coords[4], coords[5]);
                        break;
                    case PathIterator.SEG_CLOSE:
                        closePath();
                        break;
                    default:
                        System.out.println("Unknown path type: " + type);
                        break;
                }

                pi.next();
            }
        }

        public void setAdvance(float adv) {
            advance = adv;
            advp = new GeneralPath();
            advp.moveTo(-2, -2);
            advp.lineTo(2, 2);
            advp.moveTo(-2, 2);
            advp.lineTo(2, -2);
            advp.moveTo(adv - 2, -2);
            advp.lineTo(adv, 0);
            advp.lineTo(adv + 2, -2);
            advp.moveTo(adv, 0);
            advp.lineTo(adv, -8);
        }

        public void draw(Graphics2D g) {
            g.setColor(fillColor);
            g.fill(gp);
            g.setColor(Color.black);
            g.draw(gp);
            for (int i = 0; i < points.size(); i++) {
                GlyphPoint p = points.get(i);
                g.setColor(Color.red);
                g.draw(p.gp);
                g.setColor(Color.blue);
                g.setFont(gfont);
                g.drawString(String.valueOf(i), p.x + 3, p.y + 3);
            }
            g.setColor(Color.black);
            //	    System.out.println("Advance: "+advance);
            g.draw(advp);
            if (name != null) {
                g.setFont(gfont);
                g.drawString(name, 0, -40);
            }
        }
    }

    private void readGlyphs(int base) {
        for (int i = 1; i < nglyphs; i++) {
            System.out.println("Reading glyph " + safe(getSID(glyphnames[i])));
            charnames.add(getSID(glyphnames[i]));
        }
    }

    /**
     * Read a single glyph, given its offset
     */
    public GlyphData readGlyph(String sid) {
        int index = getNameIndex(sid);

        // now find the glyph with that name
        for (int i = 0; i < glyphnames.length; i++) {
            if (glyphnames[i] == index) {
                return readGlyph(i);
            }
        }

        // not found -- return the unknown glyph
        return readGlyph(0);
    }

    /**
     * Read a glyph, given an index
     */
    public GlyphData readGlyph(int index) {
        String sid = getSID(index);

        if (charset.containsKey(sid)) {
            return (GlyphData) charset.get(sid);
        }

        Range r = getIndexEntry(charstringbase, index);

        FlPoint pt = new FlPoint();
        GlyphData gd = new GlyphData();

        parseGlyph(r, gd, pt);
        gd.setName(sid);

        charset.put(sid, gd);
        return gd;
    }

    /**
     * build an accented character out of two pre-defined glyphs.
     * @param x the x offset of the accent
     * @param y the y offset of the accent
     * @param a the index of the base glyph
     * @param b the index of the accent glyph
     * @param gp the GeneralPath into which the combined glyph will be
     * written.
     */
    private void buildAccentChar(float x, float y, float a, float b, GlyphData gv) {
        System.out.println("Building accent character!!!!!!");

        for (int i = 0; i < nglyphs; i++) {
            if (encoding[i] == (int) a) {
                gv.addGlyph(readGlyph(i + 1), x, y);
            } else if (encoding[i] == (int) b) {
                gv.addGlyph(readGlyph(i + 1), 0, 0);
            }
        }

    /*	if (shapes[b]!=null) {
    gp.append(shapes[b], false);
    }
    if (shapes[a]!=null) {
    gp.append(shapes[a], false);
    }*/
    }

    public int calcoffset(int base) {
        int len = getIndexSize(base);
        if (len < 1240) {
            return -107;
        } else if (len < 33900) {
            return -1131;
        } else {
            return -32768;
        }
    }

    public String getSID(int id) {
        if (id < FontSupport.stdNames.length) {
            return FontSupport.stdNames[id];
        } else {
            id -= FontSupport.stdNames.length;
            return names[id];
        }
    }

    /**
     * get the index of a particular name.  The name table starts with
     * the standard names in FontSupport.stdNames, and is appended by
     * any names in the name table from this font's dictionary.
     */
    private int getNameIndex(String name) {
        int val = FontSupport.findName(name, FontSupport.stdNames);
        if (val == -1) {
            val = FontSupport.findName(name, names) + FontSupport.stdNames.length;
        }
        if (val == -1) {
            val = 0;
        }
        return val;
    }

    public void parseGlyph(Range r, GlyphData gp, FlPoint pt) {
        pos = r.getStart();
        int i;
        float x1, y1, x2, y2, x3, y3, ybase;
        int hold;
        int stemhints = 0;
        gp.setAdvance(defaultWidthX);
        while (pos < r.getEnd()) {
            int cmd = readCommand(true);
            hold = 0;
            switch (cmd) {
                case 1: // hstem
                case 3: // vstem
                    if ((stackptr & 1) == 1) {
                        gp.setAdvance(nominalWidthX + stack[0]);
                    }
                    stackptr = 0;
                    break;
                case 4: // vmoveto
                    if (stackptr > 1) {  // this is the first call, arg1 is width
                        gp.setAdvance(nominalWidthX + stack[0]);
                        stack[0] = stack[1];
                    }
                    pt.y += stack[0];
                    if (pt.open) {
                        gp.closePath();
                    }
                    pt.open = false;
                    gp.moveTo(pt.x, pt.y);
                    stackptr = 0;
                    break;
                case 5: // rlineto
                    for (i = 0; i < stackptr;) {
                        pt.x += stack[i++];
                        pt.y += stack[i++];
                        gp.lineTo(pt.x, pt.y);
                    }
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 6: // hlineto
                    for (i = 0; i < stackptr;) {
                        if ((i & 1) == 0) {
                            pt.x += stack[i++];
                        } else {
                            pt.y += stack[i++];
                        }
                        gp.lineTo(pt.x, pt.y);
                    }
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 7: // vlineto
                    for (i = 0; i < stackptr;) {
                        if ((i & 1) == 0) {
                            pt.y += stack[i++];
                        } else {
                            pt.x += stack[i++];
                        }
                        gp.lineTo(pt.x, pt.y);
                    }
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 8: // rrcurveto
                    for (i = 0; i < stackptr;) {
                        x1 = pt.x + stack[i++];
                        y1 = pt.y + stack[i++];
                        x2 = x1 + stack[i++];
                        y2 = y1 + stack[i++];
                        pt.x = x2 + stack[i++];
                        pt.y = y2 + stack[i++];
                        gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    }
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 10: // callsubr
                    hold = pos;
                    i = (int) stack[--stackptr] + lsubrsoffset;
                    Range lsubr = getIndexEntry(lsubrbase, i);
                    parseGlyph(lsubr, gp, pt);
                    pos = hold;
                    break;
                case 11: // return
                    return;
                case 14: // endchar
                    if (stackptr == 5) {
                        buildAccentChar(stack[1], stack[2], stack[3], stack[4], gp);
                    }

                    if (pt.open) {
                        gp.closePath();
                    }
                    pt.open = false;
                    stackptr = 0;
                    return;
                case 18: // hstemhm
                    if ((stackptr & 1) == 1) {
                        gp.setAdvance(nominalWidthX + stack[0]);
                    }
                    stemhints += stackptr / 2;
                    stackptr = 0;
                    break;
                case 19: // hintmask
                case 20: // cntrmask
                    if ((stackptr & 1) == 1) {
                        gp.setAdvance(nominalWidthX + stack[0]);
                    }
                    stemhints += stackptr / 2;
                    System.out.println("Added " + stackptr + " extra bits;  skipping " + ((stemhints - 1) / 8 + 1) + " from " + stemhints);
                    pos += (stemhints - 1) / 8 + 1;
                    stackptr = 0;
                    break;
                case 21: // rmoveto
                    if (stackptr > 2) {
                        gp.setAdvance(nominalWidthX + stack[0]);
                        stack[0] = stack[1];
                        stack[1] = stack[2];
                    }
                    pt.x += stack[0];
                    pt.y += stack[1];
                    if (pt.open) {
                        gp.closePath();
                    }
                    gp.moveTo(pt.x, pt.y);
                    pt.open = false;
                    stackptr = 0;
                    break;
                case 22: // hmoveto
                    if (stackptr > 1) {
                        gp.setAdvance(nominalWidthX + stack[0]);
                        stack[0] = stack[1];
                    }
                    pt.x += stack[0];
                    if (pt.open) {
                        gp.closePath();
                    }
                    gp.moveTo(pt.x, pt.y);
                    pt.open = false;
                    stackptr = 0;
                    break;
                case 23: // vstemhm
                    if ((stackptr & 1) == 1) {
                        gp.setAdvance(nominalWidthX + stack[0]);
                    }
                    stemhints += stackptr / 2;
                    stackptr = 0;
                    break;
                case 24: // rcurveline
                    for (i = 0; i < stackptr - 2;) {
                        x1 = pt.x + stack[i++];
                        y1 = pt.y + stack[i++];
                        x2 = x1 + stack[i++];
                        y2 = y1 + stack[i++];
                        pt.x = x2 + stack[i++];
                        pt.y = y2 + stack[i++];
                        gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    }
                    pt.x += stack[i++];
                    pt.y += stack[i++];
                    gp.lineTo(pt.x, pt.y);
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 25: // rlinecurve
                    for (i = 0; i < stackptr - 6;) {
                        pt.x += stack[i++];
                        pt.y += stack[i++];
                        gp.lineTo(pt.x, pt.y);
                    }
                    x1 = pt.x + stack[i++];
                    y1 = pt.y + stack[i++];
                    x2 = x1 + stack[i++];
                    y2 = y1 + stack[i++];
                    pt.x = x2 + stack[i++];
                    pt.y = y2 + stack[i++];
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 26: // vvcurveto
                    i = 0;
                    if ((stackptr & 1) == 1) { // odd number of arguments
                        pt.x += stack[i++];
                    }
                    while (i < stackptr) {
                        x1 = pt.x;
                        y1 = pt.y + stack[i++];
                        x2 = x1 + stack[i++];
                        y2 = y1 + stack[i++];
                        pt.x = x2;
                        pt.y = y2 + stack[i++];
                        gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    }
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 27: // hhcurveto
                    i = 0;
                    if ((stackptr & 1) == 1) { // odd number of arguments
                        pt.y += stack[i++];
                    }
                    while (i < stackptr) {
                        x1 = pt.x + stack[i++];
                        y1 = pt.y;
                        x2 = x1 + stack[i++];
                        y2 = y1 + stack[i++];
                        pt.x = x2 + stack[i++];
                        pt.y = y2;
                        gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    }
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 29: // callgsubr
                    hold = pos;
                    i = (int) stack[--stackptr] + gsubrsoffset;
                    Range gsubr = getIndexEntry(gsubrbase, i);
                    parseGlyph(gsubr, gp, pt);
                    pos = hold;
                    break;
                case 30: // vhcurveto
                    hold = 4;
                case 31: // hvcurveto
                    for (i = 0; i < stackptr;) {
                        boolean hv = (((i + hold) & 4) == 0);
                        x1 = pt.x + (hv ? stack[i++] : 0);
                        y1 = pt.y + (hv ? 0 : stack[i++]);
                        x2 = x1 + stack[i++];
                        y2 = y1 + stack[i++];
                        pt.x = x2 + (hv ? 0 : stack[i++]);
                        pt.y = y2 + (hv ? stack[i++] : 0);
                        if (i == stackptr - 1) {
                            if (hv) {
                                pt.x += stack[i++];
                            } else {
                                pt.y += stack[i++];
                            }
                        }
                        gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    }
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 1003: // and
                    x1 = stack[--stackptr];
                    y1 = stack[--stackptr];
                    stack[stackptr++] = ((x1 != 0) && (y1 != 0)) ? 1 : 0;
                    break;
                case 1004: // or
                    x1 = stack[--stackptr];
                    y1 = stack[--stackptr];
                    stack[stackptr++] = ((x1 != 0) || (y1 != 0)) ? 1 : 0;
                    break;
                case 1005: // not
                    x1 = stack[--stackptr];
                    stack[stackptr++] = (x1 == 0) ? 1 : 0;
                    break;
                case 1009: // abs
                    stack[stackptr - 1] = Math.abs(stack[stackptr - 1]);
                    break;
                case 1010: // add
                    x1 = stack[--stackptr];
                    y1 = stack[--stackptr];
                    stack[stackptr++] = x1 + y1;
                    break;
                case 1011: // sub
                    x1 = stack[--stackptr];
                    y1 = stack[--stackptr];
                    stack[stackptr++] = y1 - x1;
                    break;
                case 1012: // div
                    x1 = stack[--stackptr];
                    y1 = stack[--stackptr];
                    stack[stackptr++] = y1 / x1;
                    break;
                case 1014: // neg
                    stack[stackptr - 1] = -stack[stackptr - 1];
                    break;
                case 1015: // eq
                    x1 = stack[--stackptr];
                    y1 = stack[--stackptr];
                    stack[stackptr++] = (x1 == y1) ? 1 : 0;
                    break;
                case 1018: // drop
                    stackptr--;
                    break;
                case 1020: // put
                    i = (int) stack[--stackptr];
                    x1 = stack[--stackptr];
                    temps[i] = x1;
                    break;
                case 1021: // get
                    i = (int) stack[--stackptr];
                    stack[stackptr++] = temps[i];
                    break;
                case 1022: // ifelse
                    if (stack[stackptr - 2] > stack[stackptr - 1]) {
                        stack[stackptr - 4] = stack[stackptr - 3];
                    }
                    stackptr -= 3;
                    break;
                case 1023: // random
                    stack[stackptr++] = (float) Math.random();
                    break;
                case 1024: // mul
                    x1 = stack[--stackptr];
                    y1 = stack[--stackptr];
                    stack[stackptr++] = y1 * x1;
                    break;
                case 1026: // sqrt
                    stack[stackptr - 1] = (float) Math.sqrt(stack[stackptr - 1]);
                    break;
                case 1027: // dup
                    x1 = stack[stackptr - 1];
                    stack[stackptr++] = x1;
                    break;
                case 1028: // exch
                    x1 = stack[stackptr - 1];
                    stack[stackptr - 1] = stack[stackptr - 2];
                    stack[stackptr - 2] = x1;
                    break;
                case 1029: // index
                    i = (int) stack[stackptr - 1];
                    if (i < 0) {
                        i = 0;
                    }
                    stack[stackptr - 1] = stack[stackptr - 2 - i];
                    break;
                case 1030: // roll
                    i = (int) stack[--stackptr];
                    int n = (int) stack[--stackptr];
                    // roll n number by i (+ = upward)
                    if (i > 0) {
                        i = i % n;
                    } else {
                        i = n - (-i % n);
                    }
                    // x x x x i y y y -> y y y x x x x i (where i=3)
                    if (i > 0) {
                        float roll[] = new float[n];
                        System.arraycopy(stack, stackptr - 1 - i, roll, 0, i);
                        System.arraycopy(stack, stackptr - 1 - n, roll, i, n - i);
                        System.arraycopy(roll, 0, stack, stackptr - 1 - n, n);
                    }
                    break;
                case 1034: // hflex
                    x1 = pt.x + stack[0];
                    y1 = ybase = pt.y;
                    x2 = x1 + stack[1];
                    y2 = y1 + stack[2];
                    pt.x = x2 + stack[3];
                    pt.y = y2;
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    x1 = pt.x + stack[4];
                    y1 = pt.y;
                    x2 = x1 + stack[5];
                    y2 = ybase;
                    pt.x = x2 + stack[6];
                    pt.y = y2;
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 1035: // flex
                    x1 = pt.x + stack[0];
                    y1 = pt.y + stack[1];
                    x2 = x1 + stack[2];
                    y2 = y1 + stack[3];
                    pt.x = x2 + stack[4];
                    pt.y = y2 + stack[5];
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    x1 = pt.x + stack[6];
                    y1 = pt.y + stack[7];
                    x2 = x1 + stack[8];
                    y2 = y1 + stack[9];
                    pt.x = x2 + stack[10];
                    pt.y = y2 + stack[11];
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 1036: // hflex1
                    ybase = pt.y;
                    x1 = pt.x + stack[0];
                    y1 = pt.y + stack[1];
                    x2 = x1 + stack[2];
                    y2 = y1 + stack[3];
                    pt.x = x2 + stack[4];
                    pt.y = y2;
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    x1 = pt.x + stack[5];
                    y1 = pt.y;
                    x2 = x1 + stack[6];
                    y2 = y1 + stack[7];
                    pt.x = x2 + stack[8];
                    pt.y = ybase;
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    pt.open = true;
                    stackptr = 0;
                    break;
                case 1037: // flex1
                    ybase = pt.y;
                    float xbase = pt.x;
                    x1 = pt.x + stack[0];
                    y1 = pt.y + stack[1];
                    x2 = x1 + stack[2];
                    y2 = y1 + stack[3];
                    pt.x = x2 + stack[4];
                    pt.y = y2 + stack[5];
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    x1 = pt.x + stack[6];
                    y1 = pt.y + stack[7];
                    x2 = x1 + stack[8];
                    y2 = y1 + stack[9];
                    if (Math.abs(x2 - xbase) > Math.abs(y2 - ybase)) {
                        pt.x = x2 + stack[10];
                        pt.y = ybase;
                    } else {
                        pt.x = xbase;
                        pt.y = y2 + stack[10];
                    }
                    gp.curveTo(x1, y1, x2, y2, pt.x, pt.y);
                    pt.open = true;
                    stackptr = 0;
                    break;
                default:
                    System.out.println("ERROR! TYPE1C CHARSTRING CMD IS " + cmd);
                    break;
            }
        }
    }

    public static void main(String args[]) {
        if (args.length != 1) {
            System.out.println("Need the name of a cff font.");
            System.exit(0);
        }
        JFrame jf = new JFrame("Font test: " + args[0]);
        try {
            FileInputStream fis = new FileInputStream(args[0]);
            TestType1CFont panel = new TestType1CFont(fis);
            jf.getContentPane().add(panel);
            jf.pack();
            jf.setVisible(true);
            panel.requestFocus();
        } catch (IOException ioe) {
        }
    }
}
