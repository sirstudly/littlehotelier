package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One Airbnb confirmation-code import row with consolidated CSV amounts and processing status.
 */
@Entity
@Table( name = "wp_lh_airbnb_payment" )
public class AirbnbPayment {

    public static final String STATUS_IMPORTED = "imported";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_PROCESSED = "processed";
    public static final String STATUS_NEEDS_REVIEW = "needs_review";
    public static final String STATUS_ERRORED = "errored";
    public static final String STATUS_IGNORED = "ignored";

    public static final String FIELD_AMOUNT = "amount";
    public static final String FIELD_GROSS = "gross_earnings";

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    @Column( name = "confirmation_code", nullable = false, unique = true )
    private String confirmationCode;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "start_date" )
    private Date startDate;

    @Column( name = "end_date" )
    private Date endDate;

    @Column( name = "booked_date" )
    private Date bookedDate;

    @Column( name = "amount" )
    private BigDecimal amount;

    @Column( name = "service_fee" )
    private BigDecimal serviceFee;

    @Column( name = "gross_earnings" )
    private BigDecimal grossEarnings;

    @Column( name = "net_amount" )
    private BigDecimal netAmount;

    @Column( name = "net_service_fee" )
    private BigDecimal netServiceFee;

    @Column( name = "net_gross_earnings" )
    private BigDecimal netGrossEarnings;

    @Column( name = "has_reservation", nullable = false )
    private String hasReservation = "N";

    @Column( name = "has_resolution_adjustment", nullable = false )
    private String hasResolutionAdjustment = "N";

    @Column( name = "has_cancellation_fee", nullable = false )
    private String hasCancellationFee = "N";

    @Column( name = "has_adjustment", nullable = false )
    private String hasAdjustment = "N";

    @Column( name = "line_types" )
    private String lineTypes;

    @Column( name = "reservation_id" )
    private Long reservationId;

    @Column( name = "matched_yn", nullable = false )
    private String matchedYn = "N";

    @Column( name = "processing_status", nullable = false )
    private String processingStatus = STATUS_IMPORTED;

    @Column( name = "status_reason" )
    private String statusReason;

    @Column( name = "post_payment_field" )
    private String postPaymentField;

    @Column( name = "batch_id" )
    private Long batchId;

    @Column( name = "last_job_id" )
    private Long lastJobId;

    @Column( name = "created_date", insertable = false, updatable = false )
    private Timestamp createdDate;

    @Column( name = "last_updated_date", insertable = false, updatable = false )
    private Timestamp lastUpdatedDate;

    public boolean isYn( String value ) {
        return "Y".equalsIgnoreCase( value );
    }

    public BigDecimal getPostAmount() {
        if ( FIELD_GROSS.equalsIgnoreCase( postPaymentField ) ) {
            return netGrossEarnings != null ? netGrossEarnings : grossEarnings;
        }
        return netAmount != null ? netAmount : amount;
    }

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode( String confirmationCode ) {
        this.confirmationCode = confirmationCode;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate( Date startDate ) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate( Date endDate ) {
        this.endDate = endDate;
    }

    public Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount( BigDecimal amount ) {
        this.amount = amount;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee( BigDecimal serviceFee ) {
        this.serviceFee = serviceFee;
    }

    public BigDecimal getGrossEarnings() {
        return grossEarnings;
    }

    public void setGrossEarnings( BigDecimal grossEarnings ) {
        this.grossEarnings = grossEarnings;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount( BigDecimal netAmount ) {
        this.netAmount = netAmount;
    }

    public BigDecimal getNetServiceFee() {
        return netServiceFee;
    }

    public void setNetServiceFee( BigDecimal netServiceFee ) {
        this.netServiceFee = netServiceFee;
    }

    public BigDecimal getNetGrossEarnings() {
        return netGrossEarnings;
    }

    public void setNetGrossEarnings( BigDecimal netGrossEarnings ) {
        this.netGrossEarnings = netGrossEarnings;
    }

    public String getHasReservation() {
        return hasReservation;
    }

    public void setHasReservation( String hasReservation ) {
        this.hasReservation = hasReservation;
    }

    public String getHasResolutionAdjustment() {
        return hasResolutionAdjustment;
    }

    public void setHasResolutionAdjustment( String hasResolutionAdjustment ) {
        this.hasResolutionAdjustment = hasResolutionAdjustment;
    }

    public String getHasCancellationFee() {
        return hasCancellationFee;
    }

    public void setHasCancellationFee( String hasCancellationFee ) {
        this.hasCancellationFee = hasCancellationFee;
    }

    public String getHasAdjustment() {
        return hasAdjustment;
    }

    public void setHasAdjustment( String hasAdjustment ) {
        this.hasAdjustment = hasAdjustment;
    }

    public String getLineTypes() {
        return lineTypes;
    }

    public void setLineTypes( String lineTypes ) {
        this.lineTypes = lineTypes;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId( Long reservationId ) {
        this.reservationId = reservationId;
    }

    public String getMatchedYn() {
        return matchedYn;
    }

    public void setMatchedYn( String matchedYn ) {
        this.matchedYn = matchedYn;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus( String processingStatus ) {
        this.processingStatus = processingStatus;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason( String statusReason ) {
        this.statusReason = statusReason;
    }

    public String getPostPaymentField() {
        return postPaymentField;
    }

    public void setPostPaymentField( String postPaymentField ) {
        this.postPaymentField = postPaymentField;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId( Long batchId ) {
        this.batchId = batchId;
    }

    public Long getLastJobId() {
        return lastJobId;
    }

    public void setLastJobId( Long lastJobId ) {
        this.lastJobId = lastJobId;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }
}
