
package com.macbackpackers.services;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class CaptureHttpResponse implements HttpResponseListener {

    private HttpStatus status;
    private String statusText;
    private HttpHeaders headers;
    private String body;

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

    public String getBody() {
        return body;
    }

    public void setBody( String body ) {
        this.body = body;
    }

    @Override
    public void traceResponse( HttpStatus status, String statusText, HttpHeaders headers, String body ) throws IOException {
        this.status = status;
        this.statusText = statusText;
        this.headers = headers;
        this.body = body;
    }
}
