
package com.macbackpackers.beans;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.apache.commons.lang3.time.FastDateFormat;

@Entity
@Table( name = "wp_hw_booking_dates" )
public class HostelworldBookingDate {

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "dd MMM yyyy" );

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "hw_booking_id", nullable = false )
    private int bookingId;

    @Column( name = "room_type_id" )
    private Integer roomTypeId;

    @Column( name = "room_type" )
    private String roomType;

    @Column( name = "booked_date" )
    private Date bookedDate;

    @Column( name = "persons" )
    private int persons;

    @Column( name = "price" )
    private BigDecimal price;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId( int bookingId ) {
        this.bookingId = bookingId;
    }

    public Integer getRoomTypeId() {
        return roomTypeId;
    }

    public void setRoomTypeId( Integer roomTypeId ) {
        this.roomTypeId = roomTypeId;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType( String roomType ) {
        this.roomType = roomType;
    }

    public Date getBookedDate() {
        return bookedDate;
    }

    public void setBookedDate( Date bookedDate ) {
        this.bookedDate = bookedDate;
    }

    public void setBookedDate( java.util.Date bookedDate ) {
        this.bookedDate = new Date( bookedDate.getTime() );
    }

    public int getPersons() {
        return persons;
    }

    public void setPersons( int persons ) {
        this.persons = persons;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice( BigDecimal price ) {
        this.price = price;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }

}
