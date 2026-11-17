package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.macbackpackers.beans.AirbnbImportBatch;
import com.macbackpackers.beans.AirbnbPayment;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.services.AirbnbCsvParser;
import com.macbackpackers.services.AirbnbCsvParser.ParseResult;
import com.macbackpackers.services.AirbnbPaymentReconcileService;

/**
 * Parent job: parse an uploaded Airbnb CSV batch from MariaDB and upsert
 * {@link AirbnbPayment} rows, enqueueing {@link AirbnbImportTransactionJob}s for known patterns.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AirbnbImportTransactionsJob" )
public class AirbnbImportTransactionsJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        long batchId = Long.parseLong( getParameter( "batch_id" ) );
        AirbnbImportBatch batch = dao.fetchAirbnbImportBatch( batchId );
        batch.setParentJobId( Long.valueOf( getId() ) );
        batch.setStatus( "processing" );
        dao.updateAirbnbImportBatch( batch );

        ParseResult parsed = AirbnbCsvParser.parse( batch.getCsvContent() );
        for ( String err : parsed.getErrors() ) {
            LOGGER.warn( "Airbnb CSV parse issue: {}", err );
            int colon = err.indexOf( ':' );
            if ( colon > 0 ) {
                String code = err.substring( 0, colon ).trim();
                AirbnbPayment errored = new AirbnbPayment();
                errored.setConfirmationCode( code );
                errored.setBatchId( batchId );
                errored.setPostPaymentField( batch.getPostPaymentField() );
                errored.setProcessingStatus( AirbnbPayment.STATUS_ERRORED );
                errored.setStatusReason( err.substring( colon + 1 ).trim() );
                dao.upsertAirbnbPayment( errored );
            }
        }

        int enqueued = 0;
        for ( AirbnbPayment row : parsed.getPayments() ) {
            row.setBatchId( batchId );
            row.setPostPaymentField( batch.getPostPaymentField() );

            if ( isKnownPattern( row ) ) {
                row.setProcessingStatus( AirbnbPayment.STATUS_PROCESSING );
                row.setStatusReason( null );
                dao.upsertAirbnbPayment( row );

                AirbnbImportTransactionJob child = new AirbnbImportTransactionJob();
                child.setStatus( JobStatus.submitted );
                child.setConfirmationCode( row.getConfirmationCode() );
                child.setAction( AirbnbPaymentReconcileService.ACTION_AUTO );
                child.setParameter( "batch_id", String.valueOf( batchId ) );
                int childId = dao.insertJob( child );
                row.setLastJobId( Long.valueOf( childId ) );
                dao.updateAirbnbPayment( row );
                enqueued++;
            }
            else {
                row.setProcessingStatus( AirbnbPayment.STATUS_NEEDS_REVIEW );
                row.setStatusReason( "Unsupported line mix: " + row.getLineTypes() );
                dao.upsertAirbnbPayment( row );
            }
        }

        batch.setStatus( "completed" );
        dao.updateAirbnbImportBatch( batch );
        LOGGER.info( "Airbnb import batch {}: {} confirmation codes, {} child jobs enqueued",
                batchId, parsed.getPayments().size(), enqueued );
    }

    /**
     * Patterns with automated folio rules: reservation-only, resolution (+ optional reservation),
     * cancellation-fee-only.
     */
    public static boolean isKnownPattern( AirbnbPayment row ) {
        if ( AirbnbCsvParser.isAutoEligible( row ) ) {
            return true;
        }
        if ( row.isYn( row.getHasCancellationFee() ) && false == row.isYn( row.getHasReservation() )
                && false == row.isYn( row.getHasResolutionAdjustment() )
                && false == row.isYn( row.getHasAdjustment() ) ) {
            return true;
        }
        if ( row.isYn( row.getHasResolutionAdjustment() ) && false == row.isYn( row.getHasAdjustment() )
                && false == row.isYn( row.getHasCancellationFee() ) ) {
            return true;
        }
        return false;
    }

    public void setBatchId( long batchId ) {
        setParameter( "batch_id", String.valueOf( batchId ) );
    }
}
