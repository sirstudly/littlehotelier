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
 * Cold sessions may auto-recover via 2captcha AWS WAF solve + SMS/phone 2FA
 * ({@code hbo_bdc_2facode}). Failure means the solver/2FA pipeline is broken — not
 * necessarily that a manual Chrome profile re-seed is required.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BDCSeleniumVerifyLoginJob" )
public class BDCSeleniumVerifyLoginJob extends AbstractJob {

    /** Allows time for 2captcha (often 30–120s) plus optional 2FA polling. */
    private static final int MAX_WAIT_SECONDS = 180;

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
