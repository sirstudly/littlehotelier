package com.macbackpackers.jobs;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.HostelworldScraper;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Reports a card payment issue to Hostelworld and records it on the Cloudbeds booking.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.HostelworldReportPaymentIssueJob" )
public class HostelworldReportPaymentIssueJob extends AbstractJob {

    public static final String REPORTED_CARD_PAYMENT_ISSUE_NOTE = "Reported card payment issue to Hostelworld.";

    @Autowired
    @Transient
    private HostelworldScraper hostelworldScraper;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    @Transient
    @Qualifier( "webClientForHostelworld" )
    private WebClient hwlWebClient;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient cbWebClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            Reservation reservation = cloudbedsScraper.getReservationRetry( cbWebClient, getReservationId() );
            if ( reservation.containsNote( REPORTED_CARD_PAYMENT_ISSUE_NOTE ) ) {
                LOGGER.info( "Issue with card already reported to Hostelworld. Nothing to do." );
                return;
            }

            String hostelworldBookingRef = reservation.getThirdPartyIdentifier();
            if ( StringUtils.isBlank( hostelworldBookingRef ) ) {
                throw new MissingUserDataException( "Reservation " + getReservationId()
                        + " has no Hostelworld booking reference (thirdPartyIdentifier)." );
            }

            hostelworldScraper.reportPaymentIssue( hwlWebClient, hostelworldBookingRef,
                    getCardIssue(), isEmailConfirmToSelf() );
            cloudbedsScraper.addNote( cbWebClient, reservation.getReservationId(), REPORTED_CARD_PAYMENT_ISSUE_NOTE );
        }
    }

    @Override
    public void finalizeJob() {
        hwlWebClient.close();
    }

    @Override
    public int getRetryCount() {
        return 1;
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setCardIssue( String cardIssue ) {
        setParameter( "card_issue", cardIssue );
    }

    public String getCardIssue() {
        return getParameter( "card_issue" );
    }

    public void setEmailConfirmToSelf( boolean emailConfirmToSelf ) {
        setParameter( "email_confirm_to_self", Boolean.toString( emailConfirmToSelf ) );
    }

    public boolean isEmailConfirmToSelf() {
        return Boolean.parseBoolean( getParameter( "email_confirm_to_self" ) );
    }
}
