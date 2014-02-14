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

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import static java.awt.print.Printable.NO_SUCH_PAGE;
import static java.awt.print.Printable.PAGE_EXISTS;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.print.PrintException;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.MediaSize;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import org.apache.pdfbox.pdmodel.PDDocument;
import qz.exception.InvalidRawImageException;
import qz.exception.NullCommandException;

/**
 * PrintJob will provide an object to hold an entire job. It should contain the 
 * raw data for the job, the title of the job, and it's current state.
 * 
 * @author Thomas Hart
 */
public class PrintJob extends JLabel implements Runnable, Printable {
    
    private PrintJobState state = PrintJobState.STATE_CREATED;
    private final String title = "Print Job";
    private final ArrayList<PrintJobElement> rawData = new ArrayList<PrintJobElement>();;
    private Boolean running = true;
    private final int updateDelay = 100;
    private Printer printer;
    private PrintJobType type;
    private Graphics graphics;
    private PageFormat pageFormat;
    private int pageIndex;
    private PaperFormat paperSize;
    private String jobHost;
    private int jobPort;
    private boolean logPSFeatures;
    private boolean autoSize = false;
    private boolean alternatePrint;
    private int copies = 1;
    private int leftMargin = 0;
    private int topMargin = 0;
    
    public void run() {
        
        while(running) {
            //Loop
        }
    }
    
    /**
     * Cancel a print job.
     */
    public void cancel() {
        state = PrintJobState.STATE_CANCELLED;
        running = false;
    }
    
    /**
     * Get the job's current state.
     * 
     * @return The current PrintJobState
     */
    public PrintJobState getJobState() {
        return state;
    }
    
    /**
     * Get the current job title.
     * 
     * @return The job title
     */
    public String getTitle() {
        return title;
    }
    
    /**
     * Append adds a raw data PrintJobElement
     * 
     * @param appendData The data to append
     * @param charset The data's charset
     */
    public void append(ByteArrayBuilder appendData, Charset charset) {
        type = PrintJobType.TYPE_RAW;
        try {
            PrintJobElement pje = new PrintJobElement(this, appendData, PrintJobElementType.TYPE_RAW, charset);
            rawData.add(pje);
        }
        catch(NullPointerException ex) {
            LogIt.log(Level.SEVERE, "Could not append data.", ex);
        }
    }
    
    /**
     * appendImage adds a raw image PrintJobElement
     * 
     * @param imagePath The file path of the image
     * @param charset The charset of the file path
     * @param lang The raw printer language to translate the image to
     * @param imageX The width of the image
     * @param imageY The height of the image
     */
    public void appendImage(ByteArrayBuilder imagePath, Charset charset, String lang, int imageX, int imageY) {
        type = PrintJobType.TYPE_RAW;
        try {
            PrintJobElement pje = new PrintJobElement(this, imagePath, PrintJobElementType.TYPE_IMAGE, charset, lang, imageX, imageY);
            rawData.add(pje);
        }
        catch(NullPointerException ex) {
            LogIt.log(Level.SEVERE, "Could not append image.", ex);
        }
    }
    
    /**
     * appendImage adds a raw image PrintJobElement
     * 
     * @param imagePath The file path of the image
     * @param charset The charset of the file path
     * @param lang The raw printer language to translate the image to
     * @param dotDensity The dot density of the image
     */
    public void appendImage(ByteArrayBuilder imagePath, Charset charset, String lang, int dotDensity) {
        type = PrintJobType.TYPE_RAW;
        try {
            PrintJobElement pje = new PrintJobElement(this, imagePath, PrintJobElementType.TYPE_IMAGE, charset, lang, dotDensity);
            rawData.add(pje);
        }
        catch(NullPointerException ex) {
            LogIt.log(Level.SEVERE, "Could not append image.", ex);
        }
    }
    
    /**
     * appendPSImage adds an image PrintJobElement to a PostScript job
     * 
     * @param url The path of the image
     * @param charset The charset of the path
     */
    public void appendPSImage(ByteArrayBuilder url, Charset charset) {
        type = PrintJobType.TYPE_PS;
        PrintJobElement pje = new PrintJobElement(this, url, PrintJobElementType.TYPE_IMAGE_PS, charset);
        rawData.add(pje);
    }

    /**
     * appendXML pulls data from an xml file/tag and appends it to a raw print
     * job
     * 
     * @param url The path of the xml file
     * @param charset The charset of the path
     * @param xmlTag The XML tag to pull the data from
     */
    public void appendXML(ByteArrayBuilder url, Charset charset, String xmlTag) {
        type = PrintJobType.TYPE_RAW;
        PrintJobElement pje = new PrintJobElement(this, url, PrintJobElementType.TYPE_XML, charset, xmlTag);
        rawData.add(pje);
    }
    
