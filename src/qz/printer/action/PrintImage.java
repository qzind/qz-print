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
package qz.printer.action;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.common.Base64;
import qz.printer.PrintOptions;
import qz.utils.ByteUtilities;
import qz.utils.SystemUtilities;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.ResolutionSyntax;
import javax.print.attribute.standard.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * @author Tres Finocchiaro, Anton Mezerny
 */
public class PrintImage implements PrintProcessor, Printable {

    private static final Logger log = LoggerFactory.getLogger(PrintImage.class);

    private static final String JOB_NAME = "QZ-PRINT 2D Printing";

    private static final List<Integer> MAC_BAD_IMAGE_TYPES = Arrays.asList(BufferedImage.TYPE_BYTE_BINARY, BufferedImage.TYPE_CUSTOM);

    private List<BufferedImage> images;

    private int dpi = 72;
    private boolean scaleImage = false;
    private double imageRotation = 0;
    private PrintOptions.Margins pageMargins = null;


    public PrintImage() {
        images = new ArrayList<>();
    }


    @Override
    public void parseData(JSONArray printData) throws JSONException, UnsupportedOperationException {
        for(int i = 0; i < printData.length(); i++) {
            JSONObject data = printData.getJSONObject(i);

            try {
                images.add(readImage(data.getString("data")));
            }
            catch(IOException e) {
                throw new UnsupportedOperationException(String.format("Cannot parse (%s)%s as an image", data.getString("type"), data.getString("data")), e);
            }
        }

        log.debug("Parsed {} images for printing", images.size());
    }

    @Override
    public void print(PrintService service, PrintOptions options) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

        PrintOptions.Postscript psOpts = options.getPSOptions();
        if (psOpts.getColorType() != null) {
            attributes.add(psOpts.getColorType().getChromatic());
        }
        if (psOpts.isDuplex()) {
            attributes.add(Sides.DUPLEX);
        }
        if (psOpts.getOrientation() != null) {
            attributes.add(psOpts.getOrientation().getAsAttribute());
        }

        attributes.add(new PrinterResolution(psOpts.getDpi(), psOpts.getDpi(), ResolutionSyntax.DPI)); //TODO - might not be doing anything ..
        dpi = psOpts.getDpi();

        //TODO - set paper thickness

        if (psOpts.getSize() != null) {
            if (psOpts.getSize().getWidth() > 0 && psOpts.getSize().getHeight() > 0) {
                attributes.add(new MediaPrintableArea(0f, 0f, (float)psOpts.getSize().getWidth(), (float)psOpts.getSize().getHeight(), psOpts.getSize().getUnits()));
            }
            scaleImage = psOpts.getSize().isFitImage();

            // Fixes 4x6 labels on Mac OSX
            if (psOpts.getSize().getUnits() == MediaSize.INCH && psOpts.getSize().getWidth() == 4 && psOpts.getSize().getHeight() == 6) {
                attributes.add(MediaSizeName.JAPANESE_POSTCARD);
            }
        }

        pageMargins = psOpts.getMargins();
        imageRotation = psOpts.getRotation();

        log.trace("{}", Arrays.toString(attributes.toArray()));

        job.setJobName(JOB_NAME);
        job.setPrintService(service);
        job.setPrintable(this);

