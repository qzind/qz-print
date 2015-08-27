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
import qz.common.LogIt;
import qz.printer.PaperFormat;
import qz.utils.SystemUtilities;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.ColorModel;
import java.awt.print.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.Arrays;


/**
 *
 * @author Tres Finocchiaro, Anton Mezerny
 */
public class PrintPostScript implements Printable {

    public static final Logger log = Logger.getLogger(PrintPostScript.class.getName());

    private final AtomicReference<BufferedImage> bufferedImage = new AtomicReference<BufferedImage>(null);
    private final AtomicReference<ByteBuffer> bufferedPDF = new AtomicReference<ByteBuffer>(null);
    private final AtomicReference<PDFFile> pdfFile = new AtomicReference<PDFFile>(null);
    private final AtomicReference<PrintService> printServiceAtomicReference = new AtomicReference<PrintService>(null);
    private final AtomicReference<String> jobName = new AtomicReference<String>("jZebra 2D Printing");
    private final AtomicReference<Paper> paper = new AtomicReference<Paper>(null);
    private final AtomicReference<PaperFormat> paperSize = new AtomicReference<PaperFormat>(null);
    private final AtomicReference<Float> pixelsPerInch = new AtomicReference<Float>(null);
    private final AtomicReference<Integer> copies = new AtomicReference<Integer>(null);
    private final AtomicReference<Boolean> logPostScriptFeatures = new AtomicReference<Boolean>(false);
    public static final float DPI = 72f;
    //Never used, OK to delete?
    //public static final float MMPI = 280f;
    private String pdfClass;

    public PrintPostScript() {
    }
    
