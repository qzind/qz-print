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

package qz;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public class PrintHTML extends JLabel implements Printable {

    private static final Logger log = Logger.getLogger(PrintHTML.class.getName());


    private final AtomicReference<PrintService> ps = new AtomicReference<PrintService>(null);
    private final AtomicReference<String> jobName = new AtomicReference<String>("QZ-PRINT 2D Printing");
    private final AtomicInteger orientation = new AtomicInteger(PageFormat.PORTRAIT);
    private final AtomicReference<String> htmlData = new AtomicReference<String>(null);
    //private final AtomicReference<Paper> paper = new AtomicReference<Paper>(null);
    //private JLabel label;

    public PrintHTML() {
        super();
        //add(label = new JLabel());
        super.setOpaque(true);
        super.setBackground(Color.WHITE);
        //label.setBackground(Color.WHITE);
    }

    public void append(String html) {
          htmlData.set(htmlData.get() == null ? html : htmlData.get() + html);
    }

    //public void append(String html) {
    //    super.setText(super.getText() == null ? html : super.getText() + html);
    // }

   // public void clear() {
   //     super.setText(null);
   // }

    public void clear() {
        htmlData.set(null);
    }

    public String get() {
        return htmlData.get();
    }

    private String[] getHTMLDataArray() {
        return htmlData.get().split("(?i)</html>");
    }

    //public String get() {
    //    return super.getText();
    //}

    public void print() throws PrinterException {
        JFrame j = new JFrame(jobName.get());
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
                attr.add(new MediaPrintableArea(0f, 0f, getWidth()/PrintPostScript.DPI, getHeight()/PrintPostScript.DPI, MediaPrintableArea.INCH));

                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintService(ps.get());
                job.setPrintable(this);
                job.setJobName(jobName.get());
                job.print(attr);
                j.setVisible(false);
            }
        }
        
        finally{
            j.dispose();
            clear();
        }
    }

    public void setPrintParameters(String jobName, int copies) {
        // RKC: PROBLEM >>> this.ps.set(applet.getPrintService());
        this.jobName.set(jobName.replace(" ___ ", " HTML "));
        if (copies > 1) {
            setCopies(copies);
        }
    }

    /*This warning is suppressed because this is a non-implemented method that *shouldn't* be used... */
    public void setCopies(@SuppressWarnings("UnusedParameters") int copies) {
        log.log(Level.WARNING, "Copies is unsupported for printHTML()",
                new UnsupportedOperationException("Copies attribute for HTML 1.0 data has not yet been implemented"));
    }

    public int getCopies() {
        log.log(Level.WARNING, "Copies is unsupported for printHTML()",
                    new UnsupportedOperationException("Copies attribute for HTML 1.0 data has not yet been implemented"));
        return -1;
    }

    public void setPrintService(PrintService ps) {
        this.ps.set(ps);
    }

    public int print(Graphics graphics, PageFormat format, int pageIndex) throws PrinterException {
        if (graphics == null) {
            throw new PrinterException("No graphics specified");
        }
        if (format == null) {
            throw new PrinterException("No page format specified");
        }
        if (pageIndex > 0) {
            return (NO_SUCH_PAGE);
        }

        boolean doubleBuffered = super.isDoubleBuffered();
        super.setDoubleBuffered(false);

        format.setOrientation(orientation.get());

        //Paper paper = new Paper();
        //paper.setSize(8.5 * 72, 11 * 72);
        //paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        //format.setPaper(paper);
        //Paper paper = format.getPaper();
        //paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
        //format.getPaper().setImageableArea(0, 0, paper.getWidth() + 200, paper.getHeight() + 200);

        //format.getPaper().setImageableArea(-100, -100, 200, 200);



        Graphics2D graphics2D = (Graphics2D) graphics;
        graphics2D.translate(format.getImageableX(), format.getImageableY());
        //g2d.translate(paper.getImageableX(), paper.getImageableY());
        this.paint(graphics2D);
        super.setDoubleBuffered(doubleBuffered);
        return (PAGE_EXISTS);
    }
}
