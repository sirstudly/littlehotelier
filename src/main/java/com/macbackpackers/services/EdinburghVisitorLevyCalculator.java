
package com.macbackpackers.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;

/**
 * Pure calculation logic for the Edinburgh Visitor Levy.
 */
public final class EdinburghVisitorLevyCalculator {

    public static final String EXCLUSIVE_TAX_LABEL = "Edinburgh Visitor Levy 2026";
    public static final String INCLUSIVE_TAX_LABEL = "Edinburgh Visitor Levy (Inclusive)";

    private static final BigDecimal LEVY_RATE = new BigDecimal( "0.05" );
    private static final BigDecimal BDC_INCLUSIVE_LEVY_RATE = new BigDecimal( "0.06" );
    private static final BigDecimal ROOM_VAT_RATE = new BigDecimal( "0.20" );
    private static final BigDecimal BDC_GROSS_TO_NET = new BigDecimal( "1.26" );
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

    static class PersonNightRate {
        private final LocalDate date;
        private final BigDecimal grossRate;
        private final int guestCount;

        PersonNightRate( LocalDate date, BigDecimal grossRate, int guestCount ) {
            this.date = date;
            this.grossRate = grossRate;
            this.guestCount = guestCount;
        }

        LocalDate getDate() {
            return date;
        }

        BigDecimal getGrossRate() {
            return grossRate;
        }

        int getGuestCount() {
            return guestCount;
        }
    }

    public static class LevyCalculation {
        private final BigDecimal expectedLevy;
        private final BigDecimal levyBase;
        private final List<LevyNight> eligibleNights;
        private final boolean hostelworldUsesListedPrice;
        private final boolean canceledOrNoShow;

        public LevyCalculation( BigDecimal expectedLevy, BigDecimal levyBase, List<LevyNight> eligibleNights,
                boolean hostelworldUsesListedPrice, boolean canceledOrNoShow ) {
            this.expectedLevy = expectedLevy;
            this.levyBase = levyBase;
            this.eligibleNights = eligibleNights;
            this.hostelworldUsesListedPrice = hostelworldUsesListedPrice;
            this.canceledOrNoShow = canceledOrNoShow;
        }

        public BigDecimal getExpectedLevy() {
            return expectedLevy;
        }

        public BigDecimal getLevyBase() {
            return levyBase;
        }

        public List<LevyNight> getEligibleNights() {
            return eligibleNights;
        }

        public boolean isHostelworldUsesListedPrice() {
            return hostelworldUsesListedPrice;
        }

        public boolean isCanceledOrNoShow() {
            return canceledOrNoShow;
        }

        public BigDecimal getEligibleRatesTotal() {
            return eligibleNights.stream()
                    .map( LevyNight::getRate )
                    .reduce( BigDecimal.ZERO, BigDecimal::add );
        }
    }

