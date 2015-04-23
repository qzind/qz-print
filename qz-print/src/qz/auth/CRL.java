package qz.auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

/**
 * Wrapper class for the Certificate Revocation List
 * Created by Steven on 2/4/2015. Package: qz.auth Project: qz-print
 */
public class CRL {

    private static CRL instance = null;

    /**
     * The URL to the QZ CRL. Should not be changed except for dev tests
     */
    public static final String CRL_URL = "http://crl.qzindustries.com";

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
                    }
                    catch(Exception e) {
                        e.printStackTrace();
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
