/*
 * $Id: PDFImage.java,v 1.12 2010-06-14 17:32:09 lujke Exp $
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

import com.sun.pdfview.colorspace.IndexedColor;
import com.sun.pdfview.colorspace.PDFColorSpace;
import com.sun.pdfview.colorspace.YCCKColorSpace;
import com.sun.pdfview.decode.PDFDecoder;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataFormatImpl;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Encapsulates a PDF Image
 */
public class PDFImage {

    public static void dump(PDFObject obj) throws IOException {
        p("dumping PDF object: " + obj);
        if (obj == null) {
            return;
        }
        HashMap dict = obj.getDictionary();
        p("   dict = " + dict);
        for (Object key : dict.keySet()) {
            p("key = " + key + " value = " + dict.get(key));
        }
    }

    public static void p(String string) {
        System.out.println(string);
    }

    private static int[][] GREY_TO_ARGB = new int[8][];

    private static int[] getGreyToArgbMap(int numBits)
    {
        assert numBits <= 8;
        int[] argbVals = GREY_TO_ARGB[numBits - 1];
        if (argbVals == null) {
            argbVals = createGreyToArgbMap(numBits);
        }
        return argbVals;
    }

    /**
     * Create a map from all bit-patterns of a certain depth greyscale to the
     * corresponding sRGB values via the ICC colorr converter.
     * @param numBits the number of greyscale bits
     * @return a 2^bits array of standard 32-bit ARGB fits for each greyscale value
     *  at that bitdepth
     */
    private static int[] createGreyToArgbMap(int numBits)
    {
        final ColorSpace greyCs = PDFColorSpace.getColorSpace(
                PDFColorSpace.COLORSPACE_GRAY).getColorSpace();

        byte[] greyVals = new byte[1 << numBits];
        for (int i = 0; i < greyVals.length; ++i) {
            greyVals[i] = (byte) (i & 0xFF);
        }

        final int[] argbVals = new int[greyVals.length];
        final int mask = (1 << numBits) - 1;
        final WritableRaster inRaster = Raster.createPackedRaster(
                        new DataBufferByte(
                                greyVals,
                                greyVals.length),
                        greyVals.length, 1,
                        greyVals.length,
                        new int[] {mask},
                        null);

        final BufferedImage greyImage = new
                BufferedImage(
                new PdfComponentColorModel(
                        greyCs,
                        new int[] {numBits}),
                inRaster, false, null);

        final ColorModel ccm = ColorModel.getRGBdefault();
        final WritableRaster outRaster = Raster.createPackedRaster(
                        new DataBufferInt(
                                argbVals,
                                argbVals.length),
                        argbVals.length, 1,
                        argbVals.length,
                        ((PackedColorModel)ccm).getMasks(),
                        null);
        final BufferedImage srgbImage = new BufferedImage(
                ccm,
                outRaster,
                false,
                null);

        final ColorConvertOp op = new ColorConvertOp(
                greyCs,
                ColorSpace.getInstance(ColorSpace.CS_sRGB), null);

        op.filter(greyImage, srgbImage);

        GREY_TO_ARGB[numBits - 1] = argbVals;
        return argbVals;
    }

    /** color key mask. Array of start/end pairs of ranges of color components to
     *  mask out. If a component falls within any of the ranges it is clear. */
    private int[] colorKeyMask = null;
    /** the width of this image in pixels */
    private int width;
    /** the height of this image in pixels */
    private int height;
    /** the colorspace to interpret the samples in */
    private PDFColorSpace colorSpace;
    /** the number of bits per sample component */
    private int bpc;
    /** whether this image is a mask or not */
    private boolean imageMask = false;
    /** the SMask image, if any */
    private PDFImage sMask;
    /** the decode array */
    private float[] decode;
    private float[] decodeMins;
    private float[] decodeCoefficients;

    /** the actual image data */
    private PDFObject imageObj;

    /**
     * Create an instance of a PDFImage
     */
    protected PDFImage(PDFObject imageObj) {
        this.imageObj = imageObj;
    }