    /**
     * appendFile reads the contents of a file and adds the data to a raw
     * print job
     * 
     * @param url The path of the file
     * @param charset The charset of the path
     */
    public void appendFile(ByteArrayBuilder url, Charset charset) {
        type = PrintJobType.TYPE_RAW;
        PrintJobElement pje = new PrintJobElement(this, url, PrintJobElementType.TYPE_FILE, charset);
        rawData.add(pje);
    }
    
    public void appendRtfFile(ByteArrayBuilder url, Charset charset) {
        type = PrintJobType.TYPE_RTF;
        PrintJobElement pje = new PrintJobElement(this, url, PrintJobElementType.TYPE_RTF, charset);
        rawData.add(pje);
    }
    
    /**
     * appendHTML adds an HTML type PrintJobElement to an HTML PrintJob
     * 
     * @param html The HTML to add
     * @param charset The charset of the HTML
     */
    public void appendHTML(ByteArrayBuilder html, Charset charset) {
        type = PrintJobType.TYPE_HTML;
        PrintJobElement pje = new PrintJobElement(this, html, PrintJobElementType.TYPE_HTML, charset);
        rawData.add(pje);
    }
    
    /**
     * appendPDF adds a PDF file PrintJobElement to a PostScript PrintJob
     * 
     * @param url
     * @param charset 
     */
    public void appendPDF(ByteArrayBuilder url, Charset charset) {
        type = PrintJobType.TYPE_PDF;
        PrintJobElement pje = new PrintJobElement(this, url, PrintJobElementType.TYPE_PDF, charset);
        rawData.add(pje);
    }
    
    /**
     * prepareJob processes the list of PrintJobElements and gets the data
     * prepared for printing.
     * 
     * @throws InvalidRawImageException
     * @throws NullCommandException 
     */
    public void prepareJob() throws InvalidRawImageException, NullCommandException {
        
        state = PrintJobState.STATE_PROCESSING;
        
        ListIterator dataIterator = rawData.listIterator();

        while(dataIterator.hasNext()) {
            try {
                PrintJobElement pje = (PrintJobElement) dataIterator.next();
                pje.prepare();
            } catch (IOException ex) {
                LogIt.log(Level.SEVERE, "Could not prepare job.", ex);
            }
        }
        
        state = PrintJobState.STATE_PROCESSED;
        
    }
    
    /**
     * Mark the PrintJob as queued
     */
    public void queue() {
        state = PrintJobState.STATE_QUEUED;
    }
    
    /**
     * Returns a string with the contents of the job data. This is only really
     * useful for Raw data PrintJobs
     * 
     * @return A String representation of the job data
     */
    public String getInfo() {
        
        String jobInfo = "";
        
        if(type == PrintJobType.TYPE_RAW) {
            ListIterator dataIterator = rawData.listIterator();

            while(dataIterator.hasNext()) {
                PrintJobElement pje = (PrintJobElement) dataIterator.next();
                ByteArrayBuilder bytes = pje.getData();
                String info;
                try {
                    info = new String(bytes.getByteArray(), pje.getCharset().name());
                    jobInfo += info;
                } catch (UnsupportedEncodingException ex) {
                    LogIt.log(Level.SEVERE, "Unsupported encoding.", ex);
                }
            }
        }
        else {
            LogIt.log(Level.WARNING, "Unsupported job type.");
        }
        
        return jobInfo;
        
    }
    
