package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.AirbnbPayment;
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

    private static TransactionRecord payment( String paymentType, String paid, String notes ) {
        TransactionRecord t = new TransactionRecord();
        t.setType( "payment" );
        t.setPaymentType( paymentType );
        t.setPaid( paid );
        t.setNotes( notes );
        t.setDescription( notes );
        return t;
    }
}