    public static LevyCalculation calculate( Reservation reservation, Gson gson,
            LocalDate stayDateFrom ) {

        boolean canceledOrNoShow = reservation.isCanceledOrNoShow();

        if ( canceledOrNoShow ) {
            return new LevyCalculation( BigDecimal.ZERO.setScale( 2, RoundingMode.HALF_UP ),
                    BigDecimal.ZERO.setScale( 2, RoundingMode.HALF_UP ),
                    Collections.emptyList(), false, true );
        }

        Map<LocalDate, BigDecimal> ratesByDate = reservation.getRatesByDate( gson );
        List<LevyNight> eligibleNights = getEligibleNights( ratesByDate, reservation.getCheckoutDateAsLocalDate(), stayDateFrom );

        boolean hostelworldUsesListedPrice = false;
        BigDecimal levyBase;
        BigDecimal expectedLevy;

        if ( reservation.isBookingDotComBooking() ) {
            List<PersonNightRate> personNightRates = getEligiblePersonNightRates(
                    reservation, gson, reservation.getCheckoutDateAsLocalDate(), stayDateFrom );
            levyBase = BigDecimal.ZERO;
            expectedLevy = BigDecimal.ZERO;
            for ( PersonNightRate personNight : personNightRates ) {
                BigDecimal netPerGuest = personNight.getGrossRate()
                        .divide( BDC_GROSS_TO_NET, 10, RoundingMode.HALF_UP )
                        .setScale( 2, RoundingMode.HALF_UP );
                BigDecimal levyPerGuest = netPerGuest.multiply( BDC_INCLUSIVE_LEVY_RATE )
                        .setScale( 2, RoundingMode.HALF_UP );
                int guests = personNight.getGuestCount();
                levyBase = levyBase.add( netPerGuest.multiply( BigDecimal.valueOf( guests ) ) );
                expectedLevy = expectedLevy.add( levyPerGuest.multiply( BigDecimal.valueOf( guests ) ) );
            }
        }
        else if ( reservation.isHostelworldBooking() ) {
            BigDecimal eligibleTotal = eligibleNights.stream()
                    .map( LevyNight::getRate )
                    .reduce( BigDecimal.ZERO, BigDecimal::add );
            levyBase = eligibleTotal;
            BigDecimal priceListed = resolveHostelworldPriceListed( reservation );
            BigDecimal allNightsTotal = ratesByDate.values().stream()
                    .reduce( BigDecimal.ZERO, BigDecimal::add );
            if ( priceListed != null && allNightsTotal.compareTo( BigDecimal.ZERO ) > 0 ) {
                hostelworldUsesListedPrice = true;
                levyBase = priceListed.multiply( eligibleTotal )
                        .divide( allNightsTotal, 10, RoundingMode.HALF_UP );
            }
            expectedLevy = levyBase.multiply( LEVY_RATE ).setScale( 2, RoundingMode.HALF_UP );
        }
        else {
            levyBase = BigDecimal.ZERO;
            expectedLevy = BigDecimal.ZERO;
            for ( LevyNight night : eligibleNights ) {
                levyBase = levyBase.add( night.getRate() );
                expectedLevy = expectedLevy.add(
                        night.getRate().multiply( LEVY_RATE ).setScale( 2, RoundingMode.HALF_UP ) );
            }
        }

        return new LevyCalculation( expectedLevy.setScale( 2, RoundingMode.HALF_UP ),
                levyBase.setScale( 2, RoundingMode.HALF_UP ),
                eligibleNights, hostelworldUsesListedPrice, false );
    }

    static List<PersonNightRate> getEligiblePersonNightRates( Reservation reservation, Gson gson,
            LocalDate checkoutDate, LocalDate stayDateFrom ) {
        List<PersonNightRate> personNightRates = new ArrayList<>();
        if ( reservation.getBookingRooms() == null ) {
            return personNightRates;
        }
        for ( BookingRoom bookingRoom : reservation.getBookingRooms() ) {
            if ( bookingRoom.getDetailedRates() == null ) {
                continue;
            }
            JsonArray detailedRates = gson.fromJson( bookingRoom.getDetailedRates(), JsonArray.class );
            for ( int i = 0; i < detailedRates.size(); i++ ) {
                JsonObject rateLine = detailedRates.get( i ).getAsJsonObject();
                LocalDate date = LocalDate.parse( rateLine.get( "date" ).getAsString() );
                if ( date.isBefore( stayDateFrom ) || false == date.isBefore( checkoutDate ) ) {
                    continue;
                }
                BigDecimal rate = rateLine.get( "rate" ).getAsBigDecimal();
                int guestCount = resolveGuestCount( rateLine, reservation );
                personNightRates.add( new PersonNightRate( date, rate, guestCount ) );
            }
        }
        personNightRates.sort( Comparator.comparing( PersonNightRate::getDate ) );

        List<LocalDate> eligibleDates = personNightRates.stream()
                .map( PersonNightRate::getDate )
                .distinct()
                .sorted()
                .limit( MAX_LEVY_NIGHTS )
                .collect( Collectors.toList() );

        return personNightRates.stream()
                .filter( rate -> eligibleDates.contains( rate.getDate() ) )
                .collect( Collectors.toList() );
    }

