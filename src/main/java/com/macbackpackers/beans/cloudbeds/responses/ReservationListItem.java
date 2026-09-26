
package com.macbackpackers.beans.cloudbeds.responses;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A single row from the {@code mapi/reservation/list} endpoint (one per reservation).
 * Field names match the JSON response exactly (parse with identity naming policy).
 */
public class ReservationListItem {

    private String id;
    private String reservationNumber;
    private String thirdPartyConfirmationNumber;
    private String primaryGuestId;
    private String primaryGuestFirstName;
    private String primaryGuestSurname;
    private String primaryGuestEmail;
    private String bookingDatetime;
    private String bookingDatetimePropertyTimezone;
    private String checkinDate;
    private String checkoutDate;
    private Integer reservationNights;
    private BigDecimal grandTotalAmount;
    private BigDecimal reservationBalanceDueAmount;
    private String reservationSource;
    private String reservationStatus;
    private Boolean isHotelCollectBooking;

    /**
     * Maps this row onto the legacy {@code get_reservations} bean.
     * 
     * @return non-null customer
     */
    public Customer toCustomer() {
        Customer c = new Customer();
        c.setId( id );
        c.setIdentifier( reservationNumber );
        c.setThirdPartyIdentifier( thirdPartyConfirmationNumber );
        c.setCustomerId( primaryGuestId );
        c.setFirstName( StringUtils.defaultString( primaryGuestFirstName ) );
        c.setLastName( StringUtils.defaultString( primaryGuestSurname ) );
        c.setEmail( primaryGuestEmail );
        String bookingDate = StringUtils.defaultIfBlank( bookingDatetimePropertyTimezone, bookingDatetime );
        c.setBookingDate( bookingDate == null ? null : StringUtils.left( bookingDate, 10 ) );
        c.setCheckinDate( checkinDate );
        c.setCheckoutDate( checkoutDate );
        c.setNights( reservationNights == null ? null : reservationNights.toString() );
        c.setGrandTotal( grandTotalAmount == null ? null : grandTotalAmount.toPlainString() );
        c.setBalanceDue( reservationBalanceDueAmount );
        c.setSourceName( reservationSource );
        c.setStatus( reservationStatus );
        c.setIsHotelCollectBooking( Boolean.TRUE.equals( isHotelCollectBooking ) ? "1" : "0" );
        return c;
    }

    public String getId() {
        return id;
    }

    public String getReservationNumber() {
        return reservationNumber;
    }

    public String getThirdPartyConfirmationNumber() {
        return thirdPartyConfirmationNumber;
    }

    public String getReservationStatus() {
        return reservationStatus;
    }

    public String getReservationSource() {
        return reservationSource;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
