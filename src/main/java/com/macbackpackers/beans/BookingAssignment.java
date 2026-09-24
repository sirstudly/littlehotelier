package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.type.YesNoConverter;

/**
 * SCD2 version row for a Cloudbeds calendar assignment used by allocation reports.
 * Current rows have {@code valid_to = null}. Populated primarily from the calendar WebSocket;
 * REST-only columns ({@code visitor_levy_total}, {@code comments}, …) are patched in place.
 */
@Entity
@Table( name = "wp_lh_booking_assignment" )
public class BookingAssignment {

    public static final String SOURCE_GUEST = "guest";
    public static final String SOURCE_CLOSURE = "closure";

    /** Some property DBs have {@code rate_plan_name varchar(255)}; WS {@code detailed_rates} can exceed it. */
    public static final int RATE_PLAN_NAME_MAX_LENGTH = 255;

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    /** Stable key: booking_rooms_id, or {@code res:…} / {@code closure:…} fallback. */
    @Column( name = "assignment_key", nullable = false )
    private String assignmentKey;

    @Column( name = "calendar_event_id" )
    private String calendarEventId;

    @Column( name = "booking_rooms_id" )
    private String bookingRoomsId;

    @Column( name = "reservation_id" )
    private Long reservationId;

    @Column( name = "room_id" )
    private String roomId;

    @Column( name = "room" )
    private String room;

    @Column( name = "bed_name" )
    private String bedName;

