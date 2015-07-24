
package com.macbackpackers.exceptions;

/**
 * An immediate shutdown is requested.
 *
 */
public class ShutdownException extends Exception {

    private static final long serialVersionUID = -5346288723454221679L;

    public ShutdownException() {
        super();
    }

    public ShutdownException( String message ) {
        super( message );
    }

}
