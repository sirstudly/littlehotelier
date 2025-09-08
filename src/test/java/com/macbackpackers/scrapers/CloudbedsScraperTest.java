package com.macbackpackers.scrapers;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.macbackpackers.SecretsManagerTestApp;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.utils.AnyByteStringToStringConverter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.cloudbeds.requests.CustomerInfo;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.EmailTemplateInfo;
import com.macbackpackers.beans.cloudbeds.responses.Guest;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.CancelBookingJob;
import com.macbackpackers.jobs.SendTemplatedEmailJob;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith( SpringExtension.class )
@SpringBootTest( classes = SecretsManagerTestApp.class )
@TestPropertySource( properties = {
        "spring.profiles.active=crh"
} )
public class CloudbedsScraperTest {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    static {
        // Register the ByteString converter before Spring tries to resolve Secret Manager placeholders
        // This is essential for proper Secret Manager integration in tests
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() ).addConverter( new AnyByteStringToStringConverter() );
    }

    @Autowired
    CloudbedsScraper cloudbedsScraper;

    @Autowired
    WordPressDAO dao;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
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
        Reservation r = cloudbedsScraper.getReservation( webClient, "32811776" );
        LOGGER.info( ToStringBuilder.reflectionToString( r ) );
    }
    
    @Test
    public void testGetTransactionsByReservation() throws Exception {
        Reservation r = cloudbedsScraper.getReservation( webClient, "10569063" );
        assertThat( cloudbedsScraper.isExistsPaymentWithVendorTxCode( webClient, r, "x" ), is( false ) );
        assertThat( cloudbedsScraper.isExistsPaymentWithVendorTxCode( webClient, r, "CASTLE-ROCK-7878681082-1548474047" ), is( true ) );
    }

    @Test
    public void testIsExistsRefund() throws Exception {
        Reservation r = cloudbedsScraper.getReservation( webClient, "34321814" );
        assertThat( cloudbedsScraper.isExistsRefund( webClient, r ), is( true ) );
    }

    @Test
    public void testGetTransactionsForRefund() throws Exception {
        List<TransactionRecord> recs = cloudbedsScraper.getTransactionsForRefund( webClient, "53004499" );
        LOGGER.info( "Found " + recs.size() + " records." );
        recs.forEach( r -> LOGGER.info( ToStringBuilder.reflectionToString( r ) ) );
    }

    @Test
    public void testGetCustomers() throws Exception {
        List<Customer> results = cloudbedsScraper.getCustomers( webClient, 
                LocalDate.now().withDayOfMonth( 4 ), LocalDate.now().withDayOfMonth( 5 ) );
        results.forEach( t -> LOGGER.info( t.toString() ) );
    }

    @Test
    public void testGetReservations() throws Exception {
        List<Customer> results = cloudbedsScraper.getReservations( webClient, 
                LocalDate.now().withDayOfMonth( 1 ), LocalDate.now().withDayOfMonth( 2 ) );
        results.forEach( t -> LOGGER.info( t.toString() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testGetReservationsByQuery() throws Exception {
        List<Customer> results = cloudbedsScraper.getReservations( webClient, "999999999" );
        results.forEach( t -> LOGGER.info( t.toString() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testGetReservationsForBookingSources() throws Exception {
        List<Reservation> results = cloudbedsScraper.getReservationsForBookingSources( webClient, null, null,
                LocalDate.parse( "2019-05-30" ),
                LocalDate.parse( "2019-05-30" ),
                "Hostelworld & Hostelbookers", "Agoda (Channel Collect Booking)" );
        results.forEach( t -> LOGGER.info( t.getIdentifier() + " (" + t.getStatus() + ") - "
                + t.isCardDetailsPresent() + "," + t.getThirdPartyIdentifier() + ": " +
                t.getFirstName() + " " + t.getLastName() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testGetCancelledReservationsForBookingSources() throws Exception {
        List<Reservation> results = cloudbedsScraper.getCancelledReservationsForBookingSources( webClient,
                LocalDate.parse( "2019-01-17" ), // checkin start
                LocalDate.parse( "2019-01-22" ), // checkin end
                LocalDate.parse( "2019-01-16" ), // cancel start
                LocalDate.parse( "2019-01-21" ), // cancel end
                "Hostelworld & Hostelbookers" );
        results.forEach( t -> LOGGER.info( t.getIdentifier() + " (" + t.getStatus() + ") - "
                + t.isCardDetailsPresent() + "," + t.getThirdPartyIdentifier() + ": " +
                t.getFirstName() + " " + t.getLastName() + " cancelled on " + t.getCancellationDate() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testGetReservationsByBookingDate() throws Exception {
        List<Reservation> results = cloudbedsScraper.getReservationsByBookingDate( webClient, 
                LocalDate.now().plusDays( -1 ),
                LocalDate.now(),
                "confirmed,not_confirmed" )
                .stream()
                .map( c -> cloudbedsScraper.getReservationRetry( webClient, c.getId() ) )
                .collect( Collectors.toList() );
        results.forEach( t -> LOGGER.info( t.getIdentifier() + " (" + t.getStatus() + ") - "
                + t.isCardDetailsPresent() + "," + t.getThirdPartyIdentifier() + ": " +
                t.getFirstName() + " " + t.getLastName() ) );
        LOGGER.info( "Found " + results.size() + " entries" );
    }

    @Test
    public void testAddPayment() throws Exception {
        cloudbedsScraper.addPayment( webClient,
                cloudbedsScraper.getReservation( webClient, "34802644" ),
                new BigDecimal( "0.15" ), "Test payment XYZ" );
    }
    
    @Test
    public void testAddRefund() throws Exception {
        cloudbedsScraper.addRefund( webClient,
                cloudbedsScraper.getReservation( webClient, "11968561" ),
                new BigDecimal( "0.14" ), "Refund payment XYZ" );
    }
    
    @Test
    public void testAddNote() throws Exception {
        cloudbedsScraper.addNote( webClient, "9897593", "Test Note& with <> Special characters\n\t ?" );
    }

    @Test
    public void testAddArchivedNote() throws Exception {
        cloudbedsScraper.addArchivedNote( webClient, "35079875", "Sent another custom-templated email." );
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
    public void testGetRoomAssignmentsReport() throws Exception {
        JsonObject rpt = cloudbedsScraper.getRoomAssignmentsReport( webClient, LocalDate.now() );
        LOGGER.info( rpt.toString() );
    }

    @Test
    public void testLookupBookingSourceId() throws Exception {
        LOGGER.info( cloudbedsScraper.lookupBookingSourceIds( webClient, 
                "Hostelworld & Hostelbookers (Hotel Collect Booking)", "Hostelworld (Hotel Collect Booking)" ) );
    }

    @Test
    public void testGetActivityLog() throws Exception {
        cloudbedsScraper.getActivityLog( webClient, "6810494609" )
            .forEach( t -> LOGGER.info( ToStringBuilder.reflectionToString( t ) ) );
    }

    @Test
    public void testGetHostelworldLateCancellationEmailTemplate() throws Exception {
        LOGGER.info( ToStringBuilder.reflectionToString( cloudbedsScraper.getHostelworldLateCancellationEmailTemplate( webClient ) ) );
    }

    @Test
    public void testGetPropertyContent() throws Exception {
        JsonObject j = cloudbedsScraper.getPropertyContent( webClient );
        LOGGER.info( ToStringBuilder.reflectionToString( j ) );
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
        assertThat( "Only 1 reservation expected", c.size(), is( 1 ) );
        Reservation r = cloudbedsScraper.getReservation( webClient, c.get( 0 ).getId() );
        
        // now get the comment we want to copy using the OLD reservation ID
        String comment = dao.fetchGuestComments( lhReservationId ).getComments();
        assertThat( comment, notNullValue() );
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
    public void testFetchEmailTemplate() throws Exception {
        EmailTemplateInfo mail = cloudbedsScraper.fetchEmailTemplate( webClient, CloudbedsScraper.TEMPLATE_DEPOSIT_CHARGE_SUCCESSFUL );
        LOGGER.info( ToStringBuilder.reflectionToString( mail ) );
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

    @Test
    public void createCovid19CancelBookingJobsAug() throws Exception {
        cloudbedsScraper.getReservations( webClient, 
                null, // stay date start
                null, // stay date end
                LocalDate.parse( "2020-08-01" ), // checkin date start
                LocalDate.parse( "2020-08-14" ), // checkin date end
                "confirmed" ).stream()
                .filter( res -> false == "34541998".equals( res.getId() ) ) // maintenance
                .forEach( res -> { 
                    LOGGER.info( "Creating job for reservation " + res.getId() + " " + res.getCheckinDate() + " " + res.getFirstName() + " " + res.getLastName() ); 
                    CancelBookingJob j = new CancelBookingJob();
                    j.setStatus( JobStatus.aborted ); // enable manually
                    j.setReservationId( res.getId() );
                    j.setNote( "Covid-19 - forced cancellation." );
                    dao.insertJob( j );

                    SendTemplatedEmailJob j2 = new SendTemplatedEmailJob();
                    j2.setStatus( JobStatus.aborted ); // enable manually
                    j2.setEmailTemplate( "Coronavirus- Doors Closing" );
                    j2.setReservationId( res.getId() );
                    dao.insertJob( j2 );
                } );
    }

    @Test
    public void createCancellationRefundsSheetApri2020() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        
        Sheet sheet = workbook.createSheet( "Sheet 1" );
        Row header = sheet.createRow( 0 );
         
        final CellStyle HEADER_STYLE = workbook.createCellStyle();
        HEADER_STYLE.setLocked( true );
         
        final Font HEADER_FONT = workbook.createFont();
        HEADER_FONT.setFontName( "Arial" );
        HEADER_FONT.setFontHeightInPoints( (short) 10 );
        HEADER_FONT.setBold( true );
        HEADER_STYLE.setFont( HEADER_FONT );

        final Font DEFAULT_BODY_FONT = workbook.createFont();
        DEFAULT_BODY_FONT.setFontName( "Arial" );
        DEFAULT_BODY_FONT.setFontHeightInPoints( (short) 10 );
        
        final String[] HEADERS = {
                "Name", "Email", "Reservation", "3rd Party Reservation", "Booking Source", "Channel Collect", "Non-Refundable", 
                "Checkin Date", "Checkout Date", "Grand Total", "Deposit", "Amount Paid", "Balance Due", "Cancellation Date"
        };
        final int[] HEADER_WIDTH = {
                6000, 8000, 3500, 5000, 7000, 5000, 5000,
                3500, 3500, 3000, 3000, 3000, 3000, 4000
        };
        for ( int i = 0; i < HEADERS.length; i++ ) {
            Cell headerCell = header.createCell( i );
            headerCell.setCellValue( HEADERS[i] );
            headerCell.setCellStyle( HEADER_STYLE );
            sheet.setColumnWidth( i, HEADER_WIDTH[i] );
        }
        
        final CellStyle DEFAULT_STYLE = workbook.createCellStyle();
        DEFAULT_STYLE.setFont( DEFAULT_BODY_FONT );

        final CellStyle DATE_STYLE = workbook.createCellStyle();
        DATE_STYLE.setDataFormat( workbook.createDataFormat().getFormat( "yyyy-mm-dd" ) );
        DATE_STYLE.setFont( DEFAULT_BODY_FONT );
        
        final CellStyle CURRENCY_STYLE = workbook.createCellStyle();
        CURRENCY_STYLE.setDataFormat( workbook.createDataFormat().getFormat( "#,##0.00" ) );
        CURRENCY_STYLE.setFont( DEFAULT_BODY_FONT );

        AtomicInteger rowNum = new AtomicInteger( 1 );
        cloudbedsScraper.getReservations( webClient,
                null, // stay date start
                null, // stay date end
                LocalDate.parse( "2020-03-23" ), // checkin date start
                LocalDate.parse( "2020-04-30" ), // checkin date end end
                "canceled" ).stream()
                .map( c -> cloudbedsScraper.getReservationRetry( webClient, c.getId() ) )
                .forEach( res -> {
                    Row row = sheet.createRow( rowNum.getAndIncrement() );
                    Cell cell = row.createCell( 0 );
                    cell.setCellValue( res.getFirstName() + " " + res.getLastName() );
                    cell.setCellStyle( DEFAULT_STYLE );

                    cell = row.createCell( 1 );
                    cell.setCellValue( res.getEmail() );
                    cell.setCellStyle( DEFAULT_STYLE );

                    cell = row.createCell( 2 );
                    cell.setCellValue( res.getIdentifier() );
                    cell.setCellStyle( DEFAULT_STYLE );

                    cell = row.createCell( 3 );
                    cell.setCellValue( res.getThirdPartyIdentifier() );
                    cell.setCellStyle( DEFAULT_STYLE );

                    cell = row.createCell( 4 );
                    cell.setCellValue( res.getSourceName() );
                    cell.setCellStyle( DEFAULT_STYLE );

                    cell = row.createCell( 5 );
                    cell.setCellValue( res.getChannelPaymentType() );
                    cell.setCellStyle( DEFAULT_STYLE );

                    cell = row.createCell( 6 );
                    cell.setCellValue( res.isNonRefundable() );
                    cell.setCellStyle( DEFAULT_STYLE );

                    cell = row.createCell( 7 );
                    cell.setCellValue( res.getCheckinDateAsDate() );
                    cell.setCellStyle( DATE_STYLE );

                    cell = row.createCell( 8 );
                    cell.setCellValue( res.getCheckoutDateAsDate() );
                    cell.setCellStyle( DATE_STYLE );

                    cell = row.createCell( 9 );
                    cell.setCellValue( res.getGrandTotal().floatValue() );
                    cell.setCellStyle( CURRENCY_STYLE );

                    cell = row.createCell( 10 );
                    cell.setCellValue( Float.parseFloat( res.getBookingDeposit() ) );
                    cell.setCellStyle( CURRENCY_STYLE );

                    cell = row.createCell( 11 );
                    cell.setCellValue( res.getPaidValue().floatValue() );
                    cell.setCellStyle( CURRENCY_STYLE );

                    cell = row.createCell( 12 );
                    cell.setCellValue( res.getBalanceDue().floatValue() );
                    cell.setCellStyle( CURRENCY_STYLE );

                    cell = row.createCell( 13 );
                    cell.setCellValue( res.getCancellationDateAsDate() );
                    cell.setCellStyle( DATE_STYLE );
                } );

        File currDir = new File( "." );
        String path = currDir.getAbsolutePath();
        String fileLocation = path.substring( 0, path.length() - 1 ) + "src\\test\\resources\\apr2020-refunds.xlsx";

        FileOutputStream outputStream = new FileOutputStream( fileLocation );
        workbook.write( outputStream );
        workbook.close();
    }

    @Test
    public void testAddReservation() throws Exception {
        String newReservationData = IOUtils.toString( CloudbedsScraperTest.class.getClassLoader()
                .getResourceAsStream( "add_reservation_data_carla_new.json" ), StandardCharsets.UTF_8 );
        LOGGER.info( "data: " + newReservationData );
        cloudbedsScraper.addReservation( webClient, newReservationData );
    }

    @Test
    public void testGetGuestById() throws Exception {
        Guest g = cloudbedsScraper.getGuestById( webClient, "24870746" );
        String json = gson.toJson( new CustomerInfo( g ) );
        LOGGER.info( json );
    }

    @Test
    public void testChargeCardForBooking() throws Exception {
        Reservation r = cloudbedsScraper.getReservation( webClient, "43994808" );
        cloudbedsScraper.chargeCardForBooking( webClient, r, new BigDecimal( "37.26" ) );
    }
}