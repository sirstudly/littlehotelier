package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.services.ChannelProductionReportService.CommissionRate;
import com.macbackpackers.services.ChannelProductionReportService.MonthReport;
import com.macbackpackers.services.ChannelProductionReportService.SourceLine;

public class ChannelProductionReportServiceTest {

    @Test
    public void testParseMonth() throws Exception {
        JsonObject response;
        try ( InputStreamReader reader = new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream( "di_channel_production_month.json" ),
                StandardCharsets.UTF_8 ) ) {
            response = JsonParser.parseReader( reader ).getAsJsonObject();
        }

        MonthReport report = ChannelProductionReportService.parseMonth( response, YearMonth.of( 2024, 9 ) );
        List<SourceLine> lines = report.getLines();

        assertEquals( 4, lines.size() );
        assertEquals( "Booking.com", lines.get( 0 ).getSource() );
        assertEquals( new BigDecimal( "19405.83" ), lines.get( 0 ).getRevenue() );
        assertEquals( 835, lines.get( 0 ).getRoomsSold() );
        assertEquals( new BigDecimal( "23.24" ), lines.get( 0 ).getAdr() );
        assertEquals( "Hostelworld", lines.get( 1 ).getSource() );
        assertEquals( "Walk-In", lines.get( 2 ).getSource() );
        assertEquals( new BigDecimal( "41.66" ), lines.get( 2 ).getRevenue() );
        assertEquals( "Website", lines.get( 3 ).getSource() );
        assertEquals( new BigDecimal( "3607.37" ), lines.get( 3 ).getRevenue() );
        assertEquals( 163, lines.get( 3 ).getRoomsSold() );
    }

    @Test
    public void testBuildWorkbook() throws Exception {
        MonthReport aug2026 = new MonthReport( YearMonth.of( 2026, 8 ), Arrays.asList(
                line( "Booking.com", "14461.66", 382, "37.86" ),
                line( "Hostelworld", "18774.27", 493, "38.08" ),
                line( "Walk-In", "516", 14, "36.86" ),
                line( "Website", "11791.19", 278, "42.41" ) ), new BigDecimal( "81.84" ), SEEDED_RATES );
        MonthReport aug2025 = new MonthReport( YearMonth.of( 2025, 8 ), Arrays.asList(
                line( "Booking.com", "1712.86", 48, "35.68" ),
                line( "Hostelworld", "29982.80", 746, "40.19" ),
                line( "Walk-In", "58.33", 2, "29.17" ),
                line( "Website", "12343.70", 354, "34.87" ) ), null, SEEDED_RATES );
        MonthReport aug2024 = new MonthReport( YearMonth.of( 2024, 8 ), Arrays.asList(
                line( "Booking.com", "5029.19", 185, "27.18" ),
                line( "Hostelworld", "14487.73", 397, "36.49" ),
                line( "Phone", "22.22", 1, "22.22" ),
                line( "Walk-In", "315.57", 10, "31.56" ),
                line( "Website", "18357.54", 525, "34.97" ) ), null, SEEDED_RATES );

        try ( Workbook wb = ChannelProductionReportService.buildWorkbook( Arrays.asList( aug2026, aug2025, aug2024 ) ) ) {
            assertEquals( 3, wb.getNumberOfSheets() );
            assertEquals( "2026", wb.getSheetName( 0 ) );
            assertEquals( "2025", wb.getSheetName( 1 ) );
            assertEquals( "2024", wb.getSheetName( 2 ) );

            Sheet s2026 = wb.getSheet( "2026" );
            assertEquals( "Revenue 2026", string( s2026, "A1" ) );
            assertEquals( "TOTAL", string( s2026, "A7" ) );
            assertEquals( "SUM(B3:B6)", formula( s2026, "B7" ) );
            assertEquals( 31.75, number( s2026, "C3" ), 0.001 );
            assertEquals( "Room Nights", string( s2026, "A8" ) );
            assertEquals( 382, number( s2026, "B10" ), 0.001 );
            assertEquals( 32.73, number( s2026, "C10" ), 0.001 );
            assertEquals( "Average Daily Rate", string( s2026, "A15" ) );
            assertEquals( 42.41, number( s2026, "B20" ), 0.001 );

            assertEquals( "BDC COMMISSION", string( s2026, "E3" ) );
            assertEquals( "B3/6.67", formula( s2026, "F3" ) );
            assertEquals( "TOTAL COMMISSION", string( s2026, "E4" ) );
            assertEquals( "SUM(F3:F3)", formula( s2026, "F4" ) );
            assertEquals( "B7", formula( s2026, "F9" ) );
            assertEquals( "F9-F4", formula( s2026, "F10" ) );
            assertEquals( "F10-'2025'!F10", formula( s2026, "F13" ) );
            assertEquals( "F4-'2025'!F4", formula( s2026, "F14" ) );
            assertEquals( "SUM(B10:B13)", formula( s2026, "F17" ) );
            assertEquals( "% OF BEDS OCCUPIED", string( s2026, "E18" ) );
            assertEquals( 0.8184, number( s2026, "F18" ), 0.00001 );
            assertEquals( "0.00%", cellAt( s2026, "F18" ).getCellStyle().getDataFormatString() );
            assertNull( cellAt( wb.getSheet( "2025" ), "F18" ) );
            assertEquals( "F10/F17", formula( s2026, "F19" ) );

            assertEquals( "F10-'2024'!F10", formula( wb.getSheet( "2025" ), "F13" ) );

            Sheet s2024 = wb.getSheet( "2024" );
            assertEquals( "SUM(B3:B7)", formula( s2024, "B8" ) );
            assertEquals( "B8", formula( s2024, "F9" ) );
            assertEquals( "SUM(B11:B15)", formula( s2024, "F17" ) );
            assertEquals( "Average Daily Rate", string( s2024, "A17" ) );
            assertEquals( 34.97, number( s2024, "B23" ), 0.001 );
            assertNull( cellAt( s2024, "F13" ) );
            assertNull( cellAt( s2024, "F14" ) );
        }
    }

    @Test
    public void testBuildWorkbookMultipleCommissionSources() throws Exception {
        MonthReport aug2026 = new MonthReport( YearMonth.of( 2026, 8 ), Arrays.asList(
                line( "Agoda / Priceline", "398.42", 10, "39.84" ),
                line( "Airbnb (API)", "450.06", 11, "40.91" ),
                line( "Booking.com", "3509.25", 90, "38.99" ),
                line( "Hostelworld", "10000.00", 250, "40.00" ) ), null, SEEDED_RATES );
        MonthReport aug2025 = new MonthReport( YearMonth.of( 2025, 8 ), Arrays.asList(
                line( "Airbnb (API)", "139.68", 4, "34.92" ),
                line( "Booking.com", "1143.69", 30, "38.12" ),
                line( "Website", "5000.00", 120, "41.67" ) ), null, SEEDED_RATES );
        MonthReport aug2024 = new MonthReport( YearMonth.of( 2024, 8 ), Arrays.asList(
                line( "Website", "5000.00", 120, "41.67" ) ), null, SEEDED_RATES );

        try ( Workbook wb = ChannelProductionReportService.buildWorkbook( Arrays.asList( aug2026, aug2025, aug2024 ) ) ) {
            Sheet s2026 = wb.getSheet( "2026" );
            assertEquals( "AGODA COMMISSION", string( s2026, "E3" ) );
            assertEquals( "B3/6.67", formula( s2026, "F3" ) );
            assertEquals( "AIRBNB COMMISSION", string( s2026, "E4" ) );
            assertEquals( "B4/6.06", formula( s2026, "F4" ) );
            assertEquals( "BDC COMMISSION", string( s2026, "E5" ) );
            assertEquals( "B5/6.67", formula( s2026, "F5" ) );
            assertEquals( "TOTAL COMMISSION", string( s2026, "E6" ) );
            assertEquals( "SUM(F3:F5)", formula( s2026, "F6" ) );
            assertEquals( "B7", formula( s2026, "F9" ) );
            assertEquals( "F9-F6", formula( s2026, "F10" ) );
            assertEquals( "F10-'2025'!F10", formula( s2026, "F13" ) );
            assertEquals( "F6-'2025'!F5", formula( s2026, "F14" ) );

            Sheet s2025 = wb.getSheet( "2025" );
            assertEquals( "AIRBNB COMMISSION", string( s2025, "E3" ) );
            assertEquals( "BDC COMMISSION", string( s2025, "E4" ) );
            assertEquals( "SUM(F3:F4)", formula( s2025, "F5" ) );
            assertEquals( "F9-F5", formula( s2025, "F10" ) );
            assertEquals( "F5-'2024'!F3", formula( s2025, "F14" ) );

            Sheet s2024 = wb.getSheet( "2024" );
            assertEquals( "TOTAL COMMISSION", string( s2024, "E3" ) );
            assertEquals( "0", formula( s2024, "F3" ) );
            assertEquals( "F9-F3", formula( s2024, "F10" ) );
        }
    }

    @Test
    public void testSummaryShiftsWhenCommissionRowsOverflow() throws Exception {
        List<CommissionRate> rates = Arrays.asList(
                rate( "A", "6.67" ), rate( "B", "6.67" ), rate( "C", "6.67" ),
                rate( "D", "6.67" ), rate( "E", "6.67" ), rate( "F", "6.67" ) );
        MonthReport current = new MonthReport( YearMonth.of( 2026, 8 ), Arrays.asList(
                line( "A", "10", 1, "10" ), line( "B", "10", 1, "10" ), line( "C", "10", 1, "10" ),
                line( "D", "10", 1, "10" ), line( "E", "10", 1, "10" ), line( "F", "10", 1, "10" ) ),
                null, rates );
        MonthReport previous = new MonthReport( YearMonth.of( 2025, 8 ), Arrays.asList(
                line( "A", "10", 1, "10" ) ), null, rates );

        try ( Workbook wb = ChannelProductionReportService.buildWorkbook( Arrays.asList( current, previous ) ) ) {
            Sheet s2026 = wb.getSheet( "2026" );
            assertEquals( "SUM(F3:F8)", formula( s2026, "F9" ) );
            assertEquals( "GROSS REVENUE", string( s2026, "E10" ) );
            assertEquals( "F10-F9", formula( s2026, "F11" ) );
            assertEquals( "F11-'2025'!F10", formula( s2026, "F14" ) );
            assertEquals( "F9-'2025'!F4", formula( s2026, "F15" ) );
            assertEquals( "F11/F18", formula( s2026, "F20" ) );
        }
    }

    private static final List<CommissionRate> SEEDED_RATES = Arrays.asList(
            new CommissionRate( "Agoda / Priceline", "AGODA", new BigDecimal( "6.6700" ) ),
            new CommissionRate( "Airbnb (API)", "AIRBNB", new BigDecimal( "6.0600" ) ),
            new CommissionRate( "Booking.com", "BDC", new BigDecimal( "6.6700" ) ) );

    private static CommissionRate rate( String source, String divisor ) {
        return new CommissionRate( source, source, new BigDecimal( divisor ) );
    }

    private static SourceLine line( String source, String revenue, int rooms, String adr ) {
        return new SourceLine( source, new BigDecimal( revenue ), rooms, new BigDecimal( adr ) );
    }

    private static org.apache.poi.ss.usermodel.Cell cellAt( Sheet sheet, String ref ) {
        org.apache.poi.ss.util.CellReference cr = new org.apache.poi.ss.util.CellReference( ref );
        Row row = sheet.getRow( cr.getRow() );
        return row == null ? null : row.getCell( cr.getCol() );
    }

    private static String string( Sheet sheet, String ref ) {
        return cellAt( sheet, ref ).getStringCellValue();
    }

    private static String formula( Sheet sheet, String ref ) {
        return cellAt( sheet, ref ).getCellFormula();
    }

    private static double number( Sheet sheet, String ref ) {
        return cellAt( sheet, ref ).getNumericCellValue();
    }
}
