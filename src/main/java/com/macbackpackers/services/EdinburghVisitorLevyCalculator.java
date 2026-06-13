
package com.macbackpackers.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;

/**
 * Pure calculation logic for the Edinburgh Visitor Levy.
 */
public final class EdinburghVisitorLevyCalculator {

    public static final String EXCLUSIVE_TAX_LABEL = "Edinburgh Visitor Levy 2026";
    public static final String INCLUSIVE_TAX_LABEL = "Edinburgh Visitor Levy (Inclusive)";

    private static final BigDecimal LEVY_RATE = new BigDecimal( "0.05" );
    private static final int MAX_LEVY_NIGHTS = 5;
    private static final BigDecimal TOLERANCE = new BigDecimal( "0.01" );

    private EdinburghVisitorLevyCalculator() {
    }

    public static class LevyNight {
        private final LocalDate date;
        private final BigDecimal rate;

        public LevyNight( LocalDate date, BigDecimal rate ) {
            this.date = date;
            this.rate = rate;
        }

        public LocalDate getDate() {
            return date;
        }

        public BigDecimal getRate() {
            return rate;
        }
    }

    public static class LevyCalculation {
        private final BigDecimal expectedLevy;
        private final List<LevyNight> eligibleNights;
        private final boolean hostelworldGrossUp;
        private final boolean canceledOrNoShow;
        private final boolean bookingExempt;

        public LevyCalculation( BigDecimal expectedLevy, List<LevyNight> eligibleNights,
                boolean hostelworldGrossUp, boolean canceledOrNoShow, boolean bookingExempt ) {
            this.expectedLevy = expectedLevy;
            this.eligibleNights = eligibleNights;
            this.hostelworldGrossUp = hostelworldGrossUp;
            this.canceledOrNoShow = canceledOrNoShow;
            this.bookingExempt = bookingExempt;
        }

        public BigDecimal getExpectedLevy() {
            return expectedLevy;
        }

        public List<LevyNight> getEligibleNights() {
            return eligibleNights;
        }

        public boolean isHostelworldGrossUp() {
            return hostelworldGrossUp;
        }

        public boolean isCanceledOrNoShow() {
            return canceledOrNoShow;
        }

        public boolean isBookingExempt() {
            return bookingExempt;
        }

        public BigDecimal getEligibleRatesTotal() {
            return eligibleNights.stream()
                    .map( LevyNight::getRate )
                    .reduce( BigDecimal.ZERO, BigDecimal::add );
        }
    }

    public static LevyCalculation calculate( Reservation reservation, Gson gson,
            LocalDate stayDateFrom, LocalDate bookedDateFrom, BigDecimal hwlDepositFraction ) {

        boolean canceledOrNoShow = reservation.isCanceledOrNoShow();
        boolean bookingExempt = reservation.getBookingDateAsLocalDate().isBefore( bookedDateFrom );

        if ( canceledOrNoShow || bookingExempt ) {
            return new LevyCalculation( BigDecimal.ZERO.setScale( 2, RoundingMode.HALF_UP ),
                    Collections.emptyList(), false, canceledOrNoShow, bookingExempt );
        }

        List<LevyNight> eligibleNights = getEligibleNights( reservation, gson, stayDateFrom );
        BigDecimal eligibleTotal = eligibleNights.stream()
                .map( LevyNight::getRate )
                .reduce( BigDecimal.ZERO, BigDecimal::add );

        boolean hostelworldGrossUp = reservation.isHostelworldBooking();
        BigDecimal levyBase = eligibleTotal;
        if ( hostelworldGrossUp ) {
            BigDecimal depositMultiplier = BigDecimal.ONE.subtract( hwlDepositFraction );
            levyBase = eligibleTotal.divide( depositMultiplier, 10, RoundingMode.HALF_UP );
        }

        BigDecimal expectedLevy = levyBase.multiply( LEVY_RATE ).setScale( 2, RoundingMode.HALF_UP );
        return new LevyCalculation( expectedLevy, eligibleNights, hostelworldGrossUp, false, false );
    }

    static List<LevyNight> getEligibleNights( Reservation reservation, Gson gson, LocalDate stayDateFrom ) {
        Map<LocalDate, BigDecimal> ratesByDate = reservation.getRatesByDate( gson );
        LocalDate checkoutDate = reservation.getCheckoutDateAsLocalDate();

        List<LevyNight> eligibleNights = new ArrayList<>();
        for ( Map.Entry<LocalDate, BigDecimal> entry : ratesByDate.entrySet() ) {
            LocalDate night = entry.getKey();
            if ( false == night.isBefore( stayDateFrom ) && night.isBefore( checkoutDate ) ) {
                eligibleNights.add( new LevyNight( night, entry.getValue() ) );
            }
        }
        eligibleNights.sort( ( a, b ) -> a.getDate().compareTo( b.getDate() ) );

        if ( eligibleNights.size() > MAX_LEVY_NIGHTS ) {
            return eligibleNights.subList( 0, MAX_LEVY_NIGHTS );
        }
        return eligibleNights;
    }

    public static boolean isWithinTolerance( BigDecimal delta ) {
        return delta.abs().compareTo( TOLERANCE ) < 0;
    }

    public static String buildAdjustmentNote( LevyCalculation calculation ) {
        if ( calculation.isCanceledOrNoShow() ) {
            return "Reservation canceled/no-show - visitor levy not applicable. -RONBOT";
        }
        if ( calculation.isBookingExempt() ) {
            return "Booking made before levy booking date - visitor levy not applicable. -RONBOT";
        }

        StringBuilder note = new StringBuilder( "Visitor levy only applies to the first 5 nights. " );
        note.append( "Charge for the first " ).append( calculation.getEligibleNights().size() ).append( " nights is " );
        note.append( calculation.getEligibleRatesTotal().setScale( 2, RoundingMode.HALF_UP ) );
        if ( calculation.isHostelworldGrossUp() ) {
            note.append( " (HWL grossed up)" );
        }
        note.append( " @ 5% = " ).append( calculation.getExpectedLevy() );
        note.append( ". -RONBOT" );
        return note.toString();
    }

    public static boolean useInclusiveTax( Reservation reservation ) {
        return reservation.isBookingDotComBooking();
    }

    public static BigDecimal getVisitorLevyTotal( Reservation reservation ) {
        return reservation.getVisitorLevyTotal( EXCLUSIVE_TAX_LABEL, INCLUSIVE_TAX_LABEL );
    }

    /**
     * Balance due excluding visitor levy, which is collected on arrival when the guest actually stays.
     */
    public static BigDecimal getBalanceDueExcludingVisitorLevy( Reservation reservation ) {
        return reservation.getBalanceDue()
                .subtract( getVisitorLevyTotal( reservation ) )
                .setScale( 2, RoundingMode.HALF_UP );
    }
}
