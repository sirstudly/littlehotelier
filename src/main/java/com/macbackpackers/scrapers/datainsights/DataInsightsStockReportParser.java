package com.macbackpackers.scrapers.datainsights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.ChannelStayNight;
import com.macbackpackers.services.BookingSourceNormalizer;

/**
 * Flattens Data Insights stock-report {@code records} trees into {@link ChannelStayNight} rows.
 * <p>
 * Supports nested group keys used by Channel Production (stock 191), e.g.
 * {@code stay_date → source_category → reservation_source → metrics} or finer grains that
 * include {@code booking_id} / {@code room_id} in the path (detected by key shape).
 */
@Component
public class DataInsightsStockReportParser {

    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern( "yyyy-MM" );

    private final BookingSourceNormalizer sourceNormalizer;

    public DataInsightsStockReportParser( BookingSourceNormalizer sourceNormalizer ) {
        this.sourceNormalizer = sourceNormalizer;
    }

    /**
     * Parses a stock-report {@code /query/data} JSON body into stay-night facts.
     *
     * @param root full response object
     * @param groupRows ordered group column names from the request (e.g. stay_date, reservation_source_category, …)
     * @param dataOrigin provenance stamp
     * @return flattened rows (placeholder “-” groups skipped)
     */
    public List<ChannelStayNight> parseQueryData( JsonObject root, List<String> groupRows, String dataOrigin ) {
        List<ChannelStayNight> out = new ArrayList<>();
        if ( root == null || !root.has( "records" ) || !root.get( "records" ).isJsonObject() ) {
            return out;
        }
        JsonObject records = root.getAsJsonObject( "records" );
        walk( records, groupRows, 0, new PathState(), dataOrigin, out );
        return out;
    }

    private void walk( JsonObject node, List<String> groupRows, int depth, PathState path,
            String dataOrigin, List<ChannelStayNight> out ) {
        if ( isMetricsObject( node ) ) {
            emit( path, node, dataOrigin, out );
            return;
        }
        for ( Map.Entry<String, JsonElement> e : node.entrySet() ) {
            String key = e.getKey();
            if ( "-".equals( key ) || !e.getValue().isJsonObject() ) {
                continue;
            }
            PathState next = path.with( groupRows, depth, key );
            walk( e.getValue().getAsJsonObject(), groupRows, depth + 1, next, dataOrigin, out );
        }
    }

    private static boolean isMetricsObject( JsonObject node ) {
        return node.has( "room_revenue" ) || node.has( "rooms_sold" )
                || node.has( "room_rate" ) || node.has( "adr" );
    }

    private void emit( PathState path, JsonObject metrics, String dataOrigin, List<ChannelStayNight> out ) {
        if ( path.stayDate == null ) {
            return;
        }
        if ( StringUtils.isBlank( path.sourceRaw ) || "-".equals( path.sourceRaw ) ) {
            return;
        }
        BigDecimal revenue = metricAmount( metrics, "room_revenue" );
        // Stock report 191 exposes adr (aggregated), not room_rate; keep room_rate if present.
        BigDecimal rate = metricAmountOptional( metrics, "room_rate" );
        if ( rate == null ) {
            rate = metricAmountOptional( metrics, "adr" );
        }
        int sold = metricInt( metrics, "rooms_sold" );
        if ( sold <= 0 && revenue.compareTo( BigDecimal.ZERO ) == 0
                && ( rate == null || rate.compareTo( BigDecimal.ZERO ) == 0 ) ) {
            return;
        }
        if ( sold <= 0 ) {
            sold = 1;
        }

        ChannelStayNight row = new ChannelStayNight();
        row.setStayDate( path.stayDate );
        row.setReservationId( path.reservationId == null ? 0L : path.reservationId );
        row.setReservationNumber( path.reservationNumber );
        row.setRoomId( path.roomId == null ? "" : path.roomId );
        row.setRoomNumber( path.roomNumber );
        row.setSourceCategory( path.sourceCategory );
        row.setBookingSourceRaw( path.sourceRaw == null ? "" : path.sourceRaw );
        row.setBookingSource( sourceNormalizer.normalize( path.sourceRaw ) );
        row.setRoomRevenue( revenue );
        row.setRoomRate( rate );
        row.setRoomsSold( sold );
        row.setDataOrigin( dataOrigin );
        out.add( row );
    }

    static BigDecimal metricAmount( JsonObject metrics, String name ) {
        BigDecimal v = metricAmountOptional( metrics, name );
        return v == null ? BigDecimal.ZERO : v;
    }

