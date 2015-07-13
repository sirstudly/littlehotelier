package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Calendar;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.hibernate.annotations.Type;

@Entity
@Table( name = "wp_lh_calendar" )
public class Allocation {

    private final static Logger LOGGER = LogManager.getLogger(Allocation.class);

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "dd MMM yyyy" );

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;
    
    @Column( name = "job_id", nullable = false )
    private int jobId;
    
    @Column( name = "room_id" )
    private Integer roomId;

    @Column( name = "room_type_id", nullable = false )
    private int roomTypeId;

    @Column( name = "room", nullable = false )
    private String room;

    @Column( name = "bed_name" )
    private String bedName;

    @Column( name = "reservation_id" )
    private int reservationId;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "checkin_date" )
    private Date checkinDate;

    @Column( name = "checkout_date" )
    private Date checkoutDate;

    @Column( name = "payment_total" )
    private BigDecimal paymentTotal;

    @Column( name = "payment_outstanding" )
    private BigDecimal paymentOutstanding;

    @Column( name = "rate_plan_name" )
    private String ratePlanName;

    @Column( name = "payment_status" )
    private String paymentStatus;

    @Column( name = "num_guests" )
    private int numberGuests;

    @Column( name = "data_href" )
    private String dataHref;

    @Column( name = "lh_status" )
    private String status;

    @Column( name = "booking_reference" )
    private String bookingReference;

    @Column( name = "booking_source" )
    private String bookingSource;

    @Column( name = "booked_date" )
    private java.util.Date bookedDate;

    @Column( name = "eta" )
    private String eta;

    @Column( name = "notes" )
    private String notes;

    @Column( name = "viewed_yn" )
    @Type(type = "yes_no")
    private boolean viewed;

    @Column( name = "created_date" )
    private Timestamp createdDate;

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

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId( int reservationId ) {
        this.reservationId = reservationId;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId( Integer roomId ) {
        this.roomId = roomId;
    }

    
    public int getRoomTypeId() {
        return roomTypeId;
    }

    
    public void setRoomTypeId( int roomTypeId ) {
        this.roomTypeId = roomTypeId;
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

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }

    public Date getCheckinDate() {
        return checkinDate;
    }

    public void setCheckinDate( Date checkinDate ) {
        this.checkinDate = checkinDate;
    }

    public void setCheckinDate( java.util.Date checkinDate ) {
        setCheckinDate( new Date( checkinDate.getTime() ) );
    }

    public Date getCheckoutDate() {
        return checkoutDate;
    }

    public void setCheckoutDate( Date checkoutDate ) {
        this.checkoutDate = checkoutDate;
    }

    public void setCheckoutDate( java.util.Date checkoutDate ) {
        setCheckoutDate( new Date( checkoutDate.getTime() ) );
    }

    public BigDecimal getPaymentTotal() {
        return paymentTotal;
    }

    public void setPaymentTotal( BigDecimal paymentTotal ) {
        this.paymentTotal = paymentTotal;
    }

    public void setPaymentTotal( String paymentTotal ) {
        setPaymentTotal( new BigDecimal( 
                paymentTotal.replaceAll( "\u00A3", "" ) // strip pound
                .replaceAll( ",", "" )) ); // strip commas
    }

    public BigDecimal getPaymentOutstanding() {
        return paymentOutstanding;
    }

    public void setPaymentOutstanding( BigDecimal paymentOutstanding ) {
        this.paymentOutstanding = paymentOutstanding;
    }

    public void setPaymentOutstanding( String paymentOutstanding ) {
        setPaymentOutstanding( new BigDecimal( paymentOutstanding.replaceAll( "\u00A3", "" ) ) ); // strip pound
    }

    public String getRatePlanName() {
        return ratePlanName;
    }

    public void setRatePlanName( String ratePlanName ) {
        this.ratePlanName = ratePlanName;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus( String paymentStatus ) {
        this.paymentStatus = paymentStatus;
    }

    public int getNumberGuests() {
        return numberGuests;
    }

    public void setNumberGuests( int numberGuests ) {
        this.numberGuests = numberGuests;
    }

    public String getDataHref() {
        return dataHref;
    }

    public void setDataHref( String dataHref ) {
        this.dataHref = dataHref;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
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

    public java.util.Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( java.util.Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    /**
     * Converts a date of format Mmm-dd to a Date (in the past). As we're missing the year,
     * we need to take the current year (unless by doing so we get a date in the future).
     * If so, then subtract a year off the date so it's in the past.
     * 
     * @param monthDayString date in format Mmm-dd
     */ 
    public void setBookedDate( String monthDayString ) {
        try {
            Calendar c = Calendar.getInstance();
            int year = c.get( Calendar.YEAR );
            java.util.Date bookedDate = DATE_FORMAT_BOOKED_DATE.parse( monthDayString + " " + year );
            
            // if booked date is now in the future, then we got the wrong year
            // put it back a year
            if( bookedDate.after( c.getTime() ) ) {
                c.setTime( bookedDate );
                c.add( Calendar.YEAR, -1 );
                setBookedDate( c.getTime() );
            } else {
                setBookedDate( bookedDate );
            }
            
        } catch ( ParseException ex ) {
            LOGGER.error( "Unable to read date " + monthDayString, ex );
            // swallow error and continue...
        }
    }

    public String getEta() {
        return eta;
    }

    public void setEta( String eta ) {
        this.eta = eta;
    }

    public boolean isViewed() {
        return viewed;
    }

    public void setViewed( boolean viewed ) {
        this.viewed = viewed;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes( String notes ) {
        this.notes = notes;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder( this, ToStringStyle.MULTI_LINE_STYLE )
        .append("id", id)
        .append("jobId", jobId)
        .append("room", room)
        .append("bedName", bedName)
        .append("reservationId", reservationId )
        .append("guestName", guestName)
        .append("checkinDate", checkinDate)
        .append("checkoutDate", checkoutDate)
        .append("paymentTotal", paymentTotal)
        .append("paymentOutstanding", paymentOutstanding)
        .append("ratePlanName", ratePlanName)
        .append("paymentStatus", paymentStatus)
        .append("numberGuests", numberGuests)
        .append("dataHref", dataHref)
        .append("status", status)
        .append("bookingReference", bookingReference)
        .append("bookingSource", bookingSource)
        .append("bookedDate", bookedDate)
        .append("eta", eta)
        .append("notes", notes)
        .append("viewed", viewed)
        .append("createdDate", createdDate)
        .toString();
    }

}
