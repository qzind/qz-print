package qz.auth;

/**
 * Created by Steven on 2/4/2015. Package: qz.auth Project: qz-print
 */
public class CertificateVerificationException extends Exception {
    private static final long serialVersionUID = 1L;

    public CertificateVerificationException(String message, Throwable cause) {
        super(message, cause);
    }

    public CertificateVerificationException(String message) {
        super(message);
    }
}