    /** {@code sum} or {@code aggregated} (ADR) metric value, or null if absent. */
    static BigDecimal metricAmountOptional( JsonObject metrics, String name ) {
        if ( !metrics.has( name ) ) {
            return null;
        }
        JsonElement el = metrics.get( name );
        if ( el.isJsonObject() ) {
            JsonObject o = el.getAsJsonObject();
            if ( o.has( "sum" ) ) {
                return parseDecimal( o.get( "sum" ).getAsString() );
            }
            if ( o.has( "aggregated" ) ) {
                return parseDecimal( o.get( "aggregated" ).getAsString() );
            }
        }
        if ( el.isJsonPrimitive() ) {
            return parseDecimal( el.getAsString() );
        }
        return BigDecimal.ZERO;
    }

    static int metricInt( JsonObject metrics, String name ) {
        if ( !metrics.has( name ) ) {
            return 0;
        }
        JsonElement el = metrics.get( name );
        String raw;
        if ( el.isJsonObject() && el.getAsJsonObject().has( "sum" ) ) {
            raw = el.getAsJsonObject().get( "sum" ).getAsString();
        }
        else if ( el.isJsonPrimitive() ) {
            raw = el.getAsString();
        }
        else {
            return 0;
        }
        if ( StringUtils.isBlank( raw ) || "-".equals( raw.trim() ) ) {
            return 0;
        }
        return Integer.parseInt( raw.replace( ",", "" ).trim() );
    }

    static BigDecimal parseDecimal( String raw ) {
        if ( StringUtils.isBlank( raw ) || "-".equals( raw.trim() ) ) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal( raw.replace( ",", "" ).trim() );
    }

    /**
     * Holds values collected while descending the nested {@code records} tree.
     */
    static final class PathState {
        LocalDate stayDate;
        String sourceCategory;
        String sourceRaw;
        Long reservationId;
        String reservationNumber;
        String roomId;
        String roomNumber;

        PathState with( List<String> groupRows, int depth, String key ) {
            PathState n = copy();
            String col = depth < groupRows.size() ? groupRows.get( depth ) : guessColumn( key );
            apply( n, col, key );
            return n;
        }

        private PathState copy() {
            PathState n = new PathState();
            n.stayDate = stayDate;
            n.sourceCategory = sourceCategory;
            n.sourceRaw = sourceRaw;
            n.reservationId = reservationId;
            n.reservationNumber = reservationNumber;
            n.roomId = roomId;
            n.roomNumber = roomNumber;
            return n;
        }

        private static void apply( PathState n, String col, String key ) {
            if ( col == null ) {
                return;
            }
            switch ( col ) {
                case "stay_date":
                    n.stayDate = parseStayKey( key );
                    break;
                case "reservation_source_category":
                    n.sourceCategory = key;
                    break;
                case "reservation_source":
                    n.sourceRaw = key;
                    break;
                case "booking_id":
                    n.reservationId = parseLong( key );
                    break;
                case "reservation_number":
                    n.reservationNumber = key;
                    break;
                case "room_id":
                    n.roomId = key;
                    break;
                case "room_number":
                    n.roomNumber = key;
                    break;
                default:
                    // month-only stay_date keys still parse; unknown dims ignored
                    if ( n.stayDate == null ) {
                        LocalDate d = parseStayKey( key );
                        if ( d != null ) {
                            n.stayDate = d;
                        }
                    }
                    if ( n.sourceRaw == null && looksLikeSource( key ) ) {
                        n.sourceRaw = key;
                    }
                    break;
            }
        }

        private static String guessColumn( String key ) {
            if ( parseStayKey( key ) != null ) {
                return "stay_date";
            }
            if ( "Direct".equals( key ) || "OTA".equals( key ) || "GDS".equals( key ) ) {
                return "reservation_source_category";
            }
            if ( parseLong( key ) != null && key.length() >= 6 ) {
                return "booking_id";
            }
            return "reservation_source";
        }

        private static boolean looksLikeSource( String key ) {
            return key.contains( "Booking" ) || key.contains( "Hostelworld" ) || key.contains( "Walk" )
                    || key.contains( "Website" ) || key.contains( "Airbnb" ) || key.contains( "Agoda" )
                    || key.contains( "Phone" ) || key.contains( "Email" ) || key.contains( "Reception" );
        }

        static LocalDate parseStayKey( String key ) {
            if ( StringUtils.isBlank( key ) ) {
                return null;
            }
            try {
                if ( key.length() == 7 ) {
                    // yyyy-MM → first of month (month-aggregated Channel Production default)
                    return LocalDate.parse( key + "-01", ISO_DAY );
                }
                return LocalDate.parse( key, ISO_DAY );
            }
            catch ( DateTimeParseException e ) {
                try {
                    return LocalDate.parse( key, YEAR_MONTH );
                }
                catch ( DateTimeParseException e2 ) {
                    // month name abbreviations from comparison report (Jan, Feb, …) — not used for sync
                    return null;
                }
            }
        }

        static Long parseLong( String key ) {
            try {
                return Long.parseLong( key.trim() );
            }
            catch ( NumberFormatException e ) {
                return null;
            }
        }
    }
}
