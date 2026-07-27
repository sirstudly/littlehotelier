package com.macbackpackers.beans.bdc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A Booking.com VCC listed on the "Virtual cards to charge" page / fresa
 * {@code vccs_to_charge} API.
 */
public class BookingComVCCToCharge {

    private String bookingRef; // BDC hres_id
    private LocalDate chargeBeforeDate; // expiry_date — charge before this date
    private BigDecimal amount; // current_amount

    public BookingComVCCToCharge( String bookingRef, LocalDate chargeBeforeDate, BigDecimal amount ) {
        this.bookingRef = bookingRef;
        this.chargeBeforeDate = chargeBeforeDate;
        this.amount = amount;
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public void setBookingRef( String bookingRef ) {
        this.bookingRef = bookingRef;
    }

    public LocalDate getChargeBeforeDate() {
        return chargeBeforeDate;
    }

    public void setChargeBeforeDate( LocalDate chargeBeforeDate ) {
        this.chargeBeforeDate = chargeBeforeDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount( BigDecimal amount ) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "BookingComVCCToCharge{bookingRef='" + bookingRef
                + "', chargeBeforeDate=" + chargeBeforeDate
                + ", amount=" + amount + '}';
    }
}
