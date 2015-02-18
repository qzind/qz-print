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
package qz.utils;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import qz.common.ByteArrayBuilder;
import qz.common.Constants;
import qz.exception.NullCommandException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Logger;


/**
 * Common static file i/o utilities
 *
 * @author Tres Finocchiaro
 */
public class FileUtilities {

    private static final Logger log = Logger.getLogger(FileUtilities.class.getName());

    private static final String[] badExtensions = new String[] {
            "exe", "pif", "paf", "application", "msi", "com", "cmd", "bat", "lnk", // Windows Executable program or script
            "gadget", // Windows desktop gadget
            "msp", "mst", // Microsoft installer patch/transform file
            "cpl", "scr", "ins", // Control Panel/Screen Saver/Internet Settings
            "hta", // HTML application, run as trusted application without sandboxing
            "msc", // Microsoft Management Console file
            "jar", "jnlp", // Java Executable
            "vb", "vbs", "vbe", "js", "jse", "ws", "wsf", "wsc", "wsh",// Windows Script
            "ps1", "ps1xml", "ps2", "ps2xml", "ps1", "ps1xml", "ps2", "ps2xml", "psc1", "psc2", // Windows PowerShell script
            "msh", "msh1", "msh2", "mshxml", "msh1xml", "msh2xml", // Monad/PowerShell script
            "scf", "inf", // Windows Explorer/AutoRun command file
            "reg", // Windows Registry file
            "doc", "docx", "dot", "dotx", "dotm", // Microsoft Word
            "xls", "xlt", "xlm", "xlsx", "xlsm", "xltx", "xltm", "xlsb", "xla", "xlam", "xll", "xlw", // Microsoft Excel
            "ppt", "pps", "pptx", "pptm", "potx", "potm", "ppam", "ppsx", "ppsm", "sldx", "sldm", // Microsoft PowerPoint
            "ade", "adp", "adn", "accdb", "accdr", "accdt", "mdb", "mda", "mdn", "mdt", // Microsoft Access
            "mdw", "mdf", "mde", "accde", "mam", "maq", "mar", "mat", "maf", "ldb", "laccdb", // Microsoft Access
            "app", "action", "bin", "command", "workflow", // Mac OS Application/Executable
            "sh", "ksh", "csh", "pl", "py", "bash", "run",  // Unix Script
            "ipa, apk", // iOS/Android App
            "widget", // Yahoo Widget
            "url" // Internet Shortcut
    };

    public static boolean isBadExtension(String fileName) {
        String[] tokens = fileName.split("\\.(?=[^\\.]+$)");
        if (tokens.length == 2) {
            String extension = tokens[1];
            for(String s : FileUtilities.badExtensions) {
                if (s.equalsIgnoreCase(extension)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether or not the supplied path is restricted, such as the qz-tray data directory
     * Warning:  This does not follow symlinks
     * @param path
     * @return <code>true</code> if restricted, <code>false</code> otherwise
     */
    public static boolean isBadPath(String path) {
        if (SystemUtilities.isWindows()) {
            // Case insensitive
            path.toLowerCase().contains(SystemUtilities.getDataDirectory().toLowerCase());
        }
        return path.contains(SystemUtilities.getDataDirectory());
    }

    public static byte[] readRawFile(String url) throws IOException {
        ByteArrayBuilder rawCmds = new ByteArrayBuilder();
        byte[] buffer = new byte[Constants.BYTE_BUFFER_SIZE];
        DataInputStream in = new DataInputStream(new URL(url).openStream());
        while(true) {
            int len = in.read(buffer);
            if (len == -1) {
                break;
            }
            byte[] temp = new byte[len];
            System.arraycopy(buffer, 0, temp, 0, len);
            rawCmds.append(temp);
        }
        in.close();

        return rawCmds.getByteArray();
    }

    /**
     * Reads an XML file from URL, searches for the tag specified by
     * <code>dataTag</code> tag name and returns the <code>String</code> value
     * of that tag.
     *
     * @param url     location of the xml file to be read
     * @param dataTag tag in the file to be searched
     * @return value of the tag if found
     * @throws DOMException
     * @throws IOException
     * @throws NullCommandException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public static String readXMLFile(String url, String dataTag) throws DOMException, IOException, NullCommandException,
                                                                        ParserConfigurationException, SAXException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        Document doc;
        db = dbf.newDocumentBuilder();
        doc = db.parse(url);
        doc.getDocumentElement().normalize();
        log.info("Root element " + doc.getDocumentElement().getNodeName());
        NodeList nodeList = doc.getElementsByTagName(dataTag);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        throw new NullCommandException(String.format("Node \"%s\" could not be found in XML file specified", dataTag));
    }


    /* User resource files */
    private static HashMap<String, File> fileMap = new HashMap<String, File>();

    public static void printLineToFile(String fileName, String message) {
        FileWriter fw = null;
        try {
            fw = new FileWriter(getFile(fileName), true);

            message += "\r\n";
            fw.write(message);
            fw.flush();
        }
        catch(IOException e) {
            log.warning("Cannot write to file " + fileName);
            e.printStackTrace();
        }
        finally {
            if (fw != null) {
                try { fw.close(); }catch(Exception ignore) {}
            }
        }
    }

    public static File getFile(String name) {
        if (!fileMap.containsKey(name) || fileMap.get(name) == null) {
            String fileLoc = SystemUtilities.getDataDirectory();
            try {
                File locDir = new File(fileLoc);
                locDir.mkdirs();
                String ext = name == Constants.LOG_FILE ? ".log" : ".dat";

                File file = new File(fileLoc + File.separator + name + ext);
                file.createNewFile();

                fileMap.put(name, file);
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }

        return fileMap.get(name);
    }

    public static void deleteFile(String name) {
        File file = fileMap.get(name);

        if (file != null && !file.delete()) {
            log.warning("Unable to delete file " + name);
        }

        fileMap.put(name, null);
    }

    public static void deleteFromFile(String fileName, String deleteLine) {
        File file = getFile(fileName);
        File temp = getFile(Constants.TEMP_FILE);

        BufferedReader br = null;
        BufferedWriter bw = null;
        try {
            br = new BufferedReader(new FileReader(file));
            bw = new BufferedWriter(new FileWriter(temp));

            String line;
            while((line = br.readLine()) != null) {
                if (!line.equals(deleteLine)) {
                    bw.write(line + "\r\n");
                }
            }

            bw.flush();
            bw.close();
            br.close();

            deleteFile(fileName);
            temp.renameTo(file);
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        finally {
            if (br != null) {
                try { br.close(); }catch(Exception ignore) {}
            }
            if (bw != null) {
                try { bw.close(); }catch(Exception ignore) {}
            }
        }
    }

}
