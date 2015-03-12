package qz.ui;

import qz.auth.Certificate;
import qz.common.Constants;
import qz.common.LogIt;
import qz.utils.FileUtilities;
import qz.ws.PrintSocket;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Created by Tres on 2/23/2015.
 */
public class SiteManagerDialog extends BasicDialog implements Runnable {
    private JSplitPane splitPane;

    private JTabbedPane tabbedPane;

    private ContainerList<Certificate> allowList;
    private ContainerList<Certificate> blockList;

    private CertificateTable certTable;

    private JButton deleteButton;

    private Thread readerThread;
    private AtomicBoolean threadRunning;
    private AtomicReference<Certificate> deleteCertificate;

    public SiteManagerDialog(JMenuItem caller, IconCache iconCache) {
        super(caller, iconCache);
        this.certTable = new CertificateTable(null, iconCache);
        initComponents();
    }

    public void initComponents() {
        allowList = new ContainerList<Certificate>();
        allowList.setTag(Constants.ALLOW_FILE);
        blockList = new ContainerList<Certificate>();
        blockList.setTag(Constants.BLOCK_FILE);

        setIconImage(getImage(IconCache.Icon.SAVED_ICON));
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        tabbedPane = new JTabbedPane();
        appendListTab(allowList.getList(), "Allowed", IconCache.Icon.ALLOW_ICON, KeyEvent.VK_A);
        appendListTab(blockList.getList(), "Blocked", IconCache.Icon.BLOCK_ICON, KeyEvent.VK_B);

        setHeader("Sites " + (tabbedPane.getSelectedIndex() == 0 ? Constants.WHITE_LIST : Constants.BLACK_LIST).toLowerCase());

        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                certTable.setCertificate(null);
                allowList.getList().clearSelection();
                blockList.getList().clearSelection();

                switch (tabbedPane.getSelectedIndex()) {
                    case 1: setHeader("Sites " + Constants.BLACK_LIST.toLowerCase());
                        blockList.getList().setSelectedIndex(0);
                        break;
                    default:
                        setHeader("Sites " + Constants.WHITE_LIST.toLowerCase());
                        allowList.getList().setSelectedIndex(0);
                }
            }
        });

        // TODO:  Add certificate manual import capabilities
        deleteButton = addPanelButton("Delete", IconCache.Icon.DELETE_ICON, KeyEvent.VK_D);
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getSelectedList().remove(getSelectedCertificate());
                deleteCertificate.set(getSelectedCertificate());
                certTable.setCertificate(null);
                deleteButton.setEnabled(false);
            }
        });
        deleteButton.setEnabled(false);
        addKeyListener(KeyEvent.VK_DELETE, deleteButton);

        splitPane.add(tabbedPane);
        splitPane.add(new JScrollPane(certTable));
        splitPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        certTable.autoSize();

        readerThread = new Thread(this);
        threadRunning = new AtomicBoolean(false);
        deleteCertificate = new AtomicReference<Certificate>(null);

        setContent(splitPane, true);
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible && !readerThread.isAlive()) {
            threadRunning.set(true);
            readerThread = new Thread(this);
            readerThread.start();
        } else {
            threadRunning.set(false);
        }

        if (visible & getSelectedList().getList().getSelectedIndex() < 0) {
            selectFirst();
        }

        super.setVisible(visible);
    }

    public SiteManagerDialog selectFirst() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                getSelectedList().getList().setSelectedIndex(0);
            }
        });
        return this;
    }

    private void addCertificateSelectionListener(final JList list) {
        list.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (list.getSelectedValue() instanceof Certificate) {
                    certTable.setCertificate((Certificate)list.getSelectedValue());
                    deleteButton.setEnabled(true);
                } else {
                    deleteButton.setEnabled(false);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void appendListTab(JList list, String title, IconCache.Icon icon, int mnemonic) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        JScrollPane scrollPane = new JScrollPane(list);
        tabbedPane.addTab(title, getIcon(icon), scrollPane);
        tabbedPane.setMnemonicAt(tabbedPane.indexOfComponent(scrollPane), mnemonic);
        addCertificateSelectionListener(list);
        list.setCellRenderer(new CertificateListCellRenderer());
    }

    private class CertificateListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Certificate) {
                label.setIcon(SiteManagerDialog.super.getIcon(IconCache.Icon.SAVED_ICON));
            } else {
                label.setIcon(null);
            }
            return label;
        }
    }

    /**
     * Thread safe remove certificate from GUI and filesystem
     */
    public SiteManagerDialog removeCertificate(Certificate certificate) {
        final ContainerList<Certificate> certList = getSelectedList();
        if (certificate != null && FileUtilities.deleteFromFile(certList.getTag().toString(), certificate.data())) {
            certList.remove(certificate);
        } else {
            LogIt.log(Level.WARNING, String.format("Error removing %s from the list of %s sites", certificate, getSelectedTabName().toLowerCase()));
        }
        return this;
    }

    private Certificate getSelectedCertificate() {
        return (Certificate)getSelectedList().getList().getSelectedValue();
    }

    private String getSelectedTabName() {
        if (tabbedPane.getSelectedIndex() >= 0) {
            return tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
        }
        return "";
    }

    private ContainerList<Certificate> getSelectedList() {
        if (tabbedPane.getSelectedIndex() == 0) {
            return allowList;
        }
        return blockList;
    }

    // TODO:  Fix duplicate function
    public void printToLog(String message, TrayIcon.MessageType type) {
        FileUtilities.printLineToFile(Constants.LOG_FILE, String.format("[%s] %tY-%<tm-%<td %<tH:%<tM:%<tS - %s", type, new Date(), message));
    }

    private long allowTick = -1;
    private long blockTick = -1;

    public void run() {
        threadRunning.set(true);

        File allowFile = FileUtilities.getFile(Constants.ALLOW_FILE);
        File blockFile = FileUtilities.getFile(Constants.BLOCK_FILE);

        boolean initialSelection = true;

        allowTick = allowTick < 0 ? 0 : allowTick;
        blockTick = blockTick < 0 ? 0 : blockTick;

        // Reads the certificate allowed/blocked files and updates the certificate listing
        while (threadRunning.get()) {
             if (isVisible()) {
                if (deleteCertificate.get() != null) {
                    removeCertificate(deleteCertificate.getAndSet(null));
                } else if (allowFile.lastModified() > allowTick) {
                    allowTick = allowFile.lastModified();
                    readCertificates(allowList, allowFile);
                } else if (blockFile.lastModified() > blockTick) {
                    blockTick = blockFile.lastModified();
                    readCertificates(blockList, blockFile);
                } else {
                    sleep(2000);
                }

                if (initialSelection) {
                    selectFirst();
                    initialSelection = false;
                }
            }
        }
        threadRunning.set(false);
    }

    public void sleep(int millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ignore) {}
    }

    /**
     * Reads a certificate data file and updates the corresponding <code>ArrayList</code>
     * @param certList The <code>ArrayList</code> requiring updating
     * @param file The data file containing allow/block certificate information
     * @return
     */
    public ArrayList<Certificate> readCertificates(ArrayList<Certificate> certList, File file) {
        BufferedReader br = null;
        try {
            String line;
            br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                String[] data = line.split("\\t");

                if (data.length == Certificate.saveFields.length) {
                    HashMap<String, String> dataMap = new HashMap<String, String>();
                    for (int i = 0; i < data.length; i++) {
                        dataMap.put(Certificate.saveFields[i], data[i]);
                    }
                    Certificate certificate = Certificate.loadCertificate(dataMap);
                    // Don't include the unsigned certificate if we are blocking it, there is a menu option instead
                    if (!certList.contains(certificate) && !PrintSocket.UNSIGNED.equals(certificate)) {
                        certList.add(certificate);
                    }
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignore) {
                }
            }
        }
        return certList;
    }
}
