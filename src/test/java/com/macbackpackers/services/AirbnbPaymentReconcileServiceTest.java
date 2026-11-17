package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.AirbnbPayment;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.jobs.AirbnbImportTransactionsJob;
import com.macbackpackers.scrapers.CloudbedsScraper;

public class AirbnbPaymentReconcileServiceTest {

    @Test
    public void netAirbnbPrepaid_sumsCreditsAndRefunds() {
        TransactionRecord credit = payment( "airbnb", "27.88", CloudbedsScraper.AIRBNB_PREPAID_PAYMENT_DESCRIPTION );
        TransactionRecord refund = payment( "airbnb", "-27.88",
                CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION );
        BigDecimal net = AirbnbPaymentReconcileService.netAirbnbPrepaid( Arrays.asList( credit, refund ) );
        assertThat( net, comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );
    }

    @Test
    public void unsafeCredits_ignoreOnsiteTerminalAndAirbnb() {
        TransactionRecord airbnb = payment( "airbnb", "19.43", CloudbedsScraper.AIRBNB_PREPAID_PAYMENT_DESCRIPTION );
        TransactionRecord terminal = payment( "OnsiteTerminal", "5.00", "Onsite Terminal" );
        TransactionRecord card = payment( "cards", "10.00", "Visa" );
        List<TransactionRecord> unsafe = AirbnbPaymentReconcileService.unsafeNonAirbnbPaymentCredits(
                Arrays.asList( airbnb, terminal, card ) );
        assertThat( unsafe, hasSize( 1 ) );
        assertThat( unsafe.get( 0 ).getPaymentType(), is( "cards" ) );
    }

    @Test
    public void hasResolutionRefund_matchesClawbackNote() {
        TransactionRecord refund = payment( "airbnb", "-27.88",
                CloudbedsScraper.AIRBNB_RESOLUTION_REFUND_DESCRIPTION );
        assertThat( AirbnbPaymentReconcileService.hasResolutionRefund(
                Collections.singletonList( refund ), new BigDecimal( "27.88" ) ), is( true ) );
        assertThat( AirbnbPaymentReconcileService.hasResolutionRefund(
                Collections.singletonList( refund ), new BigDecimal( "10.00" ) ), is( false ) );
    }

    @Test
    public void resolveRoomRatePlusVat_usesFolioWhenPresent() {
        TransactionRecord rate = folioLine( "rate", "Room rate", "23.33" );
        TransactionRecord vat = folioLine( "tax", "VAT", "4.67" );
        Reservation res = new Reservation();
        res.setChannelPriceListed( new BigDecimal( "999.00" ) );
        BigDecimal total = AirbnbPaymentReconcileService.resolveRoomRatePlusVat(
                Arrays.asList( rate, vat ), res );
        assertThat( total, comparesEqualTo( new BigDecimal( "28.00" ) ) );
    }

    @Test
    public void resolveRoomRatePlusVat_fallsBackToChannelPriceWhenFolioEmptyOnLiveBooking() {
        Reservation res = new Reservation();
        res.setReservationId( "190181178" );
        res.setStatus( "confirmed" );
        res.setChannelPriceListed( new BigDecimal( "281.00" ) );
        BigDecimal total = AirbnbPaymentReconcileService.resolveRoomRatePlusVat(
                Collections.emptyList(), res );
        assertThat( total, comparesEqualTo( new BigDecimal( "281.00" ) ) );
    }

    @Test
    public void resolveRoomRatePlusVat_doesNotFallBackWhenCanceledOrNoShow() {
        Reservation canceled = new Reservation();
        canceled.setStatus( "canceled" );
        canceled.setChannelPriceListed( new BigDecimal( "187.00" ) );
        assertThat( AirbnbPaymentReconcileService.resolveRoomRatePlusVat(
                Collections.emptyList(), canceled ),
                comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );

        Reservation noShow = new Reservation();
        noShow.setStatus( "no_show" );
        noShow.setChannelPriceListed( new BigDecimal( "23.00" ) );
        assertThat( AirbnbPaymentReconcileService.resolveRoomRatePlusVat(
                Collections.emptyList(), noShow ),
                comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );
    }

