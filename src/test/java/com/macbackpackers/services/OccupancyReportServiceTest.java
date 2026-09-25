package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.services.OccupancyReportService.MonthOccupancy;
import com.macbackpackers.services.OccupancyReportService.OccupancyReport;
import com.macbackpackers.services.OccupancyReportService.YearChunk;

public class OccupancyReportServiceTest {

    private static List<MonthOccupancy> fixture2026() throws Exception {
        try ( InputStreamReader reader = new InputStreamReader(
                OccupancyReportServiceTest.class.getClassLoader().getResourceAsStream( "di_occupancy_rooms_sold_2026.json" ),
                StandardCharsets.UTF_8 ) ) {
            JsonObject response = JsonParser.parseReader( reader ).getAsJsonObject();
            return OccupancyReportService.parseResults( response, 2026 );
        }
    }

    @Test
    public void testParseResults() throws Exception {
        List<MonthOccupancy> months = fixture2026();
        assertEquals( 12, months.size() );
        MonthOccupancy aug = months.get( 7 );
        assertEquals( YearMonth.of( 2026, 8 ), aug.getMonth() );
        assertEquals( new BigDecimal( "81.8373" ), aug.getOccupancyPct() );
        assertEquals( 1167, aug.getBedsBooked() );
        assertEquals( new BigDecimal( "45543.12" ), aug.getRevenue() );
    }

    @Test
    public void testSplitByYear() {
        List<YearChunk> chunks = OccupancyReportService.splitByYear(
                LocalDate.of( 2025, 12, 20 ), LocalDate.of( 2026, 1, 10 ) );
        assertEquals( 2, chunks.size() );
        assertEquals( 2025, chunks.get( 0 ).year );
        assertEquals( MonthDay.of( 12, 20 ), chunks.get( 0 ).start );
        assertEquals( MonthDay.of( 12, 31 ), chunks.get( 0 ).end );
        assertEquals( 2026, chunks.get( 1 ).year );
        assertEquals( MonthDay.of( 1, 1 ), chunks.get( 1 ).start );
        assertEquals( MonthDay.of( 1, 10 ), chunks.get( 1 ).end );

        List<YearChunk> single = OccupancyReportService.splitByYear(
                LocalDate.of( 2026, 8, 1 ), LocalDate.of( 2026, 8, 31 ) );
        assertEquals( 1, single.size() );
        assertEquals( MonthDay.of( 8, 1 ), single.get( 0 ).start );
        assertEquals( MonthDay.of( 8, 31 ), single.get( 0 ).end );
    }

    @Test
    public void testAggregateSingleMonth() throws Exception {
        List<MonthOccupancy> aug = fixture2026().stream()
                .filter( m -> m.getMonth().getMonthValue() == 8 )
                .collect( Collectors.toList() );
        OccupancyReport report = OccupancyReportService.aggregate(
                LocalDate.of( 2026, 8, 1 ), LocalDate.of( 2026, 8, 31 ), aug );
        assertEquals( new BigDecimal( "81.84" ), report.getOccupancyPct() );
        assertEquals( 1167, report.getBedsBooked() );
    }

    @Test
    public void testAggregateNightWeightedAcrossPartialMonth() throws Exception {
        List<MonthOccupancy> julAug = fixture2026().stream()
                .filter( m -> m.getMonth().getMonthValue() == 7 || m.getMonth().getMonthValue() == 8 )
                .collect( Collectors.toList() );
        // 15 nights of July (81.1360) + 31 nights of August (81.8373)
        OccupancyReport report = OccupancyReportService.aggregate(
                LocalDate.of( 2026, 7, 17 ), LocalDate.of( 2026, 8, 31 ), julAug );
        assertEquals( new BigDecimal( "81.61" ), report.getOccupancyPct() );
        assertEquals( 1157 + 1167, report.getBedsBooked() );
        assertEquals( 2, report.getMonths().size() );
    }

    @Test
    public void testAggregateMidMonthPeriod() throws Exception {
        // Cloudbeds clips the month row to period_start..period_end (10–20 Aug 2025 = 11 nights)
        List<MonthOccupancy> months;
        try ( InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream( "di_occupancy_rooms_sold_2025_08_10_to_20.json" ),
                StandardCharsets.UTF_8 ) ) {
            months = OccupancyReportService.parseResults( JsonParser.parseReader( reader ).getAsJsonObject(), 2025 );
        }
        assertEquals( 1, months.size() );
        assertEquals( 412, months.get( 0 ).getBedsBooked() );

        LocalDate from = LocalDate.of( 2025, 8, 10 );
        LocalDate to = LocalDate.of( 2025, 8, 20 );
        assertEquals( 11, OccupancyReportService.nightsInRange( YearMonth.of( 2025, 8 ), from, to ) );
        OccupancyReport report = OccupancyReportService.aggregate( from, to, months );
        assertEquals( new BigDecimal( "81.42" ), report.getOccupancyPct() );
        assertEquals( new BigDecimal( "16729.88" ), report.getRevenue() );
    }

    @Test
    public void testAggregateEmpty() {
        OccupancyReport report = OccupancyReportService.aggregate(
                LocalDate.of( 2026, 8, 1 ), LocalDate.of( 2026, 8, 31 ), List.of() );
        assertNull( report.getOccupancyPct() );
        assertEquals( 0, report.getBedsBooked() );
    }
}
