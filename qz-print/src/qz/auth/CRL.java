package qz.auth;

import qz.common.TrayManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper class for the Certificate Revocation List
 * Created by Steven on 2/4/2015. Package: qz.auth Project: qz-print
 */
public class CRL {

    private static CRL instance = null;
    private static final Logger log = Logger.getLogger(TrayManager.class.getName());

    /**
     * The URL to the QZ CRL. Should not be changed except for dev tests
     */
    public static final String CRL_URL = "https://crl.qz.io";

    ArrayList<String> revokedHashes = new ArrayList<String>();
    boolean loaded = false;

    private CRL() {}

    public static CRL getInstance() {
        if (instance == null) {
            instance = new CRL();

            new Thread() {
                @Override
                public void run() {
                    BufferedReader br = null;

                    log.log(Level.INFO, "Loading CRL " + CRL_URL + "...");

                    try {
                        URL qzCRL = new URL(CRL_URL);
                        br = new BufferedReader(new InputStreamReader(qzCRL.openStream()));

                        String line;
                        while((line = br.readLine()) != null) {
                            //Ignore 0 length lines, more efficient memory usage.
                            if (!line.isEmpty()) {
                                //Ignore comments
                                if (line.charAt(0) != '#') {
                                    instance.revokedHashes.add(line);
                                }
                            }
                        }

                        instance.loaded = true;
                        log.log(Level.INFO, "Successfully loaded " + instance.revokedHashes.size() + " CRL entries from " + CRL_URL);
                    }
                    catch(Exception e) {
                        log.log(Level.WARNING, "Error loading CRL " + CRL_URL, e);
                    }
                    finally {
                        if (br != null) {
                            try { br.close(); } catch(Exception ignore) {}
                        }
                    }
                }
            }.start();
        }

        return instance;
    }

    public boolean isRevoked(String fingerprint) {
        return revokedHashes.contains(fingerprint);
    }

    public boolean isLoaded() {
        return loaded;
    }
}
