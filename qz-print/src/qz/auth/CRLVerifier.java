package qz.auth;

import java.security.cert.X509Certificate;

/**
 * Created by Steven on 2/4/2015. Package: qz.auth Project: qz-print
 */
public class CRLVerifier {
    /**
     * Verifies the certificate is not in the CRL. Default is to ignore HTTP failures.
     * @param cert the certificate to check
     * @throws CertificateVerificationException
     */
    public static void verifyCertificateCRLs(X509Certificate cert) throws CertificateVerificationException {
        verifyCertificateCRLs(cert, false);
    }

    /**
     * Verifies the certificate is not in the CRL.
     * @param cert the certificate to check
     * @param strict whether to throw an error if the HTTP request fails
     * @throws CertificateVerificationException
     */
    public static void verifyCertificateCRLs(X509Certificate cert,boolean strict) throws CertificateVerificationException {

    }
}
