
package com.macbackpackers.exceptions;

/**
 * Validation error in LH when an incorrectly formatted card number is entered and saved.
 */
public class PaymentCardNotAcceptedException extends RuntimeException {

    private static final long serialVersionUID = 6405059383301826469L;

    public PaymentCardNotAcceptedException() {
        super();
    }

}
