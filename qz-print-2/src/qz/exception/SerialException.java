/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package qz.exception;

/**
 *
 * @author Tres
 */
public class SerialException extends Exception {

    /**
     * Creates a new instance of
     * <code>SerialException</code> without detail message.
     */
    public SerialException() {
    }

    /**
     * Constructs an instance of
     * <code>SerialException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public SerialException(String msg) {
        super(msg);
    }
}
