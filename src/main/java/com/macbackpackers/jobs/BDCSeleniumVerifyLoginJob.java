package com.macbackpackers.jobs;

import com.macbackpackers.scrapers.BookingComSeleniumScraper;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

/**
 * Keeps the Booking.com Chrome profile warm by opening groups home via Selenium.
 * Fails loudly if the session is cold (sign-in / captcha) so the profile can be re-seeded.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BDCSeleniumVerifyLoginJob" )
public class BDCSeleniumVerifyLoginJob extends AbstractJob {

    private static final int MAX_WAIT_SECONDS = 60;

    @Autowired
    @Transient
    private BookingComSeleniumScraper scraper;

    @Autowired
    @Transient
    private GenericObjectPool<WebDriver> driverFactory;

    @Override
    public void processJob() throws Exception {
        WebDriver driver = driverFactory.borrowObject();
        try {
            WebDriverWait wait = new WebDriverWait( driver, Duration.ofSeconds( MAX_WAIT_SECONDS ) );
            scraper.doLogin( driver, wait );
        }
        finally {
            driverFactory.returnObject( driver );
        }
    }

    @Override
    public int getRetryCount() {
        return 1;
    }
}
