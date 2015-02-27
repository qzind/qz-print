package qz.ui;

import qz.common.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by Tres on 2/26/2015.
 *
 * Displays a basic about dialog
 */
public class AboutDialog extends BasicDialog {
    JPanel gridPanel;
    JLabel portLabel;

    String name;
    int port;

    public AboutDialog(JMenuItem menuItem, IconCache iconCache, String name, int port) {
        super(menuItem, iconCache);
        this.name = name;
        this.port = port;
        initComponents();
    }

    public void initComponents() {
        addBranding();

        gridPanel = new JPanel();

        gridPanel.setLayout(new GridLayout(4, 2));
        gridPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));

        gridPanel.add(createLabel("Software:", true));
        gridPanel.add(createLabel(name));

        portLabel = new JLabel();
        setPort(port);
        gridPanel.add(createLabel("Port Number:", true));
        gridPanel.add(portLabel);


        gridPanel.add(createLabel("Publisher:", true));
        try {
            gridPanel.add(new LinkLabel(new URL(Constants.ABOUT_URL)));
        } catch (MalformedURLException ex) {
            gridPanel.add(new LinkLabel(Constants.ABOUT_URL));
        }

        // Add description line-by-line
        gridPanel.add(createLabel("Description:", true));
        gridPanel.add(createLabel(String.format(Constants.ABOUT_DESC, Constants.ABOUT_TITLE)));

        shadeComponents();
        setContent(gridPanel, true);
    }

    public void addBranding() {
        JPanel branding = new JPanel();
        JLabel brandingLabel = new JLabel(getIcon(IconCache.Icon.LOGO_ICON));
        brandingLabel.setBorder(new EmptyBorder(Constants.BORDER_PADDING, 0, Constants.BORDER_PADDING, 0));
        branding.add(brandingLabel);
        branding.setBackground(Color.WHITE);
        branding.setBorder(new EtchedBorder(EtchedBorder.RAISED));

        // Add at index 0 to bump it to the top
        add(branding, 0);
    }

    public void shadeComponents() {
        for (int i = 0; i < gridPanel.getComponents().length; i++) {
            if (i % 4 == 0 || i % 4 == 1) {
                if (gridPanel.getComponent(i) instanceof JComponent) {
                    ((JComponent) gridPanel.getComponent(i)).setOpaque(true);
                    gridPanel.getComponent(i).setBackground(gridPanel.getComponent(i).getBackground().brighter());
                }
            }
            ((JComponent) gridPanel.getComponent(i)).setBorder(new EmptyBorder(0, Constants.BORDER_PADDING, 0, Constants.BORDER_PADDING));
        }
    }

    public JComponent createLabel(String text) {
        return createLabel(text, false);
    }

    public JComponent createLabel(String text, boolean isBold) {
        if (text.contains("\n")) {
            JLabel styleLabel = new JLabel();
            JTextArea area = new JTextArea(text);
            area.setEditable(false);
            area.setCursor(null);
            area.setFocusable(false);
            area.setFont(styleLabel.getFont());
            area.setBackground(styleLabel.getBackground());
            return area;
        } else {
            JLabel label = new JLabel(text);
            if (isBold) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            return label;
        }

    }

    public void setPort(int port) {
        this.port = port;
        this.portLabel.setText(port < 0? "None":"" + port);
    }
}
