package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.cloudbeds.responses.BalanceDetails;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TaxBreakdownItem;
import com.macbackpackers.services.EdinburghVisitorLevyCalculator.LevyCalculation;
import com.macbackpackers.services.EdinburghVisitorLevyCalculator.LevyNight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;

public class EdinburghVisitorLevyCalculatorTest {

    private static final LocalDate STAY_DATE_FROM = LocalDate.of( 2026, 7, 24 );

    private Gson gson;
    private Reservation baseReservation;

    @BeforeEach
    public void setUp() throws IOException {
        gson = new GsonBuilder()
                .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES )
                .create();
        String json = IOUtils.toString(
                getClass().getClassLoader().getResourceAsStream( "get_reservation_20220731.json" ),
                StandardCharsets.UTF_8 );
        baseReservation = gson.fromJson( json, Reservation.class );
    }

    @Test
    public void testDirectBookingLevyOnThreeNights() {
        Reservation reservation = reservationWithRates(
                "Walk-In", "2026-10-01", "2026-10-04", "2025-11-01",
                rateLine( "2026-10-01", "55.00" ),
                rateLine( "2026-10-02", "55.00" ),
                rateLine( "2026-10-03", "55.00" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "8.25" ) ) );
        assertThat( calculation.getEligibleNights().size(), is( 3 ) );
        assertThat( EdinburghVisitorLevyCalculator.useInclusiveTax( reservation ), is( false ) );
    }

    @Test
    public void testBookingDotComUsesInclusiveTax() {
        Reservation reservation = reservationWithRates(
                "Booking.com", "2026-10-01", "2026-10-02", "2025-11-01",
                rateLine( "2026-10-01", "165.00" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "7.86" ) ) );
        assertThat( EdinburghVisitorLevyCalculator.useInclusiveTax( reservation ), is( true ) );
    }

    @Test
    public void testBookingDotComTwoGuestsTwoNightsMatchesCloudbedsFolio() {
        Reservation reservation = reservationWithRates(
                "Booking.com", "2026-10-01", "2026-10-03", "2025-11-01",
                rateLine( "2026-10-01", "47.20", 2 ),
                rateLine( "2026-10-02", "47.20", 2 ) );
        reservation.setAdultsNumber( 2 );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "9.00" ) ) );
        assertThat( calculation.getLevyBase(), comparesEqualTo( new BigDecimal( "149.84" ) ) );
        assertThat( EdinburghVisitorLevyCalculator.calculateExpectedBdcRoomVat(
                        reservation, gson, STAY_DATE_FROM ),
                comparesEqualTo( new BigDecimal( "29.96" ) ) );
    }

    @Test
    public void testBookingDotComFiveNightsTwoGuestsMatchesCloudbedsFolio() {
        Reservation reservation = reservationWithRates(
                "Booking.com", "2026-07-26", "2026-07-31", "2025-11-01",
                rateLine( "2026-07-26", "26.15", 2 ),
                rateLine( "2026-07-27", "30.82", 2 ),
                rateLine( "2026-07-28", "30.82", 2 ),
                rateLine( "2026-07-29", "30.82", 2 ),
                rateLine( "2026-07-30", "30.82", 2 ) );
        reservation.setAdultsNumber( 2 );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "14.26" ) ) );
        assertThat( calculation.getLevyBase(), comparesEqualTo( new BigDecimal( "237.18" ) ) );
    }

    @Test
    public void testAgodaChannelCollectUsesInclusiveTax() {
        Reservation reservation = reservationWithRates(
                "Agoda (Channel Collect Booking)", "2026-10-01", "2026-10-02", "2025-11-01",
                rateLine( "2026-10-01", "165.00" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "7.86" ) ) );
        assertThat( EdinburghVisitorLevyCalculator.useInclusiveTax( reservation ), is( true ) );
    }

    @Test
    public void testAgodaPricelineHotelCollectMatchesBdcInclusiveCalculation() {
        Reservation reservation = reservationWithRates(
                "Agoda / Priceline (Hotel Collect Booking)", "2026-10-01", "2026-10-03", "2025-11-01",
                rateLine( "2026-10-01", "47.20", 2 ),
                rateLine( "2026-10-02", "47.20", 2 ) );
        reservation.setAdultsNumber( 2 );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "9.00" ) ) );
        assertThat( calculation.getLevyBase(), comparesEqualTo( new BigDecimal( "149.84" ) ) );
        assertThat( EdinburghVisitorLevyCalculator.useInclusiveTax( reservation ), is( true ) );
    }

    @Test
    public void testHostelworldUsesPriceListed() {
        Reservation reservation = reservationWithRates(
                "Hostelworld", "2026-10-01", "2026-10-04", "2025-11-01",
                rateLine( "2026-10-01", "51.41" ),
                rateLine( "2026-10-02", "51.41" ),
                rateLine( "2026-10-03", "51.41" ) );
        reservation.setChannelPriceListed( new BigDecimal( "181.45" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.isHostelworldUsesListedPrice(), is( true ) );
        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "9.07" ) ) );
    }

    @Test
    public void testHostelworldPriceListedFromChannelBalanceAndCommission() {
        Reservation reservation = reservationWithRates(
                "Hostelworld", "2026-10-01", "2026-10-03", "2025-11-01",
                rateLine( "2026-10-01", "35.80" ),
                rateLine( "2026-10-02", "44.07" ) );
        reservation.setChannelBalance( new BigDecimal( "79.87" ) );
        reservation.setChannelCommission( new BigDecimal( "14.09" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.isHostelworldUsesListedPrice(), is( true ) );
        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "4.70" ) ) );
    }

    @Test
    public void testHostelworldPriceListedProratedForPartialEligibility() {
        Reservation reservation = reservationWithRates(
                "Hostelworld", "2026-07-22", "2026-07-26", "2025-11-01",
                rateLine( "2026-07-22", "50.00" ),
                rateLine( "2026-07-23", "50.00" ),
                rateLine( "2026-07-24", "50.00" ),
                rateLine( "2026-07-25", "50.00" ) );
        reservation.setChannelPriceListed( new BigDecimal( "235.29" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getEligibleNights().size(), is( 2 ) );
        assertThat( calculation.isHostelworldUsesListedPrice(), is( true ) );
        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "5.88" ) ) );
    }

    @Test
    public void testStayStraddlingLevyStartDate() {
        Reservation reservation = reservationWithRates(
                "Walk-In", "2026-07-22", "2026-07-26", "2025-11-01",
                rateLine( "2026-07-22", "100.00" ),
                rateLine( "2026-07-23", "100.00" ),
                rateLine( "2026-07-24", "100.00" ),
                rateLine( "2026-07-25", "100.00" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getEligibleNights().stream().map( LevyNight::getDate ).collect( Collectors.toList() ),
                is( java.util.Arrays.asList(
                        LocalDate.of( 2026, 7, 24 ),
                        LocalDate.of( 2026, 7, 25 ) ) ) );
        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "10.00" ) ) );
    }

    @Test
    public void testCapsLevyAtFiveNights() {
        Reservation reservation = reservationWithRates(
                "Walk-In", "2026-10-01", "2026-10-08", "2025-11-01",
                rateLine( "2026-10-01", "60.00" ),
                rateLine( "2026-10-02", "60.00" ),
                rateLine( "2026-10-03", "60.00" ),
                rateLine( "2026-10-04", "60.00" ),
                rateLine( "2026-10-05", "60.00" ),
                rateLine( "2026-10-06", "60.00" ),
                rateLine( "2026-10-07", "60.00" ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getEligibleNights().size(), is( 5 ) );
        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "15.00" ) ) );
    }

    @Test
    public void testCanceledReservationHasZeroLevy() {
        Reservation reservation = reservationWithRates(
                "Walk-In", "2026-10-01", "2026-10-02", "2025-11-01",
                rateLine( "2026-10-01", "165.00" ) );
        reservation.setStatus( "canceled" );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );
        assertThat( calculation.isCanceledOrNoShow(), is( true ) );
        assertThat( EdinburghVisitorLevyCalculator.buildAdjustmentNote( calculation ),
                is( "Reservation canceled/no-show - visitor levy not applicable. -RONBOT" ) );
    }

    @Test
    public void testNoShowReservationHasZeroLevy() {
        Reservation reservation = reservationWithRates(
                "Walk-In", "2026-10-01", "2026-10-02", "2025-11-01",
                rateLine( "2026-10-01", "165.00" ) );
        reservation.setStatus( "no_show" );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );
        assertThat( calculation.isCanceledOrNoShow(), is( true ) );
    }

    @Test
    public void testResolveTaxIdFromPropertyContentFixture() throws IOException {
        String json = IOUtils.toString(
                getClass().getClassLoader().getResourceAsStream( "get_property_content_taxes.json" ),
                StandardCharsets.UTF_8 );
        JsonObject propertyContent = gson.fromJson( json, JsonObject.class );

        String exclusiveTaxId = propertyContent.get( "taxes" ).getAsJsonArray().asList().stream()
                .map( e -> e.getAsJsonObject() )
                .filter( tax -> "Edinburgh Visitor Levy 2026".equals(
                        tax.get( "name_langs" ).getAsJsonObject().get( "en" ).getAsString() ) )
                .map( tax -> tax.get( "id" ).getAsString() )
                .findFirst()
                .orElseThrow( () -> new IllegalStateException( "tax not found" ) );

        assertThat( exclusiveTaxId, is( "824186" ) );
    }

    @Test
    public void testVisitorLevyTotalFromBalanceDetailsTaxBreakdown() {
        TaxBreakdownItem inclusive = new TaxBreakdownItem();
        inclusive.setName( "Edinburgh Visitor Levy (Inclusive)" );
        inclusive.setAmount( new BigDecimal( "-0.12" ) );

        TaxBreakdownItem exclusive = new TaxBreakdownItem();
        exclusive.setName( "Edinburgh Visitor Levy 2026" );
        exclusive.setAmount( new BigDecimal( "1.56" ) );

        TaxBreakdownItem vat = new TaxBreakdownItem();
        vat.setName( "VAT" );
        vat.setAmount( new BigDecimal( "5.50" ) );

        BalanceDetails balanceDetails = new BalanceDetails();
        balanceDetails.setTaxBreakdown( Arrays.asList( inclusive, exclusive, vat ) );
        baseReservation.setBalanceDetails( balanceDetails );

        assertThat( baseReservation.getVisitorLevyTotal(
                "Edinburgh Visitor Levy 2026", "Edinburgh Visitor Levy (Inclusive)" ),
                comparesEqualTo( new BigDecimal( "1.44" ) ) );
    }

    @Test
    public void testBalanceDueExcludingVisitorLevy() {
        TaxBreakdownItem exclusive = new TaxBreakdownItem();
        exclusive.setName( "Edinburgh Visitor Levy 2026" );
        exclusive.setAmount( new BigDecimal( "1.56" ) );

        BalanceDetails balanceDetails = new BalanceDetails();
        balanceDetails.setTaxBreakdown( Arrays.asList( exclusive ) );
        baseReservation.setBalanceDetails( balanceDetails );
        baseReservation.setBalanceDue( new BigDecimal( "50.00" ) );

        assertThat( EdinburghVisitorLevyCalculator.getBalanceDueExcludingVisitorLevy( baseReservation ),
                comparesEqualTo( new BigDecimal( "48.44" ) ) );
    }

    @Test
    public void testWithinTolerance() {
        assertThat( EdinburghVisitorLevyCalculator.isWithinTolerance( new BigDecimal( "0.005" ) ), is( true ) );
        assertThat( EdinburghVisitorLevyCalculator.isWithinTolerance( new BigDecimal( "0.02" ) ), is( false ) );
    }

    @Test
    public void testBookingDotComSingleRoomFallsBackToReservationGuestCount() {
        Reservation reservation = reservationWithRates(
                "Booking.com", "2026-10-01", "2026-10-03", "2025-11-01",
                rateLine( "2026-10-01", "47.20", 0 ),
                rateLine( "2026-10-02", "47.20", 0 ) );
        reservation.setAdultsNumber( 2 );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "9.00" ) ) );
    }

    @Test
    public void testBookingDotComMultiRoomUsesOneGuestPerBedNotReservationTotal() {
        String detailedRates = "[" + String.join( ",",
                rateLine( "2026-08-24", "52.16", 0 ),
                rateLine( "2026-08-25", "52.16", 0 ) ) + "]";
        Reservation reservation = gson.fromJson( gson.toJson( baseReservation ), Reservation.class );
        reservation.setSourceName( "Booking.com" );
        reservation.setCheckinDate( "2026-08-24" );
        reservation.setCheckoutDate( "2026-08-26" );
        reservation.setBookingDateHotelTime( "2026-06-21 12:00:00" );
        reservation.setStatus( "confirmed" );
        reservation.setAdultsNumber( 5 );

        BookingRoom template = reservation.getBookingRooms().get( 0 );
        template.setDetailedRates( detailedRates );
        template.setAdults( 1 );
        template.setKids( 0 );
        reservation.setBookingRooms( Arrays.asList(
                copyBookingRoom( template, "1" ),
                copyBookingRoom( template, "2" ),
                copyBookingRoom( template, "3" ),
                copyBookingRoom( template, "4" ),
                copyBookingRoom( template, "5" ) ) );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson, STAY_DATE_FROM );

        assertThat( calculation.getExpectedLevy(), comparesEqualTo( new BigDecimal( "24.80" ) ) );
        assertThat( EdinburghVisitorLevyCalculator.calculateExpectedBdcRoomVat(
                        reservation, gson, STAY_DATE_FROM ),
                comparesEqualTo( new BigDecimal( "82.80" ) ) );
    }

    @Test
    public void testHasEligibleStayDates() {
        assertThat( EdinburghVisitorLevyCalculator.hasEligibleStayDates(
                LocalDate.of( 2026, 7, 24 ), STAY_DATE_FROM ), is( false ) );
        assertThat( EdinburghVisitorLevyCalculator.hasEligibleStayDates(
                LocalDate.of( 2026, 7, 25 ), STAY_DATE_FROM ), is( true ) );
    }

    private Reservation reservationWithRates( String sourceName, String checkin, String checkout,
            String bookingDate, String... detailedRates ) {
        Reservation reservation = gson.fromJson( gson.toJson( baseReservation ), Reservation.class );
        reservation.setSourceName( sourceName );
        reservation.setCheckinDate( checkin );
        reservation.setCheckoutDate( checkout );
        reservation.setBookingDateHotelTime( bookingDate + " 12:00:00" );
        reservation.setStatus( "confirmed" );
        reservation.setChannelPriceListed( null );
        reservation.setChannelBalance( null );
        reservation.setChannelCommission( null );
        reservation.getBookingRooms().get( 0 ).setDetailedRates(
                "[" + String.join( ",", detailedRates ) + "]" );
        return reservation;
    }

    private String rateLine( String date, String rate ) {
        return rateLine( date, rate, 1 );
    }

    private String rateLine( String date, String rate, int adults ) {
        return String.format( "{\"date\":\"%s\",\"rate\":%s,\"adults\":%d,\"kids\":0}", date, rate, adults );
    }

    private BookingRoom copyBookingRoom( BookingRoom template, String suffix ) {
        BookingRoom room = gson.fromJson( gson.toJson( template ), BookingRoom.class );
        room.setRoomIdentifier( template.getRoomIdentifier() + "-" + suffix );
        return room;
    }
}
