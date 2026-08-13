
package com.macbackpackers.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.Striped;
import com.google.gson.Gson;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.cloudbedsws.CloudbedsCalendarEvent;
import com.macbackpackers.scrapers.cloudbedsws.EdinburghVisitorLevyBookingCriteria;
import com.macbackpackers.services.EdinburghVisitorLevyCalculator.LevyCalculation;

@Service
public class EdinburghVisitorLevyService {

    private static final String ALL_STATUSES = null;

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    /** Serializes folio mutations per reservation so different bookings can run in parallel. */
    private final Striped<Lock> reservationLocks = Striped.lock( 64 );

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Value( "${evl.enabled:false}" )
    private boolean evlEnabled;

    @Value( "${evl.stay.date.from:2026-07-24}" )
    private String stayDateFrom;

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

    public static class CustomerLevyAssessment {
        private final Customer customer;
        private final LevyAssessment assessment;

        public CustomerLevyAssessment( Customer customer, LevyAssessment assessment ) {
            this.customer = customer;
            this.assessment = assessment;
        }

        public Customer getCustomer() {
            return customer;
        }

        public LevyAssessment getAssessment() {
            return assessment;
        }
    }

    /**
     * Returns bookings matching the given booking-date and/or checkin-date range that are
     * potentially levy-eligible and whose folio EVL differs from the calculated amount
     * (outside tolerance). When both ranges are set, Cloudbeds applies both filters (AND).
     * <p>
     * Assessments are performed lazily as the stream is consumed (one-shot; do not reuse).
     * I/O failures during assessment are wrapped in {@link UncheckedIOException}.
     */
    public Stream<CustomerLevyAssessment> findReservationsRequiringVisitorLevyAdjustment( WebClient webClient,
            LocalDate bookingDateStart, LocalDate bookingDateEnd,
            LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        return assessReservationsInDateRange(
                webClient, bookingDateStart, bookingDateEnd, checkinDateStart, checkinDateEnd )
                .filter( entry -> entry.getAssessment().needsAdjustment() );
    }

    /**
     * Assesses visitor levy for all potentially eligible bookings matching the given booking-date
     * and/or checkin-date range (all reservation statuses, including canceled and no_show).
     * When both ranges are set, Cloudbeds applies both filters (AND).
     * <p>
     * Assessments are performed lazily as the stream is consumed (one-shot; do not reuse).
     * I/O failures during assessment are wrapped in {@link UncheckedIOException}.
     */
    public Stream<CustomerLevyAssessment> assessReservationsInDateRange( WebClient webClient,
            LocalDate bookingDateStart, LocalDate bookingDateEnd,
            LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        return cloudbedsScraper.getReservations( webClient,
                null, null, checkinDateStart, checkinDateEnd, null, null,
                bookingDateStart, bookingDateEnd, ALL_STATUSES )
                .stream()
                .filter( this::isPotentiallyEligible )
                .map( customer -> {
                    try {
                        LevyAssessment assessment = assessVisitorLevyForBooking( webClient, customer.getId() );
                        return new CustomerLevyAssessment( customer, assessment );
                    }
                    catch ( IOException e ) {
                        throw new UncheckedIOException(
                                "Failed assessing visitor levy for reservation " + customer.getId(), e );
                    }
                } );
    }

    /**
     * Cheap eligibility check for a new {@code booked} calendar WebSocket event, mirroring
     * {@link #isPotentiallyEligible(Customer)} using fields available on the event.
     */
    public boolean isPotentiallyEligibleForNewBooking( CloudbedsCalendarEvent event ) {
        return isPotentiallyEligibleForNewBooking( event, Collections.emptySet() );
    }

    public boolean isPotentiallyEligibleForNewBooking( CloudbedsCalendarEvent event,
            Set<String> inclusiveTaxSubSourceIds ) {
        return EdinburghVisitorLevyBookingCriteria.matchesNewBookingCalendarEvent(
                event, evlEnabled, getStayDateFrom(), inclusiveTaxSubSourceIds );
    }

