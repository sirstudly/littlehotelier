package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.AirbnbPayment;
import com.macbackpackers.services.AirbnbPaymentReconcileService;
import com.macbackpackers.services.AirbnbPaymentReconcileService.ReconcileResult;

/**
 * Reconciles one Airbnb confirmation-code import row against the Cloudbeds folio.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AirbnbImportTransactionJob" )
public class AirbnbImportTransactionJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private AirbnbPaymentReconcileService reconcileService;

    @Override
    public void processJob() throws Exception {
        String confirmationCode = getConfirmationCode();
        AirbnbPayment payment = dao.fetchAirbnbPaymentByConfirmationCode( confirmationCode );
        if ( payment == null ) {
            throw new IllegalStateException( "No Airbnb payment row for " + confirmationCode );
        }
        payment.setProcessingStatus( AirbnbPayment.STATUS_PROCESSING );
        payment.setLastJobId( Long.valueOf( getId() ) );
        dao.updateAirbnbPayment( payment );

        String action = StringUtils.defaultIfBlank( getAction(), AirbnbPaymentReconcileService.ACTION_AUTO );
        try {
            ReconcileResult result = reconcileService.reconcile( cbWebClient, payment, action );
            payment.setProcessingStatus( result.getStatus() );
            payment.setStatusReason( result.getReason() );
            dao.updateAirbnbPayment( payment );
            LOGGER.info( "Airbnb import {} -> {} ({})", confirmationCode, result.getStatus(),
                    result.getReason() );
        }
        catch ( Exception e ) {
            payment.setProcessingStatus( AirbnbPayment.STATUS_ERRORED );
            payment.setStatusReason( StringUtils.abbreviate( e.getMessage(), 500 ) );
            dao.updateAirbnbPayment( payment );
            // Business/match errors complete the job successfully with errored status
            LOGGER.warn( "Airbnb import {} errored: {}", confirmationCode, e.getMessage() );
            if ( e instanceof RuntimeException && false == isBusinessError( e ) ) {
                throw e;
            }
        }
    }

    private static boolean isBusinessError( Exception e ) {
        String name = e.getClass().getName();
        return name.contains( "UnrecoverableFault" ) || name.contains( "MissingUserDataException" );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    @Override
    public int getRetryCount() {
        return 1;
    }

    public String getConfirmationCode() {
        return getParameter( "confirmation_code" );
    }

    public void setConfirmationCode( String confirmationCode ) {
        setParameter( "confirmation_code", confirmationCode );
    }

    public String getAction() {
        return getParameter( "action" );
    }

    public void setAction( String action ) {
        setParameter( "action", action );
    }
}
