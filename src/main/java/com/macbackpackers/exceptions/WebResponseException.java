
package com.macbackpackers.exceptions;

import org.htmlunit.WebResponse;

import java.io.IOException;

/**
 * An unexpected response happened.
 */
public class WebResponseException extends IOException {

    private static final long serialVersionUID = 7666149651197870952L;

    private WebResponse webResponse;

    public WebResponseException( String message, WebResponse webResponse ) {
        super( message );
        this.webResponse = webResponse;
    }

    public WebResponse getWebResponse() {
        return webResponse;
    }

}
