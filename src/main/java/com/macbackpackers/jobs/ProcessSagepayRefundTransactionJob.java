
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.SagepayService;

/**
 * Process a refund and record it against an existing Cloudbeds booking.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ProcessSagepayRefundTransactionJob" )
public class ProcessSagepayRefundTransactionJob extends AbstractJob {

    @Autowired
    @Transient
    private SagepayService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.processSagepayRefund( getId(), getTxnId() );
    }

    public int getTxnId() {
        return Integer.parseInt( getParameter( "txn_id" ) );
    }

    public void setTxnId( int txnId ) {
        setParameter( "txn_id", String.valueOf( txnId ) );
    }
}
