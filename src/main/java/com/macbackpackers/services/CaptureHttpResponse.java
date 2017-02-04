
package com.macbackpackers.services;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class CaptureHttpResponse implements HttpResponseListener {

    private HttpStatus status;
    private String statusText;
    private HttpHeaders headers;
    private byte[] body;

    public HttpStatus getStatus() {
        return status;
    }

    public void setStatus( HttpStatus status ) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText( String statusText ) {
        this.statusText = statusText;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public void setHeaders( HttpHeaders headers ) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody( byte[] body ) {
        this.body = body;
    }

    @Override
    public void traceResponse( HttpStatus status, String statusText, HttpHeaders headers, byte[] body ) throws IOException {
        this.status = status;
        this.statusText = statusText;
        this.headers = headers;
        this.body = body;
    }
}