    /**
     * Read a PDFImage from an image dictionary and stream
     *
     * @param obj the PDFObject containing the image's dictionary and stream
     * @param resources the current resources
     */
    public static PDFImage createImage(PDFObject obj, Map resources)
            throws IOException {
        // create the image
        PDFImage image = new PDFImage(obj);

        // get the width (required)
        PDFObject widthObj = obj.getDictRef("Width");
        if (widthObj == null) {
            throw new PDFParseException("Unable to read image width: " + obj);
        }
        image.setWidth(widthObj.getIntValue());

        // get the height (required)
        PDFObject heightObj = obj.getDictRef("Height");
        if (heightObj == null) {
            throw new PDFParseException("Unable to get image height: " + obj);
        }
        image.setHeight(heightObj.getIntValue());

        // figure out if we are an image mask (optional)
        PDFObject imageMaskObj = obj.getDictRef("ImageMask");
        if (imageMaskObj != null) {
            image.setImageMask(imageMaskObj.getBooleanValue());
        }

        // read the bpc and colorspace (required except for masks)
        if (image.isImageMask()) {
            image.setBitsPerComponent(1);

            // create the indexed color space for the mask
            // [PATCHED by michal.busta@gmail.com] - default value od Decode according to PDF spec. is [0, 1]
        	// so the color arry should be:
            Color[] colors = {Color.BLACK, Color.WHITE};

            PDFObject imageMaskDecode = obj.getDictRef("Decode");
            if (imageMaskDecode != null) {
                PDFObject[] array = imageMaskDecode.getArray();
                float decode0 = array[0].getFloatValue();
                if (decode0 == 1.0f) {
                    colors = new Color[]{Color.WHITE, Color.BLACK};
                }
            }
            image.setColorSpace(new IndexedColor(colors));
        } else {
            // get the bits per component (required)
            PDFObject bpcObj = obj.getDictRef("BitsPerComponent");
            if (bpcObj == null) {
                throw new PDFParseException("Unable to get bits per component: " + obj);
            }
            image.setBitsPerComponent(bpcObj.getIntValue());

            // get the color space (required)
            PDFObject csObj = obj.getDictRef("ColorSpace");
            if (csObj == null) {
                throw new PDFParseException("No ColorSpace for image: " + obj);
            }

            PDFColorSpace cs = PDFColorSpace.getColorSpace(csObj, resources);
            image.setColorSpace(cs);
        }

        // read the decode array
        PDFObject decodeObj = obj.getDictRef("Decode");
        if (decodeObj != null) {
            PDFObject[] decodeArray = decodeObj.getArray();

            float[] decode = new float[decodeArray.length];
            for (int i = 0; i < decodeArray.length; i++) {
                decode[i] = decodeArray[i].getFloatValue();
            }

            image.setDecode(decode);
        }

        // read the soft mask.
        // If ImageMask is true, this entry must not be present.
        // (See implementation note 52 in Appendix H.)
        if (imageMaskObj == null) {
            PDFObject sMaskObj = obj.getDictRef("SMask");
            if (sMaskObj == null) {
                // try the explicit mask, if there is no SoftMask
                sMaskObj = obj.getDictRef("Mask");
            }

            if (sMaskObj != null) {
                if (sMaskObj.getType() == PDFObject.STREAM) {
                    try {
                        PDFImage sMaskImage = PDFImage.createImage(sMaskObj, resources);
                        image.setSMask(sMaskImage);
                    } catch (IOException ex) {
                        p("ERROR: there was a problem parsing the mask for this object");
                        dump(obj);
                        ex.printStackTrace(System.out);
                    }
                } else if (sMaskObj.getType() == PDFObject.ARRAY) {
                    // retrieve the range of the ColorKeyMask
                    // colors outside this range will not be painted.
                    try {
                        image.setColorKeyMask(sMaskObj);
                    } catch (IOException ex) {
                        p("ERROR: there was a problem parsing the color mask for this object");
                        dump(obj);
                        ex.printStackTrace(System.out);
                    }
                }
            }
        }

        return image;
    }

    /**
     * Get the image that this PDFImage generates.
     *
     * @return a buffered image containing the decoded image data
     */
    public BufferedImage getImage() {
        try {
            BufferedImage bi = (BufferedImage) imageObj.getCache();

            if (bi == null) {
                byte[] data = null;
                ByteBuffer jpegBytes = null;
                final boolean jpegDecode = PDFDecoder.isLastFilter(imageObj, PDFDecoder.DCT_FILTERS);
                if (jpegDecode) {
                    // if we're lucky, the stream will have just the DCT
                    // filter applied to it, and we'll have a reference to
                    // an underlying mapped file, so we'll manage to avoid
                    // a copy of the encoded JPEG bytes
                    jpegBytes = imageObj.getStreamBuffer(PDFDecoder.DCT_FILTERS);
                } else {
                    data = imageObj.getStream();
                }
                // parse the stream data into an actual image
                bi = parseData(data, jpegBytes);
                imageObj.setCache(bi);
            }
//            if(bi != null)
//            	ImageIO.write(bi, "png", new File("/tmp/test/" + System.identityHashCode(this) + ".png"));
            return bi;
        } catch (IOException ioe) {
            System.out.println("Error reading image");
            ioe.printStackTrace();
            return null;
        }
    }

