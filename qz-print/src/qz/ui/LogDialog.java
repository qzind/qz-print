package qz.ui;

import qz.common.Constants;
import qz.utils.FileUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Tres on 2/26/2015.
 */
public class LogDialog extends BasicDialog implements Runnable {
    private final int ROWS = 20;
    private final int COLS = 80;

    private JScrollPane logPane;
    private JTextArea logArea;

    private JButton clearButton;

    private Thread readerThread;
    private AtomicBoolean threadRunning;
    private AtomicBoolean deleteLogFile;

    public LogDialog(JMenuItem caller, IconCache iconCache) {
        super(caller, iconCache);
        initComponents();
    }

    public void initComponents() {
        setIconImage(getImage(IconCache.Icon.LOG_ICON));
        setHeader(new LinkLabel(FileUtilities.getFile(Constants.LOG_FILE)));
        logArea = new JTextArea(ROWS, COLS);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logPane = new JScrollPane(logArea);

        // TODO:  Fix button panel resizing issues
        clearButton = addPanelButton("Clear", IconCache.Icon.DELETE_ICON, KeyEvent.VK_L);
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearButton.setEnabled(false);
                deleteLogFile.set(true);
            }
        });

        readerThread = new Thread(this);
        threadRunning = new AtomicBoolean(false);
        deleteLogFile = new AtomicBoolean(false);

        setContent(logPane, true);
        setResizable(true);
    }

    /**
     * Thread safe append
     * @param data The data to append to the log window
     */
    public LogDialog append(final String data) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logArea.append(data);
                clearButton.setEnabled(true);
            }
        });
        return this;
    }

    public LogDialog clear() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logArea.setText(null);
            }
        });
        return this;
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
        super.setVisible(visible);
    }

    public void run() {
        threadRunning.set(true);
        BufferedReader br = null;

        try {
            String buffer;

            // Reads the log file and prints to the text area
            while (threadRunning.get()) {
                if (br == null) {
                    br = new BufferedReader(new FileReader(FileUtilities.getFile(Constants.LOG_FILE)));
                }

                if (isVisible()) {
                   if (deleteLogFile.get()) {
                        br.close();
                        FileUtilities.deleteFile(Constants.LOG_FILE);
                        clear();
                        br = null;
                        deleteLogFile.set(false);
                    } else if ((buffer = br.readLine()) != null) {
                        append(buffer).append("\r\n");
                    } else sleep(500);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    threadRunning.set(false);
                    br.close();
                } catch (Exception ignore) {
                }
            }
        }
    }

    public void sleep(int millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ignore) {}
    }
}
