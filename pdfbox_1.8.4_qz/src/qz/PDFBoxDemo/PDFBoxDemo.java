package qz;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import javax.print.PrintService;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import org.apache.pdfbox.pdmodel.PDDocument;

public class PDFBoxDemo {

    public static void main(String[] args) {
        try {
            setLookAndFeel(); 
            printPDF(choosePDF(), choosePrinter());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static PrintService choosePrinter() {
        PrinterJob printJob = PrinterJob.getPrinterJob();
        if (printJob.printDialog()) {
            return printJob.getPrintService();
        } else {
            return null;
        }
    }

    public static String choosePDF() throws IOException {
        JFileChooser j = new JFileChooser(new File(".").getCanonicalPath());
        j.setDialogType(JFileChooser.FILES_AND_DIRECTORIES);
        j.setDialogTitle("Choose a PDF to print");
        j.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".pdf");
            }

            @Override
            public String getDescription() {
                return "Portable Document Format (*.pdf)";
            }
        });
        j.showOpenDialog(null);
        return j.getSelectedFile() == null ? "sample.pdf" : j.getSelectedFile().getPath();
    }

    public static void printPDF(String fileName, PrintService ps) throws IOException, PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        if (ps != null) {
            job.setPrintService(ps);
            System.out.println(fileName);
            PDDocument doc = PDDocument.load(fileName);
            doc.silentPrint(job);
        }
    }

    public static void setLookAndFeel() throws Throwable {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
}