    /**
     * print concatenates the PrintJobElements and sends the data to the proper
     * printer
     */
    @SuppressWarnings("unchecked")
    public void print() {
        state = PrintJobState.STATE_SENDING;
        
        for(int i=0; i < copies; i++) {
            if(type == PrintJobType.TYPE_RAW) {
                ByteArrayBuilder jobData = new ByteArrayBuilder();

                // Concatenate all the PrintJobElements into one ByteArrayBuilder
                ListIterator dataIterator = rawData.listIterator();

                while(dataIterator.hasNext()) {
                    PrintJobElement pje = (PrintJobElement) dataIterator.next();
                    ByteArrayBuilder bytes = pje.getData();
                    jobData.append(bytes.getByteArray());
                }

                try {

                    printer.setJobTitle(title);
                    if(jobHost != null) {
                        RawPrinter rawPrinter = (RawPrinter)printer;
                        rawPrinter.printToHost(jobData, jobHost, jobPort);
                    }
                    else if(alternatePrint) {
                        printer.printAlternate(jobData);
                    }
                    else {
                        printer.printRaw(jobData);
                    }
                } catch (PrintException ex) {
                    LogIt.log(Level.SEVERE, "Could not print raw job.", ex);
                }
            }
            else if(type == PrintJobType.TYPE_HTML) {

                ByteArrayBuilder jobData = new ByteArrayBuilder();

                // Concatenate all the PrintJobElements into one ByteArrayBuilder
                ListIterator dataIterator = rawData.listIterator();

                Charset charset = null;
                while(dataIterator.hasNext()) {
                    PrintJobElement pje = (PrintJobElement) dataIterator.next();
                    ByteArrayBuilder bytes = pje.getData();
                    jobData.append(bytes.getByteArray());
                    charset = pje.getCharset();
                }

                JFrame j = new JFrame(title);
                j.setUndecorated(true);
                j.setLayout(new FlowLayout());
                this.setBorder(null);

                String jobDataString = null;

                try {
                    if(charset != null) {
                        jobDataString = new String(jobData.getByteArray(), charset.name());
                    }
                    jobDataString += "</html>";
                } catch (UnsupportedEncodingException ex) {
                    LogIt.log(Level.SEVERE, "Unsupported encoding.", ex);
                }

                this.setText(jobDataString);
                
                j.add(this);
                j.pack();
                j.setExtendedState(JFrame.ICONIFIED);
                j.setVisible(true);
                
                // Elimate any margins
                HashPrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();             
                attr.add(new MediaPrintableArea(0f, 0f, (getWidth()) /72f, (getHeight() + topMargin) / 72f, MediaPrintableArea.INCH));               

                PrinterJob job = PrinterJob.getPrinterJob();    
                try {
                    job.setPrintService(printer.getPrintService());
                } catch (PrinterException ex) {
                    LogIt.log(Level.SEVERE, "Could not print HTML job.", ex);
                }

                if(logPSFeatures) {
                    logSupportedPrinterFeatures(job);
                }

                job.setPrintable(this);
                job.setJobName(title);
                try {
                    job.print(attr);
                } catch (PrinterException ex) {
                    LogIt.log(Level.SEVERE, "Could not print HTML job.", ex);
                }
                j.setVisible(false);
                j.dispose();

            }
            else if(type == PrintJobType.TYPE_RTF) {

                // If printer is a raw printer, log an error and bypass printing.
                if(printer instanceof RawPrinter) {
                    LogIt.log(Level.WARNING, "RTF data can not be sent to a raw printer.");
                }
                else {
                    try {

                        PrintJobElement firstElement = rawData.get(0);
                        PrinterJob job = PrinterJob.getPrinterJob();

                        if(logPSFeatures) {
                            logSupportedPrinterFeatures(job);
                        }

                        int w = firstElement.getRtfWidth();;
                        int h = firstElement.getRtfHeight();;

                        HashPrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();

                        /*
                        if (paperSize != null) {
                            attr.add(paperSize.getOrientationRequested());
                            attr.add(new MediaPrintableArea(0f, 0f, paperSize.getAutoWidth(), paperSize.getAutoHeight(), paperSize.getUnits()));
                        } else {
                            attr.add(new MediaPrintableArea(0f, 0f, w / 72f, h / 72f, MediaSize.INCH));
                        }
                        */

                        job.setPrintService(printer.getPrintService());

                        JEditorPane rtfData = firstElement.getRtfData();

                        // Use Reflection to call getPrintable on a JEditorPane if
                        // available. Must be compiled with Java >= 1.6
                        Class c = rtfData.getClass();

                        Class[] paramList = new Class[2];
                        paramList[0] = MessageFormat.class;
                        paramList[1] = MessageFormat.class;

                        Method m = c.getMethod("getPrintable", paramList);
                        MessageFormat format = new MessageFormat("");
                        Printable p = (Printable)m.invoke(rtfData, format, format);

                        job.setPrintable(p);
                        job.setJobName(title);
                        job.print(attr);

                    } catch (PrinterException ex) {
                        LogIt.log(Level.SEVERE, "Could not print RTF job.", ex);
                    }catch (IndexOutOfBoundsException ex) {
                        LogIt.log(Level.SEVERE, "Could not print RTF job.", ex);
                    }catch (IllegalAccessException ex) {
                        LogIt.log(Level.SEVERE, "Could not print RTF job.", ex);
                    }catch (InvocationTargetException ex) {
                        LogIt.log(Level.SEVERE, "Could not print RTF job.", ex);
                    } catch(NoSuchMethodException ex) {
                        LogIt.log(Level.WARNING, "RTF printing requires this applet to be compiled with Java >= 1.6");
                    } catch(IllegalArgumentException ex) {
                        LogIt.log(Level.SEVERE, "Illegal argument exception. " + ex);
                    }

                }

            }
            else if(type == PrintJobType.TYPE_PDF) {
                // If printer is a raw printer, log an error and bypass printing.
                if(printer instanceof RawPrinter) {
                    LogIt.log(Level.WARNING, "PostScript data can not be sent to a raw printer.");
                }
                else {
                    try {
                        PDDocument pdfFile;
                        pdfFile = null;
                        
                        while(pdfFile == null) {
                            pdfFile = rawData.get(0).getPDFFile();
                        }
                        
                        PrinterJob job = PrinterJob.getPrinterJob();
                        job.setPrintService(printer.getPrintService());
                        
                        pdfFile.silentPrint(job);
                        pdfFile.close();
                    } catch (PrinterException ex) {
                        LogIt.log(Level.SEVERE, "There was an error printing this job. " + ex);
                    } catch (IOException ex) {
                        LogIt.log(Level.WARNING, "Could not close PDF file. " + ex);
                    }
                    
                }
                
            }
            else if(type == PrintJobType.TYPE_PS) {

                // If printer is a raw printer, log an error and bypass printing.
                if(printer instanceof RawPrinter) {
                    LogIt.log(Level.WARNING, "PostScript data can not be sent to a raw printer.");
                }
                else {
                    try {

                        PrintJobElement firstElement = rawData.get(0);
                        PrinterJob job = PrinterJob.getPrinterJob();

                        if(logPSFeatures) {
                            logSupportedPrinterFeatures(job);
                        }

                        int w;
                        int h;

                        if (firstElement.getBufferedImage() != null) {
                            w = firstElement.getBufferedImage().getWidth();
                            h = firstElement.getBufferedImage().getHeight();
                        } 
                        /*
                        else if (firstElement.getPDFFile() != null) {
                            w = (int) firstElement.getPDFPages().getPageFormat(1).getWidth();
                            h = (int) firstElement.getPDFPages().getPageFormat(1).getHeight();
                        }
                        */
                        else if (firstElement.getRtfData() != null) {
                            w = firstElement.getRtfWidth();
                            h = firstElement.getRtfHeight();
                        }
                        else {
                            throw new PrinterException("Corrupt or missing file supplied.");
                        }

                        HashPrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();

                        if (paperSize != null) {
                            attr.add(paperSize.getOrientationRequested());
                            if (paperSize.isAutoSize()) {
                                if(rawData.get(0).getType() == PrintJobElementType.TYPE_IMAGE_PS) {
                                    paperSize.setAutoSize(rawData.get(0).getBufferedImage());
                                }
                            }
                            attr.add(new MediaPrintableArea(0f, 0f, paperSize.getAutoWidth(), paperSize.getAutoHeight(), paperSize.getUnits()));

                        } else {
                            attr.add(new MediaPrintableArea(0f, 0f, (w + leftMargin) / 72f, (h + topMargin) / 72f, MediaSize.INCH));
                        }

                        job.setPrintService(printer.getPrintService());
                        job.setPrintable(this);
                        job.setJobName(title);
                        job.print(attr);

                    } catch (PrinterException ex) {
                        LogIt.log(Level.SEVERE, "Could not print PostScript job.", ex);
                    } catch (IndexOutOfBoundsException ex) {
                        LogIt.log(Level.SEVERE, "Could not print PostScript job.", ex);
                    }
                }
            }
            else {
                LogIt.log(Level.WARNING, "Unsupported job type.");
            }
        }    
        state = PrintJobState.STATE_COMPLETE;

    }
    
