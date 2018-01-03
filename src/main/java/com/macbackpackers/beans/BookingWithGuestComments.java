
package com.macbackpackers.beans;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A search result entity for retrieving bookings with their associated guest comments.
 *
 */
public class BookingWithGuestComments {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private String bookingReference;
    private Date checkinDate; // the date of checkin
    private Date bookedDate; // the date the reservation was created
    private String guestComment;

    // the matching pattern used for retrieving the earliest charge date from the guest comment
    private static final Pattern MATCH_EARLIEST_CHARGE_DATE = 
            Pattern.compile( "You may charge it as of ([0-9]{4}\\-[0-9]{2}\\-[0-9]{2})." );
    private static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    public BookingWithGuestComments( String bookingRef, Date checkinDate, Date bookedDate, String guestComment ) {
        this.bookingReference = bookingRef;
        this.checkinDate = checkinDate;
        this.bookedDate = bookedDate;
        this.guestComment = guestComment;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference( String bookingReference ) {
        this.bookingReference = bookingReference;
    }

    public Date getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( Date checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public String getGuestComment() {
        return guestComment;
    }

    public void setGuestComment( String guestComment ) {
        this.guestComment = guestComment;
    }

    /**
     * Returns the earliest charge date for the virtual CC within the guest comments. This assumes
     * the guest comments are populated with <em>only</em> BDC bookings with virtual CCs, otherwise
     * this throws a IllegalStateException.
     * 
     * @return non-null chargeable date
     */
    public Date getEarliestChargeDate() {
        try {
            Matcher m = MATCH_EARLIEST_CHARGE_DATE.matcher( getGuestComment() );
            if ( m.find() ) {
                return DATE_FORMAT_YYYY_MM_DD.parse( m.group( 1 ) );
            }
            LOGGER.info( getBookingReference() + ": no chargeable date found; defaulting to checkin date" );
            return getCheckinDate();
        }
        catch ( ParseException e ) {
            // this shouldn't ever happen if we've setup the object correctly
            throw new IllegalStateException( e );
        }
    }

    /**
     * Convenience method for checking whether the earliest charge date has already passed.
     * 
     * @return true if charge date is before now, false otherwise.
     */
    public boolean isChargeableDateInPast() {
        return getEarliestChargeDate().before( new Date() );
    }

    /**
     * Pretty much as it says.
     * 
     * @return true if so, false if not
     */
    public boolean isCheckinDateTodayOrTomorrow() {
        Calendar c = Calendar.getInstance();
        c.add( Calendar.DATE, -1 ); // checkin date is at midnight
        Date today = c.getTime();
        c.add( Calendar.DATE, 1 ); // before tomorrow at 23:59:59
        Date tomorrow = c.getTime();
        return getCheckinDate().after( today ) && getCheckinDate().before( tomorrow );
    }
}
