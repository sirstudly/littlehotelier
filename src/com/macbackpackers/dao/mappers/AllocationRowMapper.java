package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import com.macbackpackers.beans.Allocation;

public class AllocationRowMapper implements RowMapper<Allocation> {
    
    public static final String QUERY_BY_JOB_ID_RESERVATION_ID =
            "SELECT id, job_id, room, bed_name, reservation_id, guest_name, checkin_date, checkout_date, payment_total,"
            + "     payment_outstanding, rate_plan_name, payment_status, num_guests, data_href, "
            + "     lh_status, booking_reference, booking_source, booked_date, eta, notes, viewed_yn, created_date "
            + "FROM wp_lh_calendar "
            + "WHERE job_id = ? AND reservation_id = ?";

    public Allocation mapRow( ResultSet rs, int row ) throws SQLException {
        Allocation alloc = new Allocation();
        alloc.setId( rs.getInt( "id" ) );
        alloc.setJobId( rs.getInt( "job_id" ) );
        alloc.setRoom( rs.getString( "room" ) );
        alloc.setBedName( rs.getString( "bed_name" ) );
        alloc.setReservationId( rs.getInt( "reservation_id" ) );
        alloc.setGuestName( rs.getString( "guest_name" ) );
        alloc.setCheckinDate( rs.getDate( "checkin_date" ) );
        alloc.setCheckoutDate( rs.getDate( "checkout_date" ) );
        alloc.setPaymentTotal( rs.getBigDecimal( "payment_total" ) );
        alloc.setPaymentOutstanding( rs.getBigDecimal( "payment_outstanding" ) );
        alloc.setRatePlanName( rs.getString( "rate_plan_name" ) );
        alloc.setPaymentStatus( rs.getString( "payment_status" ) );
        alloc.setNumberGuests( rs.getInt( "num_guests" ) );
        alloc.setDataHref( rs.getString( "data_href" ) );
        alloc.setStatus( rs.getString( "lh_status" ) );
        alloc.setBookingReference( rs.getString( "booking_reference" ) );
        alloc.setBookingSource( rs.getString( "booking_source" ) );
        alloc.setBookedDate( rs.getDate( "booked_date" ) );
        alloc.setEta( rs.getString( "eta" ) );
        alloc.setViewed( "Y".equalsIgnoreCase( rs.getString( "viewed_yn" ) ) );
        alloc.setNotes( rs.getString( "notes" ) );
        alloc.setCreatedDate( rs.getDate( "created_date" ) );
        return alloc;
    }
    
}