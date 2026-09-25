package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.scrapers.datainsights.CloudbedsDataInsightsClient;

/**
 * Occupancy (beds occupied %) from the Data Insights classic Occupancy report, broken down by month.
 */
@Service
public class OccupancyReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger( OccupancyReportService.class );

    @Autowired
    private CloudbedsDataInsightsClient dataInsightsClient;

    /**
     * Fetches occupancy for stay dates {@code [from, to]} (inclusive). One request per calendar year.
     */
    public OccupancyReport fetchOccupancy( WebClient webClient, LocalDate from, LocalDate to ) throws IOException {
        if ( from == null || to == null || to.isBefore( from ) ) {
            throw new IllegalArgumentException( "Invalid date range: " + from + " .. " + to );
        }
        List<MonthOccupancy> months = new ArrayList<>();
        for ( YearChunk chunk : splitByYear( from, to ) ) {
            JsonObject response = dataInsightsClient.queryOccupancy( webClient, chunk.year, chunk.start, chunk.end );
            months.addAll( parseResults( response, chunk.year ) );
        }
        OccupancyReport report = aggregate( from, to, months );
        LOGGER.info( "Occupancy {} .. {}: {}% over {} month(s)", from, to, report.getOccupancyPct(), months.size() );
        return report;
    }

    /** Splits {@code [from, to]} into per-calendar-year periods. */
    static List<YearChunk> splitByYear( LocalDate from, LocalDate to ) {
        List<YearChunk> chunks = new ArrayList<>();
        LocalDate start = from;
        while ( false == start.isAfter( to ) ) {
            LocalDate yearEnd = LocalDate.of( start.getYear(), 12, 31 );
            LocalDate end = to.isBefore( yearEnd ) ? to : yearEnd;
            chunks.add( new YearChunk( start.getYear(), MonthDay.from( start ), MonthDay.from( end ) ) );
            start = end.plusDays( 1 );
        }
        return chunks;
    }

    static List<MonthOccupancy> parseResults( JsonObject response, int year ) {
        List<MonthOccupancy> months = new ArrayList<>();
        if ( response == null || false == response.has( "results" ) || false == response.get( "results" ).isJsonArray() ) {
            return months;
        }
        for ( JsonElement el : response.getAsJsonArray( "results" ) ) {
            JsonObject r = el.getAsJsonObject();
            YearMonth month = YearMonth.of( year, Integer.parseInt( r.get( "date" ).getAsString() ) );
            months.add( new MonthOccupancy( month,
                    decimal( r, "occupancy", 4 ),
                    r.has( "accommodations_booked" ) && false == r.get( "accommodations_booked" ).isJsonNull()
                            ? r.get( "accommodations_booked" ).getAsInt() : 0,
                    decimal( r, "revenue", 2 ) ) );
        }
        return months;
    }

    private static BigDecimal decimal( JsonObject r, String field, int scale ) {
        if ( false == r.has( field ) || r.get( field ).isJsonNull() ) {
            return BigDecimal.ZERO;
        }
        return r.get( field ).getAsBigDecimal().setScale( scale, RoundingMode.HALF_UP );
    }

    /**
     * Overall occupancy is weighted by the number of nights of each month inside {@code [from, to]}
     * (capacity is not returned by the report, so it is assumed constant over the range).
     */
    static OccupancyReport aggregate( LocalDate from, LocalDate to, List<MonthOccupancy> months ) {
        List<MonthOccupancy> sorted = new ArrayList<>( months );
        sorted.sort( Comparator.comparing( MonthOccupancy::getMonth ) );

        BigDecimal weighted = BigDecimal.ZERO;
        long totalNights = 0;
        int totalBooked = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for ( MonthOccupancy m : sorted ) {
            long nights = nightsInRange( m.getMonth(), from, to );
            weighted = weighted.add( m.getOccupancyPct().multiply( BigDecimal.valueOf( nights ) ) );
            totalNights += nights;
            totalBooked += m.getBedsBooked();
            totalRevenue = totalRevenue.add( m.getRevenue() );
        }
        BigDecimal overall = totalNights == 0 ? null
                : weighted.divide( BigDecimal.valueOf( totalNights ), 2, RoundingMode.HALF_UP );
        return new OccupancyReport( from, to, sorted, overall, totalBooked, totalRevenue );
    }

    static long nightsInRange( YearMonth month, LocalDate from, LocalDate to ) {
        LocalDate start = month.atDay( 1 ).isAfter( from ) ? month.atDay( 1 ) : from;
        LocalDate end = month.atEndOfMonth().isBefore( to ) ? month.atEndOfMonth() : to;
        return end.isBefore( start ) ? 0 : ChronoUnit.DAYS.between( start, end ) + 1;
    }

    static final class YearChunk {
        final int year;
        final MonthDay start;
        final MonthDay end;

        YearChunk( int year, MonthDay start, MonthDay end ) {
            this.year = year;
            this.start = start;
            this.end = end;
        }
    }

    public static final class MonthOccupancy {
        private final YearMonth month;
        private final BigDecimal occupancyPct;
        private final int bedsBooked;
        private final BigDecimal revenue;

        public MonthOccupancy( YearMonth month, BigDecimal occupancyPct, int bedsBooked, BigDecimal revenue ) {
            this.month = month;
            this.occupancyPct = occupancyPct;
            this.bedsBooked = bedsBooked;
            this.revenue = revenue;
        }

        public YearMonth getMonth() {
            return month;
        }

        /** 0–100 */
        public BigDecimal getOccupancyPct() {
            return occupancyPct;
        }

        public int getBedsBooked() {
            return bedsBooked;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }
    }

    public static final class OccupancyReport {
        private final LocalDate from;
        private final LocalDate to;
        private final List<MonthOccupancy> months;
        private final BigDecimal occupancyPct;
        private final int bedsBooked;
        private final BigDecimal revenue;

        public OccupancyReport( LocalDate from, LocalDate to, List<MonthOccupancy> months,
                BigDecimal occupancyPct, int bedsBooked, BigDecimal revenue ) {
            this.from = from;
            this.to = to;
            this.months = Collections.unmodifiableList( new ArrayList<>( months ) );
            this.occupancyPct = occupancyPct;
            this.bedsBooked = bedsBooked;
            this.revenue = revenue;
        }

        public LocalDate getFrom() {
            return from;
        }

        public LocalDate getTo() {
            return to;
        }

        public List<MonthOccupancy> getMonths() {
            return months;
        }

        /** Night-weighted 0–100, rounded to 2 dp; null when no months were returned. */
        public BigDecimal getOccupancyPct() {
            return occupancyPct;
        }

        public int getBedsBooked() {
            return bedsBooked;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }
    }
}
