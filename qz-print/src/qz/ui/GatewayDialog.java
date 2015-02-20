package qz.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;

import qz.auth.Certificate;
import qz.common.Constants;

/**
 * Created by Tres on 2/19/2015.
 * A basic allow/block dialog with support for displaying Certificate information
 */
public class GatewayDialog extends JDialog {
    private JLabel verifiedLabel;
    private JLabel descriptionLabel;
    private LinkLabel certInfoLabel;
    private JPanel descriptionPanel;

    private JButton allowButton;
    private JButton blockButton;
    private JPanel optionsPanel;

    private JCheckBox persistentCheckBox;
    private JPanel bottomPanel;

    private JPanel mainPanel;

    private final IconCache iconCache;

    private String description;
    private Certificate cert;
    private boolean approved;

    public GatewayDialog(Frame owner, String title, IconCache iconCache) {
        super(owner, title, true);
        this.iconCache = iconCache;
        this.description = "";
        this.approved = false;
        this.setIconImage(iconCache.getImage(IconCache.Icon.DEFAULT_ICON));
        initComponents();
        refreshComponents();
    }

    private void initComponents() {
        descriptionPanel  = new JPanel();
        verifiedLabel = new JLabel();
        verifiedLabel.setBorder(new EmptyBorder(3, 3, 3, 3));
        descriptionLabel = new JLabel();

        descriptionPanel.add(verifiedLabel);
        descriptionPanel.add(descriptionLabel);
        descriptionPanel.setBorder(new EmptyBorder(3, 3, 3, 3));

        optionsPanel = new JPanel();
        allowButton = new JButton("Allow", iconCache.getIcon(IconCache.Icon.ALLOW_ICON));
        allowButton.setMnemonic(KeyEvent.VK_A);
        blockButton = new JButton("Block", iconCache.getIcon(IconCache.Icon.BLOCK_ICON));
        allowButton.setMnemonic(KeyEvent.VK_B);
        allowButton.addActionListener(buttonAction);
        blockButton.addActionListener(buttonAction);

        certInfoLabel = new LinkLabel();
        certInfoLabel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(GatewayDialog.this, cert.getFingerprint());
            }
        });

        bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        persistentCheckBox = new JCheckBox("Remember this decision", false);
        persistentCheckBox.setMnemonic(KeyEvent.VK_R);
        persistentCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                allowButton.setEnabled(!persistentCheckBox.isSelected() || cert.isValidQZCert());
            }
        });
        persistentCheckBox.setAlignmentX(RIGHT_ALIGNMENT);

        bottomPanel.add(certInfoLabel);
        bottomPanel.add(persistentCheckBox);

        optionsPanel.add(allowButton);
        optionsPanel.add(blockButton);

        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(descriptionPanel);
        mainPanel.add(optionsPanel);
        mainPanel.add(new JSeparator());
        mainPanel.add(bottomPanel);

        getContentPane().add(mainPanel);

        allowButton.requestFocusInWindow();

        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        pack();

        setLocationRelativeTo(null);    // center on main display
    }

    private final ActionListener buttonAction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            approved = e.getSource().equals(allowButton);
            setVisible(false);
        }
    };

    @Override
    public void setVisible(boolean b) {
        if (b) {
            refreshComponents();
        }
        super.setVisible(b);
    }

    public final void refreshComponents() {
        if (cert != null) {
            descriptionLabel.setText("<html>" +
                    String.format(description, "<p>" + cert.getCommonName()) +
                    "</p><strong>" + (cert.isValidQZCert() ? "Verified by " + Constants.ABOUT_COMPANY : "Unverified publisher") + "</strong>" +
                    "</html>");
            certInfoLabel.setText("certificate information");
            verifiedLabel.setIcon(iconCache.getIcon(cert.isValidQZCert() ? IconCache.Icon.VERIFIED_ICON : IconCache.Icon.UNVERIFIED_ICON));
        } else {
            descriptionLabel.setText(description);
            verifiedLabel.setIcon(null);
        }

        approved = false;
        persistentCheckBox.setSelected(false);
        allowButton.requestFocusInWindow();
        pack();
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isPersistent() {
        return this.persistentCheckBox.isSelected();
    }

    public void setCertificate(Certificate cert) {
        this.cert = cert;
    }

    public Certificate getCertificate() {
        return cert;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean prompt(String description, Certificate cert) {
        if (cert == null || cert.isBlocked()) { return false; }
        if (cert.isValidQZCert() && cert.isSaved()) { return true; }

        setDescription(description);
        setCertificate(cert);
        setVisible(true);
        return isApproved();
    }
}

