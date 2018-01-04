
package com.macbackpackers.beans;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.time.FastDateFormat;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.macbackpackers.exceptions.UnrecoverableFault;

@Entity
@Table( name = "wp_lh_calendar" )
public class Allocation {

    private final static Logger LOGGER = LoggerFactory.getLogger( Allocation.class );

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "dd-MM-yy" );

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
    @Type( type = "yes_no" )
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
                        .replaceAll( ",", "" ) ) ); // strip commas
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

    /**
     * Retrieves the corresponding DB table name for this class based on its annotation.
     * 
     * @return table name
     */
    public static String getTableName() {
        for ( Annotation ann : Allocation.class.getAnnotations() ) {
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
        for ( Field field : Allocation.class.getDeclaredFields() ) {
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
                        Type typeAnnotation = field.getDeclaredAnnotation( Type.class );
                        if ( typeAnnotation != null && "yes_no".equals( typeAnnotation.type() ) ) {
                            params.add( Boolean.TRUE.equals( field.get( this ) ) ? "Y" : "N" );
                        }
                        else {
                            params.add( field.get( this ) );
                        }
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
                .append( "room", room )
                .append( "bedName", bedName )
                .append( "reservationId", reservationId )
                .append( "guestName", guestName )
                .append( "checkinDate", checkinDate )
                .append( "checkoutDate", checkoutDate )
                .append( "paymentTotal", paymentTotal )
                .append( "paymentOutstanding", paymentOutstanding )
                .append( "ratePlanName", ratePlanName )
                .append( "paymentStatus", paymentStatus )
                .append( "numberGuests", numberGuests )
                .append( "dataHref", dataHref )
                .append( "status", status )
                .append( "bookingReference", bookingReference )
                .append( "bookingSource", bookingSource )
                .append( "bookedDate", bookedDate )
                .append( "eta", eta )
                .append( "notes", notes )
                .append( "viewed", viewed )
                .append( "createdDate", createdDate )
                .toString();
    }

}
