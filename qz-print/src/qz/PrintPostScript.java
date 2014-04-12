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
package qz;

import com.sun.pdfview.PDFFile;
import com.sun.pdfview.PDFPage;
import com.sun.pdfview.PDFRenderer;
import java.awt.AWTError;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;

import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;

import javax.print.attribute.standard.OrientationRequested;

/**
 *
 * @author Tres Finocchiaro, Anton Mezerny
 */
public class PrintPostScript implements Printable {

    private final AtomicReference<BufferedImage> bufferedImage = new AtomicReference<BufferedImage>(null);
    private final AtomicReference<ByteBuffer> bufferedPDF = new AtomicReference<ByteBuffer>(null);
    private final AtomicReference<PDFFile> pdfFile = new AtomicReference<PDFFile>(null);
    private final AtomicReference<PrintService> ps = new AtomicReference<PrintService>(null);
    private final AtomicReference<String> jobName = new AtomicReference<String>("jZebra 2D Printing");
    private final AtomicReference<Paper> paper = new AtomicReference<Paper>(null);
    private final AtomicReference<PaperFormat> paperSize = new AtomicReference<PaperFormat>(null);
    private final AtomicReference<Float> pixelsPerInch = new AtomicReference<Float>(null);
    private final AtomicReference<Integer> copies = new AtomicReference<Integer>(null);
    private final AtomicReference<Boolean> logPostScriptFeatures = new AtomicReference<Boolean>(false);
    private static final float DPI = 72f;
    private String pdfClass;

    public PrintPostScript() {
    }

    /**
     * Can be called directly
     *
     * @throws PrinterException
     */
    public void print() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        int w;
        int h;

        if (this.bufferedImage.get() != null) {
            w = bufferedImage.get().getWidth();
            h = bufferedImage.get().getHeight();
        } else if (this.getPDFFile() != null) {
            w = (int) getPDFFile().getPage(1).getWidth();
            h = (int) getPDFFile().getPage(1).getHeight();
        } else {
            throw new PrinterException("Corrupt or missing file supplied.");
        }

        // Echo some supported attributes to the screen
        if (logPostScriptFeatures.get()) {
            logSupportedPrinterFeatures(job);
        }

        // Fixes 1" white border problem - May need tweaking
        HashPrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();

        // If paper size and units are specified, use them, if not, assume the area is 
        // the image's natural size on the computer screen and use the old method
        // *Note:  Computer screen dpi's can change, but print consistancy 
        // cross-platform is more important than accuracy, so we'll always assume 72dpi
        if (paperSize.get() != null) {
            attr.add(paperSize.get().getOrientationRequested());
            if (paperSize.get().isAutoSize()) {
                paperSize.get().setAutoSize(bufferedImage.get());
            }
            attr.add(new MediaPrintableArea(0f, 0f, paperSize.get().getAutoWidth(),
                    paperSize.get().getAutoHeight(), paperSize.get().getUnits()));

        } else {
            attr.add(new MediaPrintableArea(0f, 0f, w / 72f, h / 72f, MediaSize.INCH));
        }

        logSizeCalculations(paperSize.get(), w, h);

        if (copies.get() != null) {
            attr.add(new Copies(copies.get().intValue()));
        }

        job.setPrintService(ps.get());
        job.setPrintable(this);
        job.setJobName(jobName.get());
        job.print(attr);

