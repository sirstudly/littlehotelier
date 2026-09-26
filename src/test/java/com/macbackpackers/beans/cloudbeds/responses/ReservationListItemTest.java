
package com.macbackpackers.beans.cloudbeds.responses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;

public class ReservationListItemTest {

    private ReservationListResponse response;

    @BeforeEach
    public void setUp() throws Exception {
        try (InputStreamReader reader = new InputStreamReader( getClass().getClassLoader()
                .getResourceAsStream( "reservation_list_response.json" ), StandardCharsets.UTF_8 )) {
            response = new GsonBuilder()
                    .setFieldNamingPolicy( FieldNamingPolicy.IDENTITY )
                    .create()
                    .fromJson( reader, ReservationListResponse.class );
        }
    }

    @Test
    public void parsesPagingMetadata() {
        assertTrue( response.isSuccess() );
        assertEquals( 3, response.getTotal() );
        assertEquals( 1, response.getPage() );
        assertEquals( 3, response.getCount() );
        assertEquals( 3, response.getData().size() );
    }

    @Test
    public void toCustomer_mapsChannelCollectBooking() {
        Customer c = response.getData().get( 0 ).toCustomer();
        assertEquals( "187257228", c.getId() );
        assertEquals( "7831984184714", c.getIdentifier() );
        assertEquals( "6202925288", c.getThirdPartyIdentifier() );
        assertEquals( "189099332", c.getCustomerId() );
        assertEquals( "Yvonne", c.getFirstName() );
        assertEquals( "Lynn", c.getLastName() );
        assertEquals( "2026-09-24", c.getBookingDate() );
        assertEquals( "2026-09-24", c.getCheckinDate() );
        assertEquals( "2026-09-25", c.getCheckoutDate() );
        assertEquals( "1", c.getNights() );
        assertEquals( "33.75", c.getGrandTotal() );
        assertEquals( 0, BigDecimal.ZERO.compareTo( c.getBalanceDue() ) );
        assertEquals( "Booking.com (Channel Collect Booking)", c.getSourceName() );
        assertEquals( "checked_out", c.getStatus() );
        assertFalse( c.isHotelCollectBooking() );
        assertFalse( c.isLongTermer() );
    }

    @Test
    public void toCustomer_mapsHotelCollectBooking() {
        Customer c = response.getData().get( 1 ).toCustomer();
        assertEquals( "270164-580168802", c.getThirdPartyIdentifier() );
        assertEquals( "Hostelworld (Hotel Collect Booking)", c.getSourceName() );
        assertEquals( "3", c.getNights() );
        assertTrue( c.isHotelCollectBooking() );
    }

    @Test
    public void toCustomer_mapsMultiRoomBookingAsSingleRow() {
        Customer c = response.getData().get( 2 ).toCustomer();
        assertEquals( "187240526", c.getId() );
        assertEquals( "60.9", c.getGrandTotal() );
        assertEquals( new BigDecimal( "60.9" ), c.getBalanceDue() );

        List<String> ids = response.getData().stream()
                .map( ReservationListItem::getId )
                .distinct()
                .collect( Collectors.toList() );
        assertEquals( 3, ids.size() );
    }

    @Test
    public void toCustomer_defaultsMissingNamesToBlank() {
        Customer c = new ReservationListItem().toCustomer();
        assertEquals( "", c.getFirstName() );
        assertEquals( "", c.getLastName() );
        assertEquals( "0", c.getIsHotelCollectBooking() );
        assertFalse( c.isLongTermer() );
    }
}
