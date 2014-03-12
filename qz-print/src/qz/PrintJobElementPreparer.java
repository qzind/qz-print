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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.JEditorPane;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;
import qz.exception.InvalidRawImageException;
import qz.exception.NullCommandException;

/**
 *
 * @author Thomas Hart II
 */
public class PrintJobElementPreparer implements Runnable{

    private final PrintJobElementType type;
    private final ByteArrayBuilder data;
    private final Charset charset;
    private final LanguageType lang;
    private final int dotDensity;
    private final int imageX;
    private final int imageY;
    private final String xmlTag;
    private final PrintJobElement pje;
    private final JEditorPane rtfEditor = new JEditorPane();
    
    private String pdfFileName;
    private ByteArrayBuilder preparedData;
    private BufferedImage bufferedImage;
    private PDDocument pdfFile;
    
    PrintJobElementPreparer(PrintJobElementType type, ByteArrayBuilder data, Charset charset, LanguageType lang, int dotDensity, int imageX, int imageY, String xmlTag, PrintJobElement pje) {
        this.type = type;
        this.data = data;
        this.charset = charset;
        this.lang = lang;
        this.dotDensity = dotDensity;
        this.imageX = imageX;
        this.imageY = imageY;
        this.xmlTag = xmlTag;
        this.pje = pje;
    }
    
    public void run() {
        
        // An image file, pull the file into an ImageWrapper and get the 
        // encoded data
        if(type == PrintJobElementType.TYPE_IMAGE) {
            
            try {

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
                
                this.preparedData = new ByteArrayBuilder(iw.getImageCommand());
                
            } catch (UnsupportedEncodingException ex) {
                LogIt.log(Level.WARNING, "Unsupported encoding exception: " + ex);
            } catch (IOException ex) {
                LogIt.log(Level.WARNING, "IO exception: " + ex);
            } catch (InvalidRawImageException ex) {
                LogIt.log(Level.WARNING, "Invalid raw image exception: " + ex);
            }
        }
        else if(type == PrintJobElementType.TYPE_IMAGE_PS) {
            try {
                String file = new String(data.getByteArray(), charset.name());
                if (ByteUtilities.isBase64Image(file)) {
                    byte[] imgData = Base64.decode(file.split(",")[1]);
                    InputStream in = new ByteArrayInputStream(imgData);
                    bufferedImage = ImageIO.read(in);
                } else {
                    bufferedImage = ImageIO.read(new URL(file));
                }
            } catch (UnsupportedEncodingException ex) {
                LogIt.log(Level.WARNING, "Unsupported encoding exception: " + ex);
            } catch (IOException ex) {
                LogIt.log(Level.WARNING, "IO exception: " + ex);
            }
        }
        else if(type == PrintJobElementType.TYPE_XML) {
            
            try {
                String file = new String(data.getByteArray(), charset.name());
                String dataString;
                byte[] dataByteArray;
                
                dataString = FileUtilities.readXMLFile(file, xmlTag);
                dataByteArray = Base64.decode(dataString);
                preparedData = new ByteArrayBuilder(dataByteArray);
                
            } catch (UnsupportedEncodingException ex) {
                LogIt.log(Level.WARNING, "Unsupported encoding exception: " + ex);
            } catch (IOException ex) {
                LogIt.log(Level.WARNING, "IO exception: " + ex);
            } catch (DOMException ex) {
                LogIt.log(ex);
            } catch (NullCommandException ex) {
                LogIt.log(ex);
            } catch (ParserConfigurationException ex) {
                LogIt.log(ex);
            } catch (SAXException ex) {
                LogIt.log(ex);
            }
        }
        else if(type == PrintJobElementType.TYPE_FILE) {
            try {
                String file = new String(data.getByteArray(), charset.name());
                preparedData = new ByteArrayBuilder(FileUtilities.readRawFile(file));
            } catch (UnsupportedEncodingException ex) {
                LogIt.log(ex);
            } catch (IOException ex) {
                LogIt.log(ex);
            }
        }
        else if(type == PrintJobElementType.TYPE_RTF) {
            try {
                String file = new String(data.getByteArray(), charset.name());
                preparedData = new ByteArrayBuilder(FileUtilities.readRawFile(file));
                rtfEditor.setBackground(Color.white);
                rtfEditor.setVisible(false);
                rtfEditor.setContentType("text/rtf");
                rtfEditor.setText(new String(preparedData.getByteArray(), charset.name()));
            } catch (UnsupportedEncodingException ex) {
                LogIt.log(ex);
            } catch (IOException ex) {
                LogIt.log(ex);
            }
        }
        else if(type == PrintJobElementType.TYPE_PDF) {
            try {
                pdfFileName = new String(data.getByteArray(), charset.name());
            } catch (UnsupportedEncodingException ex) {
                LogIt.log(ex);
            }
            final String fileName = pdfFileName;
            LogIt.log("DEBUG: fileName - " + fileName);
            
            pdfFile = AccessController.doPrivileged(new PrivilegedAction<PDDocument>() {
                //private PDDocument doc;
                public PDDocument run() {
                    
                    try{
                        //doc = new PDDocument();
                        URL fileUrl = new URL(fileName);
                        return PDDocument.load(fileUrl);
                    } catch (IOException ex) {
                        LogIt.log("Error reading PDF file. " + ex);
                        //return null;
                    } finally {
                        /*
                        try {
                            doc.close();
                        } catch (IOException ex) {
                            LogIt.log("Error closing PDF file. " + ex);
                        }
                        */
                        
                    }
                    
                    return null;
                }
            });
        }
        else {
            preparedData = data;
        }
        
        // Callback function on PrintJobElement
        pje.donePreparing(preparedData, bufferedImage, rtfEditor, pdfFile);
        
    }
    
}
