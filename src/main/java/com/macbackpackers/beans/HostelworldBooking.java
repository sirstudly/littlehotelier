
package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.apache.commons.lang3.time.FastDateFormat;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;

@Entity
@Table( name = "wp_hw_booking" )
public class HostelworldBooking {

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "dd MMM yyyy" );

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "guest_email" )
    private String guestEmail;

    @Column( name = "guest_phone" )
    private String guestPhone;

    @Column( name = "guest_nationality" )
    private String guestNationality;

    @Column( name = "payment_total" )
    private BigDecimal paymentTotal;

    @Column( name = "payment_outstanding" )
    private BigDecimal paymentOutstanding;

    @Column( name = "persons" )
    private String persons;

    @Column( name = "booking_reference" )
    private String bookingRef;

    @Column( name = "booking_source" )
    private String bookingSource;

    @Column( name = "booked_date" )
    private java.util.Date bookedDate;

    @Column( name = "arrival_time" )
    private java.util.Date arrivalTime;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    @OneToMany( cascade = jakarta.persistence.CascadeType.ALL, fetch = FetchType.EAGER )
    @JoinColumn( name = "hw_booking_id" )
    @Cascade( { CascadeType.SAVE_UPDATE, CascadeType.DELETE } )
    private List<HostelworldBookingDate> bookedDates = new ArrayList<HostelworldBookingDate>();

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public void setGuestEmail( String guestEmail ) {
        this.guestEmail = guestEmail;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public void setGuestPhone( String guestPhone ) {
        this.guestPhone = guestPhone;
    }

    public String getGuestNationality() {
        return guestNationality;
    }

    public void setGuestNationality( String guestNationality ) {
        this.guestNationality = guestNationality;
    }

    public BigDecimal getPaymentTotal() {
        return paymentTotal;
    }

    public void setPaymentTotal( BigDecimal paymentTotal ) {
        this.paymentTotal = paymentTotal;
    }

    /**
     * Adds the amount to the payment total.
     * 
     * @param amount valid amount to add
     */
    public void addToPaymentTotal( String amount ) {
        if ( getPaymentTotal() == null ) {
            setPaymentTotal( new BigDecimal( 0 ) );
        }
        setPaymentTotal( getPaymentTotal().add( new BigDecimal( amount ) ) );
    }

    public BigDecimal getPaymentOutstanding() {
        return paymentOutstanding;
    }

    public void setPaymentOutstanding( BigDecimal paymentOutstanding ) {
        this.paymentOutstanding = paymentOutstanding;
    }

    public String getPersons() {
        return persons;
    }

    public void setPersons( String persons ) {
        this.persons = persons;
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

    public java.util.Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( java.util.Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public java.util.Date getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime( java.util.Date arrivalTime ) {
        this.arrivalTime = arrivalTime;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }

    public List<HostelworldBookingDate> getBookedDates() {
        return bookedDates;
    }

    public void setBookedDates( List<HostelworldBookingDate> bookedDates ) {
        this.bookedDates = bookedDates;
    }

    public void addBookedDate( HostelworldBookingDate dateToAdd ) {
        this.bookedDates.add( dateToAdd );
    }
}
