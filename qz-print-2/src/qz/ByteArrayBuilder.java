/**
 * @author Antoni Ten Monrós
 * 
 * Copyright (C) 2013 Tres Finocchiaro, QZ Industries
 * Copyright (C) 2013 Antoni Ten Monrós
 *
 * IMPORTANT:  This software is dual-licensed
 * 
 * LGPL 2.1
 * This is free software.  This software and source code are released under 
 * the "LGPL 2.1 License".  A copy of this license should be distributed with 
 * this software. http://www.gnu.org/licenses/lgpl-2.1.html
 * 
 * QZ INDUSTRIES SOURCE CODE LICENSE
 * This software and source code *may* instead be distributed under the 
 * "QZ Industries Source Code License", available by request ONLY.  If source 
 * code for this project is to be made proprietary for an individual and/or a
 * commercial entity, written permission via a copy of the "QZ Industries Source
 * Code License" must be obtained first.  If you've obtained a copy of the 
 * proprietary license, the terms and conditions of the license apply only to 
 * the licensee identified in the agreement.  Only THEN may the LGPL 2.1 license
 * be voided.
 * 
 */

package qz;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;

/*
 * Provides a simple way and efficient for concatenating byte arrays, similar
 * in purpose to <code>StringBuilder</code>. Objects of this class are not 
 * thread safe and include no synchronization
 * 
 */


/**
 * @author Antoni Ten Monrós
 */

public final class ByteArrayBuilder {
    private ArrayList<byte[]> buffer;
    
    private int length = 0;
    
    private byte[] contents=null;

    /**
     * Gives the number of bytes currently stored in this <code>ByteArrayBuilder</code>
     *
     * @return the number of bytes in the <code>ByteArrayBuilder</code>
     */
    public int getLength() {
        return length;
    }

    /**
     * Creates a new <code>ByteArrayBuilder</code> and sets initial capacity to 10
     */
    public ByteArrayBuilder() {
        buffer=new ArrayList<byte[]>(10);
    }
    
    /**
     * Creates a new <code>ByteArrayBuilder</code> and sets initial capacity to 
     * <code>initialCapacity</code>
     * 
     * @param initialCapacity the initial capacity of the <code>ByteArrayBuilder</code>
     */
    public ByteArrayBuilder(int initialCapacity){
        buffer=new ArrayList<byte[]>(initialCapacity);
    }

    /**
     * Creates a new <code>ByteArrayBuilder</code>, sets initial capacity to 10
     * and appends <code>initialContents</code>
     * 
     * @param initialContents the initial contents of the ByteArrayBuilder
     */
    public ByteArrayBuilder(byte[] initialContents) {
        this();
        this.append(initialContents);
    }
    
    /**
     * Creates a new <code>ByteArrayBuilder</code>, sets initial capacity to 
     * <code>initialContents</code> and appends <code>initialContents</code>
     * 
     * @param initialContents the initial contents of the <code>ByteArrayBuilder</code>
     * @param initialCapacity the initial capacity of the <code>ByteArrayBuilder</code>
     */
    public ByteArrayBuilder(byte[] initialContents, int initialCapacity){
        this(initialCapacity);
        this.append(initialContents);
    }
    
    private void resetContents(){
        contents=null;
    }
    
    /**
     * Empties the <code>ByteArrayBuilder</code>
     */
    public void clear(){
        length=0;
        this.resetContents();
        buffer.clear();
    }
    
    /**
     * Appends a new byte array to this <code>ByteArrayBuilder</code>. 
     * Returns this same object to allow chaining calls
     * 
     * @param bs
     * @return this <code>ByteArrayBuilder</code>
     */
    public final ByteArrayBuilder append(byte[] bs){
        this.resetContents();
        length+=bs.length;
        buffer.add(bs);
        return this;
    }
    
    /**
     * Convenience method for append(byte[]) combined with a StringBuffer of specified
     * charset
     * 
     * @param s
     * @param c
     * @return
     * @throws UnsupportedEncodingException 
     */
    public final ByteArrayBuilder append(String s, Charset c ) throws UnsupportedEncodingException {
        return append(s.getBytes(c.name()));
    }
    
    /**
     * Convenience method for append(byte[]) combined with a String of specified
     * charset
     * 
     * @param s
     * @param c
     * @return
     * @throws UnsupportedEncodingException 
     */
    public final ByteArrayBuilder append(StringBuilder s, Charset c) throws UnsupportedEncodingException {
        return append(s.toString(), c);
    }
    
    /**
     * Returns the full contents of this <code>ByteArrayBuilder</code> as
     * a single <code>byte</code> array. The result is cached, so multiple
     * calls with no changes to the contents of the <code>ByteArrayBuilder</code>
     * are efficient.
     * 
     * @return The contents of this <code>ByteArrayBuilder</code> as a single <code>byte</code> array
     */
    public byte[] getByteArray(){
        if(contents==null)
        {
            contents=new byte[this.getLength()];
            int pos=0;
            for (byte[] bs:buffer){
                System.arraycopy(bs, 0, contents, pos, bs.length);
                pos+=bs.length;
            }
        }
        return contents;
    }
}
