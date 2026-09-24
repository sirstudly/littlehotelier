package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One Cloudbeds Occupancy / Channel Production fact: a booked room on a stay date.
 * Aggregating {@code room_revenue} / {@code rooms_sold} by month and {@code booking_source}
 * should match the Channel Production report.
 */
@Entity
@Table( name = "wp_lh_rpt_channel_stay_night" )
public class ChannelStayNight {

    public static final String ORIGIN_DI_OCCUPANCY = "di_occupancy";

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    @Column( name = "stay_date", nullable = false )
    private java.sql.Date stayDate;

    /** Cloudbeds booking/reservation id; {@code 0} when unknown (month/day aggregates). */
    @Column( name = "reservation_id", nullable = false )
    private long reservationId;

    @Column( name = "reservation_number" )
    private String reservationNumber;

    /** Empty string when unknown (MySQL UNIQUE treats multiple NULLs as distinct). */
    @Column( name = "room_id", nullable = false )
    private String roomId = "";

    @Column( name = "room_number" )
    private String roomNumber;

    @Column( name = "booking_source_raw", nullable = false )
    private String bookingSourceRaw = "";

    @Column( name = "booking_source" )
    private String bookingSource;

    @Column( name = "source_category" )
    private String sourceCategory;

    @Column( name = "room_rate" )
    private BigDecimal roomRate;

    @Column( name = "room_revenue", nullable = false )
    private BigDecimal roomRevenue = BigDecimal.ZERO;

    @Column( name = "rooms_sold", nullable = false )
    private int roomsSold = 1;

    @Column( name = "data_origin", nullable = false )
    private String dataOrigin = ORIGIN_DI_OCCUPANCY;

    @Column( name = "fetched_at", nullable = false )
    private Timestamp fetchedAt;

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public LocalDate getStayDate() {
        return stayDate == null ? null : stayDate.toLocalDate();
    }

    public void setStayDate( LocalDate stayDate ) {
        this.stayDate = stayDate == null ? null : java.sql.Date.valueOf( stayDate );
    }

    public long getReservationId() {
        return reservationId;
    }

    public void setReservationId( long reservationId ) {
        this.reservationId = reservationId;
    }

    public void setReservationId( Long reservationId ) {
        this.reservationId = reservationId == null ? 0L : reservationId;
    }

    public String getReservationNumber() {
        return reservationNumber;
    }

    public void setReservationNumber( String reservationNumber ) {
        this.reservationNumber = reservationNumber;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId( String roomId ) {
        this.roomId = roomId == null ? "" : roomId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber( String roomNumber ) {
        this.roomNumber = roomNumber;
    }

    public String getBookingSourceRaw() {
        return bookingSourceRaw;
    }

    public void setBookingSourceRaw( String bookingSourceRaw ) {
        this.bookingSourceRaw = bookingSourceRaw == null ? "" : bookingSourceRaw;
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource( String bookingSource ) {
        this.bookingSource = bookingSource;
    }

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory( String sourceCategory ) {
        this.sourceCategory = sourceCategory;
    }

    public BigDecimal getRoomRate() {
        return roomRate;
    }

    public void setRoomRate( BigDecimal roomRate ) {
        this.roomRate = roomRate;
    }

    public BigDecimal getRoomRevenue() {
        return roomRevenue;
    }

    public void setRoomRevenue( BigDecimal roomRevenue ) {
        this.roomRevenue = roomRevenue == null ? BigDecimal.ZERO : roomRevenue;
    }

    public int getRoomsSold() {
        return roomsSold;
    }

    public void setRoomsSold( int roomsSold ) {
        this.roomsSold = roomsSold;
    }

    public String getDataOrigin() {
        return dataOrigin;
    }

    public void setDataOrigin( String dataOrigin ) {
        this.dataOrigin = dataOrigin;
    }

    public Timestamp getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt( Timestamp fetchedAt ) {
        this.fetchedAt = fetchedAt;
    }
}
