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

    public LoggingRequestInterceptor() {
        // default constructor
    }

    /**
     * Initialise with an additional response listener.
     * 
     * @param reqListener (optional) request listener
     * @param respListener (optional) response listener
     */
    public LoggingRequestInterceptor( HttpRequestListener reqListener, HttpResponseListener respListener ) {
        this.requestListener = reqListener;
        this.responseListener = respListener;
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
        LOGGER.debug( "============================response begin==========================================" );
        LOGGER.debug( "Status code  : {}", response.getStatusCode() );
        LOGGER.debug( "Status text  : {}", response.getStatusText() );
        LOGGER.debug( "Headers      : {}", response.getHeaders() );
        LOGGER.debug( "Response body: {}", new String(body, "UTF-8") );
        LOGGER.debug( "=======================response end=================================================" );

        if ( responseListener != null ) {
            responseListener.traceResponse( response.getStatusCode(), 
                    response.getStatusText(), response.getHeaders(), body );
        }
    }

    /**
     * Masks all CardNumber elements within the given XML keeping the first 6 and last 2 characters.
     * 
     * @param xml the full XML to be masked
     * @return the XML will all CardNumber elements masked
     */
    private static String applyCardMask( String xml ) {
        String maskedString = xml;
        final String CARD_MASK = "<CardNumber>(\\d+)</CardNumber>";
        Pattern p = Pattern.compile( CARD_MASK );
        Matcher m = p.matcher( maskedString );
        while ( m.find() ) {
            String cardNum = m.group( 1 );
            maskedString = maskedString.replaceAll( cardNum, maskCardNumber( cardNum ) );
        }
        return maskedString;
    }

    /**
     * Masks a card number with periods. The first 6 and last 2 digits are left as is. e.g.
     * 1234567890123456 will return 123456........56 If the passed in number is not a number or is
     * not at least 8 characters, this will return the entire string masked with periods.
     * 
     * @param number The number in plain format
     * @return The masked card number
     */
    private static String maskCardNumber( String number ) {

        final String CARD_NUMBER_MASK = "(\\d{6})(\\d+)(\\d{2})";
        Pattern p = Pattern.compile( CARD_NUMBER_MASK );
        Matcher m = p.matcher( number );
        if ( m.find() ) {
            return m.group( 1 ) + StringUtils.repeat( '.', m.group( 2 ).length() ) + m.group( 3 );
        }
        // does not have at least 8 characters or not a number; mask entire string
        return StringUtils.repeat( '.', number.length() );
    }
}