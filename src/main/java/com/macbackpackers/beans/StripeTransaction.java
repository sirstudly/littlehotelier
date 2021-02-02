
package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

@Entity
@Table( name = "wp_stripe_transaction" )
public class StripeTransaction {

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "reservation_id" )
    private String reservationId;

    @Column( name = "booking_reference" )
    private String bookingReference;

    @Column( name = "invoice_id" )
    private Integer invoiceId;

    @Column( name = "email", nullable = false )
    private String email;

    @Column( name = "session_id" )
    private String sessionId;

    @Column( name = "checkout_session" )
    private String checkoutSession;

    @Column( name = "vendor_tx_code", nullable = false )
    private String vendorTxCode;

    @Column( name = "payment_amount", nullable = false )
    private BigDecimal paymentAmount;

    @Column( name = "payment_status" )
    private String paymentStatus;

    @Column( name = "auth_details" )
    private String authDetails;

    @Column( name = "card_type" )
    private String cardType;

    @Column( name = "last_4_digits" )
    private String last4Digits;

    @Column( name = "processed_date" )
    private Timestamp processedDate;

    @Column( name = "created_date", nullable = false )
    private Timestamp createdDate;

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId( String reservationId ) {
        this.reservationId = reservationId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference( String bookingReference ) {
        this.bookingReference = bookingReference;
    }

    public Integer getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId( Integer invoiceId ) {
        this.invoiceId = invoiceId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId( String sessionId ) {
        this.sessionId = sessionId;
    }

    public String getCheckoutSession() {
        return checkoutSession;
    }

    public void setCheckoutSession( String checkoutSession ) {
        this.checkoutSession = checkoutSession;
    }

    public String getVendorTxCode() {
        return vendorTxCode;
    }

    public void setVendorTxCode( String vendorTxCode ) {
        this.vendorTxCode = vendorTxCode;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount( BigDecimal paymentAmount ) {
        this.paymentAmount = paymentAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus( String paymentStatus ) {
        this.paymentStatus = paymentStatus;
    }

    public String getAuthDetails() {
        return authDetails;
    }

    public void setAuthDetails( String authDetails ) {
        this.authDetails = authDetails;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType( String cardType ) {
        this.cardType = cardType;
    }

    public String getLast4Digits() {
        return last4Digits;
    }

    public void setLast4Digits( String last4Digits ) {
        this.last4Digits = last4Digits;
    }

    public Timestamp getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate( Timestamp processedDate ) {
        this.processedDate = processedDate;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }

    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate( Timestamp lastUpdatedDate ) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    /**
     * Looks up the payment intent for this transaction. Stripe.apiKey needs to be set prior to
     * calling this.
     * 
     * @param gson
     * @return
     * @throws StripeException
     */
    public PaymentIntent getPaymentIntent( Gson gson ) throws StripeException {
        JsonElement pi = gson.fromJson( getCheckoutSession(), JsonElement.class ).getAsJsonObject().get( "payment_intent" );
        if ( pi == null ) {
            throw new MissingUserDataException( "Missing payment intent for vendor tx code: " + getVendorTxCode() + ". Unable to proceed." );
        }
        return PaymentIntent.retrieve( pi.getAsString() );
    }
}