    /**
     * Set the job's printer.
     * 
     * @param printer The target printer
     */
    public void setPrinter(Printer printer) {
        this.printer = printer;
    }
    
    /**
     * Get the job's current printer.
     * 
     * @return The current printer.
     */
    public Printer getPrinter() {
        return printer;
    }

    /**
     * This function is not called directly. It's used by the Printable interface to render each page
     * 
     * @param graphics
     * @param pageFormat
     * @param pageIndex
     * @return
     * @throws PrinterException 
     */
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if(pageIndex < rawData.size()) {
            PrintJobElement pje = rawData.get(pageIndex);
            if(pje.getType() == PrintJobElementType.TYPE_IMAGE_PS) {
                /* User (0,0) is typically outside the imageable area, so we must
                * translate by the X and Y values in the PageFormat to avoid clipping
                */
                Graphics2D g2d = (Graphics2D) graphics;

                // Sugested by Bahadir 8/23/2012
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                BufferedImage imgToPrint = pje.getBufferedImage();
                
                /* Now we perform our rendering */
                if(autoSize) {
                    g2d.drawImage(imgToPrint, leftMargin, topMargin, (int) pageFormat.getImageableWidth() - leftMargin, (int) pageFormat.getImageableHeight() - topMargin, imgToPrint.getMinX(), imgToPrint.getMinY(), imgToPrint.getWidth(), imgToPrint.getHeight(), null);
                }
                else {
                    g2d.drawImage(imgToPrint, leftMargin, topMargin, imgToPrint.getWidth() + leftMargin, imgToPrint.getHeight() + topMargin, imgToPrint.getMinX(), imgToPrint.getMinY(), imgToPrint.getWidth(), imgToPrint.getHeight(), null);
                }
                
                /* tell the caller that this page is part of the printed document */
                return PAGE_EXISTS;
                
            }
            /*
            else if(pje.getType() == PrintJobElementType.TYPE_PDF) {
                return pje.printPDFRenderer(graphics, pageFormat, pageIndex, leftMargin, topMargin);
            }
            */
            else if(pje.getType() == PrintJobElementType.TYPE_HTML) {
                boolean doubleBuffered = super.isDoubleBuffered();
                super.setDoubleBuffered(false);

                Graphics2D g2d = (Graphics2D) graphics;
                g2d.translate(pageFormat.getImageableX() + leftMargin, pageFormat.getImageableY() + topMargin);
                //g2d.translate(paper.getImageableX(), paper.getImageableY());
                this.paint(g2d);
                super.setDoubleBuffered(doubleBuffered);
                return (PAGE_EXISTS);
            }
        }
        
