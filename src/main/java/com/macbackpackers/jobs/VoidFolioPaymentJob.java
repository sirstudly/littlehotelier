package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.scrapers.CloudbedsScraper;

/**
 * Voids a Cloudbeds folio payment (MFD "Void Payment") by payment id or folio transaction id.
 * Uses {@code connect/payment/add} with {@code transaction_type=Void} (Terceiros / Payment Recorded
 * and similar voidable payments).
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.VoidFolioPaymentJob" )
public class VoidFolioPaymentJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Override
    public void processJob() throws Exception {
        String reservationId = getReservationId();
        String paymentId = StringUtils.trimToNull( getPaymentId() );
        String transactionId = StringUtils.trimToNull( getTransactionId() );
        if ( paymentId == null && transactionId == null ) {
            throw new MissingUserDataException(
                    "VoidFolioPaymentJob requires payment_id or transaction_id for reservation "
                            + reservationId );
        }
        String lookupId = paymentId != null ? paymentId : transactionId;
        TransactionRecord txn = cloudbedsScraper.findPaymentTransactionByPaymentId(
                cbWebClient, reservationId, lookupId );
        if ( txn == null ) {
            throw new MissingUserDataException(
                    "No folio payment matching " + lookupId + " on reservation " + reservationId );
        }
        if ( StringUtils.isBlank( txn.getPaymentId() ) ) {
            throw new MissingUserDataException(
                    "Folio line " + lookupId + " on reservation " + reservationId
                            + " has no payment_id" );
        }
        cloudbedsScraper.voidFolioPayment( cbWebClient, reservationId, txn.getPaymentId() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    @Override
    public int getRetryCount() {
        // Single attempt: a successful void followed by a retry would fail or confuse the folio.
        return 1;
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public String getPaymentId() {
        return getParameter( "payment_id" );
    }

    public void setPaymentId( String paymentId ) {
        setParameter( "payment_id", paymentId );
    }

    public String getTransactionId() {
        return getParameter( "transaction_id" );
    }

    public void setTransactionId( String transactionId ) {
        setParameter( "transaction_id", transactionId );
    }
}
