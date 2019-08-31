
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;

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
    private GenericObjectPool<WebDriver> driverFactory;

    @Override
    public void processJob() throws Exception {
        WebDriver driver = driverFactory.borrowObject();
        try {
            scraper.doLogin( driver, new WebDriverWait( driver, 60 ) );
        }
        finally {
            driverFactory.returnObject( driver );
        }
    }

    public int getRetryCount() {
        return 1;
    }
}
