
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.PaymentProcessorService;

/**
 * Process a refund and record it against an existing Cloudbeds booking.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ProcessStripeRefundTransactionJob" )
public class ProcessStripeRefundTransactionJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.processStripeRefund( getId(), getTxnId() );
    }

    public int getTxnId() {
        return Integer.parseInt( getParameter( "txn_id" ) );
    }

    public void setTxnId( int txnId ) {
        setParameter( "txn_id", String.valueOf( txnId ) );
    }
}
