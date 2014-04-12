/*
 * $Id: TTFTest.java,v 1.3 2009-08-07 23:18:33 tomoke Exp $
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

/*
 * TTFTest.java
 *
 * Created on August 9, 2003, 5:57 PM
 */

import com.sun.pdfview.font.ttf.*;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.font.*;

/**
 *
 * @author  jon
 */
public class TTFTest {  
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
       if (args.length < 1) {
	    System.out.println("Usage: ");
	    System.out.println("    TTFTest <input file> <output file>");
	    System.exit(-1);
	}

	try {
	    RandomAccessFile raf = new RandomAccessFile(args[0], "r");
 
	    int size = (int) raf.length();
            byte[] data = new byte[size];

	    raf.readFully(data);
	 
            TrueTypeFont ttf = TrueTypeFont.parseFont(data);
             
            MaxpTable maxp = (MaxpTable) ttf.getTable("maxp");
            int nglyphs = maxp.getNumGlyphs();
            
            if (ttf.getTable("cmap") == null) {
                
                byte[] map = new byte[256];
                for (int i = 0; i < map.length; i++) {
                    if (i < nglyphs) {
                        map[i] = (byte) (i);
                    } else {
                        map[i] = 0;
                    }
                }
               
                CMapFormat0 newMap = (CMapFormat0) CMap.createMap((short) 0, (short) 0);
                newMap.setMap(map);
                
                CMapFormat4 newMap4 = (CMapFormat4) CMap.createMap((short) 4, (short) 0);
                newMap4.addSegment((short) 1, (short) (nglyphs + 0), (short) 0);
                
                CmapTable cmap = (CmapTable) TrueTypeTable.createTable(ttf, "cmap");
                cmap.addCMap((short) 1, (short) 0, newMap);
                cmap.addCMap((short) 3, (short) 1, newMap4);
                
                ttf.addTable("cmap", cmap);
            }
            
            if (ttf.getTable("name") == null) {
                NameTable name = (NameTable) TrueTypeTable.createTable(ttf, "name");
                
                short platID = NameTable.PLATFORMID_MACINTOSH;
                short encID  = NameTable.ENCODINGID_MAC_ROMAN;
                short langID = NameTable.LANGUAGEID_MAC_ENGLISH;
                
              /*  name.addRecord(platID, encID, langID,
                                NameTable.NAMEID_COPYRIGHT,
                               "Copyright (c) 2000 Bigelow & Holmes Inc. Pat. Des 289,421.");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_FAMILY,
                               "Lucida Bright");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_SUBFAMILY,
                               "Regular");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_SUBFAMILY_UNIQUE,
                               "Lucida Bright Regular: B&H:2000");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_FULL_NAME,
                               "Lucida Bright Regular");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_VERSION,
                               "January 28, 2000; 1.10 (JAVA)");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_POSTSCRIPT_NAME,
                               "LucidaBright");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_TRADEMARK,
                               "Lucida is a registered trademark of Bigelow & Holmes Inc.");
                */
                
                platID = NameTable.PLATFORMID_MICROSOFT;
                encID  = 1;
                langID = 1033;
                
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_COPYRIGHT,
                               "Copyright (c) 2000 Bigelow & Holmes Inc. Pat. Des 289,421.");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_FAMILY,
                               "Lucida Bright");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_SUBFAMILY,
                               "Regular");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_SUBFAMILY_UNIQUE,
                               "Lucida Bright Regular: B&H:2000");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_FULL_NAME,
                               "Lucida Bright Regular");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_VERSION,
                               "January 28, 2000; 1.10 (JAVA)");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_POSTSCRIPT_NAME,
                               "LucidaBright");
                name.addRecord(platID, encID, langID,
                               NameTable.NAMEID_TRADEMARK,
                               "Lucida is a registered trademark of Bigelow & Holmes Inc.");
              
                ttf.addTable("name", name);
            }
            
            ttf.getTable("head");
            ttf.getTable("hhea");
            
            System.out.println(ttf);
            
            if (args.length == 2) {
                FileOutputStream fis = new FileOutputStream(args[1]);
                fis.write(ttf.writeFont());
                fis.close();
            }
            
            InputStream fontStream = new ByteArrayInputStream(ttf.writeFont());
            
            Font f = Font.createFont(Font.TRUETYPE_FONT, fontStream);
            
            System.out.println("Attributes of font " + f.getFontName() + "(" + f.getNumGlyphs() + "):");
            Map m = f.getAttributes();
            for (Iterator i = m.keySet().iterator(); i.hasNext();) {
                Object key = i.next();
                Object value = m.get(key);
                
                System.out.println(key + " = " + value);
            }
            System.out.println();
            
            Font f2 = f.deriveFont((float) 18.0);
            
            JFrame jf = new JFrame();
            jf.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent we) {
                    System.exit(1);
                }
            });
            
            JPanel thePanel = new JPanel();
            thePanel.setLayout(new GridLayout(0,32));
            
            for (int i = 0; i < nglyphs; i++) {
                JLabel lbl = new JLabel(Integer.toHexString(i) + ": ");
                
                JLabel val = new JLabel(String.valueOf((char) i));
                val.setFont(f2);
                
                thePanel.add(lbl);
                thePanel.add(val);
            }
           
            JScrollPane jsp = new JScrollPane(thePanel);
            
            jf.getContentPane().add(jsp);
                
            jf.pack();
            jf.setVisible(true);
	} catch (Exception e) {
	    e.printStackTrace();
	}
       
    }
    
}
