
package com.macbackpackers.beans;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Corresponds to a record in the table holding all transactions sent to Sagepay.
 */
@Entity
@Table( name = "v_wp_sagepay_tx_auth" )
public class SagepayTransaction {

    @Id
    @Column( name = "id" )
    private int id;

    @Column( name = "reservation_id" )
    private String reservationId;

    @Column( name = "booking_reference" )
    private String bookingReference;

    @Column( name = "first_name" )
    private String firstName;

    @Column( name = "last_name" )
    private String lastName;

    @Column( name = "email" )
    private String email;

    @Column( name = "payment_amount" )
    private BigDecimal paymentAmount;

    @Column( name = "vendor_tx_code" )
    private String vendorTxCode;

    @Column( name = "auth_status" )
    private String authStatus;

    @Column( name = "auth_status_detail" )
    private String authStatusDetail;

    @Column( name = "tx_auth_no" )
    private String vpsAuthCode; // sent to bank to identify txn during settlement

    @Column( name = "bank_auth_code" )
    private String bankAuthCode;

    @Column( name = "bank_decline_code" )
    private String bankDeclineCode;

    @Column( name = "card_type" )
    private String cardType;

    @Column( name = "last_4_digits" )
    private String lastFourDigits;

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

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName( String firstName ) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName( String lastName ) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount( BigDecimal paymentAmount ) {
        this.paymentAmount = paymentAmount;
    }

    public String getVendorTxCode() {
        return vendorTxCode;
    }

    public void setVendorTxCode( String vendorTxCode ) {
        this.vendorTxCode = vendorTxCode;
    }

    public String getAuthStatus() {
        return authStatus;
    }

    public void setAuthStatus( String authStatus ) {
        this.authStatus = authStatus;
    }

    public String getAuthStatusDetail() {
        return authStatusDetail;
    }

    public void setAuthStatusDetail( String authStatusDetail ) {
        this.authStatusDetail = authStatusDetail;
    }

    public String getVpsAuthCode() {
        return vpsAuthCode;
    }

    public void setVpsAuthCode( String vpsAuthCode ) {
        this.vpsAuthCode = vpsAuthCode;
    }

    public String getBankAuthCode() {
        return bankAuthCode;
    }

    public void setBankAuthCode( String bankAuthCode ) {
        this.bankAuthCode = bankAuthCode;
    }

    public String getBankDeclineCode() {
        return bankDeclineCode;
    }

    public void setBankDeclineCode( String bankDeclineCode ) {
        this.bankDeclineCode = bankDeclineCode;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType( String cardType ) {
        this.cardType = cardType;
    }

    /**
     * Returns the cloudbeds card type based on the sagepay card type.
     * 
     * @return card type
     */
    public String getMappedCardType() {
        switch ( getCardType() ) {
            case "VISA":
                return "visa";
            case "MC":
                return "mastercard";
            default:
                return "other";
        }
    }

    public String getLastFourDigits() {
        return lastFourDigits;
    }

    public void setLastFourDigits( String lastFourDigits ) {
        this.lastFourDigits = lastFourDigits;
    }

}
