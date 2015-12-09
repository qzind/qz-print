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
import qz.common.Constants;
import qz.printer.PrintOptions;
import qz.printer.PrintOutput;
import qz.utils.ByteUtilities;
import qz.utils.PrintingUtilities;

import javax.imageio.ImageIO;
import javax.print.attribute.PrintRequestAttributeSet;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Tres Finocchiaro, Anton Mezerny
 */
public class PrintImage extends PrintPixel implements PrintProcessor, Printable {

    private static final Logger log = LoggerFactory.getLogger(PrintImage.class);

    protected List<BufferedImage> images;

    protected boolean scaleImage = false;
    protected double imageRotation = 0;


    public PrintImage() {
        images = new ArrayList<>();
    }


    @Override
    public void parseData(JSONArray printData, PrintOptions options) throws JSONException, UnsupportedOperationException {
        for(int i = 0; i < printData.length(); i++) {
            JSONObject data = printData.getJSONObject(i);

            PrintingUtilities.Format format = PrintingUtilities.Format.valueOf(data.optString("format", "AUTO").toUpperCase());
            boolean fromBase64 = (format == PrintingUtilities.Format.BASE64)
                    || (format == PrintingUtilities.Format.AUTO && ByteUtilities.isBase64Image(data.getString("data")));

            try {
                images.add(readImage(data.getString("data"), fromBase64));
            }
            catch(IOException e) {
                throw new UnsupportedOperationException(String.format("Cannot parse (%s)%s as an image", data.getString("format"), data.getString("data")), e);
            }
        }

        log.debug("Parsed {} images for printing", images.size());
    }

    @Override
    public void print(PrintOutput output, PrintOptions options) throws PrinterException {
        if (images.isEmpty()) {
            log.warn("Nothing to print");
            return;
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(output.getPrintService());
        PageFormat page = job.getPageFormat(null);

        PrintOptions.Pixel pxlOpts = options.getPixelOptions();
        PrintRequestAttributeSet attributes = applyDefaultSettings(pxlOpts, page);

        if (pxlOpts.getSize() != null) {
            scaleImage = options.getPixelOptions().getSize().isFitImage();
        }

        imageRotation = pxlOpts.getRotation();

        job.setJobName(Constants.IMAGE_PRINT);
        job.setPrintable(this, page);

        log.info("Starting image printing ({} copies)", pxlOpts.getCopies());
        for(int i = 0; i < pxlOpts.getCopies(); i++) {
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
        imgToPrint = fixColorModel(imgToPrint);
        if (imageRotation % 360 != 0) {
            imgToPrint = rotate(imgToPrint, imageRotation);
        }

        Graphics2D graphics2D = (Graphics2D)graphics;
        // Suggested by Bahadir 8/23/2012
        graphics2D.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics2D.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        log.trace("{}", graphics2D.getRenderingHints());


        // apply image scaling
        double boundW = pageFormat.getImageableWidth();
        double boundH = pageFormat.getImageableHeight();

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

        double boundX = pageFormat.getImageableX();
        double boundY = pageFormat.getImageableY();

        log.debug("Paper area: {},{}:{},{}", (int)boundX, (int)boundY, (int)boundW, (int)boundH);
        log.trace("Image size: {},{}", imgW, imgH);

        // Now we perform our rendering
        graphics2D.drawImage(imgToPrint, (int)boundX, (int)boundY, (int)boundX + imgW, (int)boundY + imgH,
                             0, 0, imgToPrint.getWidth(), imgToPrint.getHeight(), null);

        // Valid page
        return PAGE_EXISTS;
    }


    /**
     * Reads an image from base64 or a URL.
     *
     * @param rawData URL or Base64 encoded string
     * @return BufferedImage from {@code rawData}
     */
    private static BufferedImage readImage(String rawData, boolean fromBase64) throws IOException {
        if (fromBase64) {
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
