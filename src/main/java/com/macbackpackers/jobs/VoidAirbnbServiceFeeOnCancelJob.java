package com.macbackpackers.jobs;

import java.math.BigDecimal;
import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.AirbnbPaymentReconcileService;

/**
 * On canceled/no-show Airbnb channel-collect bookings, voids ronbot Airbnb service-fee room-revenue
 * adjusts — but only after Cloudbeds has cleared folio rate+VAT lines.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.VoidAirbnbServiceFeeOnCancelJob" )
public class VoidAirbnbServiceFeeOnCancelJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Override
    public void processJob() throws Exception {
        Reservation res = cloudbedsScraper.getReservationRetry( cbWebClient, getReservationId() );
        if ( false == res.isCanceledOrNoShow() ) {
            LOGGER.info( "Reservation {} status is {}; skipping Airbnb service-fee void",
                    res.getReservationId(), res.getStatus() );
            return;
        }
        if ( false == res.isAirbnbBooking() || false == res.isChannelCollectBooking() ) {
            LOGGER.info( "Reservation {} is not Airbnb channel-collect; skipping", res.getReservationId() );
            return;
        }

        List<TransactionRecord> txns = cloudbedsScraper.getTransactionsByReservation( cbWebClient,
                res.getReservationId() );
        BigDecimal folioRates = AirbnbPaymentReconcileService.sumFolioRoomRatePlusVat( txns );
        if ( folioRates.compareTo( BigDecimal.ZERO ) > 0 ) {
            // Rates often clear slightly after status flips; retry until folio is empty of rate/VAT.
            throw new IllegalStateException( "Reservation " + res.getReservationId()
                    + " is " + res.getStatus() + " but folio still has rate+VAT " + folioRates
                    + "; deferring Airbnb service-fee void until Cloudbeds clears rates" );
        }

        int voided = cloudbedsScraper.voidAirbnbServiceFeeAdjustments( cbWebClient, res.getReservationId() );
        LOGGER.info( "Voided {} Airbnb service-fee adjust(s) on canceled/no-show reservation {}",
                voided, res.getReservationId() );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close();
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    @Override
    public int getRetryCount() {
        // Allow Cloudbeds time to wipe nightly rates after cancel/no-show.
        return 5;
    }
}
