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
import java.util.stream.Collectors;

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
import com.macbackpackers.beans.BookingSourceLookup;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.datainsights.CloudbedsDataInsightsClient;

/**
 * Builds the monthly Channel Production comparison workbook from Data Insights stock report 191
 * (month × source totals; one sheet per year).
 */
@Service
public class ChannelProductionReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger( ChannelProductionReportService.class );

    private static final String GBP_ACCOUNTING_FORMAT =
            "_-[$£-809]* #,##0.00_-;\\-[$£-809]* #,##0.00_-;_-[$£-809]* \"-\"??_-;_-@_-";

    @Autowired
    private CloudbedsDataInsightsClient dataInsightsClient;

    @Autowired
    private OccupancyReportService occupancyReportService;

    @Autowired
    private WordPressDAO dao;

    @Autowired
    private Environment environment;

    /**
     * Fetches Channel Production totals by source, plus occupancy and the commission rates
     * valid at the start of the given stay-date month.
     */
    public MonthReport fetchMonth( WebClient webClient, YearMonth month ) throws IOException {
        JsonObject response = dataInsightsClient.queryChannelProductionByMonth( webClient, month );
        MonthReport parsed = parseMonth( response, month );
        BigDecimal occupancyPct = occupancyReportService
                .fetchOccupancy( webClient, month.atDay( 1 ), month.atEndOfMonth() )
                .getOccupancyPct();
        List<CommissionRate> rates = dao.fetchCommissionRates( month.atDay( 1 ) ).stream()
                .map( CommissionRate::from )
                .collect( Collectors.toList() );
        LOGGER.info( "Channel Production {}: {} source(s), occupancy {}%, {} commission rate(s)",
                month, parsed.getLines().size(), occupancyPct, rates.size() );
        return new MonthReport( month, parsed.getLines(), occupancyPct, rates );
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
            MonthReport previous = i + 1 < reportsNewestFirst.size() ? reportsNewestFirst.get( i + 1 ) : null;
            writeSheet( workbook.createSheet( sheetName( report ) ), report, previous, i == 0, styles );
        }
        workbook.setForceFormulaRecalculation( true );
        return workbook;
    }

    static String sheetName( MonthReport report ) {
        return String.valueOf( report.getMonth().getYear() );
    }

    private static void writeSheet( Sheet sheet, MonthReport report, MonthReport previous, boolean newest, Styles styles ) {
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

        int year = report.getMonth().getYear();
        setString( sheet, 1, 0, "Revenue " + year, styles.bold );
        setString( sheet, 2, 0, "Source", null );
        setString( sheet, 2, 1, "Revenue", null );
        setString( sheet, 2, 2, "% of Revenue", null );
        for ( int i = 0; i < n; i++ ) {
            SourceLine line = lines.get( i );
            int r = revenueFirstRow + i;
            setString( sheet, r, 0, line.getSource(), null );
            setNumber( sheet, r, 1, line.getRevenue() );
            setNumber( sheet, r, 2, percentOf( line.getRevenue(), totalRevenue ) );
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

        SummaryLayout layout = SummaryLayout.of( report, newest && previous != null );
        int commissionRow = SummaryLayout.FIRST_COMMISSION_ROW;
        for ( int i = 0; i < n; i++ ) {
            SourceLine line = lines.get( i );
            CommissionRate rate = report.findCommissionRate( line.getSource() );
            if ( rate != null ) {
                setString( sheet, commissionRow, 4, rate.getLabel() + " COMMISSION", null );
                setFormula( sheet, commissionRow, 5,
                        "B" + ( revenueFirstRow + i ) + "/" + rate.getDivisor().stripTrailingZeros().toPlainString(),
                        styles.currency );
                commissionRow++;
            }
        }
        int totalCommissionRow = layout.totalCommissionRow;
        setString( sheet, totalCommissionRow, 4, "TOTAL COMMISSION", null );
        setFormula( sheet, totalCommissionRow, 5, totalCommissionRow == SummaryLayout.FIRST_COMMISSION_ROW
                ? "0"
                : "SUM(F" + SummaryLayout.FIRST_COMMISSION_ROW + ":F" + ( totalCommissionRow - 1 ) + ")", styles.currency );
        setString( sheet, layout.grossRow, 4, "GROSS REVENUE " + year, null );
        setFormula( sheet, layout.grossRow, 5, "B" + revenueTotalRow, styles.currency );
        setString( sheet, layout.netRow, 4, "NET REVENUE " + year + " (AFTER COMMISSION)", styles.bold );
        setFormula( sheet, layout.netRow, 5, "F" + layout.grossRow + "-F" + totalCommissionRow, styles.boldCurrency );
        if ( previous != null ) {
            String previousSheet = sheetName( previous );
            SummaryLayout previousLayout = SummaryLayout.of( previous, false );
            String previousNetRef = "'" + previousSheet + "'!F" + previousLayout.netRow;
            if ( layout.previousGrossRow > 0 ) {
                setString( sheet, layout.previousGrossRow, 4, "GROSS REVENUE " + previousSheet, null );
                setFormula( sheet, layout.previousGrossRow, 5,
                        "'" + previousSheet + "'!F" + previousLayout.grossRow, styles.currency );
                setString( sheet, layout.previousNetRow, 4, "NET REVENUE " + previousSheet, null );
                setFormula( sheet, layout.previousNetRow, 5, previousNetRef, styles.currency );
                previousNetRef = "F" + layout.previousNetRow;
            }
            setString( sheet, layout.moreMadeRow, 4, "HOW MUCH MORE WE MADE", styles.bold );
            setFormula( sheet, layout.moreMadeRow, 5, "F" + layout.netRow + "-" + previousNetRef, styles.boldCurrency );
            setString( sheet, layout.moreCommissionRow, 4, "HOW MUCH MORE COMMISSION WE PAID", null );
            setFormula( sheet, layout.moreCommissionRow, 5,
                    "F" + totalCommissionRow + "-'" + previousSheet + "'!F" + previousLayout.totalCommissionRow, styles.currency );
        }
        setString( sheet, layout.bedsSoldRow, 4, "BEDS SOLD", null );
        setFormula( sheet, layout.bedsSoldRow, 5, n == 0 ? "0" : "SUM(B" + roomNightsFirstRow + ":B" + roomNightsLastRow + ")", null );
        setString( sheet, layout.occupiedRow, 4, "% OF BEDS OCCUPIED", styles.bold );
        if ( report.getOccupancyPct() != null ) {
            Cell occupied = cell( sheet, layout.occupiedRow, 5 );
            occupied.setCellValue( report.getOccupancyPct().movePointLeft( 2 ).doubleValue() );
            occupied.setCellStyle( styles.percent );
        }
        setString( sheet, layout.avgPriceRow, 4, "AVG PRICE PER BED", null );
        setFormula( sheet, layout.avgPriceRow, 5, "F" + layout.netRow + "/F" + layout.bedsSoldRow, styles.currency );

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
        private final CellStyle percent;

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

            percent = workbook.createCellStyle();
            percent.setDataFormat( workbook.createDataFormat().getFormat( "0.00%" ) );
        }
    }

    /** 1-based rows of the E:F summary block; rows below the commission block shift down only if it overflows. */
    static final class SummaryLayout {
        static final int FIRST_COMMISSION_ROW = 3;
        /** Commission rows that fit above the fixed gross-revenue row. */
        private static final int MAX_UNSHIFTED_COMMISSION_ROWS = 5;

        final int totalCommissionRow;
        final int grossRow;
        final int netRow;
        /** Previous year's gross/net revenue rows; only on the newest sheet (else 0). */
        final int previousGrossRow;
        final int previousNetRow;
        final int moreMadeRow;
        final int moreCommissionRow;
        final int bedsSoldRow;
        final int occupiedRow;
        final int avgPriceRow;

        private SummaryLayout( int commissionRows, boolean withPreviousRevenue ) {
            int shift = Math.max( 0, commissionRows - MAX_UNSHIFTED_COMMISSION_ROWS );
            totalCommissionRow = FIRST_COMMISSION_ROW + commissionRows;
            grossRow = 9 + shift;
            netRow = 10 + shift;
            previousGrossRow = withPreviousRevenue ? 11 + shift : 0;
            previousNetRow = withPreviousRevenue ? 12 + shift : 0;
            moreMadeRow = ( withPreviousRevenue ? 14 : 13 ) + shift;
            moreCommissionRow = moreMadeRow + 1;
            bedsSoldRow = 17 + shift;
            occupiedRow = 18 + shift;
            avgPriceRow = 19 + shift;
        }

        static SummaryLayout of( MonthReport report, boolean withPreviousRevenue ) {
            int commissionRows = (int) report.getLines().stream()
                    .filter( line -> report.findCommissionRate( line.getSource() ) != null )
                    .count();
            return new SummaryLayout( commissionRows, withPreviousRevenue );
        }
    }

    public static final class MonthReport {
        private final YearMonth month;
        private final List<SourceLine> lines;
        private final BigDecimal occupancyPct;
        private final List<CommissionRate> commissionRates;

        public MonthReport( YearMonth month, List<SourceLine> lines ) {
            this( month, lines, null );
        }

        public MonthReport( YearMonth month, List<SourceLine> lines, BigDecimal occupancyPct ) {
            this( month, lines, occupancyPct, Collections.emptyList() );
        }

        public MonthReport( YearMonth month, List<SourceLine> lines, BigDecimal occupancyPct,
                List<CommissionRate> commissionRates ) {
            this.month = month;
            this.lines = Collections.unmodifiableList( new ArrayList<>( lines ) );
            this.occupancyPct = occupancyPct;
            this.commissionRates = Collections.unmodifiableList( new ArrayList<>( commissionRates ) );
        }

        public List<CommissionRate> getCommissionRates() {
            return commissionRates;
        }

        /** Commission rate for the source (case-insensitive), or null if none applies. */
        public CommissionRate findCommissionRate( String source ) {
            for ( CommissionRate rate : commissionRates ) {
                if ( rate.getSource().equalsIgnoreCase( source ) ) {
                    return rate;
                }
            }
            return null;
        }

        public YearMonth getMonth() {
            return month;
        }

        public List<SourceLine> getLines() {
            return lines;
        }

        /** Beds occupied, 0–100; null if unavailable. */
        public BigDecimal getOccupancyPct() {
            return occupancyPct;
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

    /** Commission = revenue / divisor for the given booking source. */
    public static final class CommissionRate {
        private final String source;
        private final String label;
        private final BigDecimal divisor;

        public CommissionRate( String source, String label, BigDecimal divisor ) {
            this.source = source;
            this.label = label;
            this.divisor = divisor;
        }

        static CommissionRate from( BookingSourceLookup lookup ) {
            return new CommissionRate( lookup.getSource(), lookup.getReportLabel(), lookup.getCommissionDivisor() );
        }

        /** Commission on {@code revenue}, rounded to 2 dp. */
        public BigDecimal commissionOn( BigDecimal revenue ) {
            return revenue.divide( divisor, 2, RoundingMode.HALF_UP );
        }

        public String getSource() {
            return source;
        }

        public String getLabel() {
            return label;
        }

        public BigDecimal getDivisor() {
            return divisor;
        }
    }
}