    static int resolveGuestCount( JsonObject rateLine, Reservation reservation ) {
        int adults = rateLine.has( "adults" ) ? rateLine.get( "adults" ).getAsInt() : 0;
        int kids = rateLine.has( "kids" ) ? rateLine.get( "kids" ).getAsInt() : 0;
        int guestsOnLine = adults + kids;
        if ( guestsOnLine > 0 ) {
            return guestsOnLine;
        }
        int reservationGuests = reservation.getNumberOfGuests();
        return reservationGuests > 0 ? reservationGuests : 1;
    }

    static BigDecimal resolveHostelworldPriceListed( Reservation reservation ) {
        if ( reservation.getChannelPriceListed() != null ) {
            return reservation.getChannelPriceListed();
        }
        if ( reservation.getChannelBalance() != null && reservation.getChannelCommission() != null ) {
            return reservation.getChannelBalance().add( reservation.getChannelCommission() );
        }
        return null;
    }

    static List<LevyNight> getEligibleNights( Map<LocalDate, BigDecimal> ratesByDate,
            LocalDate checkoutDate, LocalDate stayDateFrom ) {
        List<LevyNight> eligibleNights = new ArrayList<>();
        for ( Map.Entry<LocalDate, BigDecimal> entry : ratesByDate.entrySet() ) {
            LocalDate night = entry.getKey();
            if ( false == night.isBefore( stayDateFrom ) && night.isBefore( checkoutDate ) ) {
                eligibleNights.add( new LevyNight( night, entry.getValue() ) );
            }
        }
        eligibleNights.sort( Comparator.comparing( LevyNight::getDate ) );

        if ( eligibleNights.size() > MAX_LEVY_NIGHTS ) {
            return eligibleNights.subList( 0, MAX_LEVY_NIGHTS );
        }
        return eligibleNights;
    }

    /**
     * Returns true if the stay includes at least one night on or after the levy start date.
     * The last night of a stay is the day before checkout.
     */
    public static boolean hasEligibleStayDates( LocalDate checkoutDate, LocalDate stayDateFrom ) {
        return checkoutDate.isAfter( stayDateFrom );
    }

    public static boolean isWithinTolerance( BigDecimal delta ) {
        return delta.abs().compareTo( TOLERANCE ) < 0;
    }

    public static String buildAdjustmentNote( LevyCalculation calculation ) {
        if ( calculation.isCanceledOrNoShow() ) {
            return "Reservation canceled/no-show - visitor levy not applicable. -RONBOT";
        }

        StringBuilder note = new StringBuilder( "Visitor levy only applies to the first 5 nights. " );
        note.append( "Charge for the first " ).append( calculation.getEligibleNights().size() ).append( " nights is " );
        note.append( calculation.getLevyBase() );
        if ( calculation.isHostelworldUsesListedPrice() ) {
            note.append( " (HWL price listed)" );
        }
        note.append( " @ 5% = " ).append( calculation.getExpectedLevy() );
        note.append( ". -RONBOT" );
        return note.toString();
    }

    public static boolean useInclusiveTax( Reservation reservation ) {
        return reservation.isBookingDotComBooking();
    }

    /**
     * Room VAT on a BDC inclusive folio: 20% of net accommodation per person-night,
     * rounded per person-night before summing (matches Cloudbeds folio posting).
     */
    public static BigDecimal calculateExpectedBdcRoomVat( Reservation reservation, Gson gson,
            LocalDate stayDateFrom ) {
        List<PersonNightRate> personNightRates = getEligiblePersonNightRates(
                reservation, gson, reservation.getCheckoutDateAsLocalDate(), stayDateFrom );
        BigDecimal expectedVat = BigDecimal.ZERO;
        for ( PersonNightRate personNight : personNightRates ) {
            BigDecimal netPerGuest = personNight.getGrossRate()
                    .divide( BDC_GROSS_TO_NET, 10, RoundingMode.HALF_UP )
                    .setScale( 2, RoundingMode.HALF_UP );
            BigDecimal vatPerGuest = netPerGuest.multiply( ROOM_VAT_RATE )
                    .setScale( 2, RoundingMode.HALF_UP );
            expectedVat = expectedVat.add(
                    vatPerGuest.multiply( BigDecimal.valueOf( personNight.getGuestCount() ) ) );
        }
        return expectedVat.setScale( 2, RoundingMode.HALF_UP );
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
