package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

public class HostelworldSearchRankScraperTest {

    @Test
    public void parseOrderedHostelIds_preservesDefaultSortRank() throws Exception {
        String html;
        try ( InputStream in = getClass().getResourceAsStream( "/analytics/hw_edinburgh_search.html" ) ) {
            html = new String( in.readAllBytes(), StandardCharsets.UTF_8 );
        }
        List<String> ids = HostelworldSearchRankScraper.parseOrderedHostelIds( html );
        assertThat( ids, hasSize( 24 ) );
        assertThat( ids.indexOf( "552" ) + 1, is( 2 ) );
        assertThat( ids.indexOf( "551" ) + 1, is( 4 ) );
        assertThat( ids.indexOf( "11242" ) + 1, is( 15 ) );
    }

    @Test
    public void buildQueryLabel_matchesPlanConvention() {
        assertThat(
                HostelworldSearchRankScraper.buildQueryLabel( "Edinburgh", 1, 1, 7 ),
                is( "edinburgh-1p-1n-offset7" ) );
    }
}