        return NO_SUCH_PAGE;
    }
    
    /**
     * Set this PrintJob to print to a remote host.
     * 
     * @param jobHost The target host
     * @param jobPort The target port on the host
     */
    public void setHostOutput(String jobHost, int jobPort) {
        this.jobHost = jobHost;
        this.jobPort = jobPort;
        this.printer = new RawPrinter();
    }
    
    /**
     * Set the paper size for PostScript jobs
     * 
     * @param paperSize The target paperSize
     */
    public void setPaperSize(PaperFormat paperSize) {
        this.paperSize = paperSize;
    }

    /**
     * Set the auto size functionality for PostScript jobs
     * 
     * @param autoSize The new value for autoSize
     */
    public void setAutoSize(boolean autoSize) {
        this.autoSize = autoSize;
    }
    
    /**
     * Set the left margin for the current job
     * 
     * @param leftMargin
     */
    public void setLeftMargin(int leftMargin) {
        this.leftMargin = leftMargin;
    }
    
    /**
     * Set the top margin for the current job
     * 
     * @param topMargin
     */
    public void setTopMargin(int topMargin) {
        this.topMargin = topMargin;
    }
    
    @SuppressWarnings("unchecked")
    private void logSupportedPrinterFeatures(PrinterJob job) {
        LogIt.log("Supported Printing Attributes:");
        for (Class<?> cl : job.getPrintService().getSupportedAttributeCategories()) {
            LogIt.log("   Attr type = " + cl + "=" + job.getPrintService().getDefaultAttributeValue((Class<? extends Attribute>) cl));
        }
    }

    /**
     * Turn the PostScript feature logging on or off.
     * 
     * @param logPSFeatures The new value of logPSFeatures
     */
    void setLogPostScriptFeatures(boolean logPSFeatures) {
        this.logPSFeatures = logPSFeatures;
    }
    
    /**
     * Turn alternate printing on or off
     * 
     * @param alternatePrint The new value of alternatePrint
     */
    void setAlternatePrinting(boolean alternatePrint) {
        this.alternatePrint = alternatePrint;
    }
    
    /**
     * Set the number of copies to print
     * 
     * @param copies The number of copies to print.
     */
    void setCopies(int copies) {
        if(copies > 0) {
            this.copies = copies;
        }
        else {
            LogIt.log(Level.WARNING, "Copies must be a positive integer.");
        }
    }
    
    /**
     * Returns the number of copies currently set for the job
     * 
     * @return The number of copies
     */
    int getCopies() {
        return copies;
    }
}