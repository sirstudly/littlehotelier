
package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.PaymentProcessorService;

/**
 * Refreshes the (Stripe) refund record.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.RefreshStripeRefundTransactionJob" )
public class RefreshStripeRefundTransactionJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.refreshStripeRefundStatus( getTxnId() );
    }

    public int getTxnId() {
        return Integer.parseInt( getParameter( "txn_id" ) );
    }

    public void setTxnId( int txnId ) {
        setParameter( "txn_id", String.valueOf( txnId ) );
    }

    @Override
    public int getRetryCount() {
        return 1;
    }
}
