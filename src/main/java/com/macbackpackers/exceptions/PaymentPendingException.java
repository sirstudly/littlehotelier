
package com.macbackpackers.exceptions;

/**
 * Attempting to process a payment results in a payment that requires additional info from the
 * customer (ie. 3DS) so will be confirmed at a later date. In the meantime, handle it differently.
 *
 */
public class PaymentPendingException extends RuntimeException {

    private static final long serialVersionUID = -3306063481321194740L;

    public PaymentPendingException( String message ) {
        super( message );
    }
}
