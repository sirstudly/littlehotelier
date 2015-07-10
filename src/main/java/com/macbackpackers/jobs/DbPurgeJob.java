
package com.macbackpackers.jobs;

import java.util.Calendar;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.dao.WordPressDAO;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.DbPurgeJob" )
public class DbPurgeJob extends AbstractJob {

    @Autowired
    @Transient
    private WordPressDAO dao;

    @Override
    public void processJob() throws Exception {
        Calendar now = Calendar.getInstance();
        now.add( Calendar.DATE, -1 * getDaysToKeep() );
        dao.purgeRecordsOlderThan( now.getTime() );
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
