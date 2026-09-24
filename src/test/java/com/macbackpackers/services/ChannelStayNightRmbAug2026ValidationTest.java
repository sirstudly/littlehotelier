package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.ChannelStayNight;
import com.macbackpackers.scrapers.datainsights.DataInsightsStockReportParser;

/**
 * Acceptance numbers for RMB August 2026 Channel Production / Excel channel report.
 * <pre>
 * SELECT booking_source, SUM(room_revenue), SUM(rooms_sold)
 *   FROM wp_lh_rpt_channel_stay_night
 *  WHERE stay_date BETWEEN '2026-08-01' AND '2026-08-31'
 *  GROUP BY booking_source;
 * </pre>
 */
public class ChannelStayNightRmbAug2026ValidationTest {

    private static final Map<String, Expected> EXPECTED = Map.of(
            "Walk-In", new Expected( "516.00", 14 ),
            "Website", new Expected( "11791.19", 278 ),
            "Booking.com", new Expected( "14461.66", 382 ),
            "Hostelworld", new Expected( "18774.27", 493 ) );

    @Test
    public void rmbAugust2026ChannelProductionFixtureMatchesExcel() throws Exception {
        JsonObject root;
        try ( var in = getClass().getResourceAsStream(
                "/datainsights_channel_production_rmb_2026-08.json" ) ) {
            assertThat( in, notNullValue() );
            root = JsonParser.parseReader( new InputStreamReader( in, StandardCharsets.UTF_8 ) )
                    .getAsJsonObject();
        }

        DataInsightsStockReportParser parser = new DataInsightsStockReportParser(
                new BookingSourceNormalizer() );
        List<ChannelStayNight> rows = parser.parseQueryData(
                root,
                Arrays.asList( "stay_date", "reservation_source_category", "reservation_source" ),
                ChannelStayNight.ORIGIN_DI_OCCUPANCY );

        Map<String, BigDecimal> revenue = new LinkedHashMap<>();
        Map<String, Integer> rooms = new LinkedHashMap<>();
        for ( ChannelStayNight row : rows ) {
            String src = row.getBookingSource();
            revenue.merge( src, row.getRoomRevenue(), BigDecimal::add );
            rooms.merge( src, row.getRoomsSold(), Integer::sum );
        }

        for ( Map.Entry<String, Expected> e : EXPECTED.entrySet() ) {
            assertThat( e.getKey() + " revenue", revenue.get( e.getKey() ),
                    comparesEqualTo( e.getValue().revenue ) );
            assertThat( e.getKey() + " rooms_sold", rooms.get( e.getKey() ), is( e.getValue().roomsSold ) );
        }

        BigDecimal totalRevenue = revenue.values().stream().reduce( BigDecimal.ZERO, BigDecimal::add );
        int totalRooms = rooms.values().stream().mapToInt( Integer::intValue ).sum();
        assertThat( totalRevenue, comparesEqualTo( new BigDecimal( "45543.12" ) ) );
        assertThat( totalRooms, is( 1167 ) );
    }

    private static final class Expected {
        final BigDecimal revenue;
        final int roomsSold;

        Expected( String revenue, int roomsSold ) {
            this.revenue = new BigDecimal( revenue );
            this.roomsSold = roomsSold;
        }
    }
}
