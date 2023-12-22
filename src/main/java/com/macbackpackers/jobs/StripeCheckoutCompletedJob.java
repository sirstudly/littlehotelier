
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.PaymentProcessorService;

/**
 * A payment has completed with Stripe. Process and send corresponding email.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.StripeCheckoutCompletedJob" )
public class StripeCheckoutCompletedJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.processStripePayment( getVendorTxCode() );
    }

    public String getVendorTxCode() {
        return getParameter( "vendor_tx_code" );
    }

    public void setVendorTxCode( String vendorTxCode ) {
        setParameter( "vendor_tx_code", vendorTxCode );
    }

    @Override
    public int getRetryCount() {
        return 1; // failed attempts needs manually check
    }
}