    /**
     * Decodes jpeg data, possibly attempting a manual YCCK decode
     * if requested. Users should use {@link #getColorModel()} to
     * see which color model should now be used after a successful
     * decode.
     */
    private class JpegDecoder
    {
        /** The jpeg bytes */
        private ByteBuffer jpegData;
        /** The color model employed */
        private ColorModel cm;
        /** Whether the YCCK decode work-around should be used */
        private boolean ycckDecodeMode = false;

        /**
         * Class constructor
         * @param jpegData the JPEG data
         * @param cm the color model as presented in the PDF
         */
        private JpegDecoder(ByteBuffer jpegData, ColorModel cm) {
            this.jpegData = jpegData;
            this.cm = cm;
        }

        /**
         * Identify whether the decoder should operate in YCCK
         * decode mode, whereby the YCCK Chroma is specifically
         * looked for and the color model is changed to support
         * converting raw YCCK color values, working around
         * a lack of YCCK/CMYK report in the standard Java
         * jpeg readers. Non-YCCK images will not be decoded
         * while in this mode.
         * @param ycckDecodeMode
         */
        public void setYcckDecodeMode(boolean ycckDecodeMode) {
            this.ycckDecodeMode = ycckDecodeMode;
        }

        /**
         * Get the color model that should be used now
         * @return
         */
        public ColorModel getColorModel() {
            return cm;
        }

        /**
         * Attempt to decode the jpeg data
         * @return the successfully decoded image
         * @throws IOException if the image couldn't be decoded due
         *  to a lack of support or some IO problem
         */
        private BufferedImage decode() throws IOException {

            ImageReadParam readParam = null;
            if (getDecode() != null) {
                // we have to allocate our own buffered image so that we can
                // install our colour model which will do the desired decode
                readParam = new ImageReadParam();
                SampleModel sm =
                        cm.createCompatibleSampleModel (getWidth (), getHeight ());
                final WritableRaster raster =
                        Raster.createWritableRaster(sm, new Point(0, 0));
                readParam.setDestination(new BufferedImage(cm, raster, true, null));
            }

            final Iterator<ImageReader> jpegReaderIt =
                        ImageIO.getImageReadersByFormatName("jpeg");
            IIOException lastIioEx = null;
            while (jpegReaderIt.hasNext()) {
                try {
                    final ImageReader jpegReader = jpegReaderIt.next();
                    jpegReader.setInput(ImageIO.createImageInputStream(
                            new ByteBufferInputStream(jpegData)), true, false);
                    return readImage(jpegReader, readParam);
                } catch (IIOException e) {
                    // its most likely complaining about an unsupported image
                    // type; hopefully the next image reader will be able to
                    // understand it
                    jpegData.reset();
                    lastIioEx = e;
                }
            }

            throw lastIioEx;

        }

        private BufferedImage readImage(ImageReader jpegReader, ImageReadParam param) throws IOException {
            if (ycckDecodeMode) {
                // The standard Oracle Java JPEG readers can't deal with CMYK YCCK encoded images
                // without a little help from us. We'll try and pick up such instances and work around it.
                final IIOMetadata imageMeta = jpegReader.getImageMetadata(0);
                if (imageMeta != null) {
                    final Node standardMeta = imageMeta.getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName);
                    if (standardMeta != null) {
                        final Node chroma = getChild(standardMeta, "Chroma");
                        if (chroma != null) {
                            final Node csType = getChild(chroma, "ColorSpaceType");
                            if (csType != null) {
                                final Attr csTypeNameNode = (Attr)csType.getAttributes().getNamedItem("name");
                                if (csTypeNameNode != null && "YCCK".equals(csTypeNameNode.getValue())) {
                                    // So it's a YCCK image, and we can coax a workable image out of it
                                    // by grabbing the raw raster and installing a YCCK converting
                                    // color space wrapper around the existing (CMYK) color space; this will
                                    // do the YCCK conversion for us

                                    // first make sure we can get the unadjusted raster
                                    final Raster raster = jpegReader.readRaster(0, param);

                                    // and now use it with a YCCK converting color space.
                                    PDFImage.this.colorSpace = new PDFColorSpace(new YCCKColorSpace(colorSpace.getColorSpace()));
                                    // re-calculate the color model since the color space has changed
                                    cm = PDFImage.this.createColorModel();
                                    return new BufferedImage(
                                        cm,
                                        Raster.createWritableRaster(raster.getSampleModel(), raster.getDataBuffer(), null),
                                        true,
                                        null);

                                }
                            }
                        }
                    }
                }

                throw new IIOException("Not a YCCK image");

            } else {

                if (param != null && param.getDestination() != null) {
                    // if we've already set up a destination image then we'll use it
                    return jpegReader.read(0, param);
                } else {
                    // otherwise we'll create a new buffered image with the
                    // desired color model
                    return new BufferedImage(cm, jpegReader.read(0, param).getRaster(), true, null);
                }
            }

        }

