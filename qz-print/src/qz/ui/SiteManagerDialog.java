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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Tres on 2/23/2015.
 */
public class SiteManagerDialog extends JDialog {
    private JPanel mainPanel;

    private JTabbedPane tabbedPane;
    private JLabel headerLabel;
    private JList allowList;
    private JList blockList;

    private CertificateTable certTable;


    private JPanel buttonPanel;
    private JButton closeButton;
    private JButton deleteButton;
    private JButton importButton;

    private IconCache iconCache;

    public SiteManagerDialog(Frame owner, String title, IconCache iconCache) {
        super(owner, title, true);
        this.iconCache = iconCache;
        this.certTable = new CertificateTable(null, iconCache);
        initComponents();
    }

    public void initComponents() {
        setIconImage(iconCache.getImage(IconCache.Icon.SAVED_ICON));
        mainPanel= new JPanel();

        tabbedPane = new JTabbedPane();
        headerLabel = new JLabel(String.format(Constants.WHITE_LIST, "").replaceAll("\\s+", " "));
        allowList = appendListTab("Allowed", IconCache.Icon.ALLOW_ICON, KeyEvent.VK_A);
        blockList = appendListTab("Blocked", IconCache.Icon.BLOCK_ICON, KeyEvent.VK_B);

        tabbedPane.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                String header = tabbedPane.getSelectedIndex() == 0 ? Constants.WHITE_LIST : Constants.BLACK_LIST;
                headerLabel.setText(String.format(header, "").replaceAll("\\s+", " "));
                certTable.setCertificate(null);
                allowList.clearSelection();
                blockList.clearSelection();
            }
        });

        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importButton = appendPanelButton("Import", IconCache.Icon.SAVED_ICON, KeyEvent.VK_I);
        deleteButton = appendPanelButton("Delete", IconCache.Icon.DELETE_ICON, KeyEvent.VK_D);
        buttonPanel.add(new JSeparator(JSeparator.HORIZONTAL));
        closeButton = appendPanelButton("Close", IconCache.Icon.ALLOW_ICON, KeyEvent.VK_C);

        // TODO:  Add certificate manual import capabilities
        buttonPanel.remove(importButton);

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedCertificate();
            }
        });
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(headerLabel);
        mainPanel.add(tabbedPane);
        mainPanel.add(new JScrollPane(certTable));
        certTable.autoSize();
        mainPanel.add(buttonPanel);

        certTable.addKeyListener(keyAdapter);
        addKeyListener(keyAdapter);

        getContentPane().add(mainPanel);

        pack();

        setLocationRelativeTo(null);    // center on main display
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            allowList.removeAll();
            blockList.removeAll();
        }
        super.setVisible(visible);
    }

    private static void setList(JList list, ArrayList<Certificate> certs) {
        DefaultListModel model = (DefaultListModel)list.getModel();
        model.clear();
        for (Certificate cert : certs) {
            model.addElement(cert);
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
                }
            }
        });
    }

    private JList appendListTab(String title, IconCache.Icon icon, int mnemonic) {
        JList list = new JList(new DefaultListModel());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.VERTICAL);
        JScrollPane scrollPane = new JScrollPane(list);
        tabbedPane.addTab(title, iconCache == null ? null : iconCache.getIcon(icon), scrollPane);
        tabbedPane.setMnemonicAt(tabbedPane.indexOfComponent(scrollPane), mnemonic);
        addCertificateSelectionListener(list);
        list.setCellRenderer(new CertificateTableCellRenderer());
        list.addKeyListener(keyAdapter);
        return list;
    }

    private JButton appendPanelButton(String title, IconCache.Icon icon, int mnemonic) {
        JButton button = new JButton(title, iconCache == null ? null : iconCache.getIcon(icon));
        button.setMnemonic(mnemonic);
        buttonPanel.add(button);
        return button;
    }

    class CertificateTableCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Certificate && iconCache != null) {
                label.setIcon(iconCache.getIcon(IconCache.Icon.SAVED_ICON));
            } else {
                label.setIcon(null);
            }
            return label;
        }
    };

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

    private KeyAdapter keyAdapter = new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_DELETE:
                    if (allowList.hasFocus() || blockList.hasFocus()) {
                        deleteButton.doClick();
                    }
                    break;
                case KeyEvent.VK_ESCAPE:
                    closeButton.doClick();
                    break;
            }
        }
    };
}
