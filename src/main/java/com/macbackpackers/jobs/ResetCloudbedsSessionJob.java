
package com.macbackpackers.jobs;

import com.macbackpackers.scrapers.ChromeScraper;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

/**
 * Job that attempts to login to Cloudbeds and saves the session once successful.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ResetCloudbedsSessionJob" )
public class ResetCloudbedsSessionJob extends AbstractJob {

    @Autowired
    @Transient
    private ChromeScraper scraper;

    @Override
    public void processJob() throws Exception {
        scraper.loginToCloudbedsAndSaveSession();
    }

    @Override
    public int getRetryCount() {
        return 1;
    }

}
