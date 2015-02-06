package qz.auth;

import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PrincipalUtil;
import qz.common.Base64;
import qz.utils.FileUtilities;
import sun.security.provider.X509Factory;

import javax.security.cert.CertificateParsingException;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;

/**
 * Created by Steven on 1/27/2015. Package: qz.auth Project: qz-print
 * Wrapper to store certificate objects from
 */
public class Certificate {

    X509Certificate theCertificate;
    X509Certificate theIntermediateCertificate;

    //TODO - defaults ?
    private String fingerprint;
    private String commonName;
    private String organization;
    private String validFrom;
    private String validTo;
    //TODO - other info available ?

    private boolean valid = false;

    public static final String[] saveFields = new String[] {"fingerprint", "commonName", "organization", "validFrom", "validTo", "valid"};

    /**
     * Decodes a certificate and intermediate certificate from the given string
     *
     * @param in
     * @throws IOException
     * @throws CertificateException
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
            fingerprint=makeThumbPrint(theCertificate);
            organization = String.valueOf(PrincipalUtil.getSubjectX509Principal(theCertificate).getValues(X509Name.O).get(0));
            validFrom = theCertificate.getNotBefore().toString();
            validTo = theCertificate.getNotAfter().toString();
            valid = isValidQZCert();
        }
        catch(CertificateException e) {
            e.printStackTrace();
            CertificateParsingException certificateParsingException = new CertificateParsingException();
            certificateParsingException.initCause(e);
            throw certificateParsingException;
        }
        catch(IOException e) {
            e.printStackTrace();
            CertificateParsingException certificateParsingException = new CertificateParsingException();
            certificateParsingException.initCause(e);
            throw certificateParsingException;
        } catch (NoSuchAlgorithmException e) {
            //Shouldn't happen, indicates failure to do x.509 stuff.
            e.printStackTrace();
            CertificateParsingException certificateParsingException = new CertificateParsingException();
            certificateParsingException.initCause(e);
        }
    }

    private Certificate() {}

    /**
     * Used to rebuild a certificate for the 'Saved Sites' screen without having to decrypt the certificates again
     */
    public static Certificate loadCertificate(HashMap<String, String> data) {
        Certificate cert = new Certificate();

        cert.fingerprint = data.get("fingerprint");
        cert.commonName = data.get("commonName");
        cert.organization = data.get("organization");
        cert.validFrom = data.get("validFrom");
        cert.validTo = data.get("validTo");

        cert.valid = Boolean.parseBoolean(data.get("valid"));

        return cert;
    }


