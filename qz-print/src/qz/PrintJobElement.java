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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import static java.awt.print.Printable.PAGE_EXISTS;
import java.awt.print.PrinterException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;
import qz.exception.InvalidRawImageException;
import qz.exception.NullCommandException;

/**
 * A PrintJobElement is a piece of a PrintJob that contains a data string,
 * format and sequence number for ordering.
 * @author Thomas Hart
 */
public class PrintJobElement {
    
    private int sequence;
    private boolean prepared;
    private PrintJobElementType type;
    private PrintJob pj;
    private ByteArrayBuilder data;
    private final Charset charset;
    private int imageX = 0;
    private int imageY = 0;
    private int dotDensity = 32;
    private LanguageType lang;
    private String xmlTag;
    private BufferedImage bufferedImage;
    private PDFFile pdfFile;
    private ByteBuffer bufferedPDF;
    private JEditorPane rtfEditor = new JEditorPane();
    
    PrintJobElement(PrintJob pj, ByteArrayBuilder data, PrintJobElementType type, Charset charset, String lang, int dotDensity) {
        
        this.lang = LanguageType.getType(lang);
        this.dotDensity = dotDensity;
        
        this.pj = pj;
        this.data = data;
        this.type = type;
        this.charset = charset;
        
        prepared = false;
        
    }

    PrintJobElement(PrintJob pj, ByteArrayBuilder data, PrintJobElementType type, Charset charset, String lang, int imageX, int imageY) {
        
        this.lang = LanguageType.getType(lang);
        this.imageX = imageX;
        this.imageY = imageY;
        
        this.pj = pj;
        this.data = data;
        this.type = type;
        this.charset = charset;
        
        prepared = false;
        
    }
    PrintJobElement(PrintJob pj, ByteArrayBuilder data, PrintJobElementType type, Charset charset, String xmlTag) {
        
        this.xmlTag = xmlTag;
        
        this.pj = pj;
        this.data = data;
        this.type = type;
        this.charset = charset;
        
        prepared = false;
        
    }

    PrintJobElement(PrintJob pj, ByteArrayBuilder data, PrintJobElementType type, Charset charset) {
        
        this.pj = pj;
        this.data = data;
        this.type = type;
        this.charset = charset;
        
        prepared = false;
    }
    
    /**
     * Prepare the PrintJobElement
     * 
     * @return Boolean representing success
     * @throws IOException
     * @throws InvalidRawImageException
     * @throws NullCommandException 
     */
    public boolean prepare() throws IOException, InvalidRawImageException, NullCommandException {

        // An image file, pull the file into an ImageWrapper and get the 
        // encoded data
        if(type == PrintJobElementType.TYPE_IMAGE) {
            
            // Prepare the image
            String file = new String(data.getByteArray(), charset.name());
            
            BufferedImage bi;
            ImageWrapper iw;
            if (ByteUtilities.isBase64Image(file)) {
                byte[] imageData = Base64.decode(file.split(",")[1]);
                bi = ImageIO.read(new ByteArrayInputStream(imageData));
            } else {
                bi = ImageIO.read(new URL(file));
            }
            iw = new ImageWrapper(bi, lang);
            iw.setCharset(charset);
            // Image density setting (ESCP only)
            iw.setDotDensity(dotDensity);
            // Image coordinates, (EPL only)
            iw.setxPos(imageX);
            iw.setyPos(imageY);
            
            try {
                this.data = new ByteArrayBuilder(iw.getImageCommand());
            } catch (UnsupportedEncodingException ex) {
                LogIt.log(Level.SEVERE, "Unsupported encoding.", ex);
            }
        }
        else if(type == PrintJobElementType.TYPE_IMAGE_PS) {
            String file = new String(data.getByteArray(), charset.name());
            if (ByteUtilities.isBase64Image(file)) {
                byte[] imgData = Base64.decode(file.split(",")[1]);
                InputStream in = new ByteArrayInputStream(imgData);
                bufferedImage = ImageIO.read(in);
            } else {
                bufferedImage = ImageIO.read(new URL(file));
            }
        }
        else if(type == PrintJobElementType.TYPE_XML) {
            String file = new String(data.getByteArray(), charset.name());
            String dataString;
            byte[] dataByteArray;
            
            try {
                dataString = FileUtilities.readXMLFile(file, xmlTag);
                dataByteArray = Base64.decode(dataString);
                data = new ByteArrayBuilder(dataByteArray);
            } catch (DOMException ex) {
                LogIt.log(Level.SEVERE, "Could not prepare XML element.", ex);
            } catch (ParserConfigurationException ex) {
                LogIt.log(Level.SEVERE, "Could not prepare XML element.", ex);
            } catch (SAXException ex) {
                LogIt.log(Level.SEVERE, "Could not prepare XML element.", ex);
            }
        }
        else if(type == PrintJobElementType.TYPE_FILE) {
            String file = new String(data.getByteArray(), charset.name());
            data = new ByteArrayBuilder(FileUtilities.readRawFile(file));
        }
        else if(type == PrintJobElementType.TYPE_RTF) {
            String file = new String(data.getByteArray(), charset.name());
            String data = new String(FileUtilities.readRawFile(file), charset.name());
            rtfEditor.setBackground(Color.white);
            rtfEditor.setVisible(false);
            rtfEditor.setContentType("text/rtf");
            rtfEditor.setText(data);
        }
        else if(type == PrintJobElementType.TYPE_PDF) {
            String file = new String(data.getByteArray(), charset.name());
            bufferedPDF = ByteBuffer.wrap(ByteUtilities.readBinaryFile(file));
            try {
                pdfFile = getPDFFile();
            } catch (PrinterException ex) {
                LogIt.log(Level.SEVERE, "Could not prepare PDF element.", ex);
            }
        }

        prepared = true;
        return true;
    }
    
