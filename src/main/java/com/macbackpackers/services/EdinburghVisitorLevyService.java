
package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyCalculator.LevyCalculation;

@Service
public class EdinburghVisitorLevyService {

    private static final String ACTIVE_STATUSES = "confirmed,not_confirmed";

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Value( "${evl.tax.exclusive.label:Edinburgh Visitor Levy 2026}" )
    private String exclusiveTaxLabel;

    @Value( "${evl.tax.inclusive.label:Edinburgh Visitor Levy (Inclusive)}" )
    private String inclusiveTaxLabel;

    @Value( "${evl.stay.date.from:2026-07-24}" )
    private String stayDateFrom;

    @Value( "${evl.booked.date.from:2025-10-01}" )
    private String bookedDateFrom;

    public static class LevyAssessment {
        private final String reservationId;
        private final LevyCalculation calculation;
        private final BigDecimal currentLevy;
        private final BigDecimal expectedLevy;
        private final BigDecimal delta;

        public LevyAssessment( String reservationId, LevyCalculation calculation,
                BigDecimal currentLevy, BigDecimal expectedLevy, BigDecimal delta ) {
            this.reservationId = reservationId;
            this.calculation = calculation;
            this.currentLevy = currentLevy;
            this.expectedLevy = expectedLevy;
            this.delta = delta;
        }

        public String getReservationId() {
            return reservationId;
        }

        public LevyCalculation getCalculation() {
            return calculation;
        }

        public BigDecimal getCurrentLevy() {
            return currentLevy;
        }

        public BigDecimal getExpectedLevy() {
            return expectedLevy;
        }

        public BigDecimal getDelta() {
            return delta;
        }

        public boolean needsAdjustment() {
            return false == EdinburghVisitorLevyCalculator.isWithinTolerance( delta );
        }
    }

    public List<Customer> findReservationsRequiringVisitorLevyAdjustment( WebClient webClient,
            LocalDate bookingDateStart, LocalDate bookingDateEnd ) throws IOException {
        return cloudbedsScraper.getReservationsByBookingDate( webClient, bookingDateStart, bookingDateEnd,
                ACTIVE_STATUSES ).stream()
                .filter( this::requiresVisitorLevyAdjustment )
                .collect( Collectors.toList() );
    }

    public boolean requiresVisitorLevyAdjustment( Customer customer ) {
        if ( customer.getBookingDate() != null
                && EdinburghVisitorLevyCalculator.isBookingExempt(
                        LocalDate.parse( customer.getBookingDate() ), getBookedDateFrom() ) ) {
            return false;
        }
        if ( customer.getCheckoutDate() != null
                && false == EdinburghVisitorLevyCalculator.hasEligibleStayDates(
                        LocalDate.parse( customer.getCheckoutDate() ), getStayDateFrom() ) ) {
            return false;
        }
        if ( isHostelworldBooking( customer ) ) {
            return true;
        }
        return getNumberOfNights( customer ) > 5;
    }

    public LevyAssessment assessVisitorLevyForBooking( WebClient webClient, String reservationId ) throws IOException {
        Reservation reservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );
        return assessVisitorLevy( reservation );
    }

    public LevyAssessment assessVisitorLevy( Reservation reservation ) {
        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, getStayDateFrom(), getBookedDateFrom() );

        BigDecimal currentLevy = reservation.getVisitorLevyTotal( exclusiveTaxLabel, inclusiveTaxLabel );
        BigDecimal expectedLevy = calculation.getExpectedLevy();
        BigDecimal delta = expectedLevy.subtract( currentLevy ).setScale( 2, RoundingMode.HALF_UP );

        return new LevyAssessment( reservation.getReservationId(), calculation, currentLevy, expectedLevy, delta );
    }

    public void processVisitorLevyForBooking( WebClient webClient, String reservationId ) throws IOException {
        Reservation reservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );
        LevyAssessment assessment = assessVisitorLevy( reservation );

        LOGGER.info( "Reservation {}: expected levy={}, current levy={}, delta={}",
                reservationId, assessment.getExpectedLevy(), assessment.getCurrentLevy(), assessment.getDelta() );

        if ( false == assessment.needsAdjustment() ) {
            LOGGER.info( "Visitor levy already correct for reservation {}", reservationId );
            return;
        }

        applyVisitorLevyAdjustment( webClient, reservation, assessment );
    }

    public void logDryRunAssessment( Customer customer, LevyAssessment assessment ) {
        if ( assessment.needsAdjustment() ) {
            LOGGER.info( "ADJUSTMENT NEEDED: Res #{} ({}) {} {} ({} nights): expected={}, current={}, delta={}",
                    customer.getId(), customer.getSourceName(), customer.getFirstName(), customer.getLastName(),
                    customer.getNights(), assessment.getExpectedLevy(), assessment.getCurrentLevy(),
                    assessment.getDelta() );
        }
        else {
            LOGGER.info( "OK: Res #{} ({}) {} {} ({} nights): levy={}",
                    customer.getId(), customer.getSourceName(), customer.getFirstName(), customer.getLastName(),
                    customer.getNights(), assessment.getExpectedLevy() );
        }
    }

    private void applyVisitorLevyAdjustment( WebClient webClient, Reservation reservation,
            LevyAssessment assessment ) throws IOException {
        String taxLabel = EdinburghVisitorLevyCalculator.useInclusiveTax( reservation )
                ? inclusiveTaxLabel : exclusiveTaxLabel;
        String taxId = cloudbedsScraper.resolveTaxIdByLabel( webClient, taxLabel );

        String note = EdinburghVisitorLevyCalculator.buildAdjustmentNote( assessment.getCalculation() );
        BigDecimal delta = assessment.getDelta();
        if ( delta.compareTo( BigDecimal.ZERO ) < 0 ) {
            cloudbedsScraper.adjustVisitorLevyCharge( webClient, reservation, taxId, delta.abs(), note );
        }
        else {
            cloudbedsScraper.addVisitorLevyCharge( webClient, reservation, taxId, delta );
        }
    }

    private LocalDate getStayDateFrom() {
        return LocalDate.parse( stayDateFrom );
    }

    private LocalDate getBookedDateFrom() {
        return LocalDate.parse( bookedDateFrom );
    }

    private boolean isHostelworldBooking( Customer customer ) {
        return customer.getSourceName() != null && customer.getSourceName().startsWith( "Hostelworld" );
    }

    private int getNumberOfNights( Customer customer ) {
        return customer.getNights() == null ? 0 : Integer.parseInt( customer.getNights() );
    }
}
