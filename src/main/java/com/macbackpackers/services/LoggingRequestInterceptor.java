package com.macbackpackers.services;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

public class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    private HttpRequestListener requestListener; // optional listener
    private HttpResponseListener responseListener; // optional listener
    private CardMask cardMask; // optional card mask
    private RegexMask[] requestMasks; // optional requests masks

    public LoggingRequestInterceptor() {
        // default constructor
    }

    /**
     * Initialise with an additional response listener.
     * 
     * @param reqListener (optional) request listener
     * @param respListener (optional) response listener
     * @param cardMask (optional) card mask
     */
    public LoggingRequestInterceptor( HttpRequestListener reqListener, HttpResponseListener respListener, CardMask cardMask, RegexMask... requestMasks ) {
        this.requestListener = reqListener;
        this.responseListener = respListener;
        this.cardMask = cardMask;
        this.requestMasks = requestMasks;
    }

    @Override
    public ClientHttpResponse intercept( HttpRequest request, byte[] body, ClientHttpRequestExecution execution ) throws IOException {
        traceRequest( request, body );
        ClientHttpResponse response = execution.execute( request, body );
        traceResponse( response );
        return response;
    }

    private void traceRequest( HttpRequest request, byte[] body ) throws IOException {
        String maskedBody = applyCardMask( new String( body, "UTF-8" ) );
        if ( requestMasks != null ) {
            for ( RegexMask regexMask : requestMasks ) {
                maskedBody = regexMask.applyMask( maskedBody );
            }
        }
        LOGGER.debug( "===========================request begin================================================" );
        LOGGER.debug( "URI         : {}", request.getURI() );
        LOGGER.debug( "Method      : {}", request.getMethod() );
        LOGGER.debug( "Headers     : {}", request.getHeaders() );
        LOGGER.debug( "Request body: {}", maskedBody );
        LOGGER.debug( "==========================request end================================================" );

        if ( requestListener != null ) {
            requestListener.traceRequest( request.getURI(), request.getMethod(), 
                    request.getHeaders(), maskedBody );
        }
    }

    private void traceResponse( ClientHttpResponse response ) throws IOException {
        byte[] body = StreamUtils.copyToByteArray( response.getBody() );
        String maskedBody = applyCardMask( new String( body, "UTF-8" ) );
        LOGGER.debug( "============================response begin==========================================" );
        LOGGER.debug( "Status code  : {}", response.getStatusCode() );
        LOGGER.debug( "Status text  : {}", response.getStatusText() );
        LOGGER.debug( "Headers      : {}", response.getHeaders() );
        LOGGER.debug( "Response body: {}", maskedBody );
        LOGGER.debug( "=======================response end=================================================" );

        if ( responseListener != null ) {
            responseListener.traceResponse( HttpStatus.valueOf( response.getStatusCode().value() ),
                    response.getStatusText(), response.getHeaders(), new String( body, "UTF-8" ) );
        }
    }

    /**
     * Masks all CardNumber elements within the given XML (if applicable).
     * 
     * @param xml the full XML to be masked
     * @return the XML will all CardNumber elements masked
     */
    private String applyCardMask( String xml ) {
        if ( cardMask != null ) {
            return cardMask.applyCardSecurityCodeMask( cardMask.applyCardMask( xml ) );
        }
        return xml;
    }

}