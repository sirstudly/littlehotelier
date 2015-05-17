package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import com.macbackpackers.beans.Allocation;

public class AllocationRowMapper implements RowMapper<Allocation> {

    public Allocation mapRow( ResultSet rs, int row ) throws SQLException {
        Allocation alloc = new Allocation();
        alloc.setId( rs.getInt( "id" ) );
        alloc.setJobId( rs.getInt( "job_id" ) );
        alloc.setRoom( rs.getString( "room" ) );
        alloc.setBedName( rs.getString( "bed_name" ) );
        alloc.setCheckinDate( rs.getDate( "checkin_date" ) );
        alloc.setCheckoutDate( rs.getDate( "checkout_date" ) );
        alloc.setPaymentTotal( rs.getBigDecimal( "payment_total" ) );
        alloc.setPaymentOutstanding( rs.getBigDecimal( "payment_outstanding" ) );
        alloc.setRatePlanName( rs.getString( "rate_plan_name" ) );
        alloc.setPaymentStatus( rs.getString( "payment_status" ) );
        alloc.setNumberGuests( rs.getInt( "num_guests" ) );
        alloc.setDataHref( rs.getString( "data_href" ) );
        alloc.setNotes( rs.getString( "notes" ) );
        alloc.setCreatedDate( rs.getDate( "created_date" ) );
        return alloc;
    }

}