package qz.printer.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.printer.PrintOptions;
import qz.utils.SystemUtilities;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Sides;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.util.Arrays;
import java.util.List;

public abstract class PrintPostScript {

    private static final Logger log = LoggerFactory.getLogger(PrintPostScript.class);

    private static final List<Integer> MAC_BAD_IMAGE_TYPES = Arrays.asList(BufferedImage.TYPE_BYTE_BINARY, BufferedImage.TYPE_CUSTOM);


    protected PrintRequestAttributeSet applyDefaultSettings(PrintOptions.Postscript psOpts, PageFormat page) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

        //apply general attributes
        if (psOpts.getColorType() != null) {
            attributes.add(psOpts.getColorType().getChromatic());
        }
        if (psOpts.isDuplex()) {
            attributes.add(Sides.DUPLEX);
        }
        if (psOpts.getOrientation() != null) {
            attributes.add(psOpts.getOrientation().getAsAttribute());
        }

        // Fixes 4x6 labels on Mac OSX
        if (psOpts.getSize() != null && psOpts.getUnits() == PrintOptions.Unit.INCH && psOpts.getSize().getWidth() == 4 && psOpts.getSize().getHeight() == 6) {
            attributes.add(MediaSizeName.JAPANESE_POSTCARD);
        }

        //TODO - set paper thickness
        //TODO - set printer tray

        // Java print using inches at 72dpi
        final float CONVERT = psOpts.getUnits().asInch();

        log.trace("DPI: {}", (psOpts.getDensity() * CONVERT));
        float dpFactor = ((psOpts.getDensity() * CONVERT) / 72f);

        //apply sizing and margins
        Paper paper = new Paper();

        float pageX = 0f;
        float pageY = 0f;
        float pageW = (float)page.getWidth() * dpFactor;
        float pageH = (float)page.getHeight() * dpFactor;

        //page size
        if (psOpts.getSize() != null && psOpts.getSize().getWidth() > 0 && psOpts.getSize().getHeight() > 0) {
            pageW = (float)psOpts.getSize().getWidth() * psOpts.getDensity();
            pageH = (float)psOpts.getSize().getHeight() * psOpts.getDensity();

            paper.setSize((pageW * CONVERT) * dpFactor, (pageH * CONVERT) * dpFactor);
        }

        //margins
        if (psOpts.getMargins() != null) {
            pageX += psOpts.getMargins().left() * psOpts.getDensity();
            pageY += psOpts.getMargins().top() * psOpts.getDensity();
            pageW -= (psOpts.getMargins().right() + psOpts.getMargins().left()) * psOpts.getDensity();
            pageH -= (psOpts.getMargins().bottom() + psOpts.getMargins().top()) * psOpts.getDensity();
        }

        if (pageW > 0 && pageH > 0) {
            paper.setImageableArea(pageX * dpFactor, pageY * dpFactor, pageW * dpFactor, pageH * dpFactor);
            log.debug("Custom size: {},{}:{},{}", paper.getImageableX(), paper.getImageableY(), paper.getImageableWidth(), paper.getImageableHeight());

            page.setPaper(paper);
        } else {
            log.trace("Cannot apply custom size: {},{}:{},{}", pageX * dpFactor, pageY * dpFactor, pageW * dpFactor, pageH * dpFactor);
        }

        log.trace("{}", Arrays.toString(attributes.toArray()));

        return attributes;
    }

    /**
     * FIXME:  Temporary fix for OS X 10.10 hard crash.
     * See https://github.com/qzind/qz-print/issues/75
     */
    protected BufferedImage fixColorModel(BufferedImage imgToPrint) {
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

        return imgToPrint;
    }

}
