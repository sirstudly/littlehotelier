
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.scrapers.BookingComScraper;

/**
 * Job that attempts to verify if we're logged into Booking.com or if not, login.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BDCVerifyLoginJob" )
public class BDCVerifyLoginJob extends AbstractJob {

    @Autowired
    @Transient
    private BookingComScraper scraper;

    @Autowired
    @Transient
    @Qualifier( "webClientForBDC" )
    private WebClient webClient;

    @Override
    public void processJob() throws Exception {
        scraper.doLogin( webClient );
    }

    public int getRetryCount() {
        return 1;
    }
}
