package qz.auth;

import com.estontorise.simplersa.RSAKeyImpl;
import com.estontorise.simplersa.RSAToolFactory;
import com.estontorise.simplersa.interfaces.RSAKey;
import com.estontorise.simplersa.interfaces.RSATool;
import org.apache.commons.ssl.X509CertificateChainBuilder;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PrincipalUtil;
import qz.common.Base64;
import qz.common.Constants;
import qz.common.LogIt;
import qz.utils.ByteUtilities;
import qz.utils.FileUtilities;
import sun.security.provider.X509Factory;

import javax.security.cert.CertificateParsingException;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by Steven on 1/27/2015. Package: qz.auth Project: qz-print
 * Wrapper to store certificate objects from
 */
public class Certificate {

    public static Certificate trustedRootCert = null;

    static {
        try {
            trustedRootCert = new Certificate("-----BEGIN CERTIFICATE-----\n" +
                                                 "MIIDczCCAlugAwIBAgIJAL84/Wb/WNmOMA0GCSqGSIb3DQEBCwUAMFAxCzAJBgNV\n" +
                                                 "BAYTAlVTMQ0wCwYDVQQIDARPaGlvMRswGQYDVQQKDBJTZWxsZXJzVG9vbGJveC5j\n" +
                                                 "b20xFTATBgNVBAMMDFNUQi1yb290Q0EwMTAeFw0xNTAyMDkyMTU4MjFaFw0xODAy\n" +
                                                 "MDgyMTU4MjFaMFAxCzAJBgNVBAYTAlVTMQ0wCwYDVQQIDARPaGlvMRswGQYDVQQK\n" +
                                                 "DBJTZWxsZXJzVG9vbGJveC5jb20xFTATBgNVBAMMDFNUQi1yb290Q0EwMTCCASIw\n" +
                                                 "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKs5YF3m1Nf06M2yb6tsiHzaPzbI\n" +
                                                 "7fP51RoBXV+TIkG7OKvDlAhEJJVg0JGV080Oi6It2pWrjRhVLkJ4t12Q5QoFmftv\n" +
                                                 "oDf3fyYoagB1KPDylFvxmKvJiyn1dxkSRh8FJIkIpcNhvClG3vsMelyjq1dGfuGm\n" +
                                                 "wHDsHkc7v+PgMxlp5yRlcid90n7YCVSbkrFAklJNqjcYYqe+1vqHL4yJnG8TLqBd\n" +
                                                 "SKxgJbmE/YWay9iYKkY0C/pu4K9LZAkBO0Xf8tJUR1FWSH/FW6DuuGtZ9aCQUW6C\n" +
                                                 "aw2BuzHQYoDKQ5le+JkZbwhLeizoWbMx5PGixq+ZkKT3C6ziIDNDMGDcL7cCAwEA\n" +
                                                 "AaNQME4wHQYDVR0OBBYEFL9FwzfseceN2U2YVHkhppgYxDzbMB8GA1UdIwQYMBaA\n" +
                                                 "FL9FwzfseceN2U2YVHkhppgYxDzbMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEL\n" +
                                                 "BQADggEBAB3tVNRGBeIk/YOAolx3uEyBcaN0tCvVnhaPOIK1trv/3p6iq6J16iS7\n" +
                                                 "8T5CFpA9LRyraOpEiPJF3e8LGMwcZnOnvy7COvMhDV9uWc+mx8VW6roUiBMRNEdP\n" +
                                                 "aafNCt3oBjOwkG9+BdaRa5JNbxTDIKzAO7G+f/wqX12trS9/yOq1toJgbCsV+j1q\n" +
                                                 "paowd79tdP0zXcd06vFiC3cSyxeEH5HQODcZLmbBQcqTEdyde74I2l8LnIi+war0\n" +
                                                 "Cf0vbK6BGNKYfzitDkhp9pH4GGDodrF18q4KwUDl3J9uw+2hMQzIwSpidTJbU7f/\n" +
                                                 "jSRl2nlQvk2VwRcRIzahoPWydVdTwZw=                                \n" +
                                                 "-----END CERTIFICATE-----");
            trustedRootCert.valid = true;
        }
        catch(javax.security.cert.CertificateParsingException e) {
            e.printStackTrace();
        }
    }

    X509Certificate theCertificate;
    X509Certificate theIntermediateCertificate;

    private String fingerprint;
    private String commonName;
    private String organization;
    private Date validFrom;
    private Date validTo;

    private boolean valid = false; //used by review sites UI only

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final String[] saveFields = new String[] {"fingerprint", "commonName", "organization", "validFrom", "validTo", "valid"};

    /**
     * Decodes a certificate and intermediate certificate from the given string
     */
    public Certificate(String in) throws CertificateParsingException {

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            //Setup X.509

            String[] split = in.split("--START INTERMEDIATE CERT--");

            byte[] serverCertificate = Base64.decode(split[0].replaceAll(X509Factory.BEGIN_CERT, "").replaceAll(X509Factory.END_CERT, ""));
            //Strip beginning and end

            theCertificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(serverCertificate));
            //Generate cert

