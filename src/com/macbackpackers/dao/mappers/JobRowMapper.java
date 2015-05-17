package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.AbstractJob;

@Component
public class JobRowMapper implements RowMapper<AbstractJob> {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    public static final String QUERY_BY_JOB_ID =
            "SELECT job_id, classname, status, created_date, last_updated_date "
                    + "  FROM wp_lh_jobs WHERE job_id = ?";
    
    @Autowired
    private ApplicationContext appContext;

    public AbstractJob mapRow( ResultSet rs, int row ) throws SQLException {
        
        try {
            LOGGER.info( "Attempting to load Job " + rs.getInt( "job_id" ) );
            AbstractJob j = (AbstractJob) appContext.getBean( Class.forName( rs.getString( "classname" ) ) );
            
            j.setId( rs.getInt( "job_id" ) );
            j.setClassName( rs.getString( "classname" ) );
            j.setStatus( JobStatus.valueOf( rs.getString( "status" ) ) );
            j.setCreatedDate( rs.getTimestamp( "created_date" ) );
            j.setLastUpdatedDate( rs.getTimestamp( "last_updated_date" ) );
            j.setParameters( appContext.getBean( WordPressDAO.class ).getJobParameters( j.getId() ) );
            return j;
        }
        catch( ClassNotFoundException ex ) {
            LOGGER.error( "Unable to instantiate class", ex );
            throw new BeanInstantiationException( Job.class, "Unable to instantiate " + ex.getMessage() );
        }
    }

}
