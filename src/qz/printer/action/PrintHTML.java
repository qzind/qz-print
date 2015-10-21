/**
 * @author Tres Finocchiaro
 *
 * Copyright (C) 2013 Tres Finocchiaro, QZ Industries
 *
 * IMPORTANT:  This software is dual-licensed
 *
 * LGPL 2.1
 * This is free software.  This software and source code are released under
 * the "LGPL 2.1 License".  A copy of this license should be distributed with
 * this software. http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * QZ INDUSTRIES SOURCE CODE LICENSE
 * This software and source code *may* instead be distributed under the
 * "QZ Industries Source Code License", available by request ONLY.  If source
 * code for this project is to be made proprietary for an individual and/or a
 * commercial entity, written permission via a copy of the "QZ Industries Source
 * Code License" must be obtained first.  If you've obtained a copy of the
 * proprietary license, the terms and conditions of the license apply only to
 * the licensee identified in the agreement.  Only THEN may the LGPL 2.1 license
 * be voided.
 *
 */

package qz.printer.action;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qz.printer.PrintOptions;

import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.swing.*;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PrintHTML implements PrintProcessor, Printable {

    private static final Logger log = LoggerFactory.getLogger(PrintHTML.class);
    private static final String JOB_NAME = "QZ-PRINT HTML Printing";


    @Override
    public void parseData(JSONArray printData) throws JSONException, UnsupportedOperationException {
        //TODO
    }

    @Override
    public void print(PrintService service, PrintOptions options) throws PrintException, PrinterException {
        //TODO
    }


    public PrintHTML() {
        super();
        //add(label = new JLabel());
        //super.setOpaque(true);
        //super.setBackground(Color.WHITE);
        //label.setBackground(Color.WHITE);
    }


    public void print() throws PrinterException {
        /*
        JFrame j = new JFrame(JOB_NAME.get());
        j.setUndecorated(true);
        j.setLayout(new FlowLayout());
        this.setBorder(null);

        try{
            for (String s : getHTMLDataArray()) {
                this.setText(s + "</html>");
                j.add(this);


                j.pack();
                j.setExtendedState(Frame.ICONIFIED);
                j.setVisible(true);

                // Eliminate any margins
                HashPrintRequestAttributeSet attr = new HashPrintRequestAttributeSet();
                attr.add(new MediaPrintableArea(0f, 0f, getWidth()/ PrintPostScript.DPI, getHeight()/PrintPostScript.DPI, MediaPrintableArea.INCH));

                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintService(ps.get());
                job.setPrintable(this);
                job.setJobName(JOB_NAME.get());
                job.print(attr);
                j.setVisible(false);
            }
        }

        finally{
            j.dispose();
            clear();
        }
        */
    }


    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        if (graphics == null) { throw new PrinterException("No graphics specified"); }
        if (pageFormat == null) { throw new PrinterException("No page format specified"); }

        /*
        boolean doubleBuffered = super.isDoubleBuffered();
        super.setDoubleBuffered(false);

        format.setOrientation(orientation.get());


        Graphics2D graphics2D = (Graphics2D) graphics;
        graphics2D.translate(format.getImageableX(), format.getImageableY());
        //g2d.translate(paper.getImageableX(), paper.getImageableY());
        this.paint(graphics2D);
        super.setDoubleBuffered(doubleBuffered);
        return PAGE_EXISTS;
        */

        return NO_SUCH_PAGE;
    }
}
