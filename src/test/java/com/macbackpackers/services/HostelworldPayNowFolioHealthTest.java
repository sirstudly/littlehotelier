package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.cloudbeds.responses.BalanceDetails;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TaxBreakdownItem;

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
    public void unhealthyWhenUnpaid() {
        Reservation res = reservation( "0.00", "36.15", "1.72" );
        assertFalse( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
    }

    @Test
    public void unhealthyWhenBalanceDueIsNeitherZeroNorEvl() {
        Reservation res = reservation( "34.43", "10.00", "1.72" );
        assertFalse( PaymentProcessorService.isHealthyChannelCollectPrepaidFolio( res ) );
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
