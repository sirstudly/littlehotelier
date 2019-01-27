
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.PaymentProcessorService;

/**
 * Register a Sagepay transaction by recording against Cloudbeds. 
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ProcessSagepayTransactionJob" )
public class ProcessSagepayTransactionJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.processSagepayTransaction( getTxnId() );
        try {
            dao.updateSagepayTransactionProcessedDate( getTxnId() );
        }
        catch ( Exception ex ) {
            LOGGER.error( "Exception updating processed date", ex );
        }
    }

    public int getTxnId() {
        return Integer.parseInt( getParameter( "txn_id" ) );
    }

    public void setTxnId( int txnId ) {
        setParameter( "txn_id", String.valueOf( txnId ) );
    }

    @Override
    public int getRetryCount() {
        return 1; // only try once - if we error out; manually correct
    }
}
