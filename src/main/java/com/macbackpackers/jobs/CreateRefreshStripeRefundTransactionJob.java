
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.services.PaymentProcessorService;

/**
 * Refreshes the (Stripe) refund records for all pending transactions.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateRefreshStripeRefundTransactionJob" )
public class CreateRefreshStripeRefundTransactionJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        dao.fetchStripeRefundsAtStatus( "pending" ).stream()
                .forEach( r -> {
                    RefreshStripeRefundTransactionJob j = new RefreshStripeRefundTransactionJob();
                    j.setStatus( JobStatus.submitted );
                    j.setTxnId( r.getId() );
                    dao.insertJob( j );
                } );
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