            if (split.length == 2) {
                byte[] intermediateCertificate = Base64.decode(split[1].replaceAll(X509Factory.BEGIN_CERT, "").replaceAll(X509Factory.END_CERT, ""));
                theIntermediateCertificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(intermediateCertificate));
            } else {
                theIntermediateCertificate = null; //Self-signed
            }

            commonName = String.valueOf(PrincipalUtil.getSubjectX509Principal(theCertificate).getValues(X509Name.CN).get(0));
            fingerprint = makeThumbPrint(theCertificate);
            organization = String.valueOf(PrincipalUtil.getSubjectX509Principal(theCertificate).getValues(X509Name.O).get(0));
            validFrom = theCertificate.getNotBefore();
            validTo = theCertificate.getNotAfter();

            if (trustedRootCert != null) {
                HashSet<X509Certificate> chain = new HashSet<X509Certificate>();
                try {
                    chain.add(trustedRootCert.theCertificate);
                    if (theIntermediateCertificate != null) { chain.add(theIntermediateCertificate); }
                    X509Certificate[] x509Certificates = X509CertificateChainBuilder.buildPath(theCertificate, chain);

                    for(X509Certificate x509Certificate : x509Certificates) {
                        if (x509Certificate.equals(trustedRootCert.theCertificate)) {
                            valid = true;
                        }
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
            CertificateParsingException certificateParsingException = new CertificateParsingException();
            certificateParsingException.initCause(e);
            throw certificateParsingException;
        }
    }

    private Certificate() {}

    /**
     * Used to rebuild a certificate for the 'Saved Sites' screen without having to decrypt the certificates again
     */
    public static Certificate loadCertificate(HashMap<String,String> data) {
        Certificate cert = new Certificate();

        cert.fingerprint = data.get("fingerprint");
        cert.commonName = data.get("commonName");
        cert.organization = data.get("organization");

        try {
            cert.validFrom = dateFormat.parse(data.get("validFrom"));
            cert.validTo = dateFormat.parse(data.get("validTo"));
        } catch (ParseException badParse) {
            cert.validFrom = new Date(0);
            cert.validTo = new Date(0);
            LogIt.log(badParse);
        }

        cert.valid = Boolean.parseBoolean(data.get("valid"));

        return cert;
    }

    /**
     * Checks given signature for given data against this certificate,
     * ensuring it is properly signed
     *
     * @param signature the signature appended to the data, base64 encoded
     * @param data      the data to check
     * @return true if signature valid, false if not
     */
    public boolean isSignatureValid(String signature, String data) {
        RSATool tool = RSAToolFactory.getRSATool();
        RSAKey thePublicKey = new RSAKeyImpl(theCertificate.getPublicKey());

        //On errors, assume failure.
        try {
            return tool.verifyWithKey(data.getBytes(), Base64.decode(signature), thePublicKey);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Checks if the certificate has been added to the local trusted store
     */
    public boolean isSaved() {
        File allowed = FileUtilities.getFile(Constants.ALLOW_FILE);

        BufferedReader br = null;
        try {
            String line;
            br = new BufferedReader(new FileReader(allowed));
            while((line = br.readLine()) != null) {
                String print = line.substring(0, line.indexOf("\t"));
                if (print.equals(getFingerprint())) {
                    return true;
                }
            }
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        finally {
            if (br != null) {
                try { br.close(); } catch(Exception ignore) {}
            }
        }

        return false;
    }

    /**
     * Checks if the certificate has been added to the local blocked store
     */
    public boolean isBlocked() {
        File blocks = FileUtilities.getFile(Constants.BLOCK_FILE);

        BufferedReader br = null;
        try {
            String line;
            br = new BufferedReader(new FileReader(blocks));
            while((line = br.readLine()) != null) {
                String print = line.substring(0, line.indexOf("\t"));
                if (print.equals(getFingerprint())) {
                    return true;
                }
            }
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        finally {
            if (br != null) {
                try { br.close(); } catch(Exception ignore) {}
            }
        }

        return false;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getOrganization() {
        return organization;
    }

    public String getValidFrom() {
        return dateFormat.format(validFrom);
    }

    public String getValidTo() {
        return dateFormat.format(validTo);
    }

    public Date getValidFromDate() {
        return validFrom;
    }

    public Date getValidToDate() {
        return validTo;
    }

    /**
     * Validates certificate against embedded cacert.
     */
    public boolean isTrusted() {
        return valid;
    }

    public static String makeThumbPrint(X509Certificate cert) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return ByteUtilities.bytesToHex(digest, false);
    }

    public String data() {
        return getFingerprint() + "\t" +
                getCommonName() + "\t" +
                getOrganization() + "\t" +
                getValidFrom() + "\t" +
                getValidTo() + "\t" +
                isTrusted();
    }

    @Override
    public String toString() {
        return getOrganization() + " (" + getCommonName() + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Certificate) {
            return ((Certificate)obj).data().equals(this.data());
        }
        return super.equals(obj);
    }
}
