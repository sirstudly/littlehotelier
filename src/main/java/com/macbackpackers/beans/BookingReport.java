
package com.macbackpackers.beans;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.exceptions.UnrecoverableFault;

@Entity
@Table( name = "rpt_bookings" )
public class BookingReport {

    private final static Logger LOGGER = LoggerFactory.getLogger( BookingReport.class );

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "dd-MM-yy" );

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "job_id", nullable = false )
    private int jobId;

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

    @Column( name = "paid_value" )
    private BigDecimal paidValue;

    @Column( name = "num_guests" )
    private int numberGuests;

    @Column( name = "booking_reference" )
    private String bookingReference;

    @Column( name = "booking_source" )
    private String bookingSource;

    @Column( name = "country" )
    private String country;

    @Column( name = "booked_date" )
    private java.util.Date bookedDate;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    /**
     * Default constructor.
     */
    public BookingReport() {
        // nothing to do
    }

    /**
     * Initialise from Reservation object.
     * 
     * @param jobId job id
     * @param r object to initialise from
     */
    public BookingReport( int jobId, Reservation r ) {
        setJobId( jobId );
        setReservationId( Integer.parseInt( r.getReservationId() ) );
        setGuestName( r.getFirstName() + " " + r.getLastName() );
        setCheckinDate( LocalDate.parse( r.getCheckinDate() ) );
        setCheckoutDate( LocalDate.parse( r.getCheckoutDate() ) );
        setPaymentTotal( r.getGrandTotal() );
        setPaidValue( r.getPaidValue() );
        setNumberGuests( r.getAdultsNumber() + r.getKidsNumber() );
        setBookingReference( StringUtils.defaultIfBlank( r.getThirdPartyIdentifier(), r.getIdentifier() ) );
        setBookingSource( r.getSourceName() );
        setCountry( r.getCountryName() );
        setBookedDate( LocalDate.parse( r.getBookingDateHotelTime().substring( 0, 10 ) ) );
    }

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
    
    public void setCheckinDate( LocalDate checkinDate ) {
        setCheckinDate( Date.from( checkinDate.atStartOfDay( ZoneId.of( "GMT" ) ).toInstant() ) );
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
    
    public void setCheckoutDate( LocalDate checkoutDate ) {
        setCheckoutDate( Date.from( checkoutDate.atStartOfDay( ZoneId.of( "GMT" ) ).toInstant() ) );
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
                        .replaceAll( ",", "" ) ) ); // strip commas
    }

    public BigDecimal getPaidValue() {
        return paidValue;
    }

    public void setPaidValue( BigDecimal paidValue ) {
        this.paidValue = paidValue;
    }

    public int getNumberGuests() {
        return numberGuests;
    }

    public void setNumberGuests( int numberGuests ) {
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

    public String getCountry() {
        return country;
    }

    public void setCountry( String country ) {
        this.country = country;
    }

    public java.util.Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( java.util.Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public void setBookedDate( LocalDate bookedDate ) {
        this.bookedDate = Date.from( bookedDate.atStartOfDay( ZoneId.of( "GMT" ) ).toInstant() );
    }

    /**
     * Converts a date of format dd-MM-yy to a Date.
     * 
     * @param bookingDate date in format dd-MM-yy
     */
    public void setBookedDate( String bookingDate ) {
        try {
            this.bookedDate = DATE_FORMAT_BOOKED_DATE.parse( bookingDate ); 
        }
        catch ( ParseException ex ) {
            LOGGER.error( "Unable to read date " + bookingDate, ex );
            // swallow error and continue...
        }
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }

    /**
     * Retrieves the corresponding DB table name for this class based on its annotation.
     * 
     * @return table name
     */
    public static String getTableName() {
        for ( Annotation ann : BookingReport.class.getAnnotations() ) {
            if ( ann instanceof Table ) {
                Table tableAnnotation = (Table) ann;
                return tableAnnotation.name();
            }
        }
        throw new UnrecoverableFault( "Table annotation not found" );
    }
    
    /**
     * Returns a comma-delimited list of column names for this object from its annotations.
     * 
     * @return column names
     */
    public static String getColumnNames() {
        ArrayList<String> columns = new ArrayList<>();
        for ( Field field : BookingReport.class.getDeclaredFields() ) {
            for ( Annotation annotation : field.getDeclaredAnnotations() ) {
                if ( annotation instanceof Column && field.getDeclaredAnnotation( Id.class ) == null ) {
                    Column columnAnnotation = (Column) annotation;
                    columns.add( columnAnnotation.name() );
                }
            }
        }
        return StringUtils.join( columns, "," );
    }

    /**
     * Retrieves all fields on this object as a single array in the order specified by its
     * persistence annotations.
     * 
     * @return non-null array
     */
    public Object[] getAsParameters() {
        List<Object> params = new ArrayList<>();
        for ( Field field : getClass().getDeclaredFields() ) {
            for ( Annotation annotation : field.getDeclaredAnnotations() ) {
                // include only those with column annotations which are not the Id
                if ( annotation instanceof Column && field.getDeclaredAnnotation( Id.class ) == null ) {
                    try {
                        params.add( field.get( this ) );
                    }
                    catch ( IllegalArgumentException | IllegalAccessException e ) {
                        throw new UnrecoverableFault( e );
                    }
                }
            }
        }
        return params.toArray();
    }

    @Override
    public String toString() {
        return new ToStringBuilder( this, ToStringStyle.MULTI_LINE_STYLE )
                .append( "id", id )
                .append( "jobId", jobId )
                .append( "reservationId", reservationId )
                .append( "guestName", guestName )
                .append( "checkinDate", checkinDate )
                .append( "checkoutDate", checkoutDate )
                .append( "paymentTotal", paymentTotal )
                .append( "paidValue", paidValue )
                .append( "numberGuests", numberGuests )
                .append( "bookingReference", bookingReference )
                .append( "bookingSource", bookingSource )
                .append( "country", country )
                .append( "bookedDate", bookedDate )
                .append( "createdDate", createdDate )
                .toString();
    }

}
