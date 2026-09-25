package com.macbackpackers.ronbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.ronbot.dto.ChannelProductionDto;
import com.macbackpackers.ronbot.dto.OccupancyDto;
import com.macbackpackers.services.ChannelProductionReportService.CommissionRate;
import com.macbackpackers.services.ChannelProductionReportService.MonthReport;
import com.macbackpackers.services.ChannelProductionReportService.SourceLine;
import com.macbackpackers.services.OccupancyReportService.MonthOccupancy;
import com.macbackpackers.services.OccupancyReportService.OccupancyReport;

public class RonbotChannelProductionMappingTest {

    private static final List<CommissionRate> RATES = Arrays.asList(
            new CommissionRate( "Agoda / Priceline", "AGODA", new BigDecimal( "6.67" ) ),
            new CommissionRate( "Airbnb (API)", "AIRBNB", new BigDecimal( "6.06" ) ),
            new CommissionRate( "Booking.com", "BDC", new BigDecimal( "6.67" ) ) );

    @Test
    public void testTotalsAndShares() {
        MonthReport report = new MonthReport( YearMonth.of( 2026, 8 ), Arrays.asList(
                new SourceLine( "Booking.com", new BigDecimal( "14461.66" ), 382, new BigDecimal( "37.86" ) ),
                new SourceLine( "Hostelworld", new BigDecimal( "18774.27" ), 493, new BigDecimal( "38.08" ) ),
                new SourceLine( "Walk-In", new BigDecimal( "516.00" ), 14, new BigDecimal( "36.86" ) ),
                new SourceLine( "Website", new BigDecimal( "11791.19" ), 278, new BigDecimal( "42.41" ) ) ),
                null, RATES );

        ChannelProductionDto dto = RonbotReadService.toChannelProductionDto( "rmb", report );

        assertEquals( "rmb", dto.getProperty() );
        assertEquals( "2026-08", dto.getMonth() );
        List<ChannelProductionDto.Source> sources = dto.getSources();
        assertEquals( 4, sources.size() );
        assertEquals( new BigDecimal( "31.75" ), sources.get( 0 ).getRevenuePct() );
        assertEquals( new BigDecimal( "32.73" ), sources.get( 0 ).getRoomsSoldPct() );
        assertEquals( new BigDecimal( "37.86" ), sources.get( 0 ).getAdr() );
        assertEquals( new BigDecimal( "2168.16" ), sources.get( 0 ).getCommission() );
        assertNull( sources.get( 1 ).getCommission() );

        ChannelProductionDto.Totals totals = dto.getTotals();
        assertEquals( new BigDecimal( "45543.12" ), totals.getRevenue() );
        assertEquals( 1167, totals.getRoomsSold() );
        assertEquals( new BigDecimal( "2168.16" ), totals.getCommission() );
        assertEquals( new BigDecimal( "43374.96" ), totals.getNetRevenue() );
        assertEquals( new BigDecimal( "37.17" ), totals.getAvgPricePerBed() );
    }

    @Test
    public void testMultipleCommissionSources() {
        MonthReport report = new MonthReport( YearMonth.of( 2026, 8 ), Arrays.asList(
                new SourceLine( "Agoda / Priceline", new BigDecimal( "398.42" ), 10, new BigDecimal( "39.84" ) ),
                new SourceLine( "Airbnb (API)", new BigDecimal( "450.06" ), 11, new BigDecimal( "40.91" ) ),
                new SourceLine( "Booking.com", new BigDecimal( "3509.25" ), 90, new BigDecimal( "38.99" ) ),
                new SourceLine( "Hostelworld", new BigDecimal( "10000.00" ), 250, new BigDecimal( "40.00" ) ) ),
                null, RATES );

        ChannelProductionDto dto = RonbotReadService.toChannelProductionDto( "crh", report );

        assertEquals( new BigDecimal( "59.73" ), dto.getSources().get( 0 ).getCommission() );
        assertEquals( new BigDecimal( "74.27" ), dto.getSources().get( 1 ).getCommission() );
        assertEquals( new BigDecimal( "526.12" ), dto.getSources().get( 2 ).getCommission() );
        assertNull( dto.getSources().get( 3 ).getCommission() );
        assertEquals( new BigDecimal( "660.12" ), dto.getTotals().getCommission() );
        assertEquals( new BigDecimal( "13697.61" ), dto.getTotals().getNetRevenue() );
    }

    @Test
    public void testOccupancyPassThroughAndOccupancyDto() {
        ChannelProductionDto cpr = RonbotReadService.toChannelProductionDto( "rmb", new MonthReport(
                YearMonth.of( 2026, 8 ), List.of(), new BigDecimal( "81.84" ) ) );
        assertEquals( new BigDecimal( "81.84" ), cpr.getTotals().getOccupancyPct() );

        OccupancyReport report = new OccupancyReport( LocalDate.of( 2026, 7, 17 ), LocalDate.of( 2026, 8, 31 ),
                Arrays.asList(
                        new MonthOccupancy( YearMonth.of( 2026, 7 ), new BigDecimal( "81.1360" ), 1157, new BigDecimal( "29202.79" ) ),
                        new MonthOccupancy( YearMonth.of( 2026, 8 ), new BigDecimal( "81.8373" ), 1167, new BigDecimal( "45543.12" ) ) ),
                new BigDecimal( "81.61" ), 2324, new BigDecimal( "74745.91" ) );
        OccupancyDto dto = RonbotReadService.toOccupancyDto( "rmb", report );
        assertEquals( "2026-07-17", dto.getFrom() );
        assertEquals( "2026-08-31", dto.getTo() );
        assertEquals( new BigDecimal( "81.61" ), dto.getOccupancyPct() );
        assertEquals( 2324, dto.getBedsBooked() );
        assertEquals( 2, dto.getMonths().size() );
        assertEquals( "2026-08", dto.getMonths().get( 1 ).getMonth() );
        assertEquals( new BigDecimal( "81.84" ), dto.getMonths().get( 1 ).getOccupancyPct() );
    }

    @Test
    public void testNoBookingComAndEmptyMonth() {
        ChannelProductionDto noBdc = RonbotReadService.toChannelProductionDto( "hsh", new MonthReport(
                YearMonth.of( 2026, 1 ), Arrays.asList(
                        new SourceLine( "Website", new BigDecimal( "100.00" ), 4, new BigDecimal( "25.00" ) ) ),
                null, RATES ) );
        assertEquals( 0, BigDecimal.ZERO.compareTo( noBdc.getTotals().getCommission() ) );
        assertEquals( new BigDecimal( "100.00" ), noBdc.getTotals().getNetRevenue() );
        assertEquals( new BigDecimal( "25.00" ), noBdc.getTotals().getAvgPricePerBed() );

        ChannelProductionDto empty = RonbotReadService.toChannelProductionDto( "lsh",
                new MonthReport( YearMonth.of( 2026, 2 ), List.of() ) );
        assertEquals( 0, empty.getSources().size() );
        assertEquals( 0, empty.getTotals().getRoomsSold() );
        assertNull( empty.getTotals().getAvgPricePerBed() );
    }
}
