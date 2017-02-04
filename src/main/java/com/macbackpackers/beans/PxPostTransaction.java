
package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Corresponds to a record in the table holding all transactions
 * sent to PxPost.
 */
@Entity
@Table( name = "wp_pxpost_transaction" )
public class PxPostTransaction {

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "booking_reference" )
    private String bookingReference;

    @Column( name = "post_date" )
    private Timestamp postDate;

    @Column( name = "masked_card_number" )
    private String maskedCardNumber;

    @Column( name = "payment_amount" )
    private BigDecimal paymentAmount;

    @Column( name = "payment_request_xml" )
    private String paymentRequestXml;

    @Column( name = "payment_response_http_code" )
    private Integer paymentResponseHttpCode;

    @Column( name = "payment_response_xml" )
    private String paymentResponseXml;

    @Column( name = "payment_status_response_xml" )
    private String paymentStatusResponseXml;

    @Column( name = "successful" )
    private Boolean successful;

    @Column( name = "help_text" )
    private String helpText;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference( String bookingReference ) {
        this.bookingReference = bookingReference;
    }

    public Timestamp getPostDate() {
        return postDate;
    }

    public void setPostDate( Timestamp postDate ) {
        this.postDate = postDate;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public void setMaskedCardNumber( String maskedCardNumber ) {
        this.maskedCardNumber = maskedCardNumber;
    }

    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    public void setPaymentAmount( BigDecimal paymentAmount ) {
        this.paymentAmount = paymentAmount;
    }

    public String getPaymentRequestXml() {
        return paymentRequestXml;
    }

    public void setPaymentRequestXml( String paymentRequestXml ) {
        this.paymentRequestXml = paymentRequestXml;
    }

    public Integer getPaymentResponseHttpCode() {
        return paymentResponseHttpCode;
    }

    public void setPaymentResponseHttpCode( Integer paymentResponseHttpCode ) {
        this.paymentResponseHttpCode = paymentResponseHttpCode;
    }

    public String getPaymentResponseXml() {
        return paymentResponseXml;
    }

    public void setPaymentResponseXml( String paymentResponseXml ) {
        this.paymentResponseXml = paymentResponseXml;
    }

    public String getPaymentStatusResponseXml() {
        return paymentStatusResponseXml;
    }

    public void setPaymentStatusResponseXml( String paymentStatusResponseXml ) {
        this.paymentStatusResponseXml = paymentStatusResponseXml;
    }

    public Boolean getSuccessful() {
        return successful;
    }

    public void setSuccessful( Boolean successful ) {
        this.successful = successful;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText( String helpText ) {
        this.helpText = helpText;
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

}