        /**
         * Get a named child node
         * @param aNode the node
         * @param aChildName the name of the child node
         * @return the first direct child node with that name or null
         *  if it doesn't exist
         */
        private Node getChild(Node aNode, String aChildName) {
            for (int i = 0; i < aNode.getChildNodes().getLength(); ++i) {
                final Node child = aNode.getChildNodes().item(i);
                if (child.getNodeName().equals(aChildName)) {
                    return child;
                }
            }
            return null;
        }
    }

    /**
     * <p>Parse the image stream into a buffered image.  Note that this is
     * guaranteed to be called after all the other setXXX methods have been
     * called.</p>
     *
     * <p>NOTE: the color convolving is extremely slow on large images.
     * It would be good to see if it could be moved out into the rendering
     * phases, where we might be able to scale the image down first.</p
     *
     * @param data the data when already completely filtered and uncompressed
     * @param jpegData a byte buffer if data still requiring the DCDTecode filter
     *  is being used
     */
    protected BufferedImage parseData(byte[] data, ByteBuffer jpegData) throws IOException {
//        String hex;
//        String name;
//        synchronized (System.out) {
//            System.out.println("\n\n" + name + ": " + data.length);
//            for (int i = 0; i < data.length; i++) {
//                hex = "0x" + Integer.toHexString(0xFF & data[i]);
//                System.out.print(hex);
//                if (i < data.length - 1) {
//                    System.out.print(", ");
//                }
//                if ((i + 1) % 25 == 0) {
//                    System.out.print("\n");
//                }
//            }
//            System.out.println("\n");
//            System.out.flush();
//        }

        // pick a color model, based on the number of components and
        // bits per component
        ColorModel cm = createColorModel();

        BufferedImage bi = null;
        if (jpegData != null) {


            // Use imageio to decode the JPEG into
            // a BufferedImage. Perhaps JAI will be installed
            // so that decodes will be faster and better supported

            // TODO - strictly speaking, the application of the YUV->RGB
            // transformation when reading JPEGs does not adhere to the spec.
            // We're just going to let java read this in - as it is, the standard
            // jpeg reader looks for the specific Adobe marker header so that
            // it may apply the transform, so that's good. If that marker
            // isn't present, then it also applies a number of other heuristics
            // to determine whether the transform should be applied.
            // (http://java.sun.com/javase/6/docs/api/javax/imageio/metadata/doc-files/jpeg_metadata.html)
            // In practice, it probably almost always does the right thing here,
            // though note that the present or default value of the ColorTransform
            // dictionary entry is not being observed, so there is scope for
            // error. Hopefully the JAI reader does the same.

            // We might need to attempt this with multiple readers, so let's
            // remember where the jpeg data starts
            jpegData.mark();

            JpegDecoder decoder = new JpegDecoder(jpegData, cm);

            IIOException decodeEx = null;
            try {
                bi = decoder.decode();
            } catch (IIOException e) {
                decodeEx = e;
                // The native readers weren't able to process the image.
                // One common situation is that the image is YCCK encoded,
                // which isn't supported by the default jpeg readers.
                // We've got a work-around we can attempt, though:
                decoder.setYcckDecodeMode(true);
                try {
                    bi = decoder.decode();
                } catch (IOException e2) {
                    // It probably wasn't the YCCK issue! We'll drop
                    // through and allow the initial exception to be reported
                }
            }

            // the decoder may have requested installation of a new color model
            cm = decoder.getColorModel();

            // make these immediately unreachable, as the referenced
            // jpeg data might be quite large
            jpegData = null;
            decoder = null;

            if (bi == null) {
                // This isn't pretty, but it's what's been happening
                // previously, so we'll preserve it for the time
                // being. At least we'll offer a hint now!
                assert decodeEx != null;
                throw new IIOException(decodeEx.getMessage() +
                        ". Maybe installing JAI for expanded image format " +
                        "support would help?", decodeEx);
            }




        } else {
            DataBuffer db = new DataBufferByte(data, data.length);

            // create a compatible raster
            SampleModel sm =
                    cm.createCompatibleSampleModel (getWidth (), getHeight ());
            WritableRaster raster;
            try {
                raster =
                Raster.createWritableRaster (sm, db, new Point (0, 0));
            } catch (RasterFormatException e) {
                // this here seems a bit on the odd side. Is this really required,
                // or was it compensating for an old bug?
                int calculatedLineBits = getWidth() *
                        getColorSpace().getNumComponents() *
                        getBitsPerComponent();
                int calculatedLineBytes = (calculatedLineBits + 7 / 8);
                int calculatedBytes = calculatedLineBytes * getHeight();
                if (calculatedBytes > data.length) {
                    byte[] tempLargerData = new byte[calculatedBytes];
                    System.arraycopy (data, 0, tempLargerData, 0, data.length);
                    db = new DataBufferByte (tempLargerData, calculatedBytes);
                    raster = Raster.createWritableRaster(sm, db, new Point(0, 0));
                } else {
                    throw e;
                }
            }

            /*
             * Workaround for a bug on the Mac -- a class cast exception in
             * drawImage() due to the wrong data buffer type (?)
             */
            if (cm instanceof IndexColorModel) {
                IndexColorModel icm = (IndexColorModel) cm;

                // choose the image type based on the size
                int type = BufferedImage.TYPE_BYTE_BINARY;
                if (getBitsPerComponent() == 8) {
                    type = BufferedImage.TYPE_BYTE_INDEXED;
                }

                // create the image with an explicit indexed color model.
                bi = new BufferedImage(getWidth(), getHeight(), type, icm);

                // set the data explicitly as well
                bi.setData(raster);
            } else {
                bi = new BufferedImage(cm, raster, true, null);
            }
        }

        ColorSpace cs = cm.getColorSpace();
        ColorSpace rgbCS = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        if (isGreyscale(cs) && bpc <= 8 && getDecode() == null && jpegData == null) {
            bi = convertGreyscaleToArgb(data, bi);
        } else if (!isImageMask() && cs instanceof ICC_ColorSpace && !cs.equals(rgbCS)
                && !Boolean.getBoolean("PDFRenderer.avoidColorConvertOp")) {

            // users can use the PDFRenderer.avoidColorConvertOp property to avoid
            // use of this color convert op which may segfault on some platforms
            // due to a variety of problems related to thread safety and
            // the native cmm library underlying this conversioon op, e.g.,
            // https://forums.oracle.com/forums/thread.jspa?threadID=1261882&tstart=225&messageID=5356357
            // (Unix platforms seem the most affected)

            // If the system is bug-free, though, this does make use
            // of native libraries and sees a not insignificant speed-up,
            // though it's still not exactly fast. If we don't run this op
            // now, it's performed at some later stage, but without using
            // the native code
            ColorConvertOp op = new ColorConvertOp(cs, rgbCS, null);

            BufferedImage converted = new BufferedImage(getWidth(),
                    getHeight(), BufferedImage.TYPE_INT_ARGB);

            bi = op.filter(bi, converted);
        }

        // add in the alpha data supplied by the SMask, if any
        PDFImage sMaskImage = getSMask();
        if (sMaskImage != null) {
            BufferedImage si = sMaskImage.getImage();

            BufferedImage outImage = new BufferedImage(getWidth(),
                    getHeight(), BufferedImage.TYPE_INT_ARGB);

            int[] srcArray = new int[width];
            int[] maskArray = new int[width];

            for (int i = 0; i < height; i++) {
                bi.getRGB(0, i, width, 1, srcArray, 0, width);
                si.getRGB(0, i, width, 1, maskArray, 0, width);

                for (int j = 0; j < width; j++) {
                    int ac = 0xff000000;

                    maskArray[j] = ((maskArray[j] & 0xff) << 24) | (srcArray[j] & ~ac);
                }

                outImage.setRGB(0, i, width, 1, maskArray, 0, width);
            }

            bi = outImage;
        }

        return (bi);
    }

    private boolean isGreyscale(ColorSpace aCs)
    {
        return aCs == PDFColorSpace.getColorSpace(PDFColorSpace.COLORSPACE_GRAY).
                getColorSpace();
    }

    private BufferedImage convertGreyscaleToArgb(byte[] data, BufferedImage bi)
    {
        // we use an optimised greyscale colour conversion, as with scanned
        // greyscale/mono documents consisting of nothing but page-size
        // images, using the ICC converter is perhaps 15 times slower than this
        // method. Using an example scanned, mainly monochrome document, on this
        // developer's machine pages took an average of 3s to render using the
        // ICC converter filter, and around 115ms using this method. We use
        // pre-calculated tables generated using the ICC converter to map between
        // each possible greyscale value and its desired value in sRGB.
        // We also try to avoid going through SampleModels, WritableRasters or
        // BufferedImages as that takes about 3 times as long.
        final int[] convertedPixels = new int[getWidth() * getHeight()];
        final WritableRaster r = bi.getRaster();
        int i = 0;
        final int[] greyToArgbMap = getGreyToArgbMap(bpc);
        if (bpc == 1) {
            int calculatedLineBytes = (getWidth() + 7) / 8;
            int rowStartByteIndex;
            // avoid hitting the WritableRaster for the common 1 bpc case
            if (greyToArgbMap[0] == 0 && greyToArgbMap[1] == 0xFFFFFFFF) {
                // optimisation for common case of a direct map to full white
                // and black, using bit twiddling instead of consulting the
                // greyToArgb map
                for (int y = 0; y < getHeight(); ++y) {
                    // each row is byte-aligned
                    rowStartByteIndex = y * calculatedLineBytes;
                    for (int x = 0; x < getWidth(); ++x) {
                        final byte b = data[rowStartByteIndex + x / 8];
                        final int white = b >> (7 - (x & 7)) & 1;
                        // if white == 0, white - 1 will be 0xFFFFFFFF,
                        //  which when xored with 0xFFFFFF will produce 0
                        // if white == 1, white - 1 will be 0,
                        //  which when xored with 0xFFFFFF will produce 0xFFFFFF
                        //  (ignoring the top two bytes, which are always set high anyway)
                        convertedPixels[i] = 0xFF000000 | ((white - 1) ^ 0xFFFFFF);
                        ++i;
                    }
                }
            } else {
                // 1 bpc case where we can't bit-twiddle and need to consult
                // the map
                for (int y = 0; y < getHeight(); ++y) {
                    rowStartByteIndex = y * calculatedLineBytes;
                    for (int x = 0; x < getWidth(); ++x) {
                        final byte b = data[rowStartByteIndex + x / 8];
                        final int val = b >> (7 - (x & 7)) & 1;
                        convertedPixels[i] = greyToArgbMap[val];
                        ++i;
                    }
                }
            }
        } else {
            for (int y = 0; y < getHeight(); ++y) {
                for (int x = 0; x < getWidth(); ++x) {
                    final int greyscale = r.getSample(x, y, 0);
                    convertedPixels[i] = greyToArgbMap[greyscale];
                    ++i;
                }
            }
        }

        final ColorModel ccm = ColorModel.getRGBdefault();
        return new BufferedImage(
                ccm,
                Raster.createPackedRaster(
                        new DataBufferInt(
                                convertedPixels,
                                convertedPixels.length),
                        getWidth(), getHeight(),
                        getWidth(), ((PackedColorModel)ccm).getMasks(),
                        null),
                false,
                null);
    }

    /**
     * Get the image's width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Set the image's width
     */
    protected void setWidth(int width) {
        this.width = width;
    }

    /**
     * Get the image's height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Set the image's height
     */
    protected void setHeight(int height) {
        this.height = height;
    }

    /**
     * set the color key mask. It is an array of start/end entries
     * to indicate ranges of color indicies that should be masked out.
     *
     * @param maskArrayObject
     */
    private void setColorKeyMask(PDFObject maskArrayObject) throws IOException {
        PDFObject[] maskObjects = maskArrayObject.getArray();
        colorKeyMask = null;
        int[] masks = new int[maskObjects.length];
        for (int i = 0; i < masks.length; i++) {
            masks[i] = maskObjects[i].getIntValue();
        }
        colorKeyMask = masks;
    }

    /**
     * Get the colorspace associated with this image, or null if there
     * isn't one
     */
    protected PDFColorSpace getColorSpace() {
        return colorSpace;
    }

    /**
     * Set the colorspace associated with this image
     */
    protected void setColorSpace(PDFColorSpace colorSpace) {
        this.colorSpace = colorSpace;
    }

    /**
     * Get the number of bits per component sample
     */
    protected int getBitsPerComponent() {
        return bpc;
    }

    /**
     * Set the number of bits per component sample
     */
    protected void setBitsPerComponent(int bpc) {
        this.bpc = bpc;
    }

    /**
     * Return whether or not this is an image mask
     */
    public boolean isImageMask() {
        return imageMask;
    }

    /**
     * Set whether or not this is an image mask
     */
    public void setImageMask(boolean imageMask) {
        this.imageMask = imageMask;
    }

    /**
     * Return the soft mask associated with this image
     */
    public PDFImage getSMask() {
        return sMask;
    }

    /**
     * Set the soft mask image
     */
    protected void setSMask(PDFImage sMask) {
        this.sMask = sMask;
    }

    /**
     * Get the decode array
     */
    protected float[] getDecode() {
        return decode;
    }

    /**
     * Set the decode array
     */
    protected void setDecode(float[] decode) {
        float max = (1 << getBitsPerComponent()) - 1;
        this.decode = decode;
        this.decodeCoefficients = new float[decode.length / 2];
        this.decodeMins = new float[decode.length / 2];
        for (int i = 0; i < decode.length; i += 2) {
            decodeMins[i/2] = decode[i];
            decodeCoefficients[i/2] = (decode[i + 1] - decode[i]) / max;
        }
    }

    /**
     * get a Java ColorModel consistent with the current color space,
     * number of bits per component and decode array
     *
     * @param bpc the number of bits per component
     */
    private ColorModel createColorModel() {
        PDFColorSpace cs = getColorSpace();

        if (cs instanceof IndexedColor) {
            IndexedColor ics = (IndexedColor) cs;

            byte[] components = ics.getColorComponents();
            int num = ics.getCount();

            // process the decode array
            if (decode != null) {
                byte[] normComps = new byte[components.length];

                // move the components array around
                for (int i = 0; i < num; i++) {
                    byte[] orig = new byte[1];
                    orig[0] = (byte) i;

                    float[] res = normalize(orig, null, 0);
                    int idx = (int) res[0];

                    normComps[i * 3] = components[idx * 3];
                    normComps[(i * 3) + 1] = components[(idx * 3) + 1];
                    normComps[(i * 3) + 2] = components[(idx * 3) + 2];
                }

                components = normComps;
            }

            // make sure the size of the components array is 2 ^ numBits
            // since if it's not, Java will complain
            int correctCount = 1 << getBitsPerComponent();
            if (correctCount < num) {
                byte[] fewerComps = new byte[correctCount * 3];

                System.arraycopy(components, 0, fewerComps, 0, correctCount * 3);

                components = fewerComps;
                num = correctCount;
            }
            if (colorKeyMask == null || colorKeyMask.length == 0) {
                return new IndexColorModel(getBitsPerComponent(), num, components,
                        0, false);
            } else {
                byte[] aComps = new byte[num * 4];
                int idx = 0;
                for (int i = 0; i < num; i++) {
                    aComps[idx++] = components[(i * 3)];
                    aComps[idx++] = components[(i * 3) + 1];
                    aComps[idx++] = components[(i * 3) + 2];
                    aComps[idx++] = (byte) 0xFF;
                }
                for (int i = 0; i < colorKeyMask.length; i += 2) {
                    for (int j = colorKeyMask[i]; j <= colorKeyMask[i + 1]; j++) {
                        aComps[(j * 4) + 3] = 0;    // make transparent
                    }
                }
                return new IndexColorModel(getBitsPerComponent(), num, aComps,
                        0, true);
            }
        } else {
            int[] bits = new int[cs.getNumComponents()];
            for (int i = 0; i < bits.length; i++) {
                bits[i] = getBitsPerComponent();
            }

            return decode != null ?
                    new DecodeComponentColorModel(cs.getColorSpace(), bits) :
                    new PdfComponentColorModel(cs.getColorSpace(), bits);
        }
    }

    private ColorModel createColorModel(PDFColorSpace cs) {
        if (cs instanceof IndexedColor) {
            IndexedColor ics = (IndexedColor) cs;

            byte[] components = ics.getColorComponents();
            int num = ics.getCount();

            // process the decode array
            if (decode != null) {
                byte[] normComps = new byte[components.length];

                // move the components array around
                for (int i = 0; i < num; i++) {
                    byte[] orig = new byte[1];
                    orig[0] = (byte) i;

                    float[] res = normalize(orig, null, 0);
                    int idx = (int) res[0];

                    normComps[i * 3] = components[idx * 3];
                    normComps[(i * 3) + 1] = components[(idx * 3) + 1];
                    normComps[(i * 3) + 2] = components[(idx * 3) + 2];
                }

                components = normComps;
            }

            // make sure the size of the components array is 2 ^ numBits
            // since if it's not, Java will complain
            int correctCount = 1 << getBitsPerComponent();
            if (correctCount < num) {
                byte[] fewerComps = new byte[correctCount * 3];

                System.arraycopy(components, 0, fewerComps, 0, correctCount * 3);

                components = fewerComps;
                num = correctCount;
            }
            if (colorKeyMask == null || colorKeyMask.length == 0) {
                return new IndexColorModel(getBitsPerComponent(), num, components,
                        0, false);
            } else {
                byte[] aComps = new byte[num * 4];
                int idx = 0;
                for (int i = 0; i < num; i++) {
                    aComps[idx++] = components[(i * 3)];
                    aComps[idx++] = components[(i * 3) + 1];
                    aComps[idx++] = components[(i * 3) + 2];
                    aComps[idx++] = (byte) 0xFF;
                }
                for (int i = 0; i < colorKeyMask.length; i += 2) {
                    for (int j = colorKeyMask[i]; j <= colorKeyMask[i + 1]; j++) {
                        aComps[(j * 4) + 3] = 0;    // make transparent
                    }
                }
                return new IndexColorModel(getBitsPerComponent(), num, aComps,
                        0, true);
            }
        } else {
            int[] bits = new int[cs.getNumComponents()];
            for (int i = 0; i < bits.length; i++) {
                bits[i] = getBitsPerComponent();
            }

            return decode != null ?
                    new DecodeComponentColorModel(cs.getColorSpace(), bits) :
                    new PdfComponentColorModel(cs.getColorSpace(), bits);
        }
    }

    /**
     * Normalize an array of values to match the decode array
     */
    private float[] normalize(byte[] pixels, float[] normComponents,
            int normOffset) {

        if (normComponents == null) {
            normComponents = new float[normOffset + pixels.length];
        }

        // trivial loop unroll - saves a little time
        switch (pixels.length) {
        case 4:
            normComponents[normOffset + 3] = decodeMins[3] + (float)(pixels[3] & 0xFF) * decodeCoefficients[3];
        case 3:
            normComponents[normOffset + 2] = decodeMins[2] + (float)(pixels[2] & 0xFF) * decodeCoefficients[2];
        case 2:
            normComponents[normOffset + 1] = decodeMins[1] + (float)(pixels[1] & 0xFF) * decodeCoefficients[1];
        case 1:
            normComponents[normOffset ] = decodeMins[0] + (float)(pixels[0] & 0xFF) * decodeCoefficients[0];
        break;
        default:
            throw new IllegalArgumentException("Someone needs to add support for more than 4 components");
        }
        return normComponents;
    }

    /**
     * A wrapper for ComponentColorSpace which normalizes based on the
     * decode array.
     */
    static class PdfComponentColorModel extends ComponentColorModel {

        int bitsPerComponent;

        public PdfComponentColorModel(ColorSpace cs, int[] bpc) {
            super(cs, bpc, false, false, Transparency.OPAQUE,
                    DataBuffer.TYPE_BYTE);

            pixel_bits = bpc.length * bpc[0];
            this.bitsPerComponent = bpc[0];
        }

        @Override
        public SampleModel createCompatibleSampleModel(int width, int height) {

            if (bitsPerComponent >= 8) {
                assert bitsPerComponent == 8 || bitsPerComponent == 16;
                final int numComponents = getNumComponents();
                int[] bandOffsets = new int[numComponents];
                for (int i=0; i < numComponents; i++) {
                    bandOffsets[i] = i;
                }
                return new PixelInterleavedSampleModel(
                        getTransferType(), width, height,
                        numComponents,
                        width * numComponents,
                        bandOffsets);
            } else {
                switch (getPixelSize()) {
                    case 1:
                    case 2:
                    case 4:
                        // pixels don't span byte boundaries, so we can use the standard multi pixel
                        // packing, which offers a slight performance advantage over the other sample
                        // model, which must consider such cases. Given that sample model interactions
                        // can dominate processing, this small distinction is worthwhile
                        return new MultiPixelPackedSampleModel(getTransferType(),
                            width,
                            height,
                            getPixelSize());
                    default:
                        // pixels will cross byte boundaries
                        assert getTransferType() == DataBuffer.TYPE_BYTE;
                        return new PdfSubByteSampleModel(width, height, getNumComponents(), bitsPerComponent);
                }
            }
        }

        @Override
        public boolean isCompatibleRaster(Raster raster) {
            if (bitsPerComponent < 8 || getNumComponents() == 1) {
                SampleModel sm = raster.getSampleModel();
                return sm.getSampleSize(0) == bitsPerComponent;
            }
            return super.isCompatibleRaster(raster);
        }

    }

    class DecodeComponentColorModel extends PdfComponentColorModel
    {
        DecodeComponentColorModel(ColorSpace cs, int[] bpc)
        {
            super(cs, bpc);
        }

        public int getRGB(Object inData) {
            float[] norm = getNormalizedComponents(inData, null, 0);
            // super-class wants to do a (VERY expensive!) colorspace conversion
            // here, but we'll ignore it - I think we'll already have the
            // colour space converted.
            float[] rgb = norm;//this.colorSpace.toRGB(norm);
            // Note that getNormalizedComponents returns non-premult values
            return (this.getAlpha(inData) << 24)
                | (((int) (rgb[0] * 255.0f + 0.5f)) << 16)
                | (((int) (rgb[1] * 255.0f + 0.5f)) << 8)
                | (((int) (rgb[2] * 255.0f + 0.5f)));
        }
        @Override
        public float[] getNormalizedComponents(Object pixel,
                float[] normComponents, int normOffset) {
            return normalize((byte[]) pixel, normComponents, normOffset);
        }
    }
}
