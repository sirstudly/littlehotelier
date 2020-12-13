
package com.macbackpackers.beans.cloudbeds.responses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

public class Reservation extends CloudbedsJsonResponse {

    private String reservationId;
    private String status;
    private String firstName;
    private String lastName;
    private String email;
    private String countryName;
    private String customerId;
    private BigDecimal grandTotal;
    private String bookingDeposit;
    private BigDecimal balanceDue;
    private String bookingVia;
    private String checkinDate;
    private String checkoutDate;
    private String nights;
    private String cancellationDate;
    private String sourceName;
    private String identifier;
    private String specialRequests;
    private String thirdPartyIdentifier;
    private String bookingDateHotelTime;
    private String usedRoomTypes;
    private Integer kidsNumber;
    private Integer adultsNumber;
    private String channelName;
    private String channelPaymentType;
    private String isHotelCollectBooking;
    private BigDecimal paidValue;
    private List<BookingRoom> bookingRooms;
    private List<BookingNote> notes;
    private String creditCardId;
    private String creditCardType;
    private String creditCardLast4Digits;

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId( String reservationId ) {
        this.reservationId = reservationId;
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

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName( String countryName ) {
        this.countryName = countryName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId( String customerId ) {
        this.customerId = customerId;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal == null ? null : grandTotal.setScale( 2, RoundingMode.HALF_UP );
    }

    public void setGrandTotal( BigDecimal grandTotal ) {
        this.grandTotal = grandTotal;
    }

    public String getBookingDeposit() {
        return bookingDeposit;
    }

    public void setBookingDeposit( String bookingDeposit ) {
        this.bookingDeposit = bookingDeposit;
    }

    public BigDecimal getBalanceDue() {
        return balanceDue == null ? null : balanceDue.setScale( 2, RoundingMode.HALF_UP );
    }

    public void setBalanceDue( BigDecimal balanceDue ) {
        this.balanceDue = balanceDue;
    }

    /**
     * Returns true iff balance due is 0 (or negative)
     * 
     * @return if anything is still left to be paid on booking
     */
    public boolean isPaid() {
        return getBalanceDue().compareTo( BigDecimal.ZERO ) <= 0;
    }

    /**
     * Return true iff this is a non-refundable booking.
     * 
     * @return true if non-refundable; false otherwise
     */
    public boolean isNonRefundable() {
        return "Non-refundable".equalsIgnoreCase( getUsedRoomTypes() )
                || "nonref".equalsIgnoreCase( getUsedRoomTypes() );
    }

    public String getBookingVia() {
        return bookingVia;
    }

    public void setBookingVia( String bookingVia ) {
        this.bookingVia = bookingVia;
    }

    public String getCheckinDate() {
        return checkinDate;
    }

    public Date getCheckinDateAsDate() {
        return parseISODate( getCheckinDate() );        
    }

    public LocalDate getCheckinDateAsLocalDate() {
        return LocalDate.parse( getCheckinDate() );        
    }

    public void setCheckinDate( String checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public String getCheckoutDate() {
        return checkoutDate;
    }

    public Date getCheckoutDateAsDate() {
        return parseISODate( getCheckoutDate() );
    }

    public LocalDate getCheckoutDateAsLocalDate() {
        return LocalDate.parse( getCheckoutDate() );        
    }

    public void setCheckoutDate( String checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public boolean isCheckinDateTodayOrInPast() {
        return LocalDate.parse( getCheckinDate() ).compareTo( LocalDate.now() ) <= 0;
    }

    public boolean isCheckinDateInAugust() {
        return LocalDate.parse( getCheckinDate() ).getMonth() == Month.AUGUST;
    }

    public boolean isCheckoutDateTodayOrTomorrow() {
        LocalDate localCheckoutDate = LocalDate.parse( getCheckoutDate() );
        return localCheckoutDate.compareTo( LocalDate.now() ) == 0 ||
                localCheckoutDate.compareTo( LocalDate.now().plusDays( 1 ) ) == 0;
    }

    public String getNights() {
        return nights;
    }

    public void setNights( String nights ) {
        this.nights = nights;
    }

    public String getCancellationDate() {
        return cancellationDate;
    }

    public Date getCancellationDateAsDate() {
        return parseISODateTime( getCancellationDate() );        
    }

    public void setCancellationDate( String cancellationDate ) {
        this.cancellationDate = cancellationDate;
    }

    private static Date parseISODate( String dateValue ) {
        return dateValue == null ? null
                : Date.from( LocalDate.from( DateTimeFormatter.ISO_LOCAL_DATE.parse( dateValue ) )
                        .atStartOfDay().toInstant( ZoneOffset.UTC ) );
    }

    private static Date parseISODateTime( String dateValue ) {
        return dateValue == null ? null
                : Date.from( LocalDate.from( DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").parse( dateValue ) )
                        .atStartOfDay().toInstant( ZoneOffset.UTC ) );
    }

    /**
     * Returns true iff the cancellation date/time falls within the late-cancellation window from
     * the checkin date (at 3pm).
     * 
     * @param hoursBefore3pmOnCheckinDate number of hours prior to checkin date for
     *            late-cancellation
     * @return true if cancellation date occurs after the late-cancellation window begins
     */
    public boolean isLateCancellation( int hoursBefore3pmOnCheckinDate ) {
        // compare checkin-date (3pm)
        LocalDateTime checkinDateTime = LocalDateTime.parse( getCheckinDate() + "T15:00:00",
                DateTimeFormatter.ISO_LOCAL_DATE_TIME );

        LocalDateTime cancellationDateTime = LocalDateTime.parse(
                getCancellationDate(), DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ) );

        return cancellationDateTime.plusHours( hoursBefore3pmOnCheckinDate )
                .compareTo( checkinDateTime ) > 0;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName( String sourceName ) {
        this.sourceName = sourceName;
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

    public String getSpecialRequests() {
        return specialRequests;
    }

    public void setSpecialRequests( String specialRequests ) {
        this.specialRequests = specialRequests;
    }

    public String getBookingDateHotelTime() {
        return bookingDateHotelTime;
    }

    public void setBookingDateHotelTime( String bookingDateHotelTime ) {
        this.bookingDateHotelTime = bookingDateHotelTime;
    }

    public String getUsedRoomTypes() {
        return usedRoomTypes;
    }

    public void setUsedRoomTypes( String usedRoomTypes ) {
        this.usedRoomTypes = usedRoomTypes;
    }

    public Integer getKidsNumber() {
        return kidsNumber;
    }

    public void setKidsNumber( Integer kidsNumber ) {
        this.kidsNumber = kidsNumber;
    }

    public Integer getAdultsNumber() {
        return adultsNumber;
    }

    public void setAdultsNumber( Integer adultsNumber ) {
        this.adultsNumber = adultsNumber;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName( String channelName ) {
        this.channelName = channelName;
    }

    public String getChannelPaymentType() {
        return channelPaymentType;
    }

    public void setChannelPaymentType( String channelPaymentType ) {
        this.channelPaymentType = channelPaymentType;
    }

    public String getIsHotelCollectBooking() {
        return isHotelCollectBooking;
    }

    public void setIsHotelCollectBooking( String isHotelCollectBooking ) {
        this.isHotelCollectBooking = isHotelCollectBooking;
    }

    public boolean isHotelCollectBooking() {
        return "1".equals( getIsHotelCollectBooking() );
    }

    public boolean isChannelCollectBooking() {
        return "Channel".equals( getChannelPaymentType() ) && false == isHotelCollectBooking();
    }

    public BigDecimal getPaidValue() {
        return paidValue;
    }

    public void setPaidValue( BigDecimal paidValue ) {
        this.paidValue = paidValue;
    }

    public List<BookingRoom> getBookingRooms() {
        return bookingRooms;
    }

    public void setBookingRooms( List<BookingRoom> bookingRooms ) {
        this.bookingRooms = bookingRooms;
    }

    public List<BookingNote> getNotes() {
        return notes;
    }

    public void setNotes( List<BookingNote> notes ) {
        this.notes = notes;
    }

    /**
     * Returns concatenated list of notes.
     * 
     * @return notes (or null if none found)
     */
    public String getNotesAsString() {
        if ( getNotes() == null || getNotes().isEmpty() ) {
            return null; // nothing to show
        }
        StringBuffer result = new StringBuffer();
        for ( BookingNote note : getNotes() ) {
            result.append( note.toString() ).append( "\n" );
        }
        return result.toString();
    }

    public boolean isCardDetailsPresent() {
        return creditCardId != null;
    }

    public String getCreditCardId() {
        return creditCardId;
    }

    public void setCreditCardId( String creditCardId ) {
        this.creditCardId = creditCardId;
    }

    public String getCreditCardType() {
        return creditCardType;
    }

    public void setCreditCardType( String creditCardType ) {
        this.creditCardType = creditCardType;
    }

    public String getCreditCardLast4Digits() {
        return creditCardLast4Digits;
    }

    public void setCreditCardLast4Digits( String creditCardLast4Digits ) {
        this.creditCardLast4Digits = creditCardLast4Digits;
    }

    /**
     * Returns the amount of the first night for this booking.
     * 
     * @param gson Gson encoder/decoder
     * @return non-null amount
     */
    public BigDecimal getRateFirstNight( Gson gson ) {
        return getBookingRooms().stream()
                .flatMap( br -> StreamSupport.stream( gson.fromJson(
                        br.getDetailedRates(), JsonArray.class ).spliterator(), false ) )
                .map( dr -> dr.getAsJsonObject() )
                .filter( dr -> getCheckinDate().equals( dr.get( "date" ).getAsString() ) )
                .map( dr -> dr.get( "rate" ).getAsBigDecimal() )
                .reduce( BigDecimal.ZERO, BigDecimal::add )
                .setScale( 2, RoundingMode.HALF_UP );
    }

    public int getNumberOfGuests() {
        return (getAdultsNumber() == null ? 0 : getAdultsNumber())
                + (getKidsNumber() == null ? 0 : getKidsNumber());

    }

    public boolean isPrepaid() {
        return getSpecialRequests() != null && (
                getSpecialRequests().contains( "You have received a virtual credit card for this reservation" ) ||
                getSpecialRequests().contains( "THIS RESERVATION HAS BEEN PRE-PAID" ) ||
                getSpecialRequests().contains( "The VCC shown is still not active" ));
    }

    /**
     * Searches all notes for the given substring.
     * 
     * @param substr substring to match
     * @return true if at least one note contains substring, false otherwise
     */
    public boolean containsNote( String substr ) {
        return getNotes() != null && getNotes().stream()
                .filter( n -> n.getNotes().contains( substr ) )
                .findFirst()
                .isPresent();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString( this );
    }
}
