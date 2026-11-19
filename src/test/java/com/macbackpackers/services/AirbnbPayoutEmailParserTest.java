package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.macbackpackers.services.AirbnbPayoutEmailParser.LineItem;
import com.macbackpackers.services.AirbnbPayoutEmailParser.ParseResult;

public class AirbnbPayoutEmailParserTest {

    @Test
    public void parsesMultiCodePayoutBody() {
        String subject = "We sent a payout of £288.99 GBP";
        String body = ""
                + "<html><body>"
                + "<p>£288.99 GBP was sent today</p>"
                + "<p>Your money was sent on November 8</p>"
                + "<p>Macbackpackers Ltd, Checking 5674 (GBP)</p>"
                + "<p>Payout ID</p>"
                + "<p>Jacopo Bidese £47.32 GBP</p>"
                + "<p>HMSP3JP2MM</p>"
                + "<p>Julie Marceau £241.67 GBP</p>"
                + "<p>HM8J8H2KRM</p>"
                + "<p>Total paid: £288.99 GBP</p>"
                + "</body></html>";
        ParseResult result = AirbnbPayoutEmailParser.parse( subject, body );
        assertThat( result.isPayoutEmail(), is( true ) );
        assertThat( result.getLines(), hasSize( 2 ) );
        LineItem a = result.getLines().get( 0 );
        assertThat( a.getConfirmationCode(), is( "HMSP3JP2MM" ) );
        assertThat( a.getAmount(), comparesEqualTo( new BigDecimal( "47.32" ) ) );
        assertThat( a.getLineType(), is( AirbnbPayoutEmailParser.LINE_RESERVATION ) );
        assertThat( a.getGuestName(), containsString( "Jacopo" ) );
        LineItem b = result.getLines().get( 1 );
        assertThat( b.getConfirmationCode(), is( "HM8J8H2KRM" ) );
        assertThat( b.getAmount(), comparesEqualTo( new BigDecimal( "241.67" ) ) );
        assertThat( b.isReservation(), is( true ) );
    }

    @Test
    public void parsesSingleCodePayout() {
        String subject = "We sent a payout of £65.06 GBP";
        String body = "£65.06 GBP was sent today\n"
                + "Guest Name £65.06 GBP\n"
                + "HMD34KFXT3\n"
                + "Total paid: £65.06 GBP\n";
        ParseResult result = AirbnbPayoutEmailParser.parse( subject, body );
        assertThat( result.getLines(), hasSize( 1 ) );
        assertThat( result.getLines().get( 0 ).getConfirmationCode(), is( "HMD34KFXT3" ) );
        assertThat( result.getLines().get( 0 ).getAmount(), comparesEqualTo( new BigDecimal( "65.06" ) ) );
    }

    @Test
    public void parsesResolutionAdjustmentAsNegative() {
        // HMDMYQNX2Z Nov 10 bank payout: reservation remittance was earlier; this email claws back £30
        String subject = "We sent a payout of £81.53 GBP";
        String body = ""
                + "<p>£81.53 GBP was sent today</p>"
                + "<p>Payout ID</p>"
                + "<p>Rocky Henry -£30.00 GBP</p>"
                + "<p>Resolution Adjustment — 11/6/2026 - 11/8/2026</p>"
                + "<p>HMDMYQNX2Z</p>"
                + "<p>Riley Guerrero £58.30 GBP</p>"
                + "<p>HMBMPZKY2A</p>"
                + "<p>Kazuki Ishiyama £19.43 GBP</p>"
                + "<p>HM22HKQ44C</p>"
                + "<p>Jordan Eckermann £33.80 GBP</p>"
                + "<p>HMHSYZM35M</p>"
                + "<p>Total paid: £81.53 GBP</p>";
        ParseResult result = AirbnbPayoutEmailParser.parse( subject, body );
        assertThat( result.getLines(), hasSize( 4 ) );

        LineItem resolution = result.getLines().stream()
                .filter( l -> "HMDMYQNX2Z".equals( l.getConfirmationCode() ) )
                .findFirst().orElse( null );
        assertThat( resolution, notNullValue() );
        assertThat( resolution.getLineType(), is( AirbnbPayoutEmailParser.LINE_RESOLUTION ) );
        assertThat( resolution.getAmount(), comparesEqualTo( new BigDecimal( "-30.00" ) ) );

        LineItem reservation = result.getLines().stream()
                .filter( l -> "HMBMPZKY2A".equals( l.getConfirmationCode() ) )
                .findFirst().orElse( null );
        assertThat( reservation, notNullValue() );
        assertThat( reservation.isReservation(), is( true ) );
        assertThat( reservation.getAmount(), comparesEqualTo( new BigDecimal( "58.30" ) ) );
    }

    @Test
    public void parsesPriorReservationPayoutForSameCode() {
        String subject = "We sent a payout of £732.61 GBP";
        String body = "Rocky Henry £732.61 GBP\nHMDMYQNX2Z\nTotal paid: £732.61 GBP\n";
        ParseResult result = AirbnbPayoutEmailParser.parse( subject, body );
        assertThat( result.getLines(), hasSize( 1 ) );
        assertThat( result.getLines().get( 0 ).getConfirmationCode(), is( "HMDMYQNX2Z" ) );
        assertThat( result.getLines().get( 0 ).getAmount(), comparesEqualTo( new BigDecimal( "732.61" ) ) );
        assertThat( result.getLines().get( 0 ).isReservation(), is( true ) );
    }

    @Test
    public void ignoresNonPayoutSubjects() {
        ParseResult result = AirbnbPayoutEmailParser.parse(
                "Canceled: Reservation HME9AQJKHJ for December 26 – 28",
                "These dates are now open\nHME9AQJKHJ\n" );
        assertThat( result.isPayoutEmail(), is( false ) );
        assertThat( result.getLines(), empty() );
    }

    @Test
    public void parsesCancelConfirmationCodeFromSubject() {
        assertThat( AirbnbPayoutEmailParser.isCancelSubject(
                "Canceled: Reservation HME9AQJKHJ for December 26 – 28" ), is( true ) );
        assertThat( AirbnbPayoutEmailParser.parseCancelConfirmationCode(
                "Canceled: Reservation HME9AQJKHJ for December 26 – 28" ), is( "HME9AQJKHJ" ) );
        assertThat( AirbnbPayoutEmailParser.parseCancelConfirmationCode(
                "Reservation canceled: Oct 20 – 21, 2026" ), nullValue() );
        assertThat( AirbnbPayoutEmailParser.isPayoutSubject( "We sent a payout of £12.00 GBP" ), is( true ) );
        assertThat( AirbnbPayoutEmailParser.isPayoutSubject( "Reservation confirmed - Bob" ), is( false ) );
    }
}
