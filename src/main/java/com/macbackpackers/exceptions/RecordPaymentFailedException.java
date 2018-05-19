
package com.macbackpackers.exceptions;

/**
 * Something wonky happened when we tried to record the payment details.
 *
 */
public class RecordPaymentFailedException extends Exception {

    private static final long serialVersionUID = -3261865605682069418L;

    public RecordPaymentFailedException( String message ) {
        super( message );
    }
}
