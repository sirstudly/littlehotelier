
package com.macbackpackers.exceptions;

import com.gargoylesoftware.htmlunit.WebResponse;

/**
 * Thrown when an attempt to charge a card fails miserably.
 *
 */
public class PaymentNotAuthorizedException extends WebResponseException {

    private static final long serialVersionUID = 7516538818949097030L;

    public PaymentNotAuthorizedException( String message, WebResponse webResponse ) {
        super( message, webResponse );
    }

}
