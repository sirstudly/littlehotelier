package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.exceptions.UnrecoverableFault;

public class HostelworldPayNowAppStateParseTest {

    private HostelworldScraper scraper;

    @BeforeEach
    public void setUp() throws Exception {
        scraper = new HostelworldScraper();
        // gson field is package-autowired; inject via reflection for unit parse tests
        java.lang.reflect.Field gsonField = HostelworldScraper.class.getDeclaredField( "gson" );
        gsonField.setAccessible( true );
        gsonField.set( scraper, new Gson() );
    }

    @Test
    public void extractAppStateBookingDetails_readsPayNowGbpBalanceDueNow() throws Exception {
        JsonObject details = scraper.extractAppStateBookingDetails( fixture( "hostelworld_paynow_app_state.html" ) );
        assertThat( details.get( "type" ).getAsString(), is( "PAYNOW" ) );
        assertThat( details.get( "currency" ).getAsString(), is( "GBP" ) );
        assertThat( new BigDecimal( details.get( "balanceDueNow" ).getAsString() ),
                comparesEqualTo( new BigDecimal( "28.23" ) ) );
        assertThat( scraper.resolvePayNowAmountGbp( details, "551-580788801" ),
                comparesEqualTo( new BigDecimal( "28.23" ) ) );
    }

    @Test
    public void resolvePayNowAmountGbp_usesBalanceDueNowNotTotalPrice_forEurTxnBooking() throws Exception {
        JsonObject details = scraper.extractAppStateBookingDetails(
                fixture( "hostelworld_paynow_app_state_eur_txn.html" ) );
        assertThat( details.get( "transactionCurrency" ).getAsString(), is( "EUR" ) );
        assertThat( new BigDecimal( details.get( "totalPrice" ).getAsString() ),
                comparesEqualTo( new BigDecimal( "28.35" ) ) );

        BigDecimal amount = scraper.resolvePayNowAmountGbp( details, "551-580776191" );
        assertThat( amount, comparesEqualTo( new BigDecimal( "23.25" ) ) );
        assertThat( amount, not( comparesEqualTo( new BigDecimal( "28.35" ) ) ) );
    }

    @Test
    public void extractJsonObjectLiteral_ignoresTrailingScript() {
        String html = "const APP_STATE = {\"a\":1}; const TRANSLATIONS = {\"x\":2};";
        int from = html.indexOf( '=' ) + 1;
        String json = HostelworldScraper.extractJsonObjectLiteral( html, from );
        assertThat( json, is( "{\"a\":1}" ) );
    }

    @Test
    public void extractAppStateBookingDetails_missingAppStateThrows() {
        assertThrows( UnrecoverableFault.class,
                () -> scraper.extractAppStateBookingDetails( "<html>no state</html>" ) );
    }

    @Test
    public void resolvePayNowAmountGbp_missingBalanceDueNowThrows() {
        JsonObject details = new Gson().fromJson(
                "{\"type\":\"PAYNOW\",\"currency\":\"GBP\",\"totalPrice\":\"28.35\"}", JsonObject.class );
        assertThrows( UnrecoverableFault.class,
                () -> scraper.resolvePayNowAmountGbp( details, "551-580776191" ) );
    }

    private static String fixture( String name ) throws Exception {
        return IOUtils.toString(
                HostelworldPayNowAppStateParseTest.class.getClassLoader().getResourceAsStream( name ),
                StandardCharsets.UTF_8 );
    }
}
