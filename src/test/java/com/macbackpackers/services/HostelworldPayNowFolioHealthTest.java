package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.cloudbeds.responses.BalanceDetails;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TaxBreakdownItem;
import com.macbackpackers.exceptions.UnrecoverableFault;

public class HostelworldPayNowFolioHealthTest {

    @Test
    public void healthyWhenFullyPaidIncludingEvl() {
        Reservation res = reservation( "34.43", "0.00", "1.72" );
        assertTrue( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void healthyWhenOnlyEvlRemains() {
        Reservation res = reservation( "34.43", "1.72", "1.72" );
        assertTrue( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void healthyWhenBalanceDueWithinToleranceAboveEvl() {
        // job 818669 shape: channel remittance left 6.11 vs EVL 6.08
        Reservation res = reservation( "99.63", "6.11", "6.08" );
        assertTrue( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void healthyWhenBalanceDueWithinToleranceBelowEvl() {
        Reservation res = reservation( "99.66", "6.05", "6.08" );
        assertTrue( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void healthyWhenBalanceDueAtToleranceBoundary() {
        Reservation res = reservation( "34.43", "1.82", "1.72" );
        assertTrue( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void unhealthyWhenUnpaid() {
        Reservation res = reservation( "0.00", "36.15", "1.72" );
        assertFalse( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void unhealthyWhenBalanceDueJustOutsideTolerance() {
        Reservation res = reservation( "34.43", "1.83", "1.72" );
        assertFalse( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void unhealthyWhenBalanceDueIsNeitherZeroNorEvl() {
        Reservation res = reservation( "34.43", "10.00", "1.72" );
        assertFalse( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void requireChannelBalance_returnsPositiveAmount() {
        Reservation res = new Reservation();
        res.setReservationId( "189870634" );
        res.setChannelBalance( new BigDecimal( "99.63" ) );
        assertThat( PaymentProcessorService.requireHostelworldPayNowChannelBalance( res ),
                comparesEqualTo( new BigDecimal( "99.63" ) ) );
    }

    @Test
    public void requireChannelBalance_rejectsNullOrNonPositive() {
        Reservation res = new Reservation();
        res.setReservationId( "189870634" );
        assertThrows( UnrecoverableFault.class,
                () -> PaymentProcessorService.requireHostelworldPayNowChannelBalance( res ) );
        res.setChannelBalance( BigDecimal.ZERO );
        assertThrows( UnrecoverableFault.class,
                () -> PaymentProcessorService.requireHostelworldPayNowChannelBalance( res ) );
        res.setChannelBalance( new BigDecimal( "-1.00" ) );
        assertThrows( UnrecoverableFault.class,
                () -> PaymentProcessorService.requireHostelworldPayNowChannelBalance( res ) );
    }

    private static Reservation reservation( String paid, String balanceDue, String evl ) {
        Reservation res = new Reservation();
        res.setPaidValue( new BigDecimal( paid ) );
        res.setBalanceDue( new BigDecimal( balanceDue ) );
        TaxBreakdownItem levy = new TaxBreakdownItem();
        levy.setName( "Edinburgh Visitor Levy" );
        levy.setAmount( new BigDecimal( evl ) );
        BalanceDetails balanceDetails = new BalanceDetails();
        balanceDetails.setTaxBreakdown( Collections.singletonList( levy ) );
        res.setBalanceDetails( balanceDetails );
        return res;
    }
}
