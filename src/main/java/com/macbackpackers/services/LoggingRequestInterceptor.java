package com.macbackpackers.services;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

public class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    private HttpRequestListener requestListener; // optional listener
    private HttpResponseListener responseListener; // optional listener
    private CardMask cardMask; // optional card mask

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
    public LoggingRequestInterceptor( HttpRequestListener reqListener, HttpResponseListener respListener, CardMask cardMask ) {
        this.requestListener = reqListener;
        this.responseListener = respListener;
        this.cardMask = cardMask;
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
            responseListener.traceResponse( response.getStatusCode(), 
                    response.getStatusText(), response.getHeaders(), new String( body, "UTF-8" )  );
        }
    }

    /**
     * Masks all CardNumber elements within the given XML keeping the first 6 and last 2 characters.
     * 
     * @param xml the full XML to be masked
     * @return the XML will all CardNumber elements masked
     */
    private String applyCardMask( String xml ) {
        if(cardMask != null) {
            String maskedString = xml;
            Pattern p = Pattern.compile( cardMask.getCardMaskMatchRegex() );
            Matcher m = p.matcher( maskedString );
            while ( m.find() ) {
                String cardNum = m.group( 1 );
                maskedString = maskedString.replaceAll( cardNum, cardMask.replaceCardWith( cardNum ) );
            }
            return maskedString;
        }
        return xml;
    }

}