    @Test
    public void isLiveBookingForRoomRateFallback_onlyConfirmedCheckedInCheckedOut() {
        assertThat( AirbnbPaymentReconcileService.isLiveBookingForRoomRateFallback(
                reservationWithStatus( "confirmed" ) ), is( true ) );
        assertThat( AirbnbPaymentReconcileService.isLiveBookingForRoomRateFallback(
                reservationWithStatus( "checked_in" ) ), is( true ) );
        assertThat( AirbnbPaymentReconcileService.isLiveBookingForRoomRateFallback(
                reservationWithStatus( "checked_out" ) ), is( true ) );
        assertThat( AirbnbPaymentReconcileService.isLiveBookingForRoomRateFallback(
                reservationWithStatus( "canceled" ) ), is( false ) );
        assertThat( AirbnbPaymentReconcileService.isLiveBookingForRoomRateFallback(
                reservationWithStatus( "no_show" ) ), is( false ) );
    }

    @Test
    public void hasServiceFeeAdjust_matchesRonbotNote() {
        TransactionRecord adjust = folioLine( "adjustment", "Room Revenue - Manual", "-8.68" );
        adjust.setNotes( CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE );
        assertThat( AirbnbPaymentReconcileService.hasServiceFeeAdjust(
                Collections.singletonList( adjust ) ), is( true ) );
        assertThat( AirbnbPaymentReconcileService.hasServiceFeeAdjust(
                Collections.emptyList() ), is( false ) );
    }

    @Test
    public void sumFolioRoomRatePlusVat_ignoresServiceFeeAdjust() {
        TransactionRecord rate = folioLine( "rate", "Room rate", "20.00" );
        TransactionRecord adjust = folioLine( "adjustment", "Room Revenue - Manual", "-3.57" );
        adjust.setNotes( CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE );
        assertThat( AirbnbPaymentReconcileService.sumFolioRoomRatePlusVat(
                Arrays.asList( rate, adjust ) ),
                comparesEqualTo( new BigDecimal( "20.00" ) ) );
        assertThat( AirbnbPaymentReconcileService.sumFolioRoomRatePlusVat( Collections.emptyList() ),
                comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );
    }

    @Test
    public void resolveRoomRatePlusVat_staysZeroWithoutFolioOrChannel() {
        Reservation res = new Reservation();
        res.setStatus( "confirmed" );
        BigDecimal total = AirbnbPaymentReconcileService.resolveRoomRatePlusVat(
                Collections.emptyList(), res );
        assertThat( total, comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );
    }

    @Test
    public void knownPatterns_coverHsh2026Cases() {
        AirbnbPayment reservationOnly = new AirbnbPayment();
        reservationOnly.setHasReservation( "Y" );
        reservationOnly.setAmount( new BigDecimal( "19.43" ) );
        reservationOnly.setGrossEarnings( new BigDecimal( "23.00" ) );
        reservationOnly.setNetAmount( new BigDecimal( "19.43" ) );
        reservationOnly.setPostPaymentField( AirbnbPayment.FIELD_AMOUNT );
        assertThat( AirbnbImportTransactionsJob.isKnownPattern( reservationOnly ), is( true ) );

        AirbnbPayment resolution = new AirbnbPayment();
        resolution.setHasReservation( "Y" );
        resolution.setHasResolutionAdjustment( "Y" );
        resolution.setNetAmount( BigDecimal.ZERO.setScale( 2 ) );
        assertThat( AirbnbImportTransactionsJob.isKnownPattern( resolution ), is( true ) );

        AirbnbPayment cancellation = new AirbnbPayment();
        cancellation.setHasCancellationFee( "Y" );
        cancellation.setAmount( new BigDecimal( "-37.57" ) );
        assertThat( AirbnbImportTransactionsJob.isKnownPattern( cancellation ), is( true ) );
    }

    private static Reservation reservationWithStatus( String status ) {
        Reservation res = new Reservation();
        res.setStatus( status );
        return res;
    }

    private static TransactionRecord payment( String paymentType, String paid, String notes ) {
        TransactionRecord t = new TransactionRecord();
        t.setType( "payment" );
        t.setPaymentType( paymentType );
        t.setPaid( paid );
        t.setNotes( notes );
        t.setDescription( notes );
        return t;
    }

    private static TransactionRecord folioLine( String type, String description, String credit ) {
        TransactionRecord t = new TransactionRecord();
        t.setType( type );
        t.setDescription( description );
        t.setCredit( credit );
        return t;
    }
}