    /**
     * Checks given signature for given data against this certificate,
     * ensuring it is properly signed
     * @param signature the signature appended to the data
     * @param data the data to check
     * @return true if signature valid, false if not
     */
    public boolean isSignatureValid(String signature, String data)
    {
        Signature sig;
        try {
            sig = Signature.getInstance("SHA1withDSA", "SUN");
            sig.initVerify(theCertificate);
            sig.update(data.getBytes());
            return sig.verify(signature.getBytes());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        //On errors, assume failure.
        return false;
    }


    /**
     * Checks if the certificate has been added to the local trusted store
     *
     * @return
     */
    public boolean isSaved() {
        File allowed = FileUtilities.getFile("allowed");

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
     *
     * @return
     */
    public boolean isBlocked() {
        File blocks = FileUtilities.getFile("blocked");

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
        return validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    /**
     * Validates certificate against QZ cacert.
     *
     * @return
     */
    public boolean isValidQZCert() {
        return false;
        //TODO: Finish
    }


    public static void main(String[] args) throws NoSuchAlgorithmException, CertificateEncodingException {
        try {
            Certificate test = new Certificate("-----BEGIN CERTIFICATE-----\n" +
                    "MIIGtjCCBZ6gAwIBAgIDAhtyMA0GCSqGSIb3DQEBBQUAMIGMMQswCQYDVQQGEwJJ\n" +
                    "TDEWMBQGA1UEChMNU3RhcnRDb20gTHRkLjErMCkGA1UECxMiU2VjdXJlIERpZ2l0\n" +
                    "YWwgQ2VydGlmaWNhdGUgU2lnbmluZzE4MDYGA1UEAxMvU3RhcnRDb20gQ2xhc3Mg\n" +
                    "MiBQcmltYXJ5IEludGVybWVkaWF0ZSBTZXJ2ZXIgQ0EwHhcNMTQwNDE1MTMzNDQ3\n" +
                    "WhcNMTYwNDE1MDY1MzA4WjCBojEZMBcGA1UEDRMQRzJhZVlDTzZZdThZRklaQjEL\n" +
                    "MAkGA1UEBhMCVVMxDTALBgNVBAgTBE9oaW8xDzANBgNVBAcTBkRheXRvbjEYMBYG\n" +
                    "A1UEChMPU3RldmVuIEplbm5pc29uMRcwFQYDVQQDEw53d3cua2Q4cmhvLm5ldDEl\n" +
                    "MCMGCSqGSIb3DQEJARYWc3R2bmplbm5pc29uQGdtYWlsLmNvbTCCASIwDQYJKoZI\n" +
                    "hvcNAQEBBQADggEPADCCAQoCggEBAKb48uj6mT+WiDcWv5r56L3lGENKcbUJLaIV\n" +
                    "lbQkiw5Wms2/3UzbStSBVW95CqDRwdryNmBOhjvKq0M6hQUkcFdGmxHNXm7WqdbO\n" +
                    "hdeAJ8u0lJBHMKEYnlpHAcgCqtGPCoXb/lMs29dtKYf9HhQeq3J3Zha3nuRKFamF\n" +
                    "6NUlR/CkS2JnfRf3qLSeo8Xx+Mo8UCJisx6LlyCesXcLzL8RsedITCNnHyWProgl\n" +
                    "8AyA6apsWiY7f2koUKFFuLeVpp0KQjvOKgnKG9Vj9GIyMfZVv0seltJWiu/RDI/8\n" +
                    "yQbSbZJWNPBr5JCyeH/HCsYGd0L8qQkgrLzPPX2dkNcwH9TPHsUCAwEAAaOCAwcw\n" +
                    "ggMDMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgOoMB0GA1UdJQQWMBQGCCsGAQUFBwMC\n" +
                    "BggrBgEFBQcDATAdBgNVHQ4EFgQUdD6k3xjFBiCRnhybtDULoZezWogwHwYDVR0j\n" +
                    "BBgwFoAUEdsjRf1UzGpxb4SKA9e+9wEvJoYwQwYDVR0RBDwwOoIOd3d3LmtkOHJo\n" +
                    "by5uZXSCCmtkOHJoby5uZXSCDnN2bi5rZDhyaG8ubmV0ggwqLmtkOHJoby5uZXQw\n" +
                    "ggFWBgNVHSAEggFNMIIBSTAIBgZngQwBAgIwggE7BgsrBgEEAYG1NwECAzCCASow\n" +
                    "LgYIKwYBBQUHAgEWImh0dHA6Ly93d3cuc3RhcnRzc2wuY29tL3BvbGljeS5wZGYw\n" +
                    "gfcGCCsGAQUFBwICMIHqMCcWIFN0YXJ0Q29tIENlcnRpZmljYXRpb24gQXV0aG9y\n" +
                    "aXR5MAMCAQEagb5UaGlzIGNlcnRpZmljYXRlIHdhcyBpc3N1ZWQgYWNjb3JkaW5n\n" +
                    "IHRvIHRoZSBDbGFzcyAyIFZhbGlkYXRpb24gcmVxdWlyZW1lbnRzIG9mIHRoZSBT\n" +
                    "dGFydENvbSBDQSBwb2xpY3ksIHJlbGlhbmNlIG9ubHkgZm9yIHRoZSBpbnRlbmRl\n" +
                    "ZCBwdXJwb3NlIGluIGNvbXBsaWFuY2Ugb2YgdGhlIHJlbHlpbmcgcGFydHkgb2Js\n" +
                    "aWdhdGlvbnMuMDUGA1UdHwQuMCwwKqAooCaGJGh0dHA6Ly9jcmwuc3RhcnRzc2wu\n" +
                    "Y29tL2NydDItY3JsLmNybDCBjgYIKwYBBQUHAQEEgYEwfzA5BggrBgEFBQcwAYYt\n" +
                    "aHR0cDovL29jc3Auc3RhcnRzc2wuY29tL3N1Yi9jbGFzczIvc2VydmVyL2NhMEIG\n" +
                    "CCsGAQUFBzAChjZodHRwOi8vYWlhLnN0YXJ0c3NsLmNvbS9jZXJ0cy9zdWIuY2xh\n" +
                    "c3MyLnNlcnZlci5jYS5jcnQwIwYDVR0SBBwwGoYYaHR0cDovL3d3dy5zdGFydHNz\n" +
                    "bC5jb20vMA0GCSqGSIb3DQEBBQUAA4IBAQDQl5/nwtL7DfYPdAuAvUZuyp7WhmmL\n" +
                    "vD/6DxozCgwS4dkD4Ft5K3gaUsCPO5uPaQV+GPwn4lqqd6495QPRFc9Nu3TpcEQ8\n" +
                    "+fnfhOcDFrt0oDIr0HAqQwyvIt1KKTWidkFnIowpgsfWDALKIR2EvPgh/FxsEzOd\n" +
                    "//IIp0mRl+L8GbTJNuXX319VMPhSVMGvH8UAcnrud1O34/Q7u+Q0nwjidZPovoH3\n" +
                    "fgyZwA4RFH7SJOqpXWbeqeRPIbXuKjcfEoFqRaFz352l2wyiUV+GPJ1IJSOWeTvU\n" +
                    "cPCawAyufX4ul0CEty4cmhG8uKJPZrnn5r23cfkmlKTt1JNkai1bWhj0\n" +
                    "-----END CERTIFICATE-----");
        }
        catch(CertificateParsingException e) {
            e.printStackTrace();
        }
    }

    public static String makeThumbPrint(X509Certificate cert) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] der = cert.getEncoded();
        md.update(der);
        byte[] digest = md.digest();
        return hexify(digest);
    }

    private static String hexify(byte bytes[]) {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
                            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        StringBuilder buf = new StringBuilder(bytes.length * 2);

        for(byte aByte : bytes) {
            buf.append(hexDigits[(aByte & 0xf0) >> 4]);
            buf.append(hexDigits[aByte & 0x0f]);
        }

        return buf.toString();
    }

    public String toString() {
        return getFingerprint() +"\t"+
                getCommonName() +"\t"+
                getOrganization() +"\t" +
                getValidFrom() +"\t"+
                getValidTo() +"\t"+
                isValidQZCert();
    }

}
