
package com.macbackpackers.exceptions;

/**
 * Something wonky happened when we tried to record the payment details.
 *
 */
public class RecordPaymentFailedException extends RuntimeException {

    private static final long serialVersionUID = -4992140185086638629L;

    public RecordPaymentFailedException( String message ) {
        super( message );
    }
}
