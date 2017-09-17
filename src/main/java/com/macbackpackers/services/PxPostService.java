package com.macbackpackers.services;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.xml.Cvc2Presence;
import com.macbackpackers.beans.xml.TxnRequest;
import com.macbackpackers.beans.xml.TxnResponse;

/**
 * For sending transactions via PX Post.
 * 
 */
@Service
public class PxPostService {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
   
    /** The service URL */
    @Value( "${pxpost.url}" )
    private String pxPostUrl;

    @Value( "${pxpost.username}" )
    private String pxPostUsername;

    @Value( "${pxpost.password}" )
    private String pxPostPassword;

    /**
     * Get the status of the given transaction.
     * 
     * @param txnId unique transaction ID
     * @param respListener (optional) response listener
     * @return (non-null) response from server
     * @throws RestClientException on I/O error
     */
    public TxnResponse getStatus( String txnId, HttpResponseListener respListener ) throws RestClientException {
        LOGGER.info( "PX Post status for transaction " + txnId );
        return postRequest(constructTxnStatusRequest(txnId), null, respListener);
    }
    
    /**
     * Process a payment through the payment gateway.
     * 
     * @param txnId unique transaction ID
     * @param merchantRef (optional) reference to appear in reports
     * @param cardDetails customer card details
     * @param amount (amount of transaction in pounds, pence. 
     * @param reqListener (optional) request listener
     * @param respListener (optional) response listener
     * @return non-null response from gateway
     * @throws RestClientException on I/O error
     */
    public TxnResponse processPayment( String txnId, String merchantRef, CardDetails cardDetails, BigDecimal amount, 
            HttpRequestListener reqListener, HttpResponseListener respListener ) throws RestClientException {
        LOGGER.info( "PX Post processing payment for " + merchantRef + " the amount of £" + amount );
        return postRequest( constructPurchaseRequest( txnId, merchantRef, cardDetails, amount ), 
                reqListener, respListener );
    }

    /**
     * Builds a purchase request.
     * 
     * @param txnId unique transaction ID
     * @param merchantRef (optional) reference to appear in reports
     * @param cardDetails customer card details
     * @param amount (amount of transaction in pounds, pence. 
     * @return non-null request
     */
    private TxnRequest constructPurchaseRequest(
            String txnId, String merchantRef, CardDetails cardDetails, BigDecimal amount) {

        final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("###0.00");
        TxnRequest req = new TxnRequest();
        req.setPostUsername( getPxPostUsername() );
        req.setPostPassword( getPxPostPassword() );
        req.setCardHolderName( cardDetails.getName() );
        req.setCardNumber( cardDetails.getCardNumber() );
        req.setAmount( CURRENCY_FORMAT.format( amount ) );
        req.setDateExpiry( cardDetails.getExpiry() );
        req.setCvc2( cardDetails.getCvv() );
        req.setCvc2Presence( cardDetails.getCvv() == null ? 
                Cvc2Presence.CVC_NOT_PRESENT : Cvc2Presence.INCLUDED_BY_MERCHANT );
        req.setInputCurrency( "GBP" );
        req.setTxnType( "Purchase" );
        req.setTxnId( txnId );
        req.setMerchantReference( merchantRef );
        return req;
    }

    /**
     * Builds a status request.
     * 
     * @param txnId unique transaction ID for original transaction
     * @return non-null request
     */
    private TxnRequest constructTxnStatusRequest(String txnId) {
        TxnRequest req = new TxnRequest();
        req.setPostUsername( getPxPostUsername() );
        req.setPostPassword( getPxPostPassword() );
        req.setTxnType( "Status" );
        req.setTxnId( txnId );
        return req;
    }

    /**
     * Posts the given request to the payment processor. Returns the response.
     * 
     * @param txnReq request to serialise and POST
     * @param reqListener (optional) request listener
     * @param respListener (optional) response listener
     * @return response
     * @throws RestClientException on I/O error
     */
    private TxnResponse postRequest(TxnRequest txnReq, HttpRequestListener reqListener, 
            HttpResponseListener respListener) throws RestClientException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_XML, MediaType.TEXT_XML));
        
        RestTemplate restTemplate = new RestTemplate( new BufferingClientHttpRequestFactory( new SimpleClientHttpRequestFactory() ) );
        restTemplate.setInterceptors( Arrays.<ClientHttpRequestInterceptor> asList(
                new LoggingRequestInterceptor( reqListener, respListener, new PxPostCardMask(),
                        new RegexMask( "(<PostUsername>)(.*)(</PostUsername>)", m -> {
                            return m.find() ? m.group( 1 ) + "********" + m.group( 3 ) : null;
                        } ),
                        new RegexMask( "(<PostPassword>)(.*)(</PostPassword>)", m -> {
                            return m.find() ? m.group( 1 ) + "********" + m.group( 3 ) : null;
                        } ) ) ) );

        HttpEntity<TxnRequest> request = new HttpEntity<>(txnReq, headers);
        ResponseEntity<TxnResponse> response = 
                restTemplate.exchange(getPxPostUrl(), HttpMethod.POST, request, TxnResponse.class );
        return response.getBody();
    }

    /**
     * Returns the PX post username.
     * 
     * @return non-null username
     */
    private String getPxPostUsername() {
        return pxPostUsername;
    }

    /**
     * Returns the PX post password.
     * 
     * @return non-null password
     */
    private String getPxPostPassword() {
        return pxPostPassword;
    }

    /**
     * Returns the URL to post transactions against.
     * 
     * @return non-null URL
     */
    private String getPxPostUrl() {
        return pxPostUrl;
    }
}
