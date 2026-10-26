package com.macbackpackers.jobs;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.PaymentProcessorService;

/**
 * Records a one-time Cloudbeds AirBnB Prepaid Card payment for an Airbnb (API) channel-collect
 * booking for balance due excluding EVL.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AirbnbPrepaidPaymentJob" )
public class AirbnbPrepaidPaymentJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Override
    public void processJob() throws Exception {
        paymentProcessor.recordAirbnbPrepaidPayment( cbWebClient, getReservationId() );
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
}
