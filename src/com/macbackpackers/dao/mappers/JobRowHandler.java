package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.dbutils.ResultSetHandler;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

public class JobRowHandler implements ResultSetHandler<Job> {

    @Override
    public Job handle( ResultSet rs ) throws SQLException {
        if ( rs.next() ) {
            Job j = new Job();
            j.setId( rs.getInt( "job_id" ) );
            j.setName( rs.getString( "name" ) );
            j.setStatus( JobStatus.valueOf( rs.getString( "status" ) ) );
            j.setCreatedDate( rs.getTimestamp( "created_date" ) );
            j.setLastUpdatedDate( rs.getTimestamp( "last_updated_date" ) );
            return j;
        }
        throw new IllegalStateException( "No rows found" );
    }

}
