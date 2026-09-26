package com.macbackpackers.ronbot.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cloudbeds reservation list result for a {@link ReservationSearchCriteria}, capped at the
 * requested limit. Rows come straight from the list endpoint (no per-reservation load).
 */
public class ReservationSearchDto {

    private String property;
    private int count;
    /** True when more rows matched than were returned. */
    private boolean truncated;
    private List<Row> reservations;

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated( boolean truncated ) {
        this.truncated = truncated;
    }

    public String getProperty() {
        return property;
    }

    public void setProperty( String property ) {
        this.property = property;
    }

    public int getCount() {
        return count;
    }

    public void setCount( int count ) {
        this.count = count;
    }

    public List<Row> getReservations() {
        return reservations;
    }

    public void setReservations( List<Row> reservations ) {
        this.reservations = reservations;
    }

    public static class Row {
        private String reservationId;
        private String identifier;
        private String thirdPartyIdentifier;
        private String status;
        private String firstName;
        private String lastName;
        private String sourceName;
        private String checkinDate;
        private String checkoutDate;
        private String nights;
        private String bookingDate;
        private BigDecimal grandTotal;
        private BigDecimal balanceDue;

        public String getReservationId() {
            return reservationId;
        }

        public void setReservationId( String reservationId ) {
            this.reservationId = reservationId;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier( String identifier ) {
            this.identifier = identifier;
        }

        public String getThirdPartyIdentifier() {
            return thirdPartyIdentifier;
        }

        public void setThirdPartyIdentifier( String thirdPartyIdentifier ) {
            this.thirdPartyIdentifier = thirdPartyIdentifier;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus( String status ) {
            this.status = status;
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

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName( String sourceName ) {
            this.sourceName = sourceName;
        }

        public String getCheckinDate() {
            return checkinDate;
        }

        public void setCheckinDate( String checkinDate ) {
            this.checkinDate = checkinDate;
        }

        public String getCheckoutDate() {
            return checkoutDate;
        }

        public void setCheckoutDate( String checkoutDate ) {
            this.checkoutDate = checkoutDate;
        }

        public String getNights() {
            return nights;
        }

        public void setNights( String nights ) {
            this.nights = nights;
        }

        public String getBookingDate() {
            return bookingDate;
        }

        public void setBookingDate( String bookingDate ) {
            this.bookingDate = bookingDate;
        }

        public BigDecimal getGrandTotal() {
            return grandTotal;
        }

        public void setGrandTotal( BigDecimal grandTotal ) {
            this.grandTotal = grandTotal;
        }

        public BigDecimal getBalanceDue() {
            return balanceDue;
        }

        public void setBalanceDue( BigDecimal balanceDue ) {
            this.balanceDue = balanceDue;
        }
    }
}
