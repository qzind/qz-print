package qz.printer.action;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JavaFX container for taking HTML snapshots.
 * Used by PrintHTML to generate printable images.
 * <p/>
 * Do not use constructor (used by JavaFX), instead call {@code WebApp.initialize()}
 */
public class WebApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(WebApp.class);

    public static final double DEFAULT_WIDTH = 1280; //width used for the browser
    private static final int PAUSES = 5; //number of pauses during capture before assuming failure

    private static WebApp instance = null;

    private static Stage stage;
    private static WebView webView;
    private static double pageWidth;

    private static PauseTransition snap;

    //listens for a Succeeded state to activate image capture
    private static ChangeListener<Worker.State> stateListener = new ChangeListener<Worker.State>() {
        @Override
        public void changed(ObservableValue<? extends Worker.State> ov, Worker.State oldState, Worker.State newState) {
            log.trace("New state: {} > {}", oldState, newState);

            if (newState == Worker.State.SUCCEEDED) {
                webView.setPrefWidth(pageWidth);
                webView.autosize();

                //we have to resize the width first, for responsive html, then calculate the best fit height
                PauseTransition resize = new PauseTransition(Duration.millis(100));
                resize.setOnFinished(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        String heightText = webView.getEngine().executeScript("Math.max(document.body.offsetHeight, document.body.scrollHeight)").toString();
                        double height = Double.parseDouble(heightText);

                        webView.setPrefHeight(height);
                        webView.autosize();

                        snap.playFromStart();
                    }
                });

                resize.playFromStart();
            }
        }
    };

    //listens for load progress
    private static ChangeListener<Number> workDoneListener = new ChangeListener<Number>() {
        @Override
        public void changed(ObservableValue<? extends Number> ov, Number oldWork, Number newWork) {
            log.trace("Done: {} > {}", oldWork, newWork);
        }
    };


    /** Called by JavaFX thread */
    public WebApp() {
        instance = this;
    }

    /** Starts JavaFX thread if not already running */
    public static void initialize() {
        if (instance == null) {
            new Thread() {
                public void run() {
                    Application.launch(WebApp.class);
                }
            }.start();
        }
    }

    @Override
    public void start(Stage st) throws Exception {
        log.debug("Started JavaFX");

        webView = new WebView();
        Scene sc = new Scene(webView);

        stage = st;
        stage.setScene(sc);

        Worker<Void> worker = webView.getEngine().getLoadWorker();
        worker.stateProperty().addListener(stateListener);
        worker.workDoneProperty().addListener(workDoneListener);

        //prevents JavaFX from shutting down when hiding window
        Platform.setImplicitExit(false);
    }


    /** Capture image from pure HTML */
    public static BufferedImage captureHTML(String html, double width) throws IOException {
        return capture(html, false, width);
    }

    /** Capture image from HTML on URL */
    public static BufferedImage captureFile(String location, double width) throws IOException {
        return capture(location, true, width);
    }

    /**
     * Sets up capture to run on JavaFX thread and returns snapshot of rendered page
     *
     * @param source   The html to be rendered for capture
     * @param fromFile If the passed {@code source} is from a url/file location
     * @return BufferedImage of the rendered html
     */
    private static BufferedImage capture(final String source, final boolean fromFile, final double width) throws IOException {
        final AtomicReference<BufferedImage> capture = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        //ensure JavaFX has started before we run
        for(int i = 0; i < PAUSES; i++) {
            if (webView != null) {
                break;
            }

            log.trace("Waiting for JavaFX..");
            try { Thread.sleep(1000); } catch(Exception ignore) {}
        }

        if (webView == null) {
            throw new IOException("JavaFX did not start");
        }

        // run these actions on the JavaFX thread
        Platform.runLater(new Thread() {
            public void run() {
                try {
                    pageWidth = width;

                    webView.setPrefSize(100, 100);
                    webView.autosize();

                    stage.show(); //FIXME - will not capture without showing stage
                    stage.toBack();

                    //ran when engine reaches SUCCEEDED state, takes snapshot of loaded html
                    snap = new PauseTransition(Duration.millis(100));
                    snap.setOnFinished(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent actionEvent) {
                            try {
                                log.debug("Capturing image");

                                WritableImage snapshot = webView.snapshot(new SnapshotParameters(), null);
                                capture.set(SwingFXUtils.fromFXImage(snapshot, null));

                                stage.hide(); //hide stage so users won't have to manually close it
                            }
                            catch(Throwable t) {
                                error.set(t);
                            }
                        }
                    });

                    //actually begin loading the html
                    if (fromFile) {
                        webView.getEngine().load(source);
                    } else {
                        webView.getEngine().loadContent(source, "text/html");
                    }
                }
                catch(Throwable t) {
                    error.set(t);
                }
            }
        });

        //wait for the image to be captured or an error to be thrown
        Throwable t = error.get();
        for(int i = 0; i < PAUSES; i++) {
            if (capture.get() != null || (t = error.get()) != null) {
                break;
            }

            log.trace("Waiting for capture..");
            try { Thread.sleep(1000); } catch(Exception ignore) {}
        }

        if (capture.get() == null) {
            throw new IOException(t);
        }

        return capture.get();
    }

}
