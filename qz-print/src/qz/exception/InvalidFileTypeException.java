package qz.exception;

/**
 *
 * @author Tres
 */
public class InvalidFileTypeException extends Exception {

    /**
     * Creates a new instance of
     * <code>InvalidRawImageException</code> without detail message.
     */
    public InvalidFileTypeException() {
    }

    /**
     * Constructs an instance of
     * <code>InvalidRawImageException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public InvalidFileTypeException(String msg) {
        super(msg);
    }
}
