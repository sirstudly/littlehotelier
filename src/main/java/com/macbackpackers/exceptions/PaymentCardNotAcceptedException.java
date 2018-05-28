
package com.macbackpackers.exceptions;

/**
 * Validation error in LH when an incorrectly formatted card number is entered and saved.
 */
public class PaymentCardNotAcceptedException extends RuntimeException {

    private static final long serialVersionUID = -829476788823018044L;

    public PaymentCardNotAcceptedException() {
        super();
    }

    public PaymentCardNotAcceptedException( String message ) {
        super( message );
    }

}
