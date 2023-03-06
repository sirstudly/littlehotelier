
package com.macbackpackers.jobs;

import com.macbackpackers.services.CloudbedsService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

/**
 * Job that verifies if any new bookings are from guests on the blacklist and emails reception if so.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CheckNewBookingsOnBlacklistJob" )
public class CheckNewBookingsOnBlacklistJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        cloudbedsService.createEmailsForBookingsOnBlacklist( getAllocationScraperJobId() );
    }

    public int getAllocationScraperJobId() {
        return Integer.parseInt( getParameter( "allocation_scraper_job_id" ) );
    }

    public void setAllocationScraperJobId( int jobId ) {
        setParameter( "allocation_scraper_job_id", String.valueOf( jobId ) );
    }
}
