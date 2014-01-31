/*
 * $Id: Type1Decode.java,v 1.2 2007-12-20 18:33:32 rbair Exp $
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
 * Type1Decode.java
 *
 * Created on August 28, 2003, 11:54 AM
 */

import com.sun.pdfview.HexDump;

import java.io.*;

/**
 *
 * @author  jkaplan
 */
public class Type1Decode {
    private static byte[] decrypt(byte[]d, int start, int end, int key, int skip) {
	if (end-start-skip<0) {
	    skip= 0;
	}
	byte[] o= new byte[end-start-skip];
	int r= key;
	int ipos;
	int c1= 52845;
	int c2= 22719;
	for(ipos= start; ipos<end; ipos++) {
	    int c= d[ipos]&0xff;
	    int p= (c ^ (r>>8))&0xff;
            
            if (ipos - start < 16) {
                System.out.println("c = " + Integer.toHexString(c) + 
                                   ", p = " + Integer.toHexString(p) + 
                                   ", r = " + Integer.toHexString(r));
            }
            
	    r= ((c+r)*c1+c2)&0xffff;
	    if (ipos-start-skip>=0) {
		o[ipos-start-skip]= (byte)p;
	    }
	}
	return o;
    }
   
    private static byte[] readASCII(byte[] data, int start, int end) {
        // each byte of output is derived from one character (two bytes) of
        // input
        byte[] o = new byte[(end - start) / 2];
        
        int count = 0;
    	int bit = 0;
    
        for (int loc = start; loc < end ; loc ++) {
            char c = (char) (data[loc] & 0xff);
            byte b = (byte) 0;
            
            if (c >= '0' && c <= '9') {
                b = (byte) (c - '0');
            } else if (c >= 'a' && c <= 'f') {
                b = (byte) (10 + (c - 'a'));
            } else if (c >= 'A' && c <= 'F') {
                b = (byte) (10 + (c - 'A'));
            } else {
                // linefeed or something.  Skip.
                continue;
            }
           
            // which half of the byte are we?
            if ((bit++ % 2) == 0) {
                o[count] = (byte) (b << 4);
            } else {
                o[count++] |= b;
            }
        }
        
        return o;
    }

    private static boolean isASCII(byte[] data, int start) {
        // look at the first 4 bytes
        for (int i = start; i < start + 4; i++) {
            // get the byte as a character
            char c = (char) (data[i] & 0xff);
            
            if (c >= '0' && c <= '9') {
                continue;
            } else if (c >= 'a' && c <= 'f') {
                continue;
            } else if (c >= 'A' && c <= 'F') {
                continue;
            } else {
                // out of range
                return false;
            }
        }
        
        // all were in range, so it is ASCII
        return true;
    }
    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage:");
            System.out.println("    Type1Decode <filename> <decode-start>");
            System.exit(-1);
        }
    
        try {
            File file = new File(args[0]);
            
            if (!file.exists() || !file.canRead()) {
                System.out.println("Can't read file: " + args[0]);
                System.exit(-1);
            }
            
            FileInputStream fis = new FileInputStream(file);
            
            byte[] data = new byte[(int) file.length()];
            int cur = 0;
            while (cur < file.length()) {
                cur += fis.read(data, cur, data.length - cur);
            }
            
            int start = 0;
            
            if ((data[0] & 0xff)== 0x80) {
                start = (data[2] & 0xff);
                start |= (data[3] & 0xff) << 8;
                start |= (data[4] & 0xff) << 16;
                start |= (data[5] & 0xff) << 24;
                
                start += 6;
            } else if (args.length > 1) {
                start = Integer.parseInt(args[1]);
            } else {
                System.out.println("Unable to read size");
                System.exit(-1);
            }
            
            int size = data.length - start;
            
            if (isASCII(data, start)) {
                data = readASCII(data, start, size);
                start = 0;
                size = data.length;
            } else if((data[start] & 0xff) == 0x80) {
                size = (data[start + 2] & 0xff);
                size |= (data[start + 3] & 0xff) << 8;
                size |= (data[start + 4] & 0xff) << 16;
                size |= (data[start + 5] & 0xff) << 24;
                
                start += 6;
            }
            
            byte[] outData = decrypt(data, start, start + size, 55665, 4);
            
            HexDump.printData(outData);
            
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
}
