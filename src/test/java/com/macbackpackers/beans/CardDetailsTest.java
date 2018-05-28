
package com.macbackpackers.beans;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class CardDetailsTest {

    @Test
    public void testGetCloudbedsCardTypeFromBinRange() throws Exception {
        assertCardType( "5105105105105100", "master" );
        assertCardType( "4111111111111111", "visa" );
        assertCardType( "4012888888881881", "visa" );
        assertCardType( "5105105444444444", "master" );
        assertCardType( "2221105105105100", "master" );
        assertCardType( "5005105105105100", "maestro" );
        assertCardType( "5605105105105100", "maestro" );
        assertCardType( "5705105105105100", "maestro" );
        assertCardType( "5805105105105100", "maestro" );
        assertCardType( "2305105444444444", "master" );
        assertCardType( "2405105444444444", "master" );
        assertCardType( "2505105444444444", "master" );
        assertCardType( "2605105444444444", "master" );
        assertCardType( "2705105444444444", "master" );
        assertCardType( "5205105444444444", "master" );
        assertCardType( "5305105444444444", "master" );
        assertCardType( "5405105444444444", "master" );
        assertCardType( "5505105444444444", "master" );
        assertCardTypeThrowsException( "XXXXXXXXXXXX" );
        assertCardTypeThrowsException( "5905105444444444" );
        assertCardTypeThrowsException( "2805105444444444" );
        assertCardTypeThrowsException( "2905105444444444" );
        assertCardTypeThrowsException( "3123412341234123" );
        assertCardTypeThrowsException( "7777444477774444" );
    }

    private static void assertCardType( String cardnum, String cardtype ) {
        CardDetails cd = new CardDetails();
        cd.setCardNumber( cardnum );
        Assert.assertThat( cd.getCloudbedsCardTypeFromBinRange(), Matchers.is( cardtype ) );
    }

    private static void assertCardTypeThrowsException( String cardnum ) {
        CardDetails cd = new CardDetails();
        cd.setCardNumber( cardnum );
        try {
            String cardType = cd.getCloudbedsCardTypeFromBinRange();
            Assert.fail( "Expected exception, observed " + cardType );
        }
        catch ( IllegalArgumentException ex ) {
            // expected
        }
    }
}
