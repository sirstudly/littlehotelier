package com.macbackpackers.services;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public interface HttpResponseListener {

    void traceResponse(HttpStatus status, String statusText, HttpHeaders headers, byte[] body) throws IOException;
}
