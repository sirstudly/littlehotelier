
package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table( name = "wp_lh_rpt_unpaid_deposit" )
public class UnpaidDepositReportEntry {

    // used for extracting the reservation ID from the dataHref field
    private static final Pattern DATA_HREF_PATTERN = Pattern.compile( "reservations/(\\d*)/edit" );
    
    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "job_id" )
    private int jobId;

    @Column( name = "guest_name" )
    private String guestNames;

    @Column( name = "checkin_date" )
    private Date checkinDate;

    @Column( name = "checkout_date" )
    private Date checkoutDate;

    @Column( name = "data_href" )
    private String dataHref;

    @Column( name = "booking_reference" )
    private String bookingRef;

    @Column( name = "booking_source" )
    private String bookingSource;

    @Column( name = "booked_date" )
    private Date bookedDate;

    @Column( name = "payment_total" )
    private BigDecimal paymentTotal;

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

    public String getGuestNames() {
        return guestNames;
    }

    public void setGuestNames( String guestNames ) {
        this.guestNames = guestNames;
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

    public String getDataHref() {
        return dataHref;
    }

    public void setDataHref( String dataHref ) {
        this.dataHref = dataHref;
    }
    
    /**
     * Convenience method for extracting the reservation id from {@code dataHref}.
     * 
     * @return reservation ID
     * @throws NumberFormatException if reservation id could not be derived
     */
    public int getReservationId() throws NumberFormatException {
        Matcher m = DATA_HREF_PATTERN.matcher( getDataHref() );
        if ( m.find() ) {
            return Integer.parseInt( m.group( 1 ) );
        }
        throw new NumberFormatException( "Unable to find reservation id from data href " + getDataHref() );
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

    public Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public BigDecimal getPaymentTotal() {
        return paymentTotal;
    }

    public void setPaymentTotal( BigDecimal paymentTotal ) {
        this.paymentTotal = paymentTotal;
    }

}
