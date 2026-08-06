package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table( name = "wp_lh_rpt_mostly_full_dorms" )
public class MostlyFullDormReportEntry {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "job_id" )
    private int jobId;

    @Column( name = "reservation_id" )
    private Long reservationId;

    @Column( name = "guest_name" )
    private String guestNames;

    @Column( name = "booking_reference" )
    private String bookingRef;

    @Column( name = "booking_source" )
    private String bookingSource;

    @Column( name = "checkin_date" )
    private Date checkinDate;

    @Column( name = "checkout_date" )
    private Date checkoutDate;

    @Column( name = "booked_date" )
    private Date bookedDate;

    @Column( name = "payment_outstanding" )
    private BigDecimal paymentOutstanding;

    @Column( name = "data_href" )
    private String dataHref;

    @Column( name = "num_guests" )
    private int numGuests;

    @Column( name = "room_capacity" )
    private Integer roomCapacity;

    @Column( name = "notes" )
    private String notes;

    @Column( name = "viewed_yn" )
    private String viewed;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId( int jobId ) {
        this.jobId = jobId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId( Long reservationId ) {
        this.reservationId = reservationId;
    }

    public String getGuestNames() {
        return guestNames;
    }

    public void setGuestNames( String guestNames ) {
        this.guestNames = guestNames;
    }

    public String getBookingRef() {
        return bookingRef;
    }

    public void setBookingRef( String bookingRef ) {
        this.bookingRef = bookingRef;
    }

    public String getBookingSource() {
        return bookingSource;
    }

    public void setBookingSource( String bookingSource ) {
        this.bookingSource = bookingSource;
    }

    public Date getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( Date checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public Date getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( Date checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public BigDecimal getPaymentOutstanding() {
        return paymentOutstanding;
    }

    public void setPaymentOutstanding( BigDecimal paymentOutstanding ) {
        this.paymentOutstanding = paymentOutstanding;
    }

    public String getDataHref() {
        return dataHref;
    }

    public void setDataHref( String dataHref ) {
        this.dataHref = dataHref;
    }

    public int getNumGuests() {
        return numGuests;
    }

    public void setNumGuests( int numGuests ) {
        this.numGuests = numGuests;
    }

    public Integer getRoomCapacity() {
        return roomCapacity;
    }

    public void setRoomCapacity( Integer roomCapacity ) {
        this.roomCapacity = roomCapacity;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes( String notes ) {
        this.notes = notes;
    }

    public String getViewed() {
        return viewed;
    }

    public void setViewed( String viewed ) {
        this.viewed = viewed;
    }

}
