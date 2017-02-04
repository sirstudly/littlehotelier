package com.macbackpackers.services;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public interface HttpRequestListener {

    void traceRequest(URI uri, HttpMethod method, HttpHeaders headers, String body);
}