    /**
     * Check if the PrintJobElement has been prepared
     * 
     * @return Boolean representing whether the PrintJobElement is prepared
     */
    public boolean isPrepared() {
        return prepared;
    }
    
    /**
     * Get the PrintJobElement's data. In the case of a raw element this is raw 
     * data, though other element types will use this to hold a url
     * 
     * @return The element's data
     */
    public ByteArrayBuilder getData() {
        return data;
    }
    
    /**
     * Getter for the bufferedImage. This is used for PostScript image files
     * 
     * @return The buffered image data
     */
    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }
    
    /**
     * Getter for the PrintJobElement's charset
     * 
     * @return The element's charset
     */
    public Charset getCharset() {
        return charset;
    }
    
    /**
     * Getter for the PrintJobElement's type
     * 
     * @return The element's type
     */
    public PrintJobElementType getType() {
        return type;
    }
    
    /**
     * Getter for the PDFFile object. This object is used for pdf PrintJobElements
     * 
     * @return The processed PDFFile
     * @throws PrinterException
     */
    public PDFFile getPDFFile() throws PrinterException {
        
        if (pdfFile == null && bufferedPDF != null) {
            try {    
                pdfFile = new PDFFile(this.bufferedPDF);
            } catch (IOException ex) {
                LogIt.log(Level.SEVERE, "Could not get PDF file.", ex);
            }
        }
        
        return pdfFile;
    }
    
    /**
     * This function controls PDF rendering to a graphics context that can be
     * used for printing
     * 
     * @param graphics The target graphics context
     * @param pageFormat The desired pageFormat
     * @param pageIndex The page index currently being processed
     * @return PAGE_EXISTS on success
     * @throws PrinterException
     */
    public int printPDFRenderer(Graphics graphics, PageFormat pageFormat, int pageIndex, int leftMargin, int topMargin) throws PrinterException {
        /*
         * Important:  This uses class reflection to instantiate and invoke
         * PDFRenderer to reduce reliance on the project's classpath for 3rd 
         * party libraries.
         */
        PDFFile pdf = getPDFFile();
        
        int pg = pageIndex + 1;
        
        if (pdf == null) {
            throw new PrinterException("No PDF data specified");
        }
        
        // fit the PDFPage into the printing area
        Graphics2D g2 = (Graphics2D) graphics;
        PDFPage page = pdf.getPage(pg);
        
        Rectangle2D pageBox = (Rectangle2D)page.getPageBox();
        Rectangle imgBounds = pageBox.getBounds();
        imgBounds.x = leftMargin;
        imgBounds.y = topMargin;
        
        Rectangle2D bBox = (Rectangle2D)page.getBBox();
        
        PDFRenderer pgs = new PDFRenderer(page, g2, imgBounds, bBox, Color.WHITE);
        try {
            page.waitForFinish();
        } catch (InterruptedException ex) {
            LogIt.log(Level.SEVERE, "Printing was interrupted.", ex);
        }
        pgs.run();
        
        return PAGE_EXISTS;
        
    }
    
    /**
     * This function returns the RTF data to the print job for printing
     * @return The JEditorPane containing the rendered RTF data
     */
    public JEditorPane getRtfData() {
        return rtfEditor;
    }
    
    /**
     * This function returns the RTF data height to the print job for printing
     * @return The height of the RTF pane
     */
    public int getRtfHeight() {
        return rtfEditor.getHeight();
    }
    
    /**
     * This function returns the rtf data width to the print job for printing
     * @return The width of the RTF pane
     */
    public int getRtfWidth() {
        return rtfEditor.getWidth();
    }
}
