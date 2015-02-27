package qz.ui;

import qz.common.Constants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * Created by Tres on 2/23/2015.
 */
public class BasicDialog extends JDialog {
    private JPanel mainPanel;
    private JLabel headerLabel;
    private JComponent contentComponent;

    private JPanel buttonPanel;
    private JButton closeButton;

    private IconCache iconCache;

    private int stockButtonCount = 0;

    public BasicDialog(JMenuItem caller, IconCache iconCache) {
        super((Frame)null, caller.getText().replaceAll("\\.+", ""), true);
        this.iconCache = iconCache;
        initBasicComponents();
    }

    public BasicDialog(Frame owner, String title, IconCache iconCache) {
        super(owner, title, true);
        this.iconCache = iconCache;
        initBasicComponents();
    }

    public void initBasicComponents() {
        setIconImage(iconCache.getImage(IconCache.Icon.DEFAULT_ICON));
        mainPanel= new JPanel();
        mainPanel.setBorder(new EmptyBorder(Constants.BORDER_PADDING, Constants.BORDER_PADDING, Constants.BORDER_PADDING, Constants.BORDER_PADDING));

        headerLabel = new JLabel();
        headerLabel.setBorder(new EmptyBorder(0, 0, Constants.BORDER_PADDING, Constants.BORDER_PADDING));
        add(headerLabel);

        buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(new JSeparator(JSeparator.HORIZONTAL));
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        closeButton = appendPanelButton("Close", IconCache.Icon.ALLOW_ICON, KeyEvent.VK_C);
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
        stockButtonCount = buttonPanel.getComponents().length;

        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(headerLabel);
        mainPanel.add(contentComponent = new JLabel("Hello world!"));
        mainPanel.add(buttonPanel);

        addKeyListener(KeyEvent.VK_ESCAPE, closeButton);

        getContentPane().add(mainPanel);
        setResizable(false);

        pack();

        setLocationRelativeTo(null);    // center on main display
    }

    public JLabel setHeader(String header) {
        headerLabel.setText(String.format(header, "").replaceAll("\\s+", " "));
        return headerLabel;
    }

    public JLabel setHeaderLabel(JLabel headerLabel) {
        headerLabel.setAlignmentX(this.headerLabel.getAlignmentX());
        headerLabel.setBorder(this.headerLabel.getBorder());
        mainPanel.add(headerLabel, indexOf(this.headerLabel));
        mainPanel.remove(indexOf(this.headerLabel));
        this.headerLabel = headerLabel;
        mainPanel.invalidate();
        return headerLabel;
    }

    @Override
    public Component add(Component component) {
        if (component != null && component instanceof JComponent) {
            ((JComponent)component).setAlignmentX(LEFT_ALIGNMENT);
        }
        return mainPanel.add(component);
    }

    @Override
    public Component add(Component component, int index) {
        if (component != null && component instanceof JComponent) {
            ((JComponent)component).setAlignmentX(LEFT_ALIGNMENT);
        }
        return mainPanel.add(component, index);
    }

    public JComponent setContent(JComponent contentComponent, boolean autoCenter) {
        if (contentComponent != null) {
            contentComponent.setAlignmentX(LEFT_ALIGNMENT);
            mainPanel.add(contentComponent, indexOf(this.contentComponent));
        }

        mainPanel.remove(indexOf(this.contentComponent));
        this.contentComponent = contentComponent;
        mainPanel.invalidate();
        pack();
        if (autoCenter) {
            setLocationRelativeTo(null);
        }
        return contentComponent;
    }

    public JButton appendPanelButton(String title, IconCache.Icon icon, int mnemonic) {
        JButton button = new JButton(title, iconCache == null ? null : iconCache.getIcon(icon));
        button.setMnemonic(mnemonic);
        buttonPanel.add(button, buttonPanel.getComponents().length - stockButtonCount);
        return button;
    }

    public void addKeyListener(int virtualKey, final AbstractButton actionButton) {
        getRootPane().getInputMap(JRootPane.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(virtualKey, 0), actionButton.toString());
        getRootPane().getActionMap().put(actionButton.toString(), new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actionButton.doClick();
            }
        });
    }

    public int indexOf(Component findComponent) {
        int i = -1;
        for (Component currentComponent : mainPanel.getComponents()) {
            i++;
            if (findComponent == currentComponent) {
                break;
            }
        }
        return i;
    }

    public BufferedImage getImage(IconCache.Icon icon) {
        if (iconCache != null) {
            return iconCache.getImage(icon);
        }
        return null;
    }

    public ImageIcon getIcon(IconCache.Icon icon) {
        if (iconCache != null) {
            return iconCache.getIcon(icon);
        }
        return null;
    }
}
