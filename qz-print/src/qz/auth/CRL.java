package qz.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Wrapper class for the Certificate Revocation List
 * Created by Steven on 2/4/2015. Package: qz.auth Project: qz-print
 */
public class CRL {
    /**
     * The URL to the QZ CRL. Should not be changed except for dev tests
     */
    public static final String CRL_URL="https://hosted.kd8rho.net/crl-temp.txt";

    ArrayList<String> revokedHashes=new ArrayList<String>();


    public static CRL getQzCrl() throws IOException {
        ArrayList<String> httpResult=new ArrayList<String>();
        try {
            URL qzCRL=new URL(CRL_URL);
            BufferedReader theReader=new BufferedReader(new InputStreamReader(qzCRL.openStream()));
            String line;
            while((line=theReader.readLine())!=null)
            {
                //Ignore 0 length lines, more efficient memory usage.
                if(line.length()!=0)
                {
                    //Ignore comments
                    if(line.charAt(0)!='#')
                    {
                        httpResult.add(line);
                    }
                }
            }
            CRL theNewCRL=new CRL();
            theNewCRL.revokedHashes=httpResult;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            CRL crl = getQzCrl();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
