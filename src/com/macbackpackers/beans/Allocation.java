package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class Allocation {

    private final Logger LOGGER = LogManager.getLogger(getClass());

    public static final SimpleDateFormat DATE_FORMAT_BOOKED_DATE = new SimpleDateFormat( "dd MMM yyyy" );

    private int id;
    private int jobId;
    private int roomId;
    private String room;
    private String bedName;
    private int reservationId;
    private String guestName;
    private Date checkinDate;
    private Date checkoutDate;
    private BigDecimal paymentTotal;
    private BigDecimal paymentOutstanding;
    private String ratePlanName;
    private String paymentStatus;
    private int numberGuests;
    private String dataHref;
    private String status;
    private String bookingReference;
    private String bookingSource;
    private java.util.Date bookedDate;
    private String eta;
    private String notes;
    private boolean viewed;
    private Date createdDate;

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

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId( int roomId ) {
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

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Date createdDate ) {
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
