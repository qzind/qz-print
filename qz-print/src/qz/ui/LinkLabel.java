package qz.ui;

import qz.common.LogIt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by Tres on 2/19/2015.
 */
public class LinkLabel extends JLabel {
    ArrayList<ActionListener> actionListeners;

    public LinkLabel() {
        super();
        initialize();
    }

    public LinkLabel(String text) {
        super(linkify(text));
        initialize();
    }

    public LinkLabel(final URL url) {
        super(linkify(url.toString()));
        initialize();
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Desktop.getDesktop().browse(url.toURI());
                } catch (Exception ex) {
                    LogIt.log(ex);
                }
            }
        });
    }

    public LinkLabel(final File filePath) {
        super(linkify(filePath.getPath()));
        initialize();
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Desktop.getDesktop().open(filePath.isDirectory() ? filePath : filePath.getParentFile());
                } catch (IOException ex) {
                    LogIt.log(ex);
                }
            }
        });
    }

    private void initialize() {
        actionListeners = new ArrayList<ActionListener>();

        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (ActionListener actionListener : actionListeners) {
                    actionListener.actionPerformed(new ActionEvent(e.getSource(), e.getID(), "mouseClicked"));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e) {}

            @Override
            public void mouseEntered(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    @Override
    public void setText(String text){
        super.setText(linkify(text));
    }

    private static String linkify(String text) {
        return "<html><a href=\"#\">" + text + "</a>";
    }

    public void addActionListener(ActionListener action) {
        if (!actionListeners.contains(action)) {
            actionListeners.add(action);
        }
    }

    public void removeActionListener(ActionListener action) {
        actionListeners.remove(action);
    }

}
