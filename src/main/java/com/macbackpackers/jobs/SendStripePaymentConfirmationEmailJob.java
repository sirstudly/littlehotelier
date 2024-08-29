
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that sends email to guest after a successful payment was done.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.SendStripePaymentConfirmationEmailJob" )
public class SendStripePaymentConfirmationEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.sendStripePaymentConfirmationGmail( webClient, getVendorTxCode() );
        }
    }

    public String getVendorTxCode() {
        return getParameter( "vendor_tx_code" );
    }

    public void setVendorTxCode( String vendorTxCode ) {
        setParameter( "vendor_tx_code", vendorTxCode );
    }

    @Override
    public int getRetryCount() {
        return 2; // limit failed email attempts
    }
}
