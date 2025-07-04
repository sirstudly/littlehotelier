package com.macbackpackers.jobs;

import com.macbackpackers.scrapers.HostelworldScraper;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Acknowledges full payment has been taken on the Hostelworld portal.
 * @deprecated this doesn't work anymore; no need for it really
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
