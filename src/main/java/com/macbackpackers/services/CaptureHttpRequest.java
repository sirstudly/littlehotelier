
package com.macbackpackers.services;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public class CaptureHttpRequest implements HttpRequestListener {

    private URI uri;
    private HttpMethod method;
    private HttpHeaders headers;
    private String body;

    public URI getUri() {
        return uri;
    }

    public void setUri( URI uri ) {
        this.uri = uri;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod( HttpMethod method ) {
        this.method = method;
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
    public void traceRequest( URI uri, HttpMethod method, HttpHeaders headers, String body ) {
        this.uri = uri;
        this.method = method;
        this.headers = headers;
        this.body = body;
    }
}
