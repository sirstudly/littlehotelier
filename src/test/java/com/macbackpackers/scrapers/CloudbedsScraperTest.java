package com.macbackpackers.scrapers;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class CloudbedsScraperTest {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    CloudbedsScraper cloudbedsScraper;

    @Autowired
    WordPressDAO dao;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
//    @Qualifier( "webClientJavascriptDisabled" )
    WebClient webClient;
    
    @Autowired 
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Test
    public void testLoadDashboard() throws Exception {
        cloudbedsScraper.loadDashboard( webClient );
        webClient.waitForBackgroundJavaScript( 360000 );
        Page dashboard = webClient.getCurrentWindow().getEnclosedPage();
        LOGGER.info( dashboard.getWebResponse().getContentAsString() );
    }

    @Test
    public void testLoadReservationPage() throws Exception {
        cloudbedsScraper.loadReservationPage( webClient, "22557712" );
        Page dashboard = webClient.getCurrentWindow().getEnclosedPage();
        LOGGER.info( dashboard.getWebResponse().getContentAsString() );
    }
    
    @Test
    public void testGetReservation() throws Exception {
        Reservation r = cloudbedsScraper.getReservation( webClient, "10569063" );
        LOGGER.info( ToStringBuilder.reflectionToString( r ) );
    }
    
    @Test
    public void testGetTransactionsByReservation() throws Exception {
        Reservation r = cloudbedsScraper.getReservation( webClient, "10569063" );
        Assert.assertThat( cloudbedsScraper.isExistsSagepayPaymentWithVendorTxCode( webClient, r, "x" ), Matchers.is( false ) );
        Assert.assertThat( cloudbedsScraper.isExistsSagepayPaymentWithVendorTxCode( webClient, r, "CASTLE-ROCK-7878681082-1548474047" ), Matchers.is( true ) );
    }

    @Test
    public void testGetCustomers() throws Exception {
        List<Customer> results = cloudbedsScraper.getCustomers( webClient, 
                LocalDate.now().withDayOfMonth( 1 ), LocalDate.now().withDayOfMonth( 2 ) );
        results.stream().forEach( t -> LOGGER.info( t.toString() ) );
    }

    @Test
    public void testGetReservations() throws Exception {
        List<Customer> results = cloudbedsScraper.getReservations( webClient, 
                LocalDate.now().withDayOfMonth( 1 ), LocalDate.now().withDayOfMonth( 2 ) );
        results.stream().forEach( t -> LOGGER.info( t.toString() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testGetReservationsForBookingSources() throws Exception {
        List<Reservation> results = cloudbedsScraper.getReservationsForBookingSources( webClient, null, null,
                LocalDate.now().withMonth( 5 ).withDayOfMonth( 30 ),
                LocalDate.now().withMonth( 5 ).withDayOfMonth( 30 ),
                "Hostelworld & Hostelbookers", "Agoda (Channel Collect Booking)" );
        results.stream().forEach( t -> LOGGER.info( t.getIdentifier() + " (" + t.getStatus() + ") - "
                + t.isCardDetailsPresent() + "," + t.getThirdPartyIdentifier() + ": " +
                t.getFirstName() + " " + t.getLastName() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testGetCancelledReservationsForBookingSources() throws Exception {
        List<Reservation> results = cloudbedsScraper.getCancelledReservationsForBookingSources( webClient,
                LocalDate.now().withDayOfMonth( 17 ), // checkin start
                LocalDate.now().withDayOfMonth( 19 ), // checkin end
                LocalDate.now().withDayOfMonth( 18 ), // cancel start
                LocalDate.now().withDayOfMonth( 19 ), // cancel end
                "Hostelworld & Hostelbookers" );
        results.stream().forEach( t -> LOGGER.info( t.getIdentifier() + " (" + t.getStatus() + ") - "
                + t.isCardDetailsPresent() + "," + t.getThirdPartyIdentifier() + ": " +
                t.getFirstName() + " " + t.getLastName() + " cancelled on " + t.getCancellationDate() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testAddPayment() throws Exception {
        cloudbedsScraper.addPayment( webClient,
                cloudbedsScraper.getReservation( webClient, "9814194" ),
                "visa", new BigDecimal( "0.15" ), "Test payment XYZ" );
    }
    
    @Test
    public void testAddNote() throws Exception {
        cloudbedsScraper.addNote( webClient, "9897593", "Test Note& with <> Special characters\n\t ?" );
    }
    
    @Test
    public void testAddCreditCard() throws Exception {
        CardDetails cd = new CardDetails();
        cd.setCardNumber( "4917300000000008" );
        cd.setName( "scrooge mcduck" );
        //cd.setCvv( "987" );
        cd.setExpiry( "0829" );
        cloudbedsScraper.addCardDetails( webClient, "10384646", cd );
    }
    
    @Test
    public void testPing() throws Exception {
        cloudbedsScraper.ping( webClient );
    }
    
    @Test
    public void testValidateLoggedIn() throws Exception {
        cloudbedsScraper.validateLoggedIn( webClient );
    }

    @Test
    public void testCopyNotes() throws Exception {
        // find all bookings with "virtual cc details"
        addNoteToCloudbedsReservation( "BDC-2041863340", 9238060 );
    }

    @Test
    public void testGetAllStaffAllocations() throws Exception {
        JsonObject rpt = cloudbedsScraper.getAllStaffAllocations( webClient, LocalDate.now() );
        LOGGER.info( rpt.toString() );
    }

    @Test
    public void testLookupBookingSourceId() throws Exception {
        LOGGER.info( cloudbedsScraper.lookupBookingSourceIds( webClient, 
                "Hostelworld & Hostelbookers", "Booking.com (Channel Collect Booking)" ) );
    }

    @Test
    public void testGetActivityLog() throws Exception {
        cloudbedsScraper.getActivityLog( webClient, "6810494609" )
            .stream().forEach( t -> LOGGER.info( ToStringBuilder.reflectionToString( t ) ) );
    }

    @Test
    public void testGetHostelworldLateCancellationEmailTemplate() throws Exception {
        LOGGER.info( ToStringBuilder.reflectionToString( cloudbedsScraper.getHostelworldLateCancellationEmailTemplate( webClient ) ) );
    }

    @Test
    public void testGetPropertyContent() throws Exception {
        cloudbedsScraper.getPropertyContent( webClient );
    }

    /**
     * Adds a note to the reservation held in Cloudbeds using the comments on the given LH reservation ID.
     * 
     * @param bookingReference the reference to query for on cloudbeds
     * @param lhReservationId the LH reservation ID we want to copy the comment from
     */
    private void addNoteToCloudbedsReservation( String bookingReference, int lhReservationId ) throws Exception {
        LOGGER.info( "Processing booking " + bookingReference );
        // first find the reservation on CB using the BDC reference
        List<Customer> c = cloudbedsScraper.getReservations( webClient, 
                bookingReference.startsWith( "BDC-" ) ? bookingReference.substring( 4 ) : bookingReference );
        Assert.assertThat( "Only 1 reservation expected", c.size(), Matchers.is( 1 ) );
        Reservation r = cloudbedsScraper.getReservation( webClient, c.get( 0 ).getId() );
        
        // now get the comment we want to copy using the OLD reservation ID
        String comment = dao.fetchGuestComments( lhReservationId ).getComments();
        Assert.assertThat( comment, Matchers.notNullValue() );
        cloudbedsScraper.addNote( webClient, r.getReservationId(), "Booking.com agent to be charged at check-in. Do NOT charge guest! \n" + comment );
    }

    @Test
    public void testDeserialise() throws Exception {
        Reservation reservation = gson.fromJson( FileUtils.readFileToString( new File( "test.json" ), Charset.defaultCharset() ), Reservation.class );
        LOGGER.info( reservation.toString() );
    }
    
    @Test
    public void testNav() throws Exception {
        HtmlPage page = cloudbedsScraper.navigateToPage( webClient, "https://hotels.cloudbeds.com/connect/17959#/calendar" );
        page.getWebClient().waitForBackgroundJavaScript( 60000 ); // wait for page to load
        LOGGER.info( page.asXml() );
    }
    
    @Test
    public void testGetEmailTemplateLastSentDate() throws Exception {
        LOGGER.info( cloudbedsScraper.getEmailLastSentDate( webClient, "19443322", CloudbedsScraper.TEMPLATE_DEPOSIT_CHARGE_DECLINED ).toString() );
    }
    
    @Test
    public void testCreateDepositChargeJobForBDC() throws Exception {
        cloudbedsScraper.getReservationsForBookingSources( webClient,
                LocalDate.now(), LocalDate.now().plusDays( 7 ),
                null, null, "Booking.com (Hotel Collect Booking)" )
                .stream()
                .filter( p -> p.getPaidValue().equals( BigDecimal.ZERO ) )
                .filter( p -> p.isHotelCollectBooking() )
                .filter( p -> p.isCardDetailsPresent() )
                .filter( p -> false == p.isPrepaid() )
                .filter( p -> false == "canceled".equalsIgnoreCase( p.getStatus() ) )
                .forEach( p -> {
                    LOGGER.info( "Creating a DepositChargeJob for " + p.getSourceName() + " #"
                            + p.getThirdPartyIdentifier() + " (" + p.getStatus() + ")" );
                    LOGGER.info( p.getFirstName() + " " + p.getLastName() );
                } );
    }

}