package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;

import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.macbackpackers.dao.WordPressDAO;

public class CloudbedsJsonRequestFactoryPagingTest {

    private final WordPressDAO dao = mock( WordPressDAO.class );

    private CloudbedsJsonRequestFactory factory;

    @BeforeEach
    public void setUp() {
        when( dao.getOption( anyString() ) ).thenReturn( "x" );
        when( dao.getOption( "hbo_cloudbeds_property_id" ) ).thenReturn( "17363" );
        when( dao.getCsrfToken() ).thenReturn( "csrf" );
        factory = new CloudbedsJsonRequestFactory();
        ReflectionTestUtils.setField( factory, "dao", dao );
    }

    private static String param( WebRequest req, String name ) {
        return req.getRequestParameters().stream()
                .filter( p -> name.equals( p.getName() ) )
                .map( NameValuePair::getValue )
                .findFirst().orElse( null );
    }

    @Test
    public void propertyActivityLogUsesUtcWindowAndFrontEndPageSize() throws IOException {
        WebRequest req = factory.createGetPropertyActivityLog( Instant.parse( "2026-10-05T09:30:00Z" ),
                Instant.parse( "2026-10-05T21:00:00Z" ), 500, "bp", "fv" );
        assertThat( param( req, "from_date" ), is( "2026-10-05" ) );
        assertThat( param( req, "from_time" ), is( "09:30 AM" ) );
        assertThat( param( req, "to_date" ), is( "2026-10-05" ) );
        assertThat( param( req, "to_time" ), is( "09:00 PM" ) );
        assertThat( param( req, "use_utc" ), is( "true" ) );
        assertThat( param( req, "iDisplayStart" ), is( "500" ) );
        assertThat( Integer.parseInt( param( req, "iDisplayLength" ) ), lessThanOrEqualTo( 250 ) );
    }

    @Test
    public void propertyActivityLogRejectsWindowOffHalfHour() {
        assertThrows( IllegalArgumentException.class, () -> factory.createGetPropertyActivityLog(
                Instant.parse( "2026-10-05T09:15:00Z" ), Instant.parse( "2026-10-05T10:00:00Z" ), 0, "bp", "fv" ) );
        assertThrows( IllegalArgumentException.class, () -> factory.createGetPropertyActivityLog(
                Instant.parse( "2026-10-05T09:00:00Z" ), Instant.parse( "2026-10-05T10:00:01Z" ), 0, "bp", "fv" ) );
    }

    @Test
    public void halfHourBoundary() {
        assertThat( CloudbedsJsonRequestFactory.isHalfHourBoundary( Instant.parse( "2026-10-05T09:00:00Z" ) ), is( true ) );
        assertThat( CloudbedsJsonRequestFactory.isHalfHourBoundary( Instant.parse( "2026-10-05T09:30:00Z" ) ), is( true ) );
        assertThat( CloudbedsJsonRequestFactory.isHalfHourBoundary( Instant.parse( "2026-10-05T09:29:00Z" ) ), is( false ) );
        assertThat( CloudbedsJsonRequestFactory.isHalfHourBoundary( Instant.parse( "2026-10-05T09:30:00.001Z" ) ), is( false ) );
    }

    @Test
    public void reservationActivityLogIsPaged() throws IOException {
        WebRequest req = factory.createGetActivityLog( "1234567890123", 250, "bp", "fv" );
        assertThat( param( req, "filter" ), is( "1234567890123" ) );
        assertThat( param( req, "iDisplayStart" ), is( "250" ) );
        assertThat( Integer.parseInt( param( req, "iDisplayLength" ) ), lessThanOrEqualTo( 250 ) );
    }

    @Test
    public void cancelledReservationsAreSortedByIdentifierAndPaged() throws IOException {
        WebRequest req = factory.createGetCancelledReservationsRequestByBookingSource( LocalDate.of( 2026, 10, 1 ),
                LocalDate.of( 2026, 10, 31 ), null, null, "1,2", 750, "bp", "fv" );
        assertThat( param( req, "iDisplayStart" ), is( "750" ) );
        assertThat( Integer.parseInt( param( req, "iDisplayLength" ) ), lessThanOrEqualTo( 250 ) );
        assertThat( param( req, "iSortCol_0" ), is( "1" ) );
    }

    @Test
    public void reservationByIdentifierSetsFlag() throws IOException {
        assertThat( param( factory.createGetReservationRequest( "1234567890123", true, "bp", "fv" ), "is_identifier" ), is( "1" ) );
        assertThat( param( factory.createGetReservationRequest( "188230061", "bp", "fv" ), "is_identifier" ), is( "0" ) );
    }
}
