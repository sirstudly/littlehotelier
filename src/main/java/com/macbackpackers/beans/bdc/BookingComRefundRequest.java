package com.macbackpackers.beans.bdc;

import java.math.BigDecimal;

public class BookingComRefundRequest {
    private String bookingRef; // BDC reference
    private String reservationId; // Cloudbeds
    private String reason;
    private BigDecimal refundAmount;

    public BookingComRefundRequest(String bookingRef, String reason, BigDecimal refundAmount) {
        this.bookingRef = bookingRef;
        this.reason = reason;
        this.refundAmount = refundAmount;
    }

    public BookingComRefundRequest(String bookingRef, String reason, String refundAmount) {
        this(bookingRef, reason, new BigDecimal(refundAmount.replaceAll("£", "")));
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public void setBookingRef(String bookingRef) {
        this.bookingRef = bookingRef;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(BigDecimal refundAmount) {
        this.refundAmount = refundAmount;
    }
}
