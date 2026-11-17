package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;

public class CloudbedsJsonRequestFactoryAirbnbManualRoomRevenueTest {

    @Test
    public void createAddManualRoomRevenueAdjustRequest_usesRoomRevenueManualAndPositiveAmount()
            throws Exception {
        WordPressDAO dao = mock( WordPressDAO.class );
        when( dao.getOption( "hbo_cloudbeds_property_id" ) ).thenReturn( "17363" );
        when( dao.getOption( "hbo_cloudbeds_useragent" ) ).thenReturn( "Mozilla/5.0 test" );
        when( dao.getOption( "hbo_cloudbeds_cookies" ) ).thenReturn( "session=test" );
        when( dao.getCsrfToken() ).thenReturn( "csrf-token" );

        CloudbedsJsonRequestFactory factory = new CloudbedsJsonRequestFactory();
        java.lang.reflect.Field daoField = CloudbedsJsonRequestFactory.class.getDeclaredField( "dao" );
        daoField.setAccessible( true );
        daoField.set( factory, dao );

        Reservation res = new Reservation();
        res.setReservationId( "190514833" );
        res.setSource( "Airbnb (API)" );
        res.setIsHotelCollectBooking( "0" );
        res.setIsRootSource( "1" );

        // UI / HAR: positive amount → negative credit (write-down)
        WebRequest req = factory.createAddManualRoomRevenueAdjustRequest(
                res, "243912807", new BigDecimal( "3.57" ),
                CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE, "portal-1", "2" );

        assertThat( req.getUrl().toString(), containsString( "add_new_adjust" ) );
        assertThat( param( req, "adjust[id]" ), is( "roomRevenue_manual" ) );
        assertThat( param( req, "adjust[amount]" ), is( "3.57" ) );
        assertThat( param( req, "adjust[notes]" ), is( CloudbedsScraper.AIRBNB_SERVICE_FEE_ADJUST_NOTE ) );
        assertThat( param( req, "adjust[reservation_id]" ), is( "190514833" ) );
        assertThat( param( req, "adjust[booking_rooms_id]" ), is( "243912807" ) );
        assertThat( param( req, "property_id" ), is( "17363" ) );
    }

    private static String param( WebRequest req, String name ) {
        return req.getRequestParameters().stream()
                .filter( p -> name.equals( p.getName() ) )
                .map( NameValuePair::getValue )
                .findFirst()
                .orElse( "" );
    }
}
