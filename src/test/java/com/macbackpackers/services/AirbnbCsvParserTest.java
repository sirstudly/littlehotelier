package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.AirbnbPayment;
import com.macbackpackers.services.AirbnbCsvParser.ParseResult;

public class AirbnbCsvParserTest {

    private static final String HEADER =
            "Date,Type,Confirmation Code,Booking date,Start date,End date,Guest,Amount,Service fee,Gross earnings\n";

    @Test
    public void parsesReservationOnly() throws Exception {
        String csv = HEADER
                + "11/06/2026,Reservation,HM9J4DACMM,11/03/2026,11/05/2026,11/06/2026,Anne Jarvis,19.43,3.57,23.00\n"
                + "11/06/2026,Payout,,,,,,,41.06,,\n";
        ParseResult result = AirbnbCsvParser.parse( csv );
        assertThat( result.getPayments(), hasSize( 1 ) );
        AirbnbPayment p = result.getPayments().get( 0 );
        assertThat( p.getConfirmationCode(), is( "HM9J4DACMM" ) );
        assertThat( p.getAmount(), comparesEqualTo( new BigDecimal( "19.43" ) ) );
        assertThat( p.getServiceFee(), comparesEqualTo( new BigDecimal( "3.57" ) ) );
        assertThat( p.getGrossEarnings(), comparesEqualTo( new BigDecimal( "23.00" ) ) );
        assertThat( p.getHasReservation(), is( "Y" ) );
        assertThat( AirbnbCsvParser.isAutoEligible( p ), is( true ) );
    }

    @Test
    public void consolidatesResolutionClawback() throws Exception {
        String csv = HEADER
                + "08/27/2026,Resolution Adjustment,HM5HZDPPWX,,07/29/2026,07/30/2026,Tyurker Okur,-27.88,,-27.88\n"
                + "08/01/2026,Reservation,HM5HZDPPWX,07/29/2026,07/29/2026,07/30/2026,Tyurker Okur,27.88,5.12,33.00\n";
        ParseResult result = AirbnbCsvParser.parse( csv );
        assertThat( result.getPayments(), hasSize( 1 ) );
        AirbnbPayment p = result.getPayments().get( 0 );
        assertThat( p.getNetAmount(), comparesEqualTo( BigDecimal.ZERO.setScale( 2 ) ) );
        assertThat( p.getHasResolutionAdjustment(), is( "Y" ) );
        assertThat( AirbnbCsvParser.isAutoEligible( p ), is( false ) );
        assertThat( AirbnbImportTransactionsJobKnownPattern.isKnown( p ), is( true ) );
    }

    @Test
    public void cancellationFeeOnlyNotAutoEligible() throws Exception {
        String csv = HEADER
                + "10/21/2026,Cancellation Fee,HMASJQ9ZJ2,,10/20/2026,10/21/2026,Vipin,-37.57,0.00,\n";
        ParseResult result = AirbnbCsvParser.parse( csv );
        AirbnbPayment p = result.getPayments().get( 0 );
        assertThat( p.getHasCancellationFee(), is( "Y" ) );
        assertThat( p.getHasReservation(), is( "N" ) );
        assertThat( AirbnbCsvParser.isAutoEligible( p ), is( false ) );
    }

    @Test
    public void pre2026StartDateIsBeforeScope() throws Exception {
        String csv = HEADER
                + "12/15/2025,Reservation,HMOLD2025,12/01/2025,12/14/2025,12/15/2025,Old Guest,10.00,1.50,11.50\n"
                + "01/02/2026,Reservation,HMNEW2026,01/01/2026,01/01/2026,01/02/2026,New Guest,20.00,3.00,23.00\n";
        ParseResult result = AirbnbCsvParser.parse( csv );
        assertThat( result.getPayments(), hasSize( 2 ) );
        AirbnbPayment oldRow = result.getPayments().stream()
                .filter( p -> "HMOLD2025".equals( p.getConfirmationCode() ) ).findFirst().orElse( null );
        AirbnbPayment newRow = result.getPayments().stream()
                .filter( p -> "HMNEW2026".equals( p.getConfirmationCode() ) ).findFirst().orElse( null );
        assertThat( oldRow, notNullValue() );
        assertThat( newRow, notNullValue() );
        assertThat( com.macbackpackers.jobs.AirbnbImportTransactionsJob.isBeforeScope( oldRow.getStartDate() ),
                is( true ) );
        assertThat( com.macbackpackers.jobs.AirbnbImportTransactionsJob.isBeforeScope( newRow.getStartDate() ),
                is( false ) );
    }

    /** Access package-visible helper without coupling test to job class load quirks. */
    static final class AirbnbImportTransactionsJobKnownPattern {
        static boolean isKnown( AirbnbPayment p ) {
            return com.macbackpackers.jobs.AirbnbImportTransactionsJob.isKnownPattern( p );
        }
    }
}