        log.info("Starting image printing ({} copies)", psOpts.getCopies());
        for(int i = 0; i < psOpts.getCopies(); i++) {
            job.print(attributes);
        }
    }


    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (graphics == null) { throw new PrinterException("No graphics specified"); }
        if (pageFormat == null) { throw new PrinterException("No page format specified"); }

        if (pageIndex + 1 > images.size()) {
            return NO_SUCH_PAGE;
        }
        log.trace("Requested page {} for printing", pageIndex);


        BufferedImage imgToPrint = images.get(pageIndex);
        if (imageRotation % 360 != 0) {
            imgToPrint = rotate(imgToPrint, imageRotation);
        }

        // Get our graphics and translate to avoid clipping outside of margins
        Graphics2D graphics2D = (Graphics2D)graphics;
        //graphics2D.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

        // Suggested by Bahadir 8/23/2012
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        log.trace("{}", graphics2D.getRenderingHints());

        // FIXME:  Temporary fix for OS X 10.10 hard crash.
        // See https://github.com/qzind/qz-print/issues/75
        if (SystemUtilities.isMac()) {
            if (MAC_BAD_IMAGE_TYPES.contains(imgToPrint.getType())) {
                BufferedImage sanitizedImage;
                ColorModel cm = imgToPrint.getColorModel();
                if (cm instanceof IndexColorModel) {
                    log.info("Image converted to 256 colors for OSX 10.10 Workaround");
                    sanitizedImage = new BufferedImage(imgToPrint.getWidth(), imgToPrint.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, (IndexColorModel)cm);
                } else {
                    log.info("Image converted to ARGB for OSX 10.10 Workaround");
                    sanitizedImage = new BufferedImage(imgToPrint.getWidth(), imgToPrint.getHeight(), BufferedImage.TYPE_INT_ARGB);
                }
                sanitizedImage.createGraphics().drawImage(imgToPrint, 0, 0, null);
                imgToPrint = sanitizedImage;
            }
        }

        //FIXME - margins are in in/mm not pixels
        log.debug("Margins: {},{}:{},{}", pageMargins.left(), pageMargins.top(), pageMargins.right(), pageMargins.bottom());

        // apply custom margins
        double boundX = pageFormat.getImageableX() + (pageMargins.left() * dpi);
        double boundY = pageFormat.getImageableY() + (pageMargins.top() * dpi);
        double boundW = (pageFormat.getImageableWidth() - ((pageMargins.right() + pageMargins.left()) * dpi));
        double boundH = (pageFormat.getImageableHeight() - ((pageMargins.bottom() + pageMargins.top()) * dpi));

        int imgX = imgToPrint.getMinX();
        int imgY = imgToPrint.getMinY();
        int imgW = imgToPrint.getWidth();
        int imgH = imgToPrint.getHeight();

        if (scaleImage) {
            // scale image to smallest edge, keeping size ratio
            if ((imgToPrint.getWidth() / imgToPrint.getHeight()) >= (boundW / boundH)) {
                imgW = (int)boundW;
                imgH = (int)(imgToPrint.getHeight() / (imgToPrint.getWidth() / boundW));
            } else {
                imgW = (int)(imgToPrint.getWidth() / (imgToPrint.getHeight() / boundH));
                imgH = (int)boundH;
            }
        }

        log.debug("Paper area: {},{}:{},{}", (int)boundX, (int)boundY, (int)boundW, (int)boundH);
        log.debug("Image area: {},{}:{},{}", imgX, imgY, imgW, imgH);

        // Now we perform our rendering
        graphics2D.drawImage(imgToPrint, (int)boundX + imgX, (int)boundY + imgY, (int)boundX + imgW, (int)boundY + imgH,
                             imgToPrint.getMinX(), imgToPrint.getMinY(), imgToPrint.getWidth(), imgToPrint.getHeight(), null);

        // Valid page
        return PAGE_EXISTS;
    }


    /**
     * Reads an image from base64 or a URL.
     *
     * @param rawData Base^$ encoded string or URL
     * @return BufferedImage from {@code rawData}
     */
    private static BufferedImage readImage(String rawData) throws IOException {
        if (ByteUtilities.isBase64Image(rawData)) {
            return ImageIO.read(new ByteArrayInputStream(Base64.decode(rawData)));
        } else {
            return ImageIO.read(new URL(rawData));
        }
    }

    /**
     * Rotates {@code image} by the specified {@code angle}.
     *
     * @param image BufferedImage to rotate
     * @param angle Rotation angle in degrees
     * @return Rotated image data
     */
    private static BufferedImage rotate(BufferedImage image, double angle) {
        double rads = Math.toRadians(angle);
        double sin = Math.abs(Math.sin(rads)), cos = Math.abs(Math.cos(rads));

        int sWidth = image.getWidth(), sHeight = image.getHeight();
        int eWidth = (int)Math.floor((sWidth * cos) + (sHeight * sin)), eHeight = (int)Math.floor((sHeight * cos) + (sWidth * sin));

        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0].getDefaultConfiguration();
        BufferedImage result = gc.createCompatibleImage(eWidth, eHeight, Transparency.TRANSLUCENT);

        Graphics2D g2d = result.createGraphics();
        g2d.translate((eWidth - sWidth) / 2, (eHeight - sHeight) / 2);
        g2d.rotate(rads, sWidth / 2, sHeight / 2);
        g2d.drawRenderedImage(image, null);
        g2d.dispose();

        return result;
    }

}
