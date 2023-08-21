package com.macbackpackers.jobs;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.scrapers.HostelworldScraper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

/**
 * Acknowledges full payment has been taken on the Hostelworld portal.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.HostelworldAcknowledgeFullPaymentTakenJob" )
public class HostelworldAcknowledgeFullPaymentTakenJob extends AbstractJob {

    @Autowired
    @Transient
    private HostelworldScraper hostelworldScraper;

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient hwlWebClient;

    @Override
    public void processJob() throws Exception {
        hostelworldScraper.acknowledgeFullPaymentTaken( hwlWebClient, getHostelworldBookingRef() );
    }

    @Override
    public void finalizeJob() {
        hwlWebClient.close();
    }

    public void setHostelworldBookingRef( String bookingRef ) {
        setParameter( "booking_ref", bookingRef );
    }

    public String getHostelworldBookingRef() {
        return getParameter( "booking_ref" );
    }
}
