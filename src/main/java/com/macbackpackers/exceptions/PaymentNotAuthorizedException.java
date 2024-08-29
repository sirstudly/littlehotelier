
package com.macbackpackers.exceptions;

import org.htmlunit.WebResponse;

/**
 * Thrown when an attempt to charge a card fails miserably.
 *
 */
public class PaymentNotAuthorizedException extends WebResponseException {

    private static final long serialVersionUID = 1029988078912415808L;

    public PaymentNotAuthorizedException( String message ) {
        super( message, null );
    }

    public PaymentNotAuthorizedException( String message, WebResponse webResponse ) {
        super( message, webResponse );
    }

}
