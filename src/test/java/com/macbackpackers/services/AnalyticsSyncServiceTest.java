package com.macbackpackers.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.analytics.BookingCreatedFact;
import com.macbackpackers.beans.analytics.ChannelStayDayFact;
import com.macbackpackers.beans.cloudbeds.responses.Customer;

public class AnalyticsSyncServiceTest {

    @Test
    public void parseStayDayRecords_extractsDayGrainSources() throws Exception {
        JsonObject response;
        try ( InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream( "/di_channel_production_day.json" ), StandardCharsets.UTF_8 ) ) {
            response = JsonParser.parseReader( reader ).getAsJsonObject();
        }

        List<ChannelStayDayFact> lines = AnalyticsSyncService.parseStayDayRecords( response, "crh" );
        assertThat( lines, hasSize( 3 ) );

        ChannelStayDayFact walkIn = lines.stream()
                .filter( l -> "Walk-In".equals( l.getBookingSource() ) )
                .findFirst()
                .orElseThrow();
        assertThat( walkIn.getStayDate(), is( LocalDate.of( 2024, 9, 15 ) ) );
        assertThat( walkIn.getSourceCategory(), is( "Direct" ) );
        assertThat( walkIn.getRoomRevenue().doubleValue(), closeTo( 41.66, 0.001 ) );
        assertThat( walkIn.getRoomsSold(), is( 2 ) );
    }

    @Test
    public void toBookingFact_mapsCustomerFields() {
        Customer c = new Customer();
        c.setId( "12345" );
        c.setBookingDate( "2024-09-15 10:00:00" );
        c.setSourceName( "Hostelworld" );
        c.setStatus( "confirmed" );
        c.setGrandTotal( "199.50" );
        c.setCheckinDate( "2024-10-01" );
        c.setCheckoutDate( "2024-10-03" );
        c.setFirstName( "Ada" );
        c.setLastName( "Lovelace" );

        BookingCreatedFact fact = AnalyticsSyncService.toBookingFact( "hsh", c, LocalDate.of( 2024, 9, 15 ) );
        assertThat( fact.getPropertyCode(), is( "hsh" ) );
        assertThat( fact.getReservationId(), is( "12345" ) );
        assertThat( fact.getBookedOn(), is( LocalDate.of( 2024, 9, 15 ) ) );
        assertThat( fact.getBookingSource(), is( "Hostelworld" ) );
        assertThat( fact.getGrandTotal(), is( new BigDecimal( "199.50" ) ) );
        assertThat( fact.getGuestName(), is( "Ada Lovelace" ) );
    }

    @Test
    public void parseStayDateKey_supportsMonthAndDay() {
        assertThat( AnalyticsSyncService.parseStayDateKey( "2024-09-15" ), is( LocalDate.of( 2024, 9, 15 ) ) );
        assertThat( AnalyticsSyncService.parseStayDateKey( "2024-09" ), is( LocalDate.of( 2024, 9, 1 ) ) );
        assertThat( AnalyticsSyncService.parseStayDateKey( "-" ), nullValue() );
    }
}
