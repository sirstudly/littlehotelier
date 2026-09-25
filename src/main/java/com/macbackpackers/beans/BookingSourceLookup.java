package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.apache.commons.lang3.StringUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-booking-source settings valid over a date range; commission = revenue / {@code commission_divisor}.
 * {@code valid_to} is inclusive; null means open-ended.
 */
@Entity
@Table( name = "wp_lh_booking_source_lookup" )
public class BookingSourceLookup {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    /** Cloudbeds reservation source name, e.g. {@code Booking.com}. */
    @Column( name = "source", nullable = false )
    private String source;

    @Column( name = "commission_label" )
    private String commissionLabel;

    @Column( name = "commission_divisor", nullable = false )
    private BigDecimal commissionDivisor;

    @Column( name = "valid_from", nullable = false )
    private LocalDate validFrom;

    @Column( name = "valid_to" )
    private LocalDate validTo;

    public BookingSourceLookup() {
        // default constructor
    }

    public BookingSourceLookup( String source, String commissionLabel, BigDecimal commissionDivisor,
            LocalDate validFrom, LocalDate validTo ) {
        this.source = source;
        this.commissionLabel = commissionLabel;
        this.commissionDivisor = commissionDivisor;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    /** Label used in reports, e.g. {@code BDC}; falls back to the upper-cased source. */
    public String getReportLabel() {
        return StringUtils.isBlank( commissionLabel ) ? source.toUpperCase() : commissionLabel.trim();
    }

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource( String source ) {
        this.source = source;
    }

    public String getCommissionLabel() {
        return commissionLabel;
    }

    public void setCommissionLabel( String commissionLabel ) {
        this.commissionLabel = commissionLabel;
    }

    public BigDecimal getCommissionDivisor() {
        return commissionDivisor;
    }

    public void setCommissionDivisor( BigDecimal commissionDivisor ) {
        this.commissionDivisor = commissionDivisor;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom( LocalDate validFrom ) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo( LocalDate validTo ) {
        this.validTo = validTo;
    }
}
