package com.macbackpackers.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.htmlunit.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.scrapers.datainsights.CloudbedsDataInsightsClient;

/**
 * Builds the monthly Channel Production comparison workbook from Data Insights stock report 191
 * (month × source totals; one sheet per year).
 */
@Service
public class ChannelProductionReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger( ChannelProductionReportService.class );

    public static final String BOOKING_COM = "Booking.com";

    public static final String BDC_COMMISSION_DIVISOR = "6.67";

    private static final String GBP_ACCOUNTING_FORMAT =
            "_-[$£-809]* #,##0.00_-;\\-[$£-809]* #,##0.00_-;_-[$£-809]* \"-\"??_-;_-@_-";

    @Autowired
    private CloudbedsDataInsightsClient dataInsightsClient;

    @Autowired
    private Environment environment;

    /**
     * Fetches Channel Production totals by source for the given stay-date month.
     */
    public MonthReport fetchMonth( WebClient webClient, YearMonth month ) throws IOException {
        JsonObject response = dataInsightsClient.queryChannelProductionByMonth( webClient, month );
        MonthReport report = parseMonth( response, month );
        LOGGER.info( "Channel Production {}: {} source(s)", month, report.getLines().size() );
        return report;
    }

    /**
     * Parses a month × category × source {@code /query/data} response. Placeholder ("-") sources
     * and sources with neither revenue nor rooms sold are dropped.
     */
    static MonthReport parseMonth( JsonObject response, YearMonth month ) {
        List<SourceLine> lines = new ArrayList<>();
        JsonObject records = response == null ? null : response.getAsJsonObject( "records" );
        if ( records != null ) {
            for ( Map.Entry<String, JsonElement> monthEntry : records.entrySet() ) {
                for ( Map.Entry<String, JsonElement> categoryEntry : monthEntry.getValue().getAsJsonObject().entrySet() ) {
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
                        lines.add( new SourceLine( source, revenue, rooms, adr ) );
                    }
                }
            }
        }
        lines.sort( Comparator.comparing( SourceLine::getSource, String.CASE_INSENSITIVE_ORDER ) );
        return new MonthReport( month, lines );
    }

    private static String metricValue( JsonObject metrics, String column, String metric ) {
        if ( false == metrics.has( column ) || false == metrics.get( column ).isJsonObject() ) {
            return null;
        }
        JsonElement el = metrics.getAsJsonObject( column ).get( metric );
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }

    /** Parses a formatted Data Insights number such as {@code "19,405.83"}; {@code "-"} is null. */
    static BigDecimal parseNumber( String value ) {
        if ( StringUtils.isBlank( value ) || "-".equals( value.trim() ) ) {
            return null;
        }
        return new BigDecimal( value.replace( ",", "" ).trim() );
    }

    /**
     * Writes one sheet per report (named by year) to a temp {@code .xlsx}. Caller must delete the file.
     *
     * @param propertyCode short property code used in the temp filename
     * @param reportsNewestFirst reports ordered newest year first; each sheet compares against the next
     */
    public File writeWorkbook( String propertyCode, List<MonthReport> reportsNewestFirst ) throws IOException {
        File outFile = File.createTempFile( "channel_production_" + propertyCode + "_", ".xlsx" );
        try ( Workbook workbook = buildWorkbook( reportsNewestFirst );
                FileOutputStream out = new FileOutputStream( outFile ) ) {
            workbook.write( out );
        }
        LOGGER.info( "Wrote {}", outFile.getAbsolutePath() );
        return outFile;
    }

    static Workbook buildWorkbook( List<MonthReport> reportsNewestFirst ) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Font defaultFont = workbook.getFontAt( 0 );
        defaultFont.setFontName( "Calibri" );
        defaultFont.setFontHeightInPoints( (short) 12 );

        Styles styles = new Styles( workbook );
        for ( int i = 0; i < reportsNewestFirst.size(); i++ ) {
            MonthReport report = reportsNewestFirst.get( i );
            String previousSheet = i + 1 < reportsNewestFirst.size()
                    ? sheetName( reportsNewestFirst.get( i + 1 ) )
                    : null;
            writeSheet( workbook.createSheet( sheetName( report ) ), report, previousSheet, styles );
        }
        workbook.setForceFormulaRecalculation( true );
        return workbook;
    }

    static String sheetName( MonthReport report ) {
        return String.valueOf( report.getMonth().getYear() );
    }

    private static void writeSheet( Sheet sheet, MonthReport report, String previousSheet, Styles styles ) {
        List<SourceLine> lines = report.getLines();
        int n = lines.size();
        BigDecimal totalRevenue = lines.stream().map( SourceLine::getRevenue ).reduce( BigDecimal.ZERO, BigDecimal::add );
        int totalRooms = lines.stream().mapToInt( SourceLine::getRoomsSold ).sum();

        // Excel rows below are 1-based
        int revenueFirstRow = 3;
        int revenueTotalRow = revenueFirstRow + n;
        int roomNightsTitleRow = revenueTotalRow + 1;
        int roomNightsFirstRow = roomNightsTitleRow + 2;
        int roomNightsLastRow = roomNightsFirstRow + n - 1;
        int adrTitleRow = roomNightsLastRow + 2;
        int adrFirstRow = adrTitleRow + 2;

        setString( sheet, 1, 0, "Revenue " + report.getMonth().getYear(), null );
        setString( sheet, 2, 0, "Source", null );
        setString( sheet, 2, 1, "Revenue", null );
        setString( sheet, 2, 2, "% of Revenue", null );
        Integer bookingComRow = null;
        for ( int i = 0; i < n; i++ ) {
            SourceLine line = lines.get( i );
            int r = revenueFirstRow + i;
            setString( sheet, r, 0, line.getSource(), null );
            setNumber( sheet, r, 1, line.getRevenue() );
            setNumber( sheet, r, 2, percentOf( line.getRevenue(), totalRevenue ) );
            if ( BOOKING_COM.equalsIgnoreCase( line.getSource() ) ) {
                bookingComRow = r;
            }
        }
        setString( sheet, revenueTotalRow, 0, "TOTAL", null );
        setFormula( sheet, revenueTotalRow, 1, n == 0 ? "0" : "SUM(B" + revenueFirstRow + ":B" + ( revenueTotalRow - 1 ) + ")", null );

        setString( sheet, roomNightsTitleRow, 0, "Room Nights", null );
        setString( sheet, roomNightsTitleRow + 1, 0, "Source", null );
        setString( sheet, roomNightsTitleRow + 1, 1, "Room Nights", null );
        setString( sheet, roomNightsTitleRow + 1, 2, "% of Room Nights", null );
        for ( int i = 0; i < n; i++ ) {
            SourceLine line = lines.get( i );
            int r = roomNightsFirstRow + i;
            setString( sheet, r, 0, line.getSource(), null );
            setNumber( sheet, r, 1, BigDecimal.valueOf( line.getRoomsSold() ) );
            setNumber( sheet, r, 2, percentOf( BigDecimal.valueOf( line.getRoomsSold() ), BigDecimal.valueOf( totalRooms ) ) );
        }

        setString( sheet, adrTitleRow, 0, "Average Daily Rate", null );
        setString( sheet, adrTitleRow + 1, 0, "Source", null );
        setString( sheet, adrTitleRow + 1, 1, "Average Daily Rate", null );
        for ( int i = 0; i < n; i++ ) {
            SourceLine line = lines.get( i );
            int r = adrFirstRow + i;
            setString( sheet, r, 0, line.getSource(), null );
            setNumber( sheet, r, 1, line.getAdr() );
        }

        setString( sheet, 3, 4, "BDC COMMISSION", null );
        setFormula( sheet, 3, 5, bookingComRow == null ? "0" : "B" + bookingComRow + "/" + BDC_COMMISSION_DIVISOR, styles.currency );
        setString( sheet, 4, 4, "TOTAL COMMISSION", null );
        setFormula( sheet, 4, 5, "SUM(F1:F3)", styles.currency );
        setString( sheet, 9, 4, "GROSS REVENUE", null );
        setFormula( sheet, 9, 5, "B" + revenueTotalRow, styles.currency );
        setString( sheet, 10, 4, "NET REVENUE (AFTER COMMISSION)", styles.bold );
        setFormula( sheet, 10, 5, "F9-F4", styles.boldCurrency );
        if ( previousSheet != null ) {
            setString( sheet, 13, 4, "HOW MUCH MORE WE MADE", styles.bold );
            setFormula( sheet, 13, 5, "F10-'" + previousSheet + "'!F10", styles.boldCurrency );
            setString( sheet, 14, 4, "HOW MUCH MORE COMMISSION WE PAID", null );
            setFormula( sheet, 14, 5, "F4-'" + previousSheet + "'!F4", styles.currency );
        }
        setString( sheet, 17, 4, "BEDS SOLD", null );
        setFormula( sheet, 17, 5, n == 0 ? "0" : "SUM(B" + roomNightsFirstRow + ":B" + roomNightsLastRow + ")", null );
        setString( sheet, 18, 4, "% OF BEDS OCCUPIED", styles.bold );
        setString( sheet, 19, 4, "AVG PRICE PER BED", null );
        setFormula( sheet, 19, 5, "F10/F17", styles.currency );

        sheet.setColumnWidth( 4, (int) ( 36.5 * 256 ) );
        sheet.setColumnWidth( 5, (int) ( 11.5 * 256 ) );
    }

    /** Percentage (0–100) rounded to 2 dp, e.g. {@code 31.75}. */
    public static BigDecimal percentOf( BigDecimal value, BigDecimal total ) {
        if ( total == null || total.signum() == 0 ) {
            return BigDecimal.ZERO;
        }
        return value.multiply( BigDecimal.valueOf( 100 ) ).divide( total, 2, RoundingMode.HALF_UP );
    }

    private static Cell cell( Sheet sheet, int excelRow, int col ) {
        Row row = sheet.getRow( excelRow - 1 );
        if ( row == null ) {
            row = sheet.createRow( excelRow - 1 );
        }
        return row.createCell( col );
    }

    private static void setString( Sheet sheet, int excelRow, int col, String value, CellStyle style ) {
        Cell c = cell( sheet, excelRow, col );
        c.setCellValue( value );
        if ( style != null ) {
            c.setCellStyle( style );
        }
    }

    private static void setNumber( Sheet sheet, int excelRow, int col, BigDecimal value ) {
        Cell c = cell( sheet, excelRow, col );
        if ( value != null ) {
            c.setCellValue( value.doubleValue() );
        }
    }

    private static void setFormula( Sheet sheet, int excelRow, int col, String formula, CellStyle style ) {
        Cell c = cell( sheet, excelRow, col );
        c.setCellFormula( formula );
        if ( style != null ) {
            c.setCellStyle( style );
        }
    }

    /** Resolves crh/hsh/rmb/lsh from the active Spring profile. */
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

    private static final class Styles {
        private final CellStyle currency;
        private final CellStyle bold;
        private final CellStyle boldCurrency;

        private Styles( Workbook workbook ) {
            Font boldFont = workbook.createFont();
            boldFont.setFontName( "Calibri" );
            boldFont.setFontHeightInPoints( (short) 12 );
            boldFont.setBold( true );
            short gbp = workbook.createDataFormat().getFormat( GBP_ACCOUNTING_FORMAT );

            currency = workbook.createCellStyle();
            currency.setDataFormat( gbp );

            bold = workbook.createCellStyle();
            bold.setFont( boldFont );

            boldCurrency = workbook.createCellStyle();
            boldCurrency.setDataFormat( gbp );
            boldCurrency.setFont( boldFont );
        }
    }

    public static final class MonthReport {
        private final YearMonth month;
        private final List<SourceLine> lines;

        public MonthReport( YearMonth month, List<SourceLine> lines ) {
            this.month = month;
            this.lines = Collections.unmodifiableList( new ArrayList<>( lines ) );
        }

        public YearMonth getMonth() {
            return month;
        }

        public List<SourceLine> getLines() {
            return lines;
        }
    }

    public static final class SourceLine {
        private final String source;
        private final BigDecimal revenue;
        private final int roomsSold;
        private final BigDecimal adr;

        public SourceLine( String source, BigDecimal revenue, int roomsSold, BigDecimal adr ) {
            this.source = source;
            this.revenue = revenue;
            this.roomsSold = roomsSold;
            this.adr = adr;
        }

        public String getSource() {
            return source;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public int getRoomsSold() {
            return roomsSold;
        }

        public BigDecimal getAdr() {
            return adr;
        }
    }
}
