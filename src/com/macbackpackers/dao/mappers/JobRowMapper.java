package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;

public class JobRowMapper implements RowMapper<Job> {

    public static final String QUERY_BY_JOB_ID =
            "SELECT job_id, name, status, created_date, last_updated_date "
                    + "  FROM wp_lh_jobs WHERE job_id = ?";

    public Job mapRow( ResultSet rs, int row ) throws SQLException {
        Job j = new Job();
        j.setId( rs.getInt( "job_id" ) );
        j.setName( rs.getString( "name" ) );
        j.setStatus( JobStatus.valueOf( rs.getString( "status" ) ) );
        j.setCreatedDate( rs.getTimestamp( "created_date" ) );
        j.setLastUpdatedDate( rs.getTimestamp( "last_updated_date" ) );
        return j;
    }

}
