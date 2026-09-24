package com.macbackpackers.scrapers.datainsights;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.ChannelStayNight;
import com.macbackpackers.services.BookingSourceNormalizer;

/**
 * Proves DI Channel Production nested records flatten and roll up to CPR / Excel totals.
 */
public class DataInsightsStockReportParserTest {

    private DataInsightsStockReportParser parser;
    private BookingSourceNormalizer normalizer;

    @BeforeEach
    public void setUp() {
        normalizer = new BookingSourceNormalizer();
        parser = new DataInsightsStockReportParser( normalizer );
    }

    @Test
    public void normalizesCloudbedsSourceLabels() {
        assertThat( normalizer.normalize( "Website/Booking Engine" ), is( "Website" ) );
        assertThat( normalizer.normalize( "Booking.com (Channel Collect Booking)" ), is( "Booking.com" ) );
        assertThat( normalizer.normalize( "Hostelworld (Hotel Collect Booking)" ), is( "Hostelworld" ) );
        assertThat( normalizer.normalize( "RECEPTION" ), is( "Reception" ) );
        assertThat( normalizer.normalize( "-" ), nullValue() );
    }

    @Test
    public void parsesMonthGrainChannelProductionFixtureMatchingExcel() throws Exception {
        JsonObject root = loadJson( "/datainsights_channel_production_rmb_2026-08.json" );
        List<String> groups = Arrays.asList(
                "stay_date", "reservation_source_category", "reservation_source" );
        List<ChannelStayNight> rows = parser.parseQueryData(
                root, groups, ChannelStayNight.ORIGIN_DI_OCCUPANCY );

        Map<String, Agg> bySource = aggregate( rows );
        assertThat( bySource.get( "Walk-In" ).revenue, comparesEqualTo( new BigDecimal( "516.00" ) ) );
        assertThat( bySource.get( "Walk-In" ).roomsSold, is( 14 ) );
        assertThat( bySource.get( "Website" ).revenue, comparesEqualTo( new BigDecimal( "11791.19" ) ) );
        assertThat( bySource.get( "Website" ).roomsSold, is( 278 ) );
        assertThat( bySource.get( "Booking.com" ).revenue, comparesEqualTo( new BigDecimal( "14461.66" ) ) );
        assertThat( bySource.get( "Booking.com" ).roomsSold, is( 382 ) );
        assertThat( bySource.get( "Hostelworld" ).revenue, comparesEqualTo( new BigDecimal( "18774.27" ) ) );
        assertThat( bySource.get( "Hostelworld" ).roomsSold, is( 493 ) );
    }

    @Test
    public void parsesDayBookingRoomGrainWalkInsMatchingCpr() throws Exception {
        JsonObject root = loadJson( "/datainsights_channel_production_walkin_day_grain_rmb_2026-08.json" );
        List<String> groups = CloudbedsDataInsightsClient.stayNightGroupColumns();
        List<ChannelStayNight> rows = parser.parseQueryData(
                root, groups, ChannelStayNight.ORIGIN_DI_OCCUPANCY );

        assertThat( rows.size(), is( 14 ) );
        assertThat( rows.stream().mapToInt( ChannelStayNight::getRoomsSold ).sum(), is( 14 ) );

        BigDecimal revenue = rows.stream()
                .map( ChannelStayNight::getRoomRevenue )
                .reduce( BigDecimal.ZERO, BigDecimal::add );
        assertThat( revenue, comparesEqualTo( new BigDecimal( "516.00" ) ) );

        // Sundstrom: two beds on 2026-08-03
        long sundstrom = rows.stream()
                .filter( r -> r.getReservationId() == 182461901L )
                .count();
        assertThat( sundstrom, is( 2L ) );

        assertThat( rows.get( 0 ).getStayDate(), notNullValue() );
        assertThat( rows.stream().allMatch( r -> r.getStayDate().getMonthValue() == 8 ), is( true ) );
    }

    @Test
    public void buildChannelProductionBodyUsesAbsoluteStayDatesAndDayGrain() {
        CloudbedsDataInsightsClient client = new CloudbedsDataInsightsClient();
        JsonObject body = client.buildChannelProductionBody(
                "18265",
                LocalDate.of( 2026, 8, 1 ),
                LocalDate.of( 2026, 8, 31 ),
                CloudbedsDataInsightsClient.stayNightGroupColumns() );

        assertThat( body.getAsJsonArray( "property_ids" ).get( 0 ).getAsString(), is( "18265" ) );
        String filters = body.getAsJsonObject( "filters" ).toString();
        assertThat( filters, containsString( "2026-08-01" ) );
        assertThat( filters, containsString( "2026-09-01" ) );

        // no month modifier on stay_date group
        assertThat( body.getAsJsonArray( "group_rows" ).toString(), not( containsString( "month" ) ) );
        assertThat( body.getAsJsonArray( "group_rows" ).size(), is( 5 ) );
    }

    private static JsonObject loadJson( String classpath ) throws Exception {
        try ( var in = DataInsightsStockReportParserTest.class.getResourceAsStream( classpath ) ) {
            assertThat( "missing " + classpath, in, notNullValue() );
            return JsonParser.parseReader( new InputStreamReader( in, StandardCharsets.UTF_8 ) )
                    .getAsJsonObject();
        }
    }

    private static Map<String, Agg> aggregate( List<ChannelStayNight> rows ) {
        return rows.stream().collect( Collectors.toMap(
                ChannelStayNight::getBookingSource,
                r -> new Agg( r.getRoomRevenue(), r.getRoomsSold() ),
                Agg::plus ) );
    }

    private static final class Agg {
        final BigDecimal revenue;
        final int roomsSold;

        Agg( BigDecimal revenue, int roomsSold ) {
            this.revenue = revenue;
            this.roomsSold = roomsSold;
        }

        Agg plus( Agg o ) {
            return new Agg( revenue.add( o.revenue ), roomsSold + o.roomsSold );
        }
    }
}
