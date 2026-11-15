package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.scrapers.GoogleHotelsScraper.ListingPrice;

public class GoogleHotelsScraperTest {

    @Test
    public void parseListingPrices_extractsUniqueStartingFromPrices() throws Exception {
        String html;
        try ( InputStream in = getClass().getResourceAsStream( "/analytics/ghs_edinburgh_listings.html" ) ) {
            html = new String( in.readAllBytes(), StandardCharsets.UTF_8 );
        }
        List<ListingPrice> listings = GoogleHotelsScraper.parseListingPrices( html );
        assertThat( listings, hasSize( greaterThanOrEqualTo( 15 ) ) );

        ListingPrice crh = listings.stream()
                .filter( l -> "Castle Rock Hostel".equals( l.name ) )
                .findFirst()
                .orElseThrow();
        assertThat( crh.lowestPrice, is( new BigDecimal( "42" ) ) );

        ListingPrice hsh = listings.stream()
                .filter( l -> "High Street Hostel".equals( l.name ) )
                .findFirst()
                .orElseThrow();
        assertThat( hsh.lowestPrice, is( new BigDecimal( "34" ) ) );
    }

    @Test
    public void looksLikeCaptcha_requiresUnusualTraffic() {
        assertThat( GoogleHotelsScraper.looksLikeCaptcha( "" ), is( true ) );
        assertThat( GoogleHotelsScraper.looksLikeCaptcha( "<html>Prices starting from £10</html>" ), is( false ) );
        assertThat( GoogleHotelsScraper.looksLikeCaptcha( "Our systems have detected unusual traffic" ),
                is( true ) );
    }

    @Test
    public void formatDateLabel_matchesGoogleHotelsInput() {
        assertThat( GoogleHotelsScraper.formatDateLabel( LocalDate.of( 2026, 11, 21 ) ),
                is( "Sat, Nov 21" ) );
        assertThat( GoogleHotelsScraper.formatDateLabel( LocalDate.of( 2026, 11, 29 ) ),
                is( "Sun, Nov 29" ) );
    }

    @Test
    public void buildSearchUrl_usesTravelSearchWithDates() {
        String url = GoogleHotelsScraper.buildSearchUrl(
                "Edinburgh", LocalDate.of( 2026, 11, 21 ), LocalDate.of( 2026, 11, 22 ) );
        assertThat( url.contains( "/travel/search?" ), is( true ) );
        assertThat( url.contains( "dates=2026-11-21,2026-11-22" ), is( true ) );
        assertThat( url.contains( "curr=GBP" ), is( true ) );
        assertThat( url.contains( "ts=" ), is( true ) );
    }

    @Test
    public void buildTravelTs_matchesLiveSuccessfulScrapeUrls() {
        // Captured from job 835900 after UI dates stuck for Edinburgh GBP searches.
        assertThat( GoogleHotelsScraper.buildTravelTs(
                "Edinburgh", LocalDate.of( 2026, 11, 15 ), LocalDate.of( 2026, 11, 16 ) ),
                is( "CAEaNwoZEhUKCC9tLzAybTc3OglFZGluYnVyZ2gaABIaEhQKBwjqDxALGA8SBwjqDxALGBAYATICCAEqCQoFOgNHQlAaAA" ) );
        assertThat( GoogleHotelsScraper.buildTravelTs(
                "Edinburgh", LocalDate.of( 2026, 11, 28 ), LocalDate.of( 2026, 11, 29 ) ),
                is( "CAEaNwoZEhUKCC9tLzAybTc3OglFZGluYnVyZ2gaABIaEhQKBwjqDxALGBwSBwjqDxALGB0YATICCAEqCQoFOgNHQlAaAA" ) );
    }
}
