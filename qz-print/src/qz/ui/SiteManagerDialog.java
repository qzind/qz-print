package qz.ui;

import qz.auth.Certificate;
import qz.common.Constants;
import qz.utils.FileUtilities;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Tres on 2/23/2015.
 */
public class SiteManagerDialog extends BasicDialog {
    private JSplitPane splitPane;

    private JTabbedPane tabbedPane;
    private JList allowList;
    private JList blockList;

    private CertificateTable certTable;

    private JButton deleteButton;

    public SiteManagerDialog(JMenuItem caller, IconCache iconCache) {
        super(caller, iconCache);
        this.certTable = new CertificateTable(null, iconCache);
        initComponents();
    }

    public void initComponents() {
        setIconImage(getImage(IconCache.Icon.SAVED_ICON));
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        tabbedPane = new JTabbedPane();
        allowList = appendListTab("Allowed", IconCache.Icon.ALLOW_ICON, KeyEvent.VK_A);
        blockList = appendListTab("Blocked", IconCache.Icon.BLOCK_ICON, KeyEvent.VK_B);

        setHeader(tabbedPane.getSelectedIndex() == 0 ? Constants.WHITE_LIST : Constants.BLACK_LIST);

        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                certTable.setCertificate(null);
                allowList.clearSelection();
                blockList.clearSelection();

                switch (tabbedPane.getSelectedIndex()) {
                    case 1: setHeader(Constants.BLACK_LIST);
                        blockList.setSelectedIndex(0);
                        break;
                    default:
                        setHeader(Constants.WHITE_LIST);
                        allowList.setSelectedIndex(0);
                }
            }
        });

        // TODO:  Add certificate manual import capabilities
        deleteButton = appendPanelButton("Delete", IconCache.Icon.DELETE_ICON, KeyEvent.VK_D);
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedCertificate();
            }
        });
        deleteButton.setEnabled(false);
        addKeyListener(KeyEvent.VK_DELETE, deleteButton);

        splitPane.add(tabbedPane);
        splitPane.add(new JScrollPane(certTable));
        splitPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        certTable.autoSize();

        setContent(splitPane, true);
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            allowList.removeAll();
            blockList.removeAll();
        }
        allowList.setSelectedIndex(0);
        super.setVisible(visible);
    }

    @SuppressWarnings("unchecked")
    private static void setList(JList list, ArrayList<Certificate> certs) {
        if (list.getModel() instanceof DefaultListModel) {
            DefaultListModel model = (DefaultListModel)list.getModel();
            model.clear();
            for (Certificate cert : certs) {
                model.addElement(cert);
            }
        }
    }

    public void setBlockList(ArrayList<Certificate> certs) {
        setList(blockList, certs);
    }

    public void setAllowList(ArrayList<Certificate> certs) {
        setList(allowList, certs);
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
    private JList appendListTab(String title, IconCache.Icon icon, int mnemonic) {
        JList list = new JList(new DefaultListModel());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        JScrollPane scrollPane = new JScrollPane(list);
        tabbedPane.addTab(title, getIcon(icon), scrollPane);
        tabbedPane.setMnemonicAt(tabbedPane.indexOfComponent(scrollPane), mnemonic);
        addCertificateSelectionListener(list);
        list.setCellRenderer(new CertificateTableCellRenderer());
        return list;
    }

    class CertificateTableCellRenderer extends DefaultListCellRenderer {
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

    private void removeSelectedCertificate() {
        String fileName;
        String action;
        JList list = getSelectedList();

        if (list == allowList) {
            fileName = Constants.ALLOW_FILE;
            action = "allowed";
        } else if(list == blockList) {
            fileName = Constants.BLOCK_FILE;
            action = "blocked";
        } else {
            return;
        }

        Certificate cert = (Certificate)list.getSelectedValue();

        if (cert != null) {
            FileUtilities.deleteFromFile(fileName, cert.data());
            DefaultListModel model = (DefaultListModel)list.getModel();
            model.removeElement(cert);
            certTable.setCertificate(null);
            list.revalidate();
            printToLog(String.format("Removed %s from the list of %s sites", cert, action), TrayIcon.MessageType.INFO);
        }
    }

    private JList getSelectedList() {
        if (tabbedPane.getSelectedIndex() == 0) {
            return allowList;
        }
        return blockList;
    }

    // TODO:  Fix duplicate function
    public void printToLog(String message, TrayIcon.MessageType type) {
        FileUtilities.printLineToFile(Constants.LOG_FILE, String.format("[%s] %tY-%<tm-%<td %<tH:%<tM:%<tS - %s", type, new Date(), message));
    }
}