    /**
     * Rotates a buffered image by the specified angle in radians.
     * Note:  To rotate in degrees convert to radians first using:
     * <code>Math.toRadians(degree);</code>
     * @param image BufferedImage to rotate
     * @param angle in radians
     * @return 
     */
    public static BufferedImage rotate(BufferedImage image, double angle) {
        LogIt.log("Rotating image " + angle);
        double sin = Math.abs(Math.sin(angle)), cos = Math.abs(Math.cos(angle));
        int w = image.getWidth(), h = image.getHeight();
        int neww = (int)Math.floor(w*cos+h*sin), newh = (int)Math.floor(h*cos+w*sin);
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0].getDefaultConfiguration();
        BufferedImage result = gc.createCompatibleImage(neww, newh, Transparency.TRANSLUCENT);
        Graphics2D g = result.createGraphics();
        g.translate((neww-w)/2, (newh-h)/2);
        g.rotate(angle, w/2, h/2);
        g.drawRenderedImage(image, null);
        g.dispose();
        return result;
    }

    /**
     * Can be called directly
     *
     * @throws PrinterException
     */
    public void print() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();

        int width;
        int height;
        
        if (this.paperSize.get() != null && this.paperSize.get().getRotation() > 0) {
            this.bufferedImage.set(rotate(this.bufferedImage.get(), Math.toRadians(this.paperSize.get().getRotation())));
            if (paperSize.get() != null) {
                // Readjust our paperSize after rotation
                 paperSize.get().setAutoSize(this.bufferedImage.get());
            }
        }

        if (this.bufferedImage.get() != null) {
            width = bufferedImage.get().getWidth();
            height = bufferedImage.get().getHeight();
        } else if (this.getPDFFile() != null) {
            width = (int) getPDFFile().getPage(1).getWidth();
            height = (int) getPDFFile().getPage(1).getHeight();
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
            log.info("A custom paper size was supplied.");
            attr.add(paperSize.get().getOrientationRequested());
            if (paperSize.get().isAutoSize()) {
                paperSize.get().setAutoSize(bufferedImage.get());
            }
            attr.add(new MediaPrintableArea(0f, 0f, paperSize.get().getAutoWidth(),
                    paperSize.get().getAutoHeight(), paperSize.get().getUnits()));

        } else {
            log.warning("A custom paper size was not supplied.");
            attr.add(new MediaPrintableArea(0f, 0f, width / DPI, height / DPI, MediaSize.INCH));
        }

        logSizeCalculations(paperSize.get(), width, height);

        job.setPrintService(printServiceAtomicReference.get());
        job.setPrintable(this);
        job.setJobName(jobName.get());

        // If copies are specified, handle them prior to printing
        if (copies.get() != null && copies.get() > 1) {
            log.info("Copies specified: " + copies.get());
            // Does the DocFlavor and Printer support "Copies" (this lies)
            //if (printServiceAtomicReference.get().isAttributeCategorySupported(Copies.class)) {
            //    LogIt.log("Printer supports copies");
            //    attr.add(new Copies(copies.get()));
            //    job.print();
            //} else {
            // Copies is unsupported, handle copies manually (yuck)
                for (int i = 0; i < copies.get(); i++) {
                    job.print(attr);
                }
            //}
        }
        // No copies specified, just print
        else {
            job.print(attr);
        }



        bufferedImage.set(null);
        bufferedPDF.set(null);
        pdfFile.set(null);
    }

    @SuppressWarnings("UnusedDeclaration")//Need to see if this should be deleted or implemented properly
    public void setPaper(Paper paper) {
        this.paper.set(paper);
    }//Unused. Probably needs to be implemented?

    /**
     * Implemented by Printable interface. Should not be called directly, see
     * print() instead
     *
     * @param graphics graphics class to pass
     * @param pageFormat the page format
     * @param pageIndex the page index
     * @return success/error code
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
            log.info("Using PDF renderer: " + className);
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
    private int printPDFRenderer(Graphics graphics, @SuppressWarnings("UnusedParameters") PageFormat pageFormat, int pageIndex) throws PrinterException {
        //Suppressing unused parameter warning. Need to see if it should actually be passed/used or not.
        //TOASK: Should we use pageFormat when printing PDFs?
        PDFFile pdf = getPDFFile();

        int currentPage = pageIndex + 1;

        if (pdf == null) {
            throw new PrinterException("No PDF data specified");
        }

        if (currentPage < 1 || currentPage > pdf.getNumPages()) {
            return NO_SUCH_PAGE;

        }

        // fit the PDFPage into the printing area
        Graphics2D graphics2D = (Graphics2D) graphics;
        PDFPage page = pdf.getPage(currentPage);

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
        //Rectangle imgbounds = new Rectangle(currentPage, currentPage)
        PDFRenderer pgs = new PDFRenderer(page, graphics2D, page.getPageBox().getBounds(), page.getBBox(), null);
        //PDFRenderer pgs = new PDFRenderer(page, graphics2D, getImageableRectangle(pageFormat), page.getBBox(), null);
        try {
            page.waitForFinish();

            pgs.run();
        } catch (InterruptedException ignore) {
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
         Graphics2D graphics2D = (Graphics2D) graphics;
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
         PDFRenderer pgs = new PDFRenderer(page, graphics2D, imgbounds, null, null);
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
        Graphics2D graphics2D = (Graphics2D) graphics;

        // Sugested by Bahadir 8/23/2012
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics2D.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        BufferedImage imgToPrint = bufferedImage.get();

        // FIXME:  Temporary fix for OS X 10.10 hard crash.
        // See https://github.com/qzind/qz-print/issues/75
        if (SystemUtilities.isMac()){
            //Add more bad types here as they come up.
            Integer[] badTypes = {BufferedImage.TYPE_BYTE_BINARY,BufferedImage.TYPE_CUSTOM};
            if (Arrays.asList(badTypes).contains(imgToPrint.getType())){
                BufferedImage sanitizedImage = null;
                ColorModel cm = imgToPrint.getColorModel(); 
                if (cm instanceof IndexColorModel){
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
        /* Now we perform our rendering */
        graphics2D.drawImage(imgToPrint, 0, 0, (int) pageFormat.getImageableWidth(), (int) pageFormat.getImageableHeight(), imgToPrint.getMinX(), imgToPrint.getMinY(), imgToPrint.getWidth(), imgToPrint.getHeight(), null);

        /* tell the caller that this page is part of the printed document */
        return PAGE_EXISTS;
    }

    /*
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

    /**
     * Returns pixels in 1 inch for the screen this is displaying on
     *
     * @return the pixels in an inch on the screen
     *
     */
    @SuppressWarnings("UnusedDeclaration")//TOASK: Unused, delete?
    public float getPixelsPerInch() {
        if (pixelsPerInch.get() == null) {
            try {
                pixelsPerInch.set((float) Toolkit.getDefaultToolkit().getScreenResolution());
            } catch (AWTError e) {
                pixelsPerInch.set(DPI);
            }
        }
        return pixelsPerInch.get();
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

    //TOASK: Unused, delete?
    @SuppressWarnings("UnusedDeclaration")
    public ByteBuffer getPDF() {
        return this.bufferedPDF.get();
    }

    public BufferedImage getImage() {
        return this.bufferedImage.get();
    }

    public void setPaperSize(PaperFormat paperSize) {
        this.paperSize.set(paperSize);
    }

    public void setPrintParameters(PrintApplet a) {
        setPrintParameters(a.getJobName(), a.getCopies(), a.getLogPostScriptFeatures());
    }

    public void setPrintParameters(String jobName, int copies, boolean logPostScriptFeatures) {
        // RKC: PROBLEM >>> setPrintService(a.getPrintService());
//        setMargin(rpa.getPSMargin());
        // RKC: PROBLEM >>> setPaperSize(a.getPaperSize());
        setCopies(copies);
        setJobName(jobName.replace(" ___ ", " PostScript "));
        setLogPostScriptFeatures(logPostScriptFeatures);
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
        this.printServiceAtomicReference.set(ps);
    }

    @SuppressWarnings("UnusedDeclaration")//TOASK: Unused, delete?
    public String getJobName() {
        return jobName.get();
    }

    public void setLogPostScriptFeatures(boolean logPostScriptFeatures) {
        this.logPostScriptFeatures.set(logPostScriptFeatures);
    }

    @SuppressWarnings("UnusedDeclaration")//TOASK: Unused, delete?
    public boolean getLogPostScriptFeatures() {
        return logPostScriptFeatures.get();
    }

    @SuppressWarnings("unchecked")
    private void logSupportedPrinterFeatures(PrinterJob job) {
        log.info("Supported Printing Attributes:");
        for (Class<?> cl : job.getPrintService().getSupportedAttributeCategories()) {
            log.info("   Attr type = " + cl + "=" + job.getPrintService().getDefaultAttributeValue((Class<? extends Attribute>) cl));
        }
    }

    public static void logSizeCalculations(PaperFormat format, float width, float height) {
        if (format != null) {
            log.info("Scaling image " + width + "px x " + height + "px to destination "
                    + format.toString());
        } else {
            log.info("Using image at \"natural\" resolution  " + width + "px " + height
                    + "px scaled to " + (int) (width / DPI) + "in x " + (int) (height / DPI)
                    + "in (assumes 72dpi)");
        }
    }

}
