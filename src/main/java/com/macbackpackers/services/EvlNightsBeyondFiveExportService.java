package com.macbackpackers.services;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.EdinburghVisitorLevyCalculator.LevyNight;

/**
 * Builds an Excel export of EVL-charged stays with calendar nights &gt; 5, enriched with Cloudbeds
 * room revenue for eligible nights 6+ (one column per night) plus quarterly summary formulas.
 */
@Service
public class EvlNightsBeyondFiveExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger( EvlNightsBeyondFiveExportService.class );

    private static final int MAX_LEVY_NIGHTS = 5;

    private static final String[] FIXED_HEADERS = {
            "reservation_id",
            "booking_reference",
            "guest_name",
            "booking_source",
            "hotel_collect_yn",
            "room_types",
            "payment_total",
            "payment_outstanding",
            "booked_date",
            "checkin_date",
            "checkout_date",
            "cloudbeds_nights",
            "num_guests",
            "status",
            "visitor_levy_total",
            "grand_total",
            "room_revenue_all_nights",
            "room_revenue_eligible_nights_1_to_5",
            "eligible_night_count"
    };

    private static final int COL_CHECKOUT_DATE = 10;
    private static final int COL_FIRST_PER_NIGHT = FIXED_HEADERS.length;

    private static final String CANDIDATE_SQL =
            "SELECT "
                    + "  a.reservation_id, "
                    + "  MAX(a.booking_reference) AS booking_reference, "
                    + "  MAX(a.guest_name) AS guest_name, "
                    + "  MAX(a.booking_source) AS booking_source, "
                    + "  MAX(a.hotel_collect_yn) AS hotel_collect_yn, "
                    + "  MAX(a.rate_plan_name) AS room_types, "
                    + "  MAX(a.payment_total) AS payment_total, "
                    + "  MAX(a.payment_outstanding) AS payment_outstanding, "
                    + "  MAX(a.booked_date) AS booked_date, "
                    + "  MIN(a.checkin_date) AS checkin_date "
                    + "FROM wp_lh_calendar a "
                    + "INNER JOIN ( "
                    + "  SELECT reservation_id, MAX(job_id) AS job_id "
                    + "  FROM wp_lh_calendar "
                    + "  WHERE reservation_id > 0 "
                    + "  GROUP BY reservation_id "
                    + ") latest "
                    + "  ON latest.reservation_id = a.reservation_id "
                    + " AND latest.job_id = a.job_id "
                    + "WHERE a.reservation_id > 0 "
                    + "GROUP BY a.reservation_id "
                    + "HAVING MAX(a.visitor_levy_total) > 0 "
                    + "   AND MAX(a.checkout_date) > '2026-07-24' "
                    + "   AND DATEDIFF(MAX(a.checkout_date), MIN(a.checkin_date)) > 5 "
                    + "ORDER BY checkin_date, a.reservation_id";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private Environment environment;

    @Value( "${evl.stay.date.from:2026-07-24}" )
    private String stayDateFromProperty;

    /**
     * Builds the report into a temp {@code .xlsx} file. Caller must delete the file when done.
     *
     * @param webClient Cloudbeds web client
     * @param propertyCode short property code (crh/hsh/rmb) used in the filename
     * @return export result including file path and row counts
     */
    public ExportResult exportToTempFile( WebClient webClient, String propertyCode ) throws Exception {
        LocalDate stayDateFrom = LocalDate.parse( stayDateFromProperty );
        File outFile = File.createTempFile( "evl_nights6plus_" + propertyCode + "_", ".xlsx" );

        LOGGER.info(
                "EVL nights 6+ export for property={} stayDateFrom={}. "
                        + "Rate semantics: direct/exclusive = VAT-inclusive gross; "
                        + "BDC/Agoda inclusive = per-person all-in; Hostelworld = net. "
                        + "Output={}",
                propertyCode, stayDateFrom, outFile.getAbsolutePath() );

        @SuppressWarnings( "unchecked" )
        List<Object[]> candidates = em.createNativeQuery( CANDIDATE_SQL ).getResultList();
        LOGGER.info( "SQL returned {} candidate reservations", candidates.size() );

        List<ExportRow> rows = new ArrayList<>( candidates.size() );
        int errors = 0;
        int maxEligibleNights = 0;

        for ( int i = 0; i < candidates.size(); i++ ) {
            Object[] c = candidates.get( i );
            String reservationId = String.valueOf( toLong( c[0] ) );
            ExportRow exportRow = new ExportRow( c );

            try {
                Reservation reservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );
                populateFromReservation( exportRow, reservation, stayDateFrom );
                maxEligibleNights = Math.max( maxEligibleNights, exportRow.eligibleNightCount );
            }
            catch ( Exception e ) {
                errors++;
                exportRow.error = e.getClass().getSimpleName() + ": " + e.getMessage();
                LOGGER.warn( "Failed to enrich reservation_id={}: {}", reservationId, e.toString() );
            }

            rows.add( exportRow );

            if ( ( i + 1 ) % 20 == 0 || i + 1 == candidates.size() ) {
                LOGGER.info( "Progress {}/{} (errors={}, maxEligibleNights={})",
                        i + 1, candidates.size(), errors, maxEligibleNights );
            }
        }

        int maxNightColumn = Math.max( MAX_LEVY_NIGHTS + 1, maxEligibleNights );
        LOGGER.info( "Writing sheet with per-night columns room_revenue_night_6 .. room_revenue_night_{}",
                maxNightColumn );

        try ( Workbook workbook = new XSSFWorkbook() ) {
            Sheet sheet = workbook.createSheet( "nights6plus" );
            CellStyle headerStyle = createHeaderStyle( workbook );
            CellStyle bodyStyle = createBodyStyle( workbook );
            CellStyle dateStyle = createDateStyle( workbook );
            CellStyle currencyStyle = createCurrencyStyle( workbook );

            List<String> headers = buildHeaders( maxNightColumn );
            Row headerRow = sheet.createRow( 0 );
            for ( int i = 0; i < headers.size(); i++ ) {
                Cell cell = headerRow.createCell( i );
                cell.setCellValue( headers.get( i ) );
                cell.setCellStyle( headerStyle );
                sheet.setColumnWidth( i, 4500 );
            }

            int rowNum = 1;
            for ( ExportRow exportRow : rows ) {
                writeRow( sheet.createRow( rowNum++ ), exportRow, maxNightColumn,
                        bodyStyle, dateStyle, currencyStyle );
            }

            int lastDataExcelRow = rows.isEmpty() ? 1 : rows.size() + 1;
            int lastPerNightCol = COL_FIRST_PER_NIGHT + Math.max( 0, maxNightColumn - MAX_LEVY_NIGHTS ) - 1;
            writeQuarterlyRoomRevenueSummaries( sheet, rowNum + 1, lastDataExcelRow, lastPerNightCol,
                    LocalDate.now(), headerStyle, currencyStyle );

            try ( FileOutputStream out = new FileOutputStream( outFile ) ) {
                workbook.write( out );
            }
        }

        LOGGER.info( "Wrote {}", outFile.getAbsolutePath() );
        return new ExportResult( outFile, rows.size(), errors, propertyCode );
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

    /**
     * Reporting quarter months: the three months of the current calendar quarter, except during
     * the first month of a quarter (Jan/Apr/Jul/Oct) when the previous quarter is used — so
     * October still reports Jul–Sep until 1 November.
     */
    static List<YearMonth> reportingQuarterMonths( LocalDate today ) {
        int month = today.getMonthValue();
        int year = today.getYear();
        boolean firstMonthOfQuarter = month % 3 == 1;
        int quarterStartMonth;
        int quarterYear = year;
        if ( firstMonthOfQuarter ) {
            if ( month == 1 ) {
                quarterStartMonth = 10;
                quarterYear = year - 1;
            }
            else {
                quarterStartMonth = month - 3;
            }
        }
        else {
            quarterStartMonth = ( ( month - 1 ) / 3 ) * 3 + 1;
        }
        YearMonth start = YearMonth.of( quarterYear, quarterStartMonth );
        List<YearMonth> months = new ArrayList<>( 3 );
        months.add( start );
        months.add( start.plusMonths( 1 ) );
        months.add( start.plusMonths( 2 ) );
        return months;
    }

    static String excelColumnLetter( int zeroBasedIndex ) {
        int n = zeroBasedIndex + 1;
        StringBuilder sb = new StringBuilder();
        while ( n > 0 ) {
            n--;
            sb.insert( 0, (char) ( 'A' + ( n % 26 ) ) );
            n /= 26;
        }
        return sb.toString();
    }

    static List<LevyNight> allEligibleNights( Map<LocalDate, BigDecimal> ratesByDate,
            LocalDate checkoutDate, LocalDate stayDateFrom ) {
        List<LevyNight> eligibleNights = new ArrayList<>();
        for ( Map.Entry<LocalDate, BigDecimal> entry : ratesByDate.entrySet() ) {
            LocalDate night = entry.getKey();
            if ( false == night.isBefore( stayDateFrom ) && night.isBefore( checkoutDate ) ) {
                eligibleNights.add( new LevyNight( night, entry.getValue() ) );
            }
        }
        eligibleNights.sort( Comparator.comparing( LevyNight::getDate ) );
        return eligibleNights;
    }

    private void writeQuarterlyRoomRevenueSummaries( Sheet sheet, int startRowIndex,
            int lastDataExcelRow, int lastPerNightColZeroBased, LocalDate today,
            CellStyle labelStyle, CellStyle currencyStyle ) {
        if ( lastPerNightColZeroBased < COL_FIRST_PER_NIGHT ) {
            LOGGER.warn( "No per-night columns; skipping quarterly summaries" );
            return;
        }

        String checkoutCol = excelColumnLetter( COL_CHECKOUT_DATE );
        String firstNightCol = excelColumnLetter( COL_FIRST_PER_NIGHT );
        String lastNightCol = excelColumnLetter( lastPerNightColZeroBased );
        String checkoutRange = "$" + checkoutCol + "$2:$" + checkoutCol + "$" + lastDataExcelRow;
        String nightsRange = "$" + firstNightCol + "$2:$" + lastNightCol + "$" + lastDataExcelRow;

        List<YearMonth> months = reportingQuarterMonths( today );
        LOGGER.info( "Quarterly summary months (as of {}): {}", today, months );

        int rowIndex = startRowIndex;
        for ( YearMonth month : months ) {
            String monthLabel = month.getMonth().getDisplayName( TextStyle.FULL, Locale.ENGLISH );
            Row row = sheet.createRow( rowIndex++ );

            Cell label = row.createCell( 0 );
            label.setCellValue( "Room Revenue for " + monthLabel );
            label.setCellStyle( labelStyle );

            LocalDate monthStart = month.atDay( 1 );
            LocalDate nextMonthStart = month.plusMonths( 1 ).atDay( 1 );
            String formula = "SUMPRODUCT((" + checkoutRange + ">=DATE("
                    + monthStart.getYear() + "," + monthStart.getMonthValue() + ",1))*("
                    + checkoutRange + "<DATE("
                    + nextMonthStart.getYear() + "," + nextMonthStart.getMonthValue() + ",1))*("
                    + nightsRange + "))";

            Cell total = row.createCell( 2 );
            total.setCellFormula( formula );
            total.setCellStyle( currencyStyle );
        }
    }

    private static List<String> buildHeaders( int maxNightColumn ) {
        List<String> headers = new ArrayList<>( FIXED_HEADERS.length + Math.max( 0, maxNightColumn - MAX_LEVY_NIGHTS ) + 1 );
        Collections.addAll( headers, FIXED_HEADERS );
        for ( int night = MAX_LEVY_NIGHTS + 1; night <= maxNightColumn; night++ ) {
            headers.add( "room_revenue_night_" + night );
        }
        headers.add( "error" );
        return headers;
    }

    private void populateFromReservation( ExportRow exportRow, Reservation reservation,
            LocalDate stayDateFrom ) {
        Map<LocalDate, BigDecimal> ratesByDate = reservation.getRatesByDate( gson );
        List<LevyNight> allEligible = allEligibleNights(
                ratesByDate, reservation.getCheckoutDateAsLocalDate(), stayDateFrom );

        List<LevyNight> firstFive = allEligible.size() <= MAX_LEVY_NIGHTS
                ? allEligible
                : allEligible.subList( 0, MAX_LEVY_NIGHTS );

        exportRow.checkinDate = toUtilDate( reservation.getCheckinDateAsLocalDate() );
        exportRow.checkoutDate = toUtilDate( reservation.getCheckoutDateAsLocalDate() );
        exportRow.cloudbedsNights = reservation.getNights();
        exportRow.numGuests = reservation.getNumberOfGuests();
        exportRow.status = reservation.getStatus();
        exportRow.visitorLevyTotal = reservation.getVisitorLevyTotal();
        exportRow.grandTotal = reservation.getGrandTotal();
        exportRow.roomRevenueAllNights = ratesByDate.values().stream()
                .reduce( BigDecimal.ZERO, BigDecimal::add );
        exportRow.roomRevenueEligibleNights1To5 = sumRates( firstFive );
        exportRow.eligibleNightCount = allEligible.size();
        exportRow.perNightRatesFrom6 = new ArrayList<>();
        for ( int i = MAX_LEVY_NIGHTS; i < allEligible.size(); i++ ) {
            exportRow.perNightRatesFrom6.add( allEligible.get( i ).getRate() );
        }
    }

    private void writeRow( Row row, ExportRow exportRow, int maxNightColumn,
            CellStyle bodyStyle, CellStyle dateStyle, CellStyle currencyStyle ) {
        Object[] c = exportRow.sqlColumns;
        setString( row, 0, String.valueOf( toLong( c[0] ) ), bodyStyle );
        setString( row, 1, asString( c[1] ), bodyStyle );
        setString( row, 2, asString( c[2] ), bodyStyle );
        setString( row, 3, asString( c[3] ), bodyStyle );
        setString( row, 4, asString( c[4] ), bodyStyle );
        setString( row, 5, asString( c[5] ), bodyStyle );
        setCurrency( row, 6, toBigDecimal( c[6] ), currencyStyle );
        setCurrency( row, 7, toBigDecimal( c[7] ), currencyStyle );
        setDate( row, 8, c[8], dateStyle );

        setDate( row, 9, exportRow.checkinDate, dateStyle );
        setDate( row, 10, exportRow.checkoutDate, dateStyle );
        setString( row, 11, exportRow.cloudbedsNights, bodyStyle );
        if ( exportRow.numGuests != null ) {
            setNumber( row, 12, BigDecimal.valueOf( exportRow.numGuests ), bodyStyle );
        }
        else {
            setString( row, 12, "", bodyStyle );
        }
        setString( row, 13, exportRow.status, bodyStyle );
        setCurrency( row, 14, exportRow.visitorLevyTotal, currencyStyle );
        setCurrency( row, 15, exportRow.grandTotal, currencyStyle );
        setCurrency( row, 16, exportRow.roomRevenueAllNights, currencyStyle );
        setCurrency( row, 17, exportRow.roomRevenueEligibleNights1To5, currencyStyle );
        setNumber( row, 18, BigDecimal.valueOf( exportRow.eligibleNightCount ), bodyStyle );

        int col = COL_FIRST_PER_NIGHT;
        for ( int night = MAX_LEVY_NIGHTS + 1; night <= maxNightColumn; night++ ) {
            int beyondIndex = night - ( MAX_LEVY_NIGHTS + 1 );
            BigDecimal rate = beyondIndex < exportRow.perNightRatesFrom6.size()
                    ? exportRow.perNightRatesFrom6.get( beyondIndex )
                    : null;
            setCurrency( row, col++, rate, currencyStyle );
        }
        setString( row, col, exportRow.error == null ? "" : exportRow.error, bodyStyle );
    }

    private static BigDecimal sumRates( List<LevyNight> nights ) {
        return nights.stream()
                .map( LevyNight::getRate )
                .reduce( BigDecimal.ZERO, BigDecimal::add );
    }

    private static CellStyle createHeaderStyle( Workbook workbook ) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName( "Arial" );
        font.setFontHeightInPoints( (short) 10 );
        font.setBold( true );
        style.setFont( font );
        return style;
    }

    private static CellStyle createBodyStyle( Workbook workbook ) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName( "Arial" );
        font.setFontHeightInPoints( (short) 10 );
        style.setFont( font );
        return style;
    }

    private static CellStyle createDateStyle( Workbook workbook ) {
        CellStyle style = createBodyStyle( workbook );
        style.setDataFormat( workbook.createDataFormat().getFormat( "yyyy-mm-dd" ) );
        return style;
    }

    private static CellStyle createCurrencyStyle( Workbook workbook ) {
        CellStyle style = createBodyStyle( workbook );
        style.setDataFormat( workbook.createDataFormat().getFormat( "#,##0.00" ) );
        return style;
    }

    private static void setString( Row row, int col, String value, CellStyle style ) {
        Cell cell = row.createCell( col );
        cell.setCellValue( value == null ? "" : value );
        cell.setCellStyle( style );
    }

    private static void setNumber( Row row, int col, BigDecimal value, CellStyle style ) {
        Cell cell = row.createCell( col );
        if ( value != null ) {
            cell.setCellValue( value.doubleValue() );
        }
        cell.setCellStyle( style );
    }

    private static void setCurrency( Row row, int col, BigDecimal value, CellStyle style ) {
        Cell cell = row.createCell( col );
        if ( value != null ) {
            cell.setCellValue( value.doubleValue() );
        }
        cell.setCellStyle( style );
    }

    private static void setDate( Row row, int col, Object value, CellStyle style ) {
        Cell cell = row.createCell( col );
        Date date = toUtilDate( value );
        if ( date != null ) {
            cell.setCellValue( date );
        }
        cell.setCellStyle( style );
    }

    private static Date toUtilDate( Object value ) {
        if ( value == null ) {
            return null;
        }
        if ( value instanceof Date ) {
            return (Date) value;
        }
        if ( value instanceof LocalDate ) {
            return java.sql.Date.valueOf( (LocalDate) value );
        }
        if ( value instanceof java.sql.Timestamp ) {
            return new Date( ( (java.sql.Timestamp) value ).getTime() );
        }
        return null;
    }

    private static String asString( Object value ) {
        return value == null ? "" : String.valueOf( value );
    }

    private static long toLong( Object value ) {
        if ( value instanceof Number ) {
            return ( (Number) value ).longValue();
        }
        return Long.parseLong( String.valueOf( value ) );
    }

    private static BigDecimal toBigDecimal( Object value ) {
        if ( value == null ) {
            return null;
        }
        if ( value instanceof BigDecimal ) {
            return (BigDecimal) value;
        }
        if ( value instanceof Number ) {
            return BigDecimal.valueOf( ( (Number) value ).doubleValue() );
        }
        return new BigDecimal( String.valueOf( value ) );
    }

    public static final class ExportResult {
        private final File file;
        private final int rowCount;
        private final int errorCount;
        private final String propertyCode;

        public ExportResult( File file, int rowCount, int errorCount, String propertyCode ) {
            this.file = file;
            this.rowCount = rowCount;
            this.errorCount = errorCount;
            this.propertyCode = propertyCode;
        }

        public File getFile() {
            return file;
        }

        public int getRowCount() {
            return rowCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public String getPropertyCode() {
            return propertyCode;
        }
    }

    private static final class ExportRow {
        private final Object[] sqlColumns;
        private Date checkinDate;
        private Date checkoutDate;
        private String cloudbedsNights;
        private Integer numGuests;
        private String status;
        private BigDecimal visitorLevyTotal;
        private BigDecimal grandTotal;
        private BigDecimal roomRevenueAllNights;
        private BigDecimal roomRevenueEligibleNights1To5;
        private int eligibleNightCount;
        private List<BigDecimal> perNightRatesFrom6 = Collections.emptyList();
        private String error;

        private ExportRow( Object[] sqlColumns ) {
            this.sqlColumns = sqlColumns;
        }
    }
}
