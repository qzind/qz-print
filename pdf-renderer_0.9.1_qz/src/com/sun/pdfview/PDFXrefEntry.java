/*
 * $Id$
 *
 * Copyright 2011 Pirion Systems Pty Ltd, 139 Warry St,
 * Fortitude Valley, Queensland, Australia
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

import java.lang.ref.SoftReference;

/**
 * An entry found in a PDFFile's xref table or xref streams, identifying
 * the position of an object within the structure of a PDF, and the latest
 * generation number for a given object. May also indicate that
 * a given object number has been freed, and that references to objects
 * with that object number should be treated as references to the null
 * object.
 *
 * @author Luke Kirby, Pirion Systems
 */
public class PDFXrefEntry {

    private static final PDFXrefEntry FREED_OBJECT =
            new PDFXrefEntry(Type.FREE, -1, -1, null);

    static PDFXrefEntry toBodyObject(int generation, int offset) {
        return new PDFXrefEntry(Type.OBJ_IN_BODY, generation, offset, null);
    }
    
    static PDFXrefEntry toStreamObject(PDFXref stream, int index) {
        // stream objects will always have generation 0
        return new PDFXrefEntry(Type.OBJ_IN_STREAM, 0, index, stream);
    }

    static PDFXrefEntry forFreedObject() {
        return FREED_OBJECT;
    }

    public boolean resolves(PDFXref ref) {
        // it's expected that the object number is relevant
        return type != Type.FREE && generation == ref.getGeneration();
    }

    enum Type {
        /**
         * Identifies a deleted object. A Type 0 reference in an xref stream,
         * or an 'f' entry in an xref table.
         */
        FREE,
        /**
         * Identifies a reference to an (uncompressed) object that isn't
         * part of a (compressed) stream, like one might see in pre-1.5
         * PDFs. * Identifies a deleted object. A Type 1 reference in an
         * xref stream or an 'n' entry in an xref table.
         */
        OBJ_IN_BODY,
        /**
         * Identifies a reference to an object that's in a (probably compressed)
         * stream. A Type 2 reference in an xref stream.
         */
        OBJ_IN_STREAM;

        public static Type forTypeField(int i) {
            if (i > 0 && i < 3) {
                return values()[i];
            } else {
                // if the type field is illegal then it should be treated
                // as a null object, and returning a free field will
                // do the job quite nicely!
                return FREE;
            }
        }

        /**
         * Make an entry for a given type based on the values of field 2
         * and field 3 in an xref stream
         * @param field2 the value of field 2
         * @param field3 the value of field 3
         * @return a corresponding entry
         */
        public PDFXrefEntry makeXrefStreamEntry(int field2, int field3) {
            switch (this) {
                case FREE:
                    return forFreedObject();
                case OBJ_IN_BODY:
                    return toBodyObject(field3, field2);
                case OBJ_IN_STREAM:
                    return toStreamObject(new PDFXref(field2, 0), field3);
                default:
                    throw new UnsupportedOperationException(
                            "Unhandled xref entry type " + this);
            }
        }
    };

    /** The type of cross-ref entry*/
    private Type type;

    /**
     * The generation number for the entry; note that there's no need for us
     * to actually store the object number itself here due to the manner
     * in which this object is stored, but we require the generation number
     * to ensure that dereferences to previous generations of this object number
     * result in the null object being returned
     */
    private int generation;

    /**
     * The offset into the file for in-body references, or the index of
     * the object within the given stream for in-stream references.
     */
    private int offset;

    /** The reference to the stream for in-stream references */
    private PDFXref stream;

    /**
     * For entries that point to an object stream, we cache the index offsets
     * here rather than parsing each time or
     * placing another field on PDFObject
     */
    private int[] objectIndexOffsets = null;

    /** A cached reference of the resolved object */
    private SoftReference<PDFObject> target = null;

    /**
     * Class constructor
     * @param type the type of entry
     * @param generation the generation number of the object the entry is for
     * @param offset the offset of the object for in-body references, or the
     *  index of an object for in-stream references; ignored for freed entries
     * @param stream a reference to the stream for in-stream references
     */
    private PDFXrefEntry(Type type, int generation, int offset, PDFXref stream) {
        this.type = type;
        this.generation = generation;
        this.offset = offset;
        this.stream = stream;
    }


    /**
     * @return the offset into the file for in-body references, or the index of
     * the object within the given stream for in-stream references.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @return the stream of the object for in-stream references;
     * <code>null</code> for other entry types
     */
    public PDFXref getStream() {
        return stream;
    }

    /**
     * @return the type of entry
     */
    public Type getType() {
        return type;
    }

    /**
     * @return the generation number of the object this entry is for
     */
    public int getGeneration() {
        return generation;
    }

    /**
     * @return any cached reference to the object that his entry refers to;
     *  <code>null</code> if this entry has yet to be looked at, or its
     *  target has been garbage collected
     */
    public PDFObject getObject() {
        if (target != null) {
            return (PDFObject) target.get();
        }

        return null;
    }

    /**
     * Cache a reference to the target object of this entry
     * @param obj the object to cache
     */
    public void setObject(PDFObject obj) {
        this.target = new SoftReference<PDFObject>(obj);
    }

    /**
     * @return for entries for object streams, the offsets of each object
     * in the stream, arranged by index number
     */
    public int[] getObjectIndexOffsets() {
        return objectIndexOffsets;
    }

    /**
     * For entries for object streams, set the offsets of each object
     * in the stream, arranged by index number
     * @param objectIndexOffsets the object index offsets
     */
    public void setObjectIndexOffsets(int[] objectIndexOffsets) {
        this.objectIndexOffsets = objectIndexOffsets;
    }
}