        bufferedImage.set(null);
        bufferedPDF.set(null);
        pdfFile.set(null);
    }

    public void setPaper(Paper paper) {
        this.paper.set(paper);
    }
    /*
     public void setMargin(double[] margin) {
     if (margin != null) {
     switch (margin.length) {
     case 1:
     setMargin(margin[0]);
     return;
     case 4:
     setMargin(margin[0], margin[1], margin[2], margin[3]);
     return;
     default:
     return;
     }
     }
     }
    
     public void setMargin(int margin) {
     setMargin((double) margin);
     }
    
     public void setMargin(double margin) {
     setMargin(margin, margin, margin, margin);
     }
    
     public void setMargin(double top, double left, double bottom, double right) {
     if (this.paper.get() == null) {
     this.paper.set(new Paper());
     }
     this.paper.get().setImageableArea(left, top, paper.get().getWidth() - (left + right), paper.get().getHeight() - (top + bottom));
     }*/

    /*public void setMargin(Rectangle r) {
     if (this.paper.get() == null) {
     this.paper.set(new Paper());
     }
     this.paper.get().setImageableArea(r.getX(), r.getY(), r.getWidth(), r.getHeight());
     }*/

    /*public int print(Applet applet, BufferedImage bufferedImage) throws PrinterException {
     this.bufferedImage.set(bufferedImage);
     return print(applet.getGraphics(), this.pageFormat.get(), 0);
     }*/
    /**
     * Implemented by Printable interface. Should not be called directly, see
     * print() instead
     *
     * @param graphics
     * @param pageFormat
     * @param pageIndex
     * @return
     * @throws PrinterException
     */
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {

        if (paper.get() != null) {
            pageFormat.setPaper(paper.get());
        }

        if (this.bufferedImage.get() != null) {
            return printImage(graphics, pageFormat, pageIndex);
        } else if (this.bufferedPDF.get() != null) {
            // PDF-Renderer plugin
            if (isClass("com.sun.pdfview.PDFFile")) {
                return printPDFRenderer(graphics, pageFormat, pageIndex);
            } else {
                throw new PrinterException("No suitable PDF render was found in the 'lib' directory.");
            }
        } else {
            throw new PrinterException("Unupported file/data type was supplied");
        }
    }

    private boolean isClass(String className) {
        if (className != null && className.equals(pdfClass)) {
            return true;
        }
        try {
            Class.forName(className);
            LogIt.log("Using PDF renderer: " + className);
            pdfClass = className;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /*private int printJPedalNonFree(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
     PdfDecoder pdf = getPDFDecoder();
    
     pdf.setPrintAutoRotateAndCenter(false);
     pdf.setPrintPageScalingMode(PrinterOptions.PAGE_SCALING_NONE);
     PdfBook pdfBook = new PdfBook(pdf, , attributes);
     pdfBook.setChooseSourceByPdfPageSize(false);
    
     return 0;
     }*/
    /**
     * JPedal renders a very poor quality image representation of the PDF
     * document. Paid version seems to support hiRes
     *
     * private int printJPedal(Graphics graphics, PageFormat pageFormat, int
     * pageIndex, boolean hiRes) throws PrinterException { PdfDecoder pdf =
     * getPDFDecoder(); try { Map mapValues = new HashMap();
     * mapValues.put(JPedalSettings.IMAGE_HIRES, true);
     * mapValues.put(JPedalSettings.EXTRACT_AT_BEST_QUALITY_MAXSCALING, new
     * Integer(1)); bufferedImage.set(hiRes ? pdf.getPageAsHiRes(pageIndex + 1,
     * mapValues) : pdf.getPageAsImage(pageIndex + 1)); return
     * printImage(graphics, pageFormat, pageIndex); } catch (Exception e) {
     * throw new PrinterException(e.getMessage()); }
     *
     *
     */
    /*int pg = pageIndex + 1;
    
     if (pdf == null) {
     throw new PrinterException("No PDF data specified");
     }
    
     if (pg < 1 || pg > pdf.getNumberOfPages()) {
     return NO_SUCH_PAGE;
     }; 
    
     pdf.setPrintAutoRotateAndCenter(false);
     try {
    
     printImage(graphics, pageFormat, pageIndex);
     }
    
     }*/
    private int printPDFRenderer(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {

        PDFFile pdf = getPDFFile();

        int pg = pageIndex + 1;

        if (pdf == null) {
            throw new PrinterException("No PDF data specified");
        }

        if (pg < 1 || pg > pdf.getNumPages()) {
            return NO_SUCH_PAGE;

        };

        // fit the PDFPage into the printing area
        Graphics2D g2 = (Graphics2D) graphics;
        PDFPage page = pdf.getPage(pg);

        //double pwidth = page.getWidth(); //pdf.getImageableWidth();
        //double pheight = page.getHeight(); //pdf.getImageableHeight();
        //double aspect = page.getAspectRatio();
        //double paperaspect = pwidth / pheight;
        /*
         Rectangle imgbounds;
        
         if (aspect > paperaspect) {
         // paper is too tall / pdfpage is too wide
         int height = (int) (pwidth / aspect);
         imgbounds = new Rectangle(
         (int) pageFormat.getImageableX(),
         (int) (pageFormat.getImageableY() + ((pheight - height) / 2)),
         (int) pwidth,
         height);
         } else {
         // paper is too wide / pdfpage is too tall
         int width = (int) (pheight * aspect);
         imgbounds = new Rectangle(
         (int) (pageFormat.getImageableX() + ((pwidth - width) / 2)),
         (int) pageFormat.getImageableY(),
         width,
         (int) pheight);
         }*//**/

        // render the page
        //Rectangle imgbounds = new Rectangle(pg, pg)
        PDFRenderer pgs = new PDFRenderer(page, g2, page.getPageBox().getBounds(), page.getBBox(), null);
        //PDFRenderer pgs = new PDFRenderer(page, g2, getImageableRectangle(pageFormat), page.getBBox(), null);
        try {
            page.waitForFinish();

            pgs.run();
        } catch (InterruptedException ie) {
        }

        return PAGE_EXISTS;
        /*
         
         // TODO: Proper resizing code... needs work

         PDFFile pdf = getPDFFile();
         // don't bother if the page number is out of range.
         if (pdf == null || pageIndex < 1 || pageIndex <= pdf.getNumPages()) {
         return NO_SUCH_PAGE;
         }
         // fit the PDFPage into the printing area
         Graphics2D g2 = (Graphics2D) graphics;
         PDFPage page = pdf.getPage(pageIndex);
         double pwidth = pageFormat.getImageableWidth();
         double pheight = pageFormat.getImageableHeight();

         double aspect = page.getAspectRatio();
         double paperaspect = pwidth / pheight;

         Rectangle imgbounds;

         if (aspect > paperaspect) {
         // paper is too tall / pdfpage is too wide
         int height = (int) (pwidth / aspect);
         imgbounds = new Rectangle(
         (int) pageFormat.getImageableX(),
         (int) (pageFormat.getImageableY() + ((pheight - height) / 2)),
         (int) pwidth,
         height);
         } else {
         // paper is too wide / pdfpage is too tall
         int width = (int) (pheight * aspect);
         imgbounds = new Rectangle(
         (int) (pageFormat.getImageableX() + ((pwidth - width) / 2)),
         (int) pageFormat.getImageableY(),
         width,
         (int) pheight);
         }

         // render the page
         PDFRenderer pgs = new PDFRenderer(page, g2, imgbounds, null, null);
         try {
         page.waitForFinish();
         pgs.run();
         } catch (InterruptedException ie) {
         }

         return PAGE_EXISTS;
         * 
         * 
         */

    }


    /*private Rectangle getImageableRectangle(PageFormat format) {
     return new Rectangle(
     (int) format.getImageableX(), (int) format.getImageableY(),
     (int) format.getImageableWidth(), (int) format.getImageableHeight());
     }*/
    private int printImage(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        /* Graphics and pageFormat are required.  Page index is zero-based */
        if (graphics == null) {
            throw new PrinterException("No graphics specified");
        }
        if (pageFormat == null) {
            throw new PrinterException("No page format specified");
        }
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }

        if (paperSize.get() != null) {
            pageFormat.setOrientation(paperSize.get().getOrientation());
        }


        /* User (0,0) is typically outside the imageable area, so we must
         * translate by the X and Y values in the PageFormat to avoid clipping
         */
        Graphics2D g2d = (Graphics2D) graphics;

        // Sugested by Bahadir 8/23/2012
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        BufferedImage imgToPrint = bufferedImage.get();
        /* Now we perform our rendering */
        g2d.drawImage(this.bufferedImage.get(), 0, 0, (int) pageFormat.getImageableWidth(), (int) pageFormat.getImageableHeight(), imgToPrint.getMinX(), imgToPrint.getMinY(), imgToPrint.getWidth(), imgToPrint.getHeight(), null);

        /* tell the caller that this page is part of the printed document */
        return PAGE_EXISTS;
    }

    /**
     * // Future image features (Thanks Anton) protected Rectangle
     * getDrawRect(PageFormat pageFormat) { Dimension imageSize =
     * getImageDpiSize();
     *
     * // Do resize if image size is more than page size if (imageSize.width >
     * pageFormat.getImageableWidth() || imageSize.height >
     * pageFormat.getImageableHeight()) { double scale =
     * getScaleByPageSize(pageFormat);
     *
     * double width = imageSize.width / scale; double heigth = imageSize.height
     * / scale;
     *
     * return new Rectangle(0, 0, (int)width, (int)heigth); }
     *
     * // Returns draw coordinates return new Rectangle(0, 0, imageSize.width,
     * imageSize.height); }
     *
     *
     * // Returning scale to print the whole image on a 1 page protected double
     * getScaleByPageSize(PageFormat pageFormat) { double scale = 1; Dimension
     * imageSize = getImageDpiSize();
     *
     * // If it's bigger we shall do nothing if (imageSize.width >
     * pageFormat.getImageableWidth() || imageSize.height >
     * pageFormat.getImageableHeight()) { double widthScale = imageSize.width /
     * pageFormat.getImageableWidth(); double heigthScale = imageSize.height /
     * pageFormat.getImageableHeight();
     *
     * scale = Math.max(widthScale, heigthScale); }
     *
     * return scale; }
     *
     *
     * // Calculates image size in Pixels private Dimension getImagePixelsSize()
     * {
     *
     * return new Dimension(bufferedImage.get().getWidth(null),
     * bufferedImage.get().getHeight(null)); }
     *
     *
     * // Returns image size in Dpi private Dimension getImageDpiSize() {
     * Dimension imageSize = getImagePixelsSize();
     *
     * int width = (int) (imageSize.width / getPixelsPerInch() * getImageDpi());
     * int heigth = (int) (imageSize.height / getPixelsPerInch() *
     * getImageDpi());
     *
     * return new Dimension(width, heigth); }
     *
     * private int getImageDpi() { return 150; }
     *
     */
    // Returns pixels in 1 inch for the screen this is displaying on
    public float getPixelsPerInch() {
        if (pixelsPerInch.get() == null) {
            try {
                pixelsPerInch.set(new Float(Toolkit.getDefaultToolkit().getScreenResolution()));
            } catch (AWTError e) {
                pixelsPerInch.set(new Float(72f));
            }
        }
        return pixelsPerInch.get().floatValue();
    }

    /*
     private BufferedImage getResizedImage(BufferedImage imageToResize){
     return null;
     }
    
     */
    private PDFFile getPDFFile() throws PrinterException {

        if (pdfFile.get() == null && bufferedPDF.get() != null) {
            try {
                pdfFile.set(new PDFFile(this.bufferedPDF.get()));
            } catch (Exception e) {
                throw new PrinterException(e.getMessage());
            }
        }
        return pdfFile.get();
    }

    public void setImage(byte[] imgData) throws IOException {
        InputStream in = new ByteArrayInputStream(imgData);
        this.bufferedImage.set(ImageIO.read(in));
    }

    public void setImage(BufferedImage bufferedImage) {
        this.bufferedImage.set(bufferedImage);
    }

    public void setPDF(ByteBuffer bufferedPDF) {
        this.bufferedPDF.set(bufferedPDF);
    }

    public ByteBuffer getPDF() {
        return this.bufferedPDF.get();
    }

    public BufferedImage getImage() {
        return this.bufferedImage.get();
    }

    public void setPaperSize(PaperFormat PaperSize) {
        this.paperSize.set(PaperSize);
    }

    public void setPrintParameters(PrintApplet a) {
        setPrintService(a.getPrintService());
//        setMargin(rpa.getPSMargin());
        setPaperSize(a.getPaperSize());
        setCopies(a.getCopies());
        setJobName(a.getJobName().replace(" ___ ", " PostScript "));
        setLogPostScriptFeatures(a.getLogPostScriptFeatures());
    }

    /*public void setAutoSize(Boolean autoSize) {
     this.autoSize.set(autoSize);
     }*/
    /*public Boolean isAutoSize() {
     return this.autoSize.get();
     }*/
    public void setCopies(Integer copies) {
        this.copies.set(copies);
    }

    public void setJobName(String jobName) {
        this.jobName.set(jobName);
    }

    public void setPrintService(PrintService ps) {
        this.ps.set(ps);
    }

    public String getJobName() {
        return jobName.get();
    }

    public void setLogPostScriptFeatures(boolean b) {
        this.logPostScriptFeatures.set(b);
    }

    public boolean getLogPostScriptFeatures() {
        return logPostScriptFeatures.get();
    }

    @SuppressWarnings("unchecked")
    private void logSupportedPrinterFeatures(PrinterJob job) {
        LogIt.log(Level.INFO, "Supported Printing Attributes:");
        for (Class<?> cl : job.getPrintService().getSupportedAttributeCategories()) {
            LogIt.log(Level.INFO, "   Attr type = " + cl + "=" + job.getPrintService().getDefaultAttributeValue((Class<? extends Attribute>) cl));
        }
    }

    public static void logSizeCalculations(PaperFormat p, float w, float h) {
        if (p != null) {
            LogIt.log("Scaling image " + w + "px x " + h + "px to destination "
                    + p.toString());
        } else {
            LogIt.log("Using image at \"natural\" resolution  " + w + "px " + h
                    + "px scaled to " + (int) (w / 72f) + "in x " + (int) (h / 72f)
                    + "in (assumes 72dpi)");
        }
    }

}