    @Column( name = "room_type_id" )
    private Integer roomTypeId;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "email" )
    private String email;

    @Column( name = "checkin_date", nullable = false )
    private java.sql.Date checkinDate;

    @Column( name = "checkout_date", nullable = false )
    private java.sql.Date checkoutDate;

    @Column( name = "bed_status" )
    private String bedStatus;

    @Column( name = "in_house_yn" )
    private String inHouseYn;

    @Column( name = "source", nullable = false )
    private String source;

    @Column( name = "payment_total" )
    private BigDecimal paymentTotal;

    @Column( name = "payment_outstanding" )
    private BigDecimal paymentOutstanding;

    /** REST-enriched (Edinburgh Visitor Levy). */
    @Column( name = "visitor_levy_total" )
    private BigDecimal visitorLevyTotal;

    @Column( name = "rate_plan_name" )
    private String ratePlanName;

    @Column( name = "num_guests" )
    private Integer numberGuests;

    @Column( name = "booking_reference" )
    private String bookingReference;

    @Column( name = "booking_source" )
    private String bookingSource;

    @Column( name = "hotel_collect_yn" )
    @Convert( converter = YesNoConverter.class )
    private Boolean hotelCollect;

    @Column( name = "booked_date" )
    private Date bookedDate;

    @Column( name = "notes" )
    private String notes;

    /** REST-enriched special requests. */
    @Column( name = "comments" )
    private String comments;

    @Column( name = "data_href" )
    private String dataHref;

    @Column( name = "viewed_yn" )
    @Convert( converter = YesNoConverter.class )
    private Boolean viewed;

    @Column( name = "last_rest_fetched_at" )
    private Timestamp lastRestFetchedAt;

    @Column( name = "valid_from", nullable = false )
    private Timestamp validFrom;

    @Column( name = "valid_to" )
    private Timestamp validTo;

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public String getAssignmentKey() {
        return assignmentKey;
    }

    public void setAssignmentKey( String assignmentKey ) {
        this.assignmentKey = assignmentKey;
    }

    public String getCalendarEventId() {
        return calendarEventId;
    }

    public void setCalendarEventId( String calendarEventId ) {
        this.calendarEventId = calendarEventId;
    }

    public String getBookingRoomsId() {
        return bookingRoomsId;
    }

    public void setBookingRoomsId( String bookingRoomsId ) {
        this.bookingRoomsId = bookingRoomsId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId( Long reservationId ) {
        this.reservationId = reservationId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId( String roomId ) {
        this.roomId = roomId;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom( String room ) {
        this.room = room;
    }

    public String getBedName() {
        return bedName;
    }

    public void setBedName( String bedName ) {
        this.bedName = bedName;
    }

    public Integer getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId( Integer roomTypeId ) {
        this.roomTypeId = roomTypeId;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public java.sql.Date getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( java.sql.Date checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public void setCheckinDate( LocalDate checkinDate ) {
        this.checkinDate = checkinDate == null ? null : java.sql.Date.valueOf( checkinDate );
    }

    public java.sql.Date getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( java.sql.Date checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public void setCheckoutDate( LocalDate checkoutDate ) {
        this.checkoutDate = checkoutDate == null ? null : java.sql.Date.valueOf( checkoutDate );
    }

    public String getBedStatus() {
        return bedStatus;
    }

    public void setBedStatus( String bedStatus ) {
        this.bedStatus = bedStatus;
    }

    public String getInHouseYn() {
        return inHouseYn;
    }

    public void setInHouseYn( String inHouseYn ) {
        this.inHouseYn = inHouseYn;
    }

    public void setInHouse( boolean inHouse ) {
        this.inHouseYn = inHouse ? "Y" : "N";
    }

    @Transient
    public boolean isInHouse() {
        return "Y".equalsIgnoreCase( inHouseYn );
    }

    public String getSource() {
        return source;
    }

    public void setSource( String source ) {
        this.source = source;
    }

    public BigDecimal getPaymentTotal() {
        return paymentTotal;
    }

    public void setPaymentTotal( BigDecimal paymentTotal ) {
        this.paymentTotal = paymentTotal;
    }

    public BigDecimal getPaymentOutstanding() {
        return paymentOutstanding;
    }

    public void setPaymentOutstanding( BigDecimal paymentOutstanding ) {
        this.paymentOutstanding = paymentOutstanding;
    }

    public BigDecimal getVisitorLevyTotal() {
        return visitorLevyTotal;
    }

    public void setVisitorLevyTotal( BigDecimal visitorLevyTotal ) {
        this.visitorLevyTotal = visitorLevyTotal;
    }

    public String getRatePlanName() {
        return ratePlanName;
    }

    public void setRatePlanName( String ratePlanName ) {
        this.ratePlanName = ratePlanName != null && ratePlanName.length() > RATE_PLAN_NAME_MAX_LENGTH
                ? ratePlanName.substring( 0, RATE_PLAN_NAME_MAX_LENGTH )
                : ratePlanName;
    }

    public Integer getNumberGuests() {
        return numberGuests;
    }

    public void setNumberGuests( Integer numberGuests ) {
        this.numberGuests = numberGuests;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference( String bookingReference ) {
        this.bookingReference = bookingReference;
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource( String bookingSource ) {
        this.bookingSource = bookingSource;
    }

    public Boolean isHotelCollect() {
        return hotelCollect;
    }

    public void setHotelCollect( Boolean hotelCollect ) {
        this.hotelCollect = hotelCollect;
    }

    public Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public void setBookedDate( LocalDate bookedDate ) {
        this.bookedDate = bookedDate == null ? null
                : java.sql.Date.from( bookedDate.atStartOfDay( ZoneId.of( "GMT" ) ).toInstant() );
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes( String notes ) {
        this.notes = notes;
    }

    public String getComments() {
        return comments;
    }

    public void setComments( String comments ) {
        this.comments = comments;
    }

    public String getDataHref() {
        return dataHref;
    }

    public void setDataHref( String dataHref ) {
        this.dataHref = dataHref;
    }

    public Boolean isViewed() {
        return viewed;
    }

    public void setViewed( Boolean viewed ) {
        this.viewed = viewed;
    }

    public Timestamp getLastRestFetchedAt() {
        return lastRestFetchedAt;
    }

    public void setLastRestFetchedAt( Timestamp lastRestFetchedAt ) {
        this.lastRestFetchedAt = lastRestFetchedAt;
    }

    public Timestamp getValidFrom() {
        return validFrom;
    }

    public void setValidFrom( Timestamp validFrom ) {
        this.validFrom = validFrom;
    }

    public Timestamp getValidTo() {
        return validTo;
    }

    public void setValidTo( Timestamp validTo ) {
        this.validTo = validTo;
    }

    @Transient
    public boolean isCurrent() {
        return validTo == null;
    }

    @Transient
    public boolean isClosure() {
        return SOURCE_CLOSURE.equalsIgnoreCase( source );
    }

    @Transient
    public boolean needsRestEnrich() {
        return lastRestFetchedAt == null && false == isClosure()
                && reservationId != null && reservationId > 0;
    }

    @Transient
    public LocalDate getCheckinLocalDate() {
        return checkinDate == null ? null : checkinDate.toLocalDate();
    }

    @Transient
    public LocalDate getCheckoutLocalDate() {
        return checkoutDate == null ? null : checkoutDate.toLocalDate();
    }

    /**
     * Copies REST-only enrich columns from a previous current version onto this WS-derived row.
     */
    public void copyEnrichFrom( BookingAssignment previous ) {
        if ( previous == null ) {
            return;
        }
        if ( visitorLevyTotal == null ) {
            visitorLevyTotal = previous.visitorLevyTotal;
        }
        if ( comments == null ) {
            comments = previous.comments;
        }
        if ( lastRestFetchedAt == null ) {
            lastRestFetchedAt = previous.lastRestFetchedAt;
        }
        // Prefer REST rate plan when WS only has detailed_rates stub
        if ( StringUtilsBlank( ratePlanName ) && false == StringUtilsBlank( previous.ratePlanName ) ) {
            ratePlanName = previous.ratePlanName;
        }
        if ( viewed == null ) {
            viewed = previous.viewed;
        }
    }

    /**
     * When REST omit {@code calendarEventId}, keep the WS id from the previous current so
     * null↔event-id flip-flops do not create spurious SCD2 versions.
     */
    public void preserveCalendarEventIdFrom( BookingAssignment previous ) {
        if ( previous == null ) {
            return;
        }
        if ( StringUtilsBlank( calendarEventId ) && false == StringUtilsBlank( previous.calendarEventId ) ) {
            calendarEventId = previous.calendarEventId;
        }
    }

    /**
     * Returns true when WS/report-relevant fields differ from {@code other} (triggers a new version).
     * REST-only enrich columns are intentionally excluded.
     */
    public boolean differsForVersioning( BookingAssignment other ) {
        if ( other == null ) {
            return true;
        }
        return false == eq( roomId, other.roomId )
                || false == eq( checkinDate, other.checkinDate )
                || false == eq( checkoutDate, other.checkoutDate )
                || false == eq( bedStatus, other.bedStatus )
                || false == eq( inHouseYn, other.inHouseYn )
                || false == eq( guestName, other.guestName )
                || false == eq( email, other.email )
                || false == eq( source, other.source )
                || false == eq( calendarEventId, other.calendarEventId )
                || false == eq( paymentTotal, other.paymentTotal )
                || false == eq( paymentOutstanding, other.paymentOutstanding )
                || false == eq( numberGuests, other.numberGuests )
                || false == eq( bookingReference, other.bookingReference )
                || false == eq( bookingSource, other.bookingSource )
                || false == eq( hotelCollect, other.hotelCollect )
                || false == eq( bookedDate, other.bookedDate )
                || false == eq( notes, other.notes )
                || false == eq( ratePlanName, other.ratePlanName )
                || false == eq( room, other.room )
                || false == eq( bedName, other.bedName )
                || false == eq( roomTypeId, other.roomTypeId );
    }

    private static boolean StringUtilsBlank( String s ) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean eq( Object a, Object b ) {
        if ( a == null ) {
            return b == null;
        }
        if ( a instanceof BigDecimal && b instanceof BigDecimal ) {
            return ( (BigDecimal) a ).compareTo( (BigDecimal) b ) == 0;
        }
        return a.equals( b );
    }
}
