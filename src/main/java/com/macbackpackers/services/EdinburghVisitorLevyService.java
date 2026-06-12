
package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyCalculator.LevyCalculation;

@Service
public class EdinburghVisitorLevyService {

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

    @Value( "${evl.hwl.deposit.fraction:0.15}" )
    private BigDecimal hwlDepositFraction;

    @Value( "${evl.stay.date.from:2026-07-24}" )
    private String stayDateFrom;

    @Value( "${evl.booked.date.from:2025-10-01}" )
    private String bookedDateFrom;

    public void processVisitorLevyForBooking( WebClient webClient, String reservationId ) throws IOException {
        Reservation reservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        LevyCalculation calculation = EdinburghVisitorLevyCalculator.calculate(
                reservation, gson,
                LocalDate.parse( stayDateFrom ),
                LocalDate.parse( bookedDateFrom ),
                hwlDepositFraction );

        String taxLabel = EdinburghVisitorLevyCalculator.useInclusiveTax( reservation )
                ? inclusiveTaxLabel : exclusiveTaxLabel;
        String taxId = cloudbedsScraper.resolveTaxIdByLabel( webClient, taxLabel );

        BigDecimal currentLevy = cloudbedsScraper.getCurrentVisitorLevyTotal(
                webClient, reservation, exclusiveTaxLabel, inclusiveTaxLabel );
        BigDecimal expectedLevy = calculation.getExpectedLevy();
        BigDecimal delta = expectedLevy.subtract( currentLevy ).setScale( 2, RoundingMode.HALF_UP );

        LOGGER.info( "Reservation {}: expected levy={}, current levy={}, delta={}",
                reservationId, expectedLevy, currentLevy, delta );

        if ( EdinburghVisitorLevyCalculator.isWithinTolerance( delta ) ) {
            LOGGER.info( "Visitor levy already correct for reservation {}", reservationId );
            return;
        }

        String note = EdinburghVisitorLevyCalculator.buildAdjustmentNote( calculation );
        if ( delta.compareTo( BigDecimal.ZERO ) < 0 ) {
            cloudbedsScraper.adjustVisitorLevyCharge( webClient, reservation, taxId, delta.abs(), note );
        }
        else {
            cloudbedsScraper.addVisitorLevyCharge( webClient, reservation, taxId, delta );
        }
    }
}
