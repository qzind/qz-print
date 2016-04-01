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
import qz.common.TrayManager;
import qz.utils.ByteUtilities;
import qz.utils.FileUtilities;

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
import java.util.logging.Logger;

/**
 * Created by Steven on 1/27/2015. Package: qz.auth Project: qz-print
 * Wrapper to store certificate objects from
 */
public class Certificate {

    public static Certificate trustedRootCert = null;
    private static final Logger log = Logger.getLogger(TrayManager.class.getName());
    private static boolean overrideTrustedRootCert = false;

    static {
        try {
            String overridePath = System.getProperty("trustedRootCert");
            if (overridePath != null) {
                try {
                    trustedRootCert = new Certificate(FileUtilities.readLocalFile(overridePath));
                    overrideTrustedRootCert = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (trustedRootCert == null) {
                trustedRootCert = new Certificate("-----BEGIN CERTIFICATE-----\n" +
                                                "MIIELzCCAxegAwIBAgIJALm151zCHDxiMA0GCSqGSIb3DQEBCwUAMIGsMQswCQYD\n" +
                                                "VQQGEwJVUzELMAkGA1UECAwCTlkxEjAQBgNVBAcMCUNhbmFzdG90YTEbMBkGA1UE\n" +
                                                "CgwSUVogSW5kdXN0cmllcywgTExDMRswGQYDVQQLDBJRWiBJbmR1c3RyaWVzLCBM\n" +
                                                "TEMxGTAXBgNVBAMMEHF6aW5kdXN0cmllcy5jb20xJzAlBgkqhkiG9w0BCQEWGHN1\n" +
                                                "cHBvcnRAcXppbmR1c3RyaWVzLmNvbTAgFw0xNTAzMDEyMzM4MjlaGA8yMTE1MDMw\n" +
                                                "MjIzMzgyOVowgawxCzAJBgNVBAYTAlVTMQswCQYDVQQIDAJOWTESMBAGA1UEBwwJ\n" +
                                                "Q2FuYXN0b3RhMRswGQYDVQQKDBJRWiBJbmR1c3RyaWVzLCBMTEMxGzAZBgNVBAsM\n" +
                                                "ElFaIEluZHVzdHJpZXMsIExMQzEZMBcGA1UEAwwQcXppbmR1c3RyaWVzLmNvbTEn\n" +
                                                "MCUGCSqGSIb3DQEJARYYc3VwcG9ydEBxemluZHVzdHJpZXMuY29tMIIBIjANBgkq\n" +
                                                "hkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuWsBa6uk+RM4OKBZTRfIIyqaaFD71FAS\n" +
                                                "7kojAQ+ySMpYuqLjIVZuCh92o1FGBvyBKUFc6knAHw5749yhLCYLXhzWwiNW2ri1\n" +
                                                "Jwx/d83Wnaw6qA3lt++u3tmiA8tsFtss0QZW0YBpFsIqhamvB3ypwu0bdUV/oH7g\n" +
                                                "/s8TFR5LrDfnfxlLFYhTUVWuWzMqEFAGnFG3uw/QMWZnQgkGbx0LMcYzdqFb7/vz\n" +
                                                "rTSHfjJsisUTWPjo7SBnAtNYCYaGj0YH5RFUdabnvoTdV2XpA5IPYa9Q597g/M0z\n" +
                                                "icAjuaK614nKXDaAUCbjki8RL3OK9KY920zNFboq/jKG6rKW2t51ZQIDAQABo1Aw\n" +
                                                "TjAdBgNVHQ4EFgQUA0XGTcD6jqkL2oMPQaVtEgZDqV4wHwYDVR0jBBgwFoAUA0XG\n" +
                                                "TcD6jqkL2oMPQaVtEgZDqV4wDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOC\n" +
                                                "AQEAijcT5QMVqrWWqpNEe1DidzQfSnKo17ZogHW+BfUbxv65JbDIntnk1XgtLTKB\n" +
                                                "VAdIWUtGZbXxrp16NEsh96V2hjDIoiAaEpW+Cp6AHhIVgVh7Q9Knq9xZ1t6H8PL5\n" +
                                                "QiYQKQgJ0HapdCxlPKBfUm/Mj1ppNl9mPFJwgHmzORexbxrzU/M5i2jlies+CXNq\n" +
                                                "cvmF2l33QNHnLwpFGwYKs08pyHwUPp6+bfci6lRvavztgvnKroWWIRq9ZPlC0yVK\n" +
                                                "FFemhbCd7ZVbrTo0NcWZM1PTAbvlOikV9eh3i1Vot+3dJ8F27KwUTtnV0B9Jrxum\n" +
                                                "W9P3C48mvwTxYZJFOu0N9UBLLg==\n" +
                                                "-----END CERTIFICATE-----");
                CRL.getInstance();  // Fetch the CRL
            }
            trustedRootCert.valid = true;
            log.info("Using trusted root certificate: CN=" + trustedRootCert.getCommonName() +
                    ", O=" + trustedRootCert.getOrganization() + " (" + trustedRootCert.getFingerprint() + ")");
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

            byte[] serverCertificate = Base64.decode(split[0].replaceAll(X509Constants.BEGIN_CERT, "").replaceAll(X509Constants.END_CERT, ""));
            //Strip beginning and end

            theCertificate = (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(serverCertificate));
            //Generate cert

            if (split.length == 2) {
                byte[] intermediateCertificate = Base64.decode(split[1].replaceAll(X509Constants.BEGIN_CERT, "").replaceAll(X509Constants.END_CERT, ""));
                theIntermediateCertificate = (X509Certificate)cf.generateCertificate(new ByteArrayInputStream(intermediateCertificate));
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
                            Date now = new Date();
                            valid = (getValidFromDate().compareTo(now) <= 0) && (getValidToDate().compareTo(now) > 0);
                        }
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }

            // Only do CRL checks on QZ-issued certificates
            if (trustedRootCert != null && !overrideTrustedRootCert) {
                CRL qzCrl = CRL.getInstance();
                if (qzCrl.isLoaded()) {
                    if (qzCrl.isRevoked(getFingerprint()) || theIntermediateCertificate == null || qzCrl.isRevoked(makeThumbPrint(theIntermediateCertificate))) {
                        log.warning("Problem verifying certificate with CRL");
                        valid = false;
                    }
                } else {
                    //Assume nothing is revoked, because we can't get the CRL
                    log.warning("Failed to retrieve QZ CRL, skipping CRL check");
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
        }
        catch(ParseException badParse) {
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
        if (signature.length() == 0) {
            return false;
        }

        RSATool tool = RSAToolFactory.getRSATool();
        RSAKey thePublicKey = new RSAKeyImpl(theCertificate.getPublicKey());

        //On errors, assume failure.
        try {
            return tool.verifyWithKey(data.getBytes(), Base64.decode(signature), thePublicKey);
        }
        catch(Exception e) {
            log.warning(e.getMessage());
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

    public static Logger getLogger() {
        return log;
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
