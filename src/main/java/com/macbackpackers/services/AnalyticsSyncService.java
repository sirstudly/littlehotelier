package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.analytics.BookingCreatedFact;
import com.macbackpackers.beans.analytics.ChannelStayDayFact;
import com.macbackpackers.beans.analytics.CompetitorBedPriceFact;
import com.macbackpackers.beans.analytics.HwSearchRankFact;
import com.macbackpackers.beans.analytics.PropertyMatch;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.dao.AnalyticsDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.GoogleHotelsScraper;
import com.macbackpackers.scrapers.HostelworldSearchRankScraper;
import com.macbackpackers.scrapers.datainsights.CloudbedsDataInsightsClient;

import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Syncs Cloudbeds booked-date and stay-date production into {@code mac_analytics}.
 */
@Service
public class AnalyticsSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger( AnalyticsSyncService.class );

    /** Inclusive stay-date chunk size for Data Insights day queries. */
    private static final int STAY_CHUNK_DAYS = 31;

    /**
     * Booked-date mart needs every reservation created that day, including ones that have since
     * checked in/out or are still pending payment (not just ops "active" statuses).
     */
    static final String BOOKING_CREATED_STATUSES =
            "confirmed,not_confirmed,canceled,no_show,checked_in,checked_out,pending_payment";

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private CloudbedsDataInsightsClient dataInsightsClient;

    @Autowired
    private AnalyticsDAO analyticsDAO;

    @Autowired
    private GoogleHotelsScraper googleHotelsScraper;

    @Autowired
    private HostelworldSearchRankScraper hostelworldSearchRankScraper;

    @Autowired
    private Environment environment;

    public String resolvePropertyCode() {
        for ( String profile : environment.getActiveProfiles() ) {
            if ( "crh".equalsIgnoreCase( profile )
                    || "hsh".equalsIgnoreCase( profile )
                    || "rmb".equalsIgnoreCase( profile )
                    || "lsh".equalsIgnoreCase( profile ) ) {
                return profile.toLowerCase();
            }
        }
        return "unknown";
    }

    /** Phase 2 Edinburgh scrapers run on the CRH processor only. */
    public boolean isCrhProfile() {
        return "crh".equalsIgnoreCase( resolvePropertyCode() );
    }

    /**
     * Scrapes Google Hotels for {@code [today, today+horizonDays)} and writes a new current
     * snapshot into {@code fact_competitor_bed_price} (SCD2: prior current rows for each stay_date
     * are end-dated).
     */
    public int syncCompetitorBedPrices( String city, int horizonDays ) throws Exception {
        if ( horizonDays < 1 ) {
            throw new IllegalArgumentException( "horizon_days must be >= 1" );
        }
        List<PropertyMatch> own = analyticsDAO.listEdinburghPropertyMatches();
        LocalDate start = LocalDate.now();
        int inserted = 0;
        ChromeDriver driver = googleHotelsScraper.openDriver();
        try {
            for ( int i = 0; i < horizonDays; i++ ) {
                LocalDate stayDate = start.plusDays( i );
                List<CompetitorBedPriceFact> facts =
                        googleHotelsScraper.scrapeStayDate( driver, city, stayDate, own );
                analyticsDAO.replaceCompetitorBedPricesForStayDate( "edn", "ghs", stayDate, facts );
                inserted += facts.size();
                LOGGER.info( "GHS {} {}: replaced current snapshot with {} row(s)",
                        city, stayDate, facts.size() );
                if ( i + 1 < horizonDays ) {
                    googleHotelsScraper.pauseBetweenDates();
                }
            }
        }
        finally {
            try {
                driver.quit();
            }
            catch ( Exception e ) {
                LOGGER.warn( "Error quitting GHS Chrome: {}", e.toString() );
            }
        }
        return inserted;
    }

    /**
     * Scrapes Hostelworld consumer search and upserts rank rows for Edinburgh properties.
     */
    public int syncHostelworldSearchRanks( WebClient webClient, String city, int guests, int nights,
            int arrivalOffsetDays ) throws IOException {
        List<PropertyMatch> matches = analyticsDAO.listEdinburghPropertyMatches();
        LocalDate arrival = LocalDate.now().plusDays( arrivalOffsetDays );
        String queryLabel = HostelworldSearchRankScraper.buildQueryLabel(
                city, guests, nights, arrivalOffsetDays );
        List<HwSearchRankFact> facts = hostelworldSearchRankScraper.scrapeRanks(
                webClient, city, arrival, nights, guests, queryLabel, matches );
        analyticsDAO.upsertHwSearchRanks( facts );
        LOGGER.info( "HW search rank {}: upserted {} row(s) query_label={}", city, facts.size(), queryLabel );
        return facts.size();
    }

    /**
     * Pulls reservations booked on each day in {@code [from, to]} and upserts into
     * {@code fact_booking_created}.
     */
    public int syncBookingCreated( WebClient webClient, LocalDate from, LocalDate to ) throws IOException {
        String property = resolvePropertyCode();
        int upserted = 0;
        for ( LocalDate day = from; !day.isAfter( to ); day = day.plusDays( 1 ) ) {
            List<Customer> reservations = cloudbedsScraper.getReservationsByBookingDate(
                    webClient, day, day, BOOKING_CREATED_STATUSES );
            List<BookingCreatedFact> facts = new ArrayList<>( reservations.size() );
            for ( Customer c : reservations ) {
                BookingCreatedFact fact = toBookingFact( property, c, day );
                if ( fact != null ) {
                    facts.add( fact );
                }
            }
            analyticsDAO.upsertBookingCreated( facts );
            upserted += facts.size();
            LOGGER.info( "Analytics booking-created {} {}: {} reservation(s)", property, day, facts.size() );
        }
        return upserted;
    }

    /**
     * Pulls Channel Production at day grain for {@code [from, to]} and replaces mart rows for
     * that range.
     */
    public int syncChannelStayDays( WebClient webClient, LocalDate from, LocalDate to ) throws IOException {
        String property = resolvePropertyCode();
        List<ChannelStayDayFact> all = new ArrayList<>();
        LocalDate chunkStart = from;
        while ( !chunkStart.isAfter( to ) ) {
            LocalDate chunkEnd = chunkStart.plusDays( STAY_CHUNK_DAYS - 1 );
            if ( chunkEnd.isAfter( to ) ) {
                chunkEnd = to;
            }
            JsonObject response = dataInsightsClient.queryChannelProductionByStayDateRange(
                    webClient, chunkStart, chunkEnd );
            all.addAll( parseStayDayRecords( response, property ) );
            chunkStart = chunkEnd.plusDays( 1 );
        }
        analyticsDAO.replaceChannelStayDays( property, from, to, all );
        LOGGER.info( "Analytics channel-stay-day {} [{} .. {}]: {} source-day row(s)",
                property, from, to, all.size() );
        return all.size();
    }

    static BookingCreatedFact toBookingFact( String propertyCode, Customer c, LocalDate fallbackBookedOn ) {
        if ( c == null || StringUtils.isBlank( c.getId() ) ) {
            return null;
        }
        BookingCreatedFact fact = new BookingCreatedFact();
        fact.setPropertyCode( propertyCode );
        fact.setReservationId( c.getId() );
        fact.setBookedOn( parseDateOr( c.getBookingDate(), fallbackBookedOn ) );
        fact.setBookingSource( StringUtils.trimToNull( c.getSourceName() ) );
        fact.setStatus( StringUtils.trimToNull( c.getStatus() ) );
        fact.setGrandTotal( parseMoney( c.getGrandTotal() ) );
        fact.setCheckinDate( parseDateOr( c.getCheckinDate(), null ) );
        fact.setCheckoutDate( parseDateOr( c.getCheckoutDate(), null ) );
        return fact;
    }

    /**
     * Parses a day-grain Channel Production {@code records} map
     * ({@code stay_date → category → source → metrics}).
     */
    static List<ChannelStayDayFact> parseStayDayRecords( JsonObject response, String propertyCode ) {
        List<ChannelStayDayFact> lines = new ArrayList<>();
        JsonObject records = response == null ? null : response.getAsJsonObject( "records" );
        if ( records == null ) {
            return lines;
        }
        for ( Map.Entry<String, JsonElement> dayEntry : records.entrySet() ) {
            LocalDate stayDate = parseStayDateKey( dayEntry.getKey() );
            if ( stayDate == null || !dayEntry.getValue().isJsonObject() ) {
                continue;
            }
            for ( Map.Entry<String, JsonElement> categoryEntry : dayEntry.getValue().getAsJsonObject().entrySet() ) {
                String category = categoryEntry.getKey();
                if ( !categoryEntry.getValue().isJsonObject() ) {
                    continue;
                }
                for ( Map.Entry<String, JsonElement> sourceEntry : categoryEntry.getValue().getAsJsonObject().entrySet() ) {
                    String source = sourceEntry.getKey();
                    if ( StringUtils.isBlank( source ) || "-".equals( source ) ) {
                        continue;
                    }
                    JsonObject metrics = sourceEntry.getValue().getAsJsonObject();
                    BigDecimal revenue = parseNumber( metricValue( metrics, "room_revenue", "sum" ) );
                    BigDecimal roomsSold = parseNumber( metricValue( metrics, "rooms_sold", "sum" ) );
                    BigDecimal adr = parseNumber( metricValue( metrics, "adr", "aggregated" ) );
                    revenue = revenue == null ? BigDecimal.ZERO : revenue;
                    int rooms = roomsSold == null ? 0 : roomsSold.intValue();
                    if ( revenue.signum() == 0 && rooms == 0 ) {
                        continue;
                    }
                    ChannelStayDayFact fact = new ChannelStayDayFact();
                    fact.setPropertyCode( propertyCode );
                    fact.setStayDate( stayDate );
                    fact.setBookingSource( source );
                    fact.setSourceCategory( "-".equals( category ) ? null : category );
                    fact.setRoomRevenue( revenue );
                    fact.setRoomsSold( rooms );
                    fact.setAdr( adr );
                    lines.add( fact );
                }
            }
        }
        return lines;
    }

    /** Accepts {@code yyyy-MM-dd} or {@code yyyy-MM} (month grain — first of month). */
    static LocalDate parseStayDateKey( String key ) {
        if ( StringUtils.isBlank( key ) || "-".equals( key ) ) {
            return null;
        }
        try {
            if ( key.length() == 7 ) {
                return YearMonth.parse( key ).atDay( 1 );
            }
            return LocalDate.parse( key.substring( 0, Math.min( 10, key.length() ) ) );
        }
        catch ( DateTimeParseException e ) {
            LOGGER.warn( "Skipping unparseable stay_date key '{}'", key );
            return null;
        }
    }

    private static LocalDate parseDateOr( String value, LocalDate fallback ) {
        if ( StringUtils.isBlank( value ) || value.length() < 10 ) {
            return fallback;
        }
        try {
            return LocalDate.parse( value.substring( 0, 10 ) );
        }
        catch ( DateTimeParseException e ) {
            return fallback;
        }
    }

    private static BigDecimal parseMoney( String value ) {
        if ( StringUtils.isBlank( value ) || "-".equals( value.trim() ) ) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal( value.replace( ",", "" ).trim() );
        }
        catch ( NumberFormatException e ) {
            return BigDecimal.ZERO;
        }
    }

    private static String metricValue( JsonObject metrics, String column, String metric ) {
        if ( false == metrics.has( column ) || false == metrics.get( column ).isJsonObject() ) {
            return null;
        }
        JsonElement el = metrics.getAsJsonObject( column ).get( metric );
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }

    static BigDecimal parseNumber( String value ) {
        if ( StringUtils.isBlank( value ) || "-".equals( value.trim() ) ) {
            return null;
        }
        return new BigDecimal( value.replace( ",", "" ).trim() );
    }
}