    public boolean isPotentiallyEligibleForCanceledOrNoShow( CloudbedsCalendarEvent event,
            Set<String> inclusiveTaxSubSourceIds ) {
        return EdinburghVisitorLevyBookingCriteria.matchesCanceledOrNoShowCalendarEvent(
                event, evlEnabled, inclusiveTaxSubSourceIds );
    }

    public boolean isEvlEnabled() {
        return evlEnabled;
    }

    private boolean isPotentiallyEligible( Customer customer ) {
        if ( false == evlEnabled ) {
            return false;
        }
        if ( customer.isLongTermer() ) {
            return false;
        }
        if ( EdinburghVisitorLevyBookingCriteria.isInclusiveTaxSourceName( customer.getSourceName() ) ) {
            return false;
        }
        if ( customer.getCheckoutDate() != null
                && false == EdinburghVisitorLevyCalculator.hasEligibleStayDates(
                        LocalDate.parse( customer.getCheckoutDate() ), getStayDateFrom() ) ) {
            return false;
        }
        return true;
    }

    public LevyAssessment assessVisitorLevyForBooking( WebClient webClient, String reservationId ) throws IOException {
        Reservation reservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );
        return assessVisitorLevy( reservation );
    }

    public LevyAssessment assessVisitorLevy( Reservation reservation ) {
        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, getStayDateFrom() );

        BigDecimal currentLevy = reservation.getVisitorLevyTotal();
        BigDecimal expectedLevy = calculation.getExpectedLevy();
        BigDecimal delta = expectedLevy.subtract( currentLevy ).setScale( 2, RoundingMode.HALF_UP );

        return new LevyAssessment( reservation.getReservationId(), calculation, currentLevy, expectedLevy, delta );
    }

    public void processVisitorLevyForBooking( WebClient webClient, String reservationId ) throws IOException {
        withReservationLock( reservationId, () -> processVisitorLevyForBookingLocked( webClient, reservationId ) );
    }

    private void processVisitorLevyForBookingLocked( WebClient webClient, String reservationId ) throws IOException {
        if ( false == evlEnabled ) {
            LOGGER.info( "Skipping visitor levy for reservation {} (evl.enabled=false)", reservationId );
            return;
        }

        Reservation reservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );
        LevyAssessment assessment = assessVisitorLevy( reservation );

        LOGGER.info( "Reservation {}: expected levy={}, current levy={}, delta={}",
                reservationId, assessment.getExpectedLevy(), assessment.getCurrentLevy(), assessment.getDelta() );

        if ( reservation.isCanceledOrNoShow() ) {
            processCanceledOrNoShowVisitorLevy( webClient, reservation, assessment );
            return;
        }

        if ( false == assessment.needsAdjustment() ) {
            LOGGER.info( "Visitor levy already correct for reservation {}", reservationId );
            return;
        }

        applyVisitorLevyAdjustment( webClient, reservation, assessment );
    }

    /**
     * Returns bookings in the given checkin-date range (all statuses) that have at least one
     * voidable folio line labeled exactly {@link EdinburghVisitorLevyCalculator#LEGACY_EXCLUSIVE_LABEL}.
     */
    public List<Customer> findReservationsWithLegacyEvlFolioLines( WebClient webClient,
            LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        List<Customer> results = new ArrayList<>();
        for ( Customer customer : cloudbedsScraper.getReservations( webClient,
                null, null, checkinDateStart, checkinDateEnd, null, null,
                null, null, ALL_STATUSES ) ) {
            if ( hasVoidableLegacyEvlFolioLines( webClient, customer.getId() ) ) {
                results.add( customer );
            }
        }
        return results;
    }

    /**
     * True when the reservation has at least one voidable tax/adjustment folio line labeled
     * exactly {@link EdinburghVisitorLevyCalculator#LEGACY_EXCLUSIVE_LABEL}.
     */
    public boolean hasVoidableLegacyEvlFolioLines( WebClient webClient, String reservationId )
            throws IOException {
        return false == listVoidableLegacyEvlFolioLines( webClient, reservationId ).isEmpty();
    }

    private List<TransactionRecord> listVoidableLegacyEvlFolioLines( WebClient webClient, String reservationId )
            throws IOException {
        return CloudbedsScraper.listVoidableTransactionsWithDescription(
                cloudbedsScraper.getTransactionsByReservation( webClient, reservationId ),
                EdinburghVisitorLevyCalculator.LEGACY_EXCLUSIVE_LABEL );
    }

    /**
     * Voids voidable folio lines labeled exactly {@link EdinburghVisitorLevyCalculator#LEGACY_EXCLUSIVE_LABEL}
     * and re-posts each line's amount under {@link EdinburghVisitorLevyCalculator#GENERIC_EXCLUSIVE_LABEL}.
     */
    public void voidAndResubmitLegacyEvlFolio( WebClient webClient, String reservationId )
            throws IOException {
        withReservationLock( reservationId, () -> voidAndResubmitLegacyEvlFolioLocked( webClient, reservationId ) );
    }

    private void voidAndResubmitLegacyEvlFolioLocked( WebClient webClient, String reservationId )
            throws IOException {
        List<TransactionRecord> legacyLines = listVoidableLegacyEvlFolioLines( webClient, reservationId );

        if ( legacyLines.isEmpty() ) {
            LOGGER.info( "No voidable legacy EVL folio lines on reservation {}", reservationId );
            return;
        }

        String targetTaxId = cloudbedsScraper.resolveTaxIdByExactEnglishName( webClient,
                EdinburghVisitorLevyCalculator.GENERIC_EXCLUSIVE_LABEL );
        LOGGER.info( "Migrating {} legacy EVL folio line(s) on reservation {} to tax ID {}",
                legacyLines.size(), reservationId, targetTaxId );

        final String migrationNote = "Legacy " + EdinburghVisitorLevyCalculator.LEGACY_EXCLUSIVE_LABEL + " migration";
        for ( TransactionRecord txn : legacyLines ) {
            BigDecimal amount = txn.getVisitorLevyContribution();
            String type = txn.getType();
            cloudbedsScraper.voidVisitorLevyTransaction( webClient, reservationId, txn );

            if ( amount.compareTo( BigDecimal.ZERO ) == 0 ) {
                LOGGER.info( "Skipped resubmit of zero-amount {} line {} on reservation {}",
                        type, txn.getId(), reservationId );
                continue;
            }

            Reservation reservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

            if ( "adjustment".equalsIgnoreCase( type ) ) {
                // Cloudbeds add_new_adjust takes a positive amount that reduces the tax (negative folio credit)
                cloudbedsScraper.adjustVisitorLevyCharge( webClient, reservation, targetTaxId,
                        amount.abs(), migrationNote );
            }
            else {
                cloudbedsScraper.addVisitorLevyCharge( webClient, reservation, targetTaxId, amount.abs() );
            }
            LOGGER.info( "Resubmitted {} {} under {} on reservation {}",
                    type, amount, EdinburghVisitorLevyCalculator.GENERIC_EXCLUSIVE_LABEL, reservationId );
        }
    }

    @FunctionalInterface
    private interface ReservationLockWork {
        void run() throws IOException;
    }

    private void withReservationLock( String reservationId, ReservationLockWork work ) throws IOException {
        Lock lock = reservationLocks.get( reservationId );
        lock.lock();
        try {
            work.run();
        }
        finally {
            lock.unlock();
        }
    }

    private void processCanceledOrNoShowVisitorLevy( WebClient webClient, Reservation reservation,
            LevyAssessment assessment ) throws IOException {
        if ( EdinburghVisitorLevyCalculator.useInclusiveTax( reservation ) ) {
            logInclusiveTaxVisitorLevyAndVatDiscrepancy( reservation, assessment );
            return;
        }

        if ( false == assessment.needsAdjustment() ) {
            LOGGER.info( "Visitor levy already zero for canceled/no-show reservation {}", reservation.getReservationId() );
            return;
        }

        int voided = cloudbedsScraper.voidVoidableVisitorLevyTransactions( webClient, reservation );
        LOGGER.info( "Voided {} EVL folio line(s) on canceled/no-show reservation {}", voided, reservation.getReservationId() );

        Reservation refreshed = cloudbedsScraper.getReservationRetry( webClient, reservation.getReservationId() );
        BigDecimal remainingLevy = refreshed.getVisitorLevyTotal();
        if ( EdinburghVisitorLevyCalculator.isWithinTolerance( assessment.getExpectedLevy().subtract( remainingLevy ) ) ) {
            LOGGER.info( "Visitor levy cleared for canceled/no-show reservation {}", reservation.getReservationId() );
        }
        else {
            throw new UnrecoverableFault( String.format(
                    "Visitor levy still %s on canceled/no-show reservation %s after voiding %d line(s)",
                    remainingLevy, reservation.getReservationId(), voided ) );
        }
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
        if ( EdinburghVisitorLevyCalculator.useInclusiveTax( reservation ) ) {
            logInclusiveTaxVisitorLevyAndVatDiscrepancy( reservation, assessment );
            return;
        }

        String taxId = cloudbedsScraper.resolveVisitorLevyTaxId( webClient, false );

        String note = EdinburghVisitorLevyCalculator.buildAdjustmentNote( assessment.getCalculation() );
        BigDecimal delta = assessment.getDelta();
        if ( cloudbedsScraper.tryVoidMatchingVisitorLevyTransaction( webClient, reservation, delta ) ) {
            LOGGER.info( "Voided matching EVL line instead of posting delta {} on reservation {}",
                    delta, reservation.getReservationId() );
            Reservation refreshed = cloudbedsScraper.getReservationRetry( webClient, reservation.getReservationId() );
            LevyAssessment refreshedAssessment = assessVisitorLevy( refreshed );
            if ( refreshedAssessment.needsAdjustment() ) {
                LOGGER.warn( "Visitor levy still outside tolerance after void on reservation {}: expected={}, current={}, delta={}",
                        reservation.getReservationId(), refreshedAssessment.getExpectedLevy(),
                        refreshedAssessment.getCurrentLevy(), refreshedAssessment.getDelta() );
            }
            return;
        }
        if ( delta.compareTo( BigDecimal.ZERO ) < 0 ) {
            cloudbedsScraper.adjustVisitorLevyCharge( webClient, reservation, taxId, delta.abs(), note );
        }
        else {
            cloudbedsScraper.addVisitorLevyCharge( webClient, reservation, taxId, delta );
        }
    }

    private void logInclusiveTaxVisitorLevyAndVatDiscrepancy( Reservation reservation, LevyAssessment assessment ) {
        BigDecimal expectedEvl = assessment.getExpectedLevy();
        BigDecimal currentEvl = assessment.getCurrentLevy();
        BigDecimal evlDelta = expectedEvl.subtract( currentEvl ).setScale( 2, RoundingMode.HALF_UP );

        BigDecimal expectedVat = EdinburghVisitorLevyCalculator.calculateExpectedBdcRoomVat(
                reservation, gson, getStayDateFrom() );
        BigDecimal currentVat = reservation.getVatTotal();
        BigDecimal vatDelta = expectedVat.subtract( currentVat ).setScale( 2, RoundingMode.HALF_UP );

        LOGGER.info( "Inclusive-tax reservation {} ({}): skipping adjustment (fixed channel total). "
                + "EVL expected={}, current={}, delta={}. VAT expected={}, current={}, delta={}.",
                reservation.getReservationId(), reservation.getSourceName(), expectedEvl, currentEvl, evlDelta,
                expectedVat, currentVat, vatDelta );
    }

    private LocalDate getStayDateFrom() {
        return LocalDate.parse( stayDateFrom );
    }

}
