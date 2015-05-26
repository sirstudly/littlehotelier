package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import com.macbackpackers.beans.BedChange;
import com.macbackpackers.beans.BedSheetEntry;

public class BedSheetRowMapper implements RowMapper<BedSheetEntry> {

    public static final String QUERY_BY_JOB_ID_CHECKOUT_DATE =
            "SELECT * from wp_bedsheets WHERE job_id = ? AND checkout_date = ?";

    public BedSheetEntry mapRow( ResultSet rs, int row ) throws SQLException {
        BedSheetEntry bs = new BedSheetEntry();
        bs.setId( rs.getInt( "id" ) );
        bs.setJobId( rs.getInt( "job_id" ) );
        bs.setRoom( rs.getString( "room" ) );
        bs.setBedName( rs.getString( "bed_name" ) );
        bs.setCheckoutDate( rs.getDate( "checkout_date" ) );
        bs.setStatus( BedChange.fromValue( rs.getString( "change_status" ) ) );
        return bs;
    }

}