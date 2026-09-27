package com.macbackpackers.scrapers.datainsights;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.scrapers.CloudbedsAccessTokenService;
import com.macbackpackers.scrapers.CloudbedsJsonRequestFactory;

/**
 * Client for Cloudbeds Data Insights: Channel Production (stock report 191) and the classic
 * Occupancy report.
 */
@Component
public class CloudbedsDataInsightsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger( CloudbedsDataInsightsClient.class );

    /** Channel Production stock report (Occupancy dataset). */
    public static final int CHANNEL_PRODUCTION_STOCK_REPORT_ID = 191;

    public static final String MULTI_LEVEL_SOURCE = "4";

    private static final String DI_BASE = "https://api.cloudbeds.com/datainsights/v1.1";

    private static final String ROOM_ASSIGNMENTS_REPORT = DI_BASE
            + "/classic_reports/daily_activity_reports/room_assignments_report";

    private static final DateTimeFormatter MONTH_DAY_FORMAT = DateTimeFormatter.ofPattern( "MM-dd" );

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Autowired
    private CloudbedsAccessTokenService accessTokenService;

    @Autowired
    @Qualifier( "gsonForCloudbedsIdentity" )
    private Gson gson;

    private static final long TOKEN_EXPIRY_MARGIN_SEC = 300;

    private String cachedAccessToken;
    private long cachedAccessTokenExp;

    /**
     * Group columns matching the default Channel Production UI (month × category × source).
     * Stock report 191 allows at most 3 {@code group_rows} ("Length must be between 1 and 3").
     */
    private static final List<String> MONTHLY_SOURCE_GROUP_COLUMNS = Arrays.asList(
            "stay_date",
            "reservation_source_category",
            "reservation_source" );

    /**
     * Queries Channel Production totals for a single calendar month of stay dates, grouped by
     * month × category × source (same as the default Channel Production UI).
     *
     * @param webClient Cloudbeds session client (cookies)
     * @param month stay-date month
     * @return parsed JSON root of {@code /query/data}
     */
    public JsonObject queryChannelProductionByMonth( WebClient webClient, YearMonth month ) throws IOException {
        String propertyId = jsonRequestFactory.getPropertyId();
        JsonObject body = buildChannelProductionBody( propertyId, month.atDay( 1 ), month.atEndOfMonth() );
        String url = DI_BASE + "/stock_reports/" + CHANNEL_PRODUCTION_STOCK_REPORT_ID
                + "/query/data?mode=Run&format=formatted";
        LOGGER.info( "Data Insights Channel Production monthly query property={} month={}", propertyId, month );
        return postJson( webClient, url, propertyId, body );
    }

    /**
     * Queries the classic Occupancy report for {@code [start, end]} (inclusive) within a single
     * {@code year}; results are broken down by month.
     *
     * @param webClient Cloudbeds session client (cookies)
     * @param year report year (the API cannot span years)
     * @param start inclusive start within {@code year}
     * @param end inclusive end within {@code year}
     * @return parsed JSON root ({@code {"year":..., "results":[{"date":"MM","occupancy":..., ...}]}})
     */
    public JsonObject queryOccupancy( WebClient webClient, int year, MonthDay start, MonthDay end )
            throws IOException {
        String propertyId = jsonRequestFactory.getPropertyId();
        JsonObject body = new JsonObject();
        body.addProperty( "report_year", String.valueOf( year ) );
        body.addProperty( "period_start", MONTH_DAY_FORMAT.format( start ) );
        body.addProperty( "period_end", MONTH_DAY_FORMAT.format( end ) );
        body.addProperty( "grouping", "month" );
        LOGGER.info( "Data Insights Occupancy query property={} year={} period=[{} .. {}]",
                propertyId, year, start, end );
        return postJson( webClient, DI_BASE + "/classic_reports/production_reports/rooms_sold", propertyId, body );
    }

    /**
     * Runs the classic Room Assignments report for every room type: one row per bed per stay date.
     *
     * @param webClient Cloudbeds session client (cookies)
     * @param stayDateFrom first stay date (inclusive)
     * @param stayDateTo last stay date (inclusive)
     * @return non-null list of report rows
     */
    public List<RoomAssignmentsReportRow> queryRoomAssignments( WebClient webClient, LocalDate stayDateFrom,
            LocalDate stayDateTo ) throws IOException {
        String propertyId = jsonRequestFactory.getPropertyId();
        JsonObject picklist = postJson( webClient, ROOM_ASSIGNMENTS_REPORT + "/picklist", propertyId,
                buildRoomAssignmentsPicklistBody( propertyId ) );
        List<String> roomTypes = parsePicklist( picklist );
        LOGGER.info( "Data Insights Room Assignments query property={} stay dates [{} .. {}] over {} room types",
                propertyId, stayDateFrom, stayDateTo, roomTypes.size() );
        if ( roomTypes.isEmpty() ) {
            return new ArrayList<>();
        }
        return parseRoomAssignments( postJson( webClient, ROOM_ASSIGNMENTS_REPORT + "/data?mode=Run", propertyId,
                buildRoomAssignmentsBody( propertyId, stayDateFrom, stayDateTo, roomTypes ) ) );
    }

    static JsonObject buildRoomAssignmentsPicklistBody( String propertyId ) {
        JsonObject body = new JsonObject();
        body.add( "property_ids", propertyIds( propertyId ) );
        JsonArray groupRows = new JsonArray();
        groupRows.add( cdfColumn( "room_type" ) );
        body.add( "group_rows", groupRows );
        return body;
    }

    static JsonObject buildRoomAssignmentsBody( String propertyId, LocalDate stayDateFrom, LocalDate stayDateTo,
            List<String> roomTypes ) {
        JsonObject body = new JsonObject();
        body.add( "property_ids", propertyIds( propertyId ) );

        JsonArray and = new JsonArray();
        JsonObject from = cdfColumn( "stay_date" );
        from.addProperty( "operator", "greater_than_or_equal" );
        from.addProperty( "value", stayDateFrom.toString() );
        and.add( from );
        JsonObject to = cdfColumn( "stay_date" );
        to.addProperty( "operator", "less_than_or_equal" );
        to.addProperty( "value", stayDateTo.toString() );
        and.add( to );
        JsonObject types = cdfColumn( "room_type" );
        types.addProperty( "operator", "list_contains" );
        JsonArray typeValues = new JsonArray();
        roomTypes.forEach( typeValues::add );
        types.add( "value", typeValues );
        and.add( types );
        JsonObject filters = new JsonObject();
        filters.add( "and", and );
        body.add( "filters", filters );

        JsonObject settings = new JsonObject();
        settings.addProperty( "details", true );
        settings.addProperty( "totals", false );
        settings.addProperty( "transpose", false );
        body.add( "settings", settings );
        return body;
    }

    /** Room type names from the picklist ({@code index} is a list of single-element rows). */
    static List<String> parsePicklist( JsonObject picklist ) {
        List<String> values = new ArrayList<>();
        JsonElement index = picklist == null ? null : picklist.get( "index" );
        if ( index != null && index.isJsonArray() ) {
            for ( JsonElement row : index.getAsJsonArray() ) {
                if ( row.isJsonArray() && row.getAsJsonArray().size() > 0 ) {
                    values.add( row.getAsJsonArray().get( 0 ).getAsString() );
                }
            }
        }
        return values;
    }

    /**
     * Parses the report: {@code index[i]} is {@code [stay_date, room_type]} and {@code records} holds
     * one column array per header, aligned with {@code index}. Missing values are {@code "-"}.
     */
    public static List<RoomAssignmentsReportRow> parseRoomAssignments( JsonObject report ) {
        List<RoomAssignmentsReportRow> rows = new ArrayList<>();
        JsonElement indexEl = report == null ? null : report.get( "index" );
        JsonElement recordsEl = report == null ? null : report.get( "records" );
        if ( indexEl == null || !indexEl.isJsonArray() || recordsEl == null || !recordsEl.isJsonObject() ) {
            return rows;
        }
        JsonArray index = indexEl.getAsJsonArray();
        JsonObject records = recordsEl.getAsJsonObject();
        for ( int i = 0; i < index.size(); i++ ) {
            JsonArray key = index.get( i ).getAsJsonArray();
            RoomAssignmentsReportRow row = new RoomAssignmentsReportRow();
            row.setStayDate( LocalDate.parse( key.get( 0 ).getAsString() ) );
            row.setRoomType( key.size() > 1 ? key.get( 1 ).getAsString() : null );
            String roomName = recordString( records, "room_name", i );
            row.setRoomName( roomName == null ? null : StringEscapeUtils.unescapeHtml4( roomName ).trim() );
            // "-1" on unassigned virtual beds (paid beds, splits)
            String bookingId = recordString( records, "booking_id", i );
            row.setBookingId( bookingId == null || bookingId.startsWith( "-" ) ? null : bookingId );
            row.setReservationNumber( recordString( records, "reservation_number", i ) );
            row.setGuestFirstName( recordString( records, "custom_filtered_first_name", i ) );
            row.setGuestLastName( recordString( records, "custom_filtered_surname", i ) );
            row.setReservationsCount( recordInt( records, "custom_reservations_count", i ) );
            row.setBlockedCount( recordInt( records, "custom_blocked_count", i ) );
            row.setOutOfServiceCount( recordInt( records, "custom_out_of_service_count", i ) );
            row.setAvailableCount( recordInt( records, "custom_available_count", i ) );
            row.setCourtesyHoldCount( recordInt( records, "custom_courtesy_hold_count", i ) );
            rows.add( row );
        }
        return rows;
    }

    private static JsonElement recordValue( JsonObject records, String column, int i ) {
        JsonElement col = records.get( column );
        if ( col == null || !col.isJsonArray() || i >= col.getAsJsonArray().size() ) {
            return null;
        }
        JsonElement v = col.getAsJsonArray().get( i );
        return v == null || v.isJsonNull() ? null : v;
    }

    private static String recordString( JsonObject records, String column, int i ) {
        JsonElement v = recordValue( records, column, i );
        String s = v == null ? null : StringUtils.trimToNull( v.getAsString() );
        return "-".equals( s ) ? null : s;
    }

    private static int recordInt( JsonObject records, String column, int i ) {
        JsonElement v = recordValue( records, column, i );
        if ( v == null ) {
            return 0;
        }
        try {
            return v.getAsInt();
        }
        catch ( RuntimeException e ) {
            return 0;
        }
    }

    private static JsonArray propertyIds( String propertyId ) {
        JsonArray ids = new JsonArray();
        ids.add( propertyId );
        return ids;
    }

    private static JsonObject cdfColumn( String column ) {
        JsonObject o = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "column", column );
        o.add( "cdf", cdf );
        return o;
    }

    JsonObject buildChannelProductionBody( String propertyId, LocalDate startDate, LocalDate endDate ) {
        JsonObject body = new JsonObject();
        JsonArray propertyIds = new JsonArray();
        propertyIds.add( propertyId );
        body.add( "property_ids", propertyIds );

        JsonObject filters = new JsonObject();
        JsonArray and = new JsonArray();
        and.add( stayDateFilter( "greater_than_or_equal", startDate.toString() ) );
        // exclusive upper bound on the day after endDate
        and.add( stayDateFilter( "less_than", endDate.plusDays( 1 ).toString() ) );
        and.add( sourceFilter( "reservation_source" ) );
        and.add( sourceFilter( "reservation_source_category" ) );
        and.add( statusFilterAll() );
        filters.add( "and", and );
        body.add( "filters", filters );

        // Must match stock report 191 definition exactly ("Columns are not the same on stock report id 191").
        // Channel Production: room_revenue(sum), rooms_sold(sum), adr (no metrics — aggregated).
        JsonArray columns = new JsonArray();
        columns.add( metricColumn( "room_revenue", "sum" ) );
        columns.add( metricColumn( "rooms_sold", "sum" ) );
        columns.add( adrColumn() );
        body.add( "columns", columns );

        JsonObject settings = new JsonObject();
        settings.addProperty( "details", false );
        settings.addProperty( "totals", false );
        settings.addProperty( "subtotals", false );
        settings.addProperty( "transpose", false );
        body.add( "settings", settings );

        JsonArray groupRows = new JsonArray();
        for ( String col : MONTHLY_SOURCE_GROUP_COLUMNS ) {
            JsonObject gr = new JsonObject();
            JsonObject cdf = new JsonObject();
            cdf.addProperty( "type", "default" );
            cdf.addProperty( "column", col );
            if ( col.startsWith( "reservation_source" ) ) {
                cdf.addProperty( "multi_level_id", Integer.parseInt( MULTI_LEVEL_SOURCE ) );
            }
            gr.add( "cdf", cdf );
            if ( "stay_date".equals( col ) ) {
                gr.addProperty( "modifier", "month" );
            }
            groupRows.add( gr );
        }
        body.add( "group_rows", groupRows );
        return body;
    }

    private static JsonObject stayDateFilter( String operator, String value ) {
        JsonObject f = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", "stay_date" );
        f.add( "cdf", cdf );
        f.addProperty( "value", value );
        f.addProperty( "operator", operator );
        return f;
    }

    private static JsonObject sourceFilter( String column ) {
        JsonObject f = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", column );
        cdf.addProperty( "multi_level_id", Integer.parseInt( MULTI_LEVEL_SOURCE ) );
        f.add( "cdf", cdf );
        f.addProperty( "value", "" );
        f.addProperty( "operator", "all" );
        return f;
    }

    private static JsonObject statusFilterAll() {
        JsonObject f = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", "reservation_status" );
        cdf.addProperty( "multi_level_id", Integer.parseInt( MULTI_LEVEL_SOURCE ) );
        f.add( "cdf", cdf );
        f.add( "value", new JsonArray() );
        f.addProperty( "operator", "all" );
        return f;
    }

    private static JsonObject metricColumn( String column, String metric ) {
        JsonObject c = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", column );
        c.add( "cdf", cdf );
        JsonArray metrics = new JsonArray();
        metrics.add( metric );
        c.add( "metrics", metrics );
        return c;
    }

    /** ADR on stock report 191 has no {@code metrics} array (server aggregates). */
    private static JsonObject adrColumn() {
        JsonObject c = new JsonObject();
        JsonObject cdf = new JsonObject();
        cdf.addProperty( "type", "default" );
        cdf.addProperty( "column", "adr" );
        c.add( "cdf", cdf );
        return c;
    }

    /**
     * Returns a cached access token, refreshing it (which rotates the one-time {@code rt} cookie)
     * only when missing or within {@link #TOKEN_EXPIRY_MARGIN_SEC} of expiry.
     */
    private synchronized String getAccessToken() throws IOException {
        long now = System.currentTimeMillis() / 1000;
        if ( cachedAccessToken == null || cachedAccessTokenExp < now + TOKEN_EXPIRY_MARGIN_SEC ) {
            CloudbedsAccessTokenService.AccessToken at = accessTokenService.refresh(
                    jsonRequestFactory.getCookies(), jsonRequestFactory.getUserAgent() );
            cachedAccessToken = at.getToken();
            cachedAccessTokenExp = parseJwtExp( cachedAccessToken );
        }
        return cachedAccessToken;
    }

    /** Reads the {@code exp} claim (epoch seconds) from a JWT, or 0 if it cannot be parsed. */
    static long parseJwtExp( String jwt ) {
        try {
            String[] parts = jwt.split( "\\." );
            String payload = new String( Base64.getUrlDecoder().decode( parts[1] ), StandardCharsets.UTF_8 );
            return JsonParser.parseString( payload ).getAsJsonObject().get( "exp" ).getAsLong();
        }
        catch ( RuntimeException e ) {
            LOGGER.warn( "Could not parse access token expiry: {}", e.getMessage() );
            return 0;
        }
    }

    private JsonObject postJson( WebClient webClient, String url, String propertyId, JsonObject body )
            throws IOException {
        // Data Insights (api.cloudbeds.com) authenticates with Bearer access token + X-Property-Id;
        // cookies alone → 401. The token is not a cookie (browser keeps it in localStorage).
        String accessToken = getAccessToken();

        WebRequest req = new WebRequest( new URL( url ), HttpMethod.POST );
        req.setAdditionalHeader( "Accept", "application/json" );
        req.setAdditionalHeader( "Content-Type", "application/json" );
        req.setAdditionalHeader( "Origin", "https://hotels.cloudbeds.com" );
        req.setAdditionalHeader( "Referer", "https://hotels.cloudbeds.com/" );
        req.setAdditionalHeader( "X-Property-Id", propertyId );
        req.setAdditionalHeader( "Authorization", "Bearer " + accessToken );
        req.setAdditionalHeader( "User-Agent", jsonRequestFactory.getUserAgent() );
        req.setRequestBody( gson.toJson( body ) );

        Page page = webClient.getPage( req );
        String text = page.getWebResponse().getContentAsString( StandardCharsets.UTF_8 );
        int status = page.getWebResponse().getStatusCode();
        if ( status == 401 ) {
            synchronized ( this ) {
                cachedAccessToken = null;
            }
        }
        if ( status < 200 || status >= 300 ) {
            throw new IOException( "Data Insights HTTP " + status + ": " + StringUtils.left( text, 500 ) );
        }
        return gson.fromJson( text, JsonObject.class );
    }
}
