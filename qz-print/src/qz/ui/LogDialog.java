package qz.ui;

import qz.common.Constants;
import qz.common.LogIt;
import qz.utils.FileUtilities;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

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
    private AtomicBoolean clearLogFile;

    public LogDialog(JMenuItem caller, IconCache iconCache) {
        super(caller, iconCache);
        initComponents();
    }

    public void initComponents() {
        setIconImage(getImage(IconCache.Icon.LOG_ICON));
        setHeader(new LinkLabel(FileUtilities.getFile(Constants.LOG_FILE, false).getParentFile()));
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
                clearLogFile.set(true);
            }
        });

        readerThread = new Thread(this);
        threadRunning = new AtomicBoolean(false);
        clearLogFile = new AtomicBoolean(false);

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
            clear();
            threadRunning.set(false);
        }
        super.setVisible(visible);
    }

    public void run() {
        threadRunning.set(true);
        BufferedReader br = null;
        File previousLogFile = null;

        try {
            String buffer;

            // Reads the log file and prints to the text area
            while (threadRunning.get()) {
                File activeLogFile = getActiveLogFile();
                if (br == null || activeLogFile != previousLogFile) {
                    previousLogFile = activeLogFile;
                    if (br != null) {
                        try { br.close(); }
                        catch (Exception ignore) {}
                    }
                    br = new BufferedReader(new FileReader(activeLogFile));
                }

                LogIt.log("Reading " + activeLogFile.getPath());

                if (isVisible()) {
                   if (clearLogFile.get()) {
                        clear();
                        clearLogFile.set(false);
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

    public File getActiveLogFile() {
        File logFile = null;
        for (int i = 0; i < Constants.LOG_ROTATIONS; i++) {
            File compareFile = FileUtilities.getFile(Constants.LOG_FILE + i, false);
            if (!compareFile.exists()) {
                continue;
            }

            if (logFile == null || compareFile.lastModified() > logFile.lastModified()) {
                logFile = compareFile;
            }
        }
        return logFile;
    }

    public void sleep(int millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ignore) {}
    }
}
