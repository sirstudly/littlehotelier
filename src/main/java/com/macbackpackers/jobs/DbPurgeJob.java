
package com.macbackpackers.jobs;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.macbackpackers.dao.WordPressDAO;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.DbPurgeJob" )
public class DbPurgeJob extends AbstractJob {

    @Autowired
    @Transient
    private WordPressDAO dao;

    @Value( "${processor.job.log.localdir}" )
    @Transient
    private String localLogDirectory;

    @Override
    public void processJob() throws Exception {
        Calendar now = Calendar.getInstance();
        now.add( Calendar.DATE, -1 * getDaysToKeep() );
        dao.purgeRecordsOlderThan( now.getTime() );

        // delete any old log files while we're at it
        deleteLogFilesOlderThan( now.getTime() );
    }

    /**
     * Deletes all log files with a modification date older than the given date.
     * 
     * @param specifiedDate keep files newer than this date
     */
    protected void deleteLogFilesOlderThan( Date specifiedDate ) {
        for ( File f : new File( localLogDirectory ).listFiles() ) {
            if ( f.getName().matches( "job-(\\d+)\\.txt" )
                    && f.lastModified() < specifiedDate.getTime() ) {
                LOGGER.info( "Deleting log file " + f.getName() );
                f.delete();
            }
        }
    }

    /**
     * Gets the number of days to keep records for. Anything older will be purged.
     * 
     * @return number of days from today to keep around
     */
    public int getDaysToKeep() {
        return Integer.parseInt( getParameter( "days" ) );
    }

    /**
     * Sets the number of days to keep records for.
     * 
     * @param days number of days from today to keep
     */
    public void setDaysToKeep( int days ) {
        setParameter( "days", String.valueOf( days ) );
    }

}
