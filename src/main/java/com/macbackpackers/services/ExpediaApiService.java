
package com.macbackpackers.services;

import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
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
import com.macbackpackers.beans.DepositPayment;
import com.macbackpackers.beans.expedia.Hotel;
import com.macbackpackers.beans.expedia.request.Authentication;
import com.macbackpackers.beans.expedia.request.Booking;
import com.macbackpackers.beans.expedia.request.BookingRetrievalRQ;
import com.macbackpackers.beans.expedia.request.ParamSet;
import com.macbackpackers.beans.expedia.response.BookingRetrievalRS;
import com.macbackpackers.beans.expedia.response.PaymentCard;
import com.macbackpackers.beans.expedia.response.RoomStay;

@Service
public class ExpediaApiService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** The service URL */
    @Value( "${expedia.url}" )
    private String postUrl;

    @Value( "${expedia.username}" )
    private String postUsername;

    @Value( "${expedia.password}" )
    private String postPassword;
    
    @Value( "${expedia.hotelid}" )
    private String hotelId;
    
    /**
     * Constructs the POST request object for a given expedia booking ref.
     * 
     * @param bookingRef expedia booking id (numeric)
     * @return non-null request
     */
    private BookingRetrievalRQ constructBookingRetrievalRequest( String bookingRef ) {
        BookingRetrievalRQ req = new BookingRetrievalRQ();
        req.setAuthentication( new Authentication() );
        req.getAuthentication().setUsername( getPostUsername() );
        req.getAuthentication().setPassword( getPostPassword() );
        req.setHotel( new Hotel( getHotelId() ) );
        req.setParamSet( new ParamSet() );
        req.getParamSet().setBooking( new Booking( bookingRef ) );
        return req;
    }

    /**
     * Returns the card details for the given expedia booking.
     * 
     * @param bookingRef expedia booking, e.g. EXP-123456789
     * @return deposit payment and matched card details for booking
     * @throws IOException on lookup error
     */
    public DepositPayment returnCardDetailsForBooking( String bookingRef ) throws IOException {
        String expediaId = bookingRef.startsWith( "EXP-" ) ? bookingRef.substring( 4 ) : bookingRef;
        
        CaptureHttpRequest paymentRequest = new CaptureHttpRequest();
        CaptureHttpResponse paymentResponse = new CaptureHttpResponse();
        BookingRetrievalRS bookingResponse = postRequest(
                constructBookingRetrievalRequest( expediaId ), paymentRequest, paymentResponse );
        
        CardDetails cardDetails = new CardDetails();
        if ( bookingResponse.getError() != null ) {
            LOGGER.error( "Error code: " + bookingResponse.getError().getCode() );
            LOGGER.error( bookingResponse.getError().getText() );
            throw new IllegalStateException( "Error found in response" );
        }
        else if ( bookingResponse.getBookings() == null
                || bookingResponse.getBookings().getBookingList() == null ) {
            throw new IllegalStateException( "Missing booking in response" );
        }
        else if ( bookingResponse.getBookings().getBookingList().size() != 1 ) {
            throw new IncorrectResultSizeDataAccessException( "Expected 1 booking but found " + bookingResponse.getBookings().getBookingList().size(), 1 );
        }

        RoomStay roomStay = bookingResponse.getBookings().getBookingList().get( 0 ).getRoomStay();
        if ( roomStay == null ) {
            throw new IllegalStateException( "Missing RoomStay in response" );
        }
        else if ( roomStay.getPaymentCard() == null ) {
            throw new IllegalStateException( "Missing PaymentCard in response" );
        }
        else if ( roomStay.getPaymentCard().getCardHolder() == null ) {
            throw new IllegalStateException( "Missing CardHolder in response" );
        }

        PaymentCard paymentCard = roomStay.getPaymentCard();
        cardDetails.setName( paymentCard.getCardHolder().getName() );
        cardDetails.setCardNumber( paymentCard.getCardNumber() );
        cardDetails.setCvv( paymentCard.getSeriesCode() );
        cardDetails.setExpiry( paymentCard.getExpireDate() );
        return new DepositPayment( roomStay.getRateForFirstNight(), cardDetails );
    }

    /**
     * Posts the given request to the expedia booking retrieval API. Returns the response.
     * 
     * @param bookingReq request to serialise and POST
     * @param reqListener (optional) request listener
     * @param respListener (optional) response listener
     * @return response
     * @throws RestClientException on I/O error
     */
    private BookingRetrievalRS postRequest(BookingRetrievalRQ bookingReq, HttpRequestListener reqListener, 
            HttpResponseListener respListener) throws RestClientException {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_XML, MediaType.TEXT_XML));
        
        RestTemplate restTemplate = new RestTemplate( new BufferingClientHttpRequestFactory( new SimpleClientHttpRequestFactory() ) );
        restTemplate.setInterceptors( Arrays.<ClientHttpRequestInterceptor> asList( 
                new LoggingRequestInterceptor( reqListener, respListener, new ExpediaCardMask() ) ) );
        
        HttpEntity<BookingRetrievalRQ> request = new HttpEntity<>(bookingReq, headers);
        ResponseEntity<BookingRetrievalRS> response = 
                restTemplate.exchange(getPostUrl(), HttpMethod.POST, request, BookingRetrievalRS.class );
        return response.getBody();
    }

    
    public String getPostUrl() {
        return postUrl;
    }

    
    public String getPostUsername() {
        return postUsername;
    }

    
    public String getPostPassword() {
        return postPassword;
    }

    
    public String getHotelId() {
        return hotelId;
    }

}
