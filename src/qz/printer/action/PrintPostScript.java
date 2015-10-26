package qz.printer.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.printer.PrintOptions;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Sides;
import java.awt.print.PageFormat;
import java.awt.print.Paper;

public abstract class PrintPostScript {

    private static final Logger log = LoggerFactory.getLogger(PrintPostScript.class);

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

        //TODO - set paper thickness

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

        return attributes;
    }

}
