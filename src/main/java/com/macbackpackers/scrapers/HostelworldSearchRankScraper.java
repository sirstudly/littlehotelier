package com.macbackpackers.scrapers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.analytics.HwSearchRankFact;
import com.macbackpackers.beans.analytics.PropertyMatch;

/**
 * Consumer-site Hostelworld city search rank (not inbox.hostelworld.com).
 * Uses classic SSR HTML ({@code /hostels/{City}}) which embeds ordered {@code /hostels/p/{id}} links.
 */
@Component
public class HostelworldSearchRankScraper {

    private static final Logger LOGGER = LoggerFactory.getLogger( HostelworldSearchRankScraper.class );

    private static final Pattern HOSTEL_PATH = Pattern.compile(
            "href=\"(?:https://www\\.hostelworld\\.com)?/hostels/p/(\\d+)[^\"]*\"" );

    /**
     * Fetches Edinburgh (or other city) search results and returns one fact per match
     * (position null when not found).
     */
    public List<HwSearchRankFact> scrapeRanks( WebClient webClient, String city, LocalDate arrival,
            int nights, int guests, String queryLabel, List<PropertyMatch> matches ) throws IOException {
        if ( nights < 1 ) {
            throw new IllegalArgumentException( "nights must be >= 1" );
        }
        LocalDate departure = arrival.plusDays( nights );
        String pageUrl = buildSearchUrl( city, arrival, departure, guests );
        LOGGER.info( "Hostelworld search: {}", pageUrl );

        HtmlPage page = webClient.getPage( pageUrl );
        String html = page.asXml();
        if ( StringUtils.isBlank( html ) || html.length() < 1000 ) {
            throw new IOException( "Hostelworld search returned empty/short HTML for " + pageUrl );
        }

        List<String> orderedIds = parseOrderedHostelIds( html );
        LOGGER.info( "Hostelworld search parsed {} unique hostel id(s) for {}", orderedIds.size(), city );
        if ( orderedIds.isEmpty() ) {
            throw new IOException( "Hostelworld search had no /hostels/p/{id} results for " + pageUrl );
        }

        LocalDate searchDate = LocalDate.now();
        List<HwSearchRankFact> facts = new ArrayList<>();
        for ( PropertyMatch match : matches ) {
            HwSearchRankFact fact = new HwSearchRankFact();
            fact.setPropertyCode( match.getPropertyCode() );
            fact.setSearchDate( searchDate );
            fact.setArrivalDate( arrival );
            fact.setQueryLabel( queryLabel );
            fact.setResultCount( orderedIds.size() );
            fact.setPageUrl( pageUrl );

            Integer position = null;
            if ( StringUtils.isNotBlank( match.getHwHostelNumber() ) ) {
                int idx = orderedIds.indexOf( match.getHwHostelNumber() );
                if ( idx >= 0 ) {
                    position = idx + 1;
                }
            }
            if ( position == null ) {
                position = findByName( html, orderedIds, match );
            }
            fact.setPosition( position );
            LOGGER.info( "HW rank {} ({}) -> {}", match.getPropertyCode(), match.getHwHostelNumber(),
                    position == null ? "not found" : position );
            facts.add( fact );
        }
        return facts;
    }

    public static String buildSearchUrl( String city, LocalDate arrival, LocalDate departure, int guests ) {
        String citySlug = city.trim().toLowerCase( Locale.ROOT ).replace( ' ', '-' );
        // Classic SSR path (PWA /pwa/wds/s shells out empty property-cards without JS).
        return "https://www.hostelworld.com/hostels/" + encodePath( citySlug )
                + "?DateFrom=" + arrival
                + "&DateTo=" + departure
                + "&NumberOfGuests=" + guests;
    }

    public static String buildQueryLabel( String city, int guests, int nights, int arrivalOffsetDays ) {
        String citySlug = city.trim().toLowerCase( Locale.ROOT ).replace( ' ', '-' );
        return citySlug + "-" + guests + "p-" + nights + "n-offset" + arrivalOffsetDays;
    }

    /** First-seen order of {@code /hostels/p/{id}} hrefs (= default search rank). */
    public static List<String> parseOrderedHostelIds( String html ) {
        Map<String, Boolean> ordered = new LinkedHashMap<>();
        Matcher m = HOSTEL_PATH.matcher( html );
        while ( m.find() ) {
            ordered.putIfAbsent( m.group( 1 ), Boolean.TRUE );
        }
        return new ArrayList<>( ordered.keySet() );
    }

    private static Integer findByName( String html, List<String> orderedIds, PropertyMatch match ) {
        // Fallback: locate card title near a hostel id
        for ( int i = 0; i < orderedIds.size(); i++ ) {
            String id = orderedIds.get( i );
            Pattern near = Pattern.compile(
                    "/hostels/p/" + Pattern.quote( id ) + "[\\s\\S]{0,400}?title=\"([^\"]+)\"|"
                            + "title=\"([^\"]+)\"[\\s\\S]{0,400}?/hostels/p/" + Pattern.quote( id ),
                    Pattern.CASE_INSENSITIVE );
            Matcher m = near.matcher( html );
            if ( m.find() ) {
                String title = StringUtils.defaultString( m.group( 1 ), m.group( 2 ) );
                if ( match.matchesName( title ) ) {
                    return i + 1;
                }
            }
        }
        return null;
    }

    private static String encodePath( String slug ) {
        // Keep hyphens; encode anything else
        try {
            return URLEncoder.encode( slug, StandardCharsets.UTF_8 ).replace( "+", "%20" );
        }
        catch ( Exception e ) {
            return slug;
        }
    }
}
