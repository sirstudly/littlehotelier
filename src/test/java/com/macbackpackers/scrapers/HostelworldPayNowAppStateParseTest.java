package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
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
    private String fixtureHtml;

    @BeforeEach
    public void setUp() throws Exception {
        scraper = new HostelworldScraper();
        // gson field is package-autowired; inject via reflection for unit parse tests
        java.lang.reflect.Field gsonField = HostelworldScraper.class.getDeclaredField( "gson" );
        gsonField.setAccessible( true );
        gsonField.set( scraper, new Gson() );

        fixtureHtml = IOUtils.toString(
                getClass().getClassLoader().getResourceAsStream( "hostelworld_paynow_app_state.html" ),
                StandardCharsets.UTF_8 );
    }

    @Test
    public void extractAppStateBookingDetails_readsPayNowGbpTotalPrice() {
        JsonObject details = scraper.extractAppStateBookingDetails( fixtureHtml );
        assertThat( details.get( "type" ).getAsString(), is( "PAYNOW" ) );
        assertThat( details.get( "currency" ).getAsString(), is( "GBP" ) );
        assertThat( new BigDecimal( details.get( "totalPrice" ).getAsString() ),
                comparesEqualTo( new BigDecimal( "34.43" ) ) );
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
}
