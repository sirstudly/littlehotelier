package com.macbackpackers.ronbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.ronbot.dto.ChannelProductionDto;
import com.macbackpackers.services.ChannelProductionReportService.MonthReport;
import com.macbackpackers.services.ChannelProductionReportService.SourceLine;

public class RonbotChannelProductionMappingTest {

    @Test
    public void testTotalsAndShares() {
        MonthReport report = new MonthReport( YearMonth.of( 2026, 8 ), Arrays.asList(
                new SourceLine( "Booking.com", new BigDecimal( "14461.66" ), 382, new BigDecimal( "37.86" ) ),
                new SourceLine( "Hostelworld", new BigDecimal( "18774.27" ), 493, new BigDecimal( "38.08" ) ),
                new SourceLine( "Walk-In", new BigDecimal( "516.00" ), 14, new BigDecimal( "36.86" ) ),
                new SourceLine( "Website", new BigDecimal( "11791.19" ), 278, new BigDecimal( "42.41" ) ) ) );

        ChannelProductionDto dto = RonbotReadService.toChannelProductionDto( "rmb", report );

        assertEquals( "rmb", dto.getProperty() );
        assertEquals( "2026-08", dto.getMonth() );
        List<ChannelProductionDto.Source> sources = dto.getSources();
        assertEquals( 4, sources.size() );
        assertEquals( new BigDecimal( "31.75" ), sources.get( 0 ).getRevenuePct() );
        assertEquals( new BigDecimal( "32.73" ), sources.get( 0 ).getRoomsSoldPct() );
        assertEquals( new BigDecimal( "37.86" ), sources.get( 0 ).getAdr() );

        ChannelProductionDto.Totals totals = dto.getTotals();
        assertEquals( new BigDecimal( "45543.12" ), totals.getRevenue() );
        assertEquals( 1167, totals.getRoomsSold() );
        assertEquals( new BigDecimal( "2168.16" ), totals.getBdcCommission() );
        assertEquals( new BigDecimal( "43374.96" ), totals.getNetRevenue() );
        assertEquals( new BigDecimal( "37.17" ), totals.getAvgPricePerBed() );
    }

    @Test
    public void testNoBookingComAndEmptyMonth() {
        ChannelProductionDto noBdc = RonbotReadService.toChannelProductionDto( "hsh", new MonthReport(
                YearMonth.of( 2026, 1 ), Arrays.asList(
                        new SourceLine( "Website", new BigDecimal( "100.00" ), 4, new BigDecimal( "25.00" ) ) ) ) );
        assertEquals( 0, BigDecimal.ZERO.compareTo( noBdc.getTotals().getBdcCommission() ) );
        assertEquals( new BigDecimal( "100.00" ), noBdc.getTotals().getNetRevenue() );
        assertEquals( new BigDecimal( "25.00" ), noBdc.getTotals().getAvgPricePerBed() );

        ChannelProductionDto empty = RonbotReadService.toChannelProductionDto( "lsh",
                new MonthReport( YearMonth.of( 2026, 2 ), List.of() ) );
        assertEquals( 0, empty.getSources().size() );
        assertEquals( 0, empty.getTotals().getRoomsSold() );
        assertNull( empty.getTotals().getAvgPricePerBed() );
    }
}
