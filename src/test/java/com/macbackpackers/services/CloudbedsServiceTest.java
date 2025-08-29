package com.macbackpackers.services;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.jobs.ChargeCustomAmountForBookingJob;
import com.macbackpackers.utils.AnyByteStringToStringConverter;
import org.apache.commons.io.IOUtils;
import org.htmlunit.WebClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.SecretsManagerTestApp;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Test configuration for Secret Manager integration testing.
 * <p>
 * This test uses @SpringBootTest to create a proper Spring Boot application context
 * that properly initializes Spring Cloud GCP Secret Manager. This is the recommended
 * approach for testing with Secret Manager.
 * <p>
 * It requires:
 * - GOOGLE_APPLICATION_CREDENTIALS environment variable to be set to the service account JSON file path
 * - Access to Google Cloud Secret Manager
 * - The secrets "db_url_crh", "db_username_crh", "db_password_crh" to exist in Secret Manager
 */
@ExtendWith( SpringExtension.class )
@SpringBootTest( classes = SecretsManagerTestApp.class )
@TestPropertySource( properties = {
        "spring.profiles.active=lsh"
} )
public class CloudbedsServiceTest {

    static {
        // Register the ByteString converter before Spring tries to resolve Secret Manager placeholders
        // This is essential for proper Secret Manager integration in tests
        ( (DefaultConversionService) DefaultConversionService.getSharedInstance() ).addConverter( new AnyByteStringToStringConverter() );
    }

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Value( "${db.username:NOT_FOUND}" )
    private String dbUsername;

    @Value( "${db.password:NOT_FOUND}" )
    private String dbPassword;

    @Autowired
    CloudbedsService cloudbedsService;

    @Autowired
    WordPressDAO dao;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    WebClient webClient;

    @Test
    public void testDumpAllocations() throws Exception {
        cloudbedsService.dumpAllocationsFrom( webClient, 9042,
                LocalDate.now().withDayOfMonth( 1 ), LocalDate.now().withDayOfMonth( 30 ) );
    }

    @Test
    public void testGetAllStaffAllocationsDaily() throws Exception {
        LocalDate currentDate = LocalDate.parse( "2018-05-25" );
        LocalDate endDate = LocalDate.parse( "2018-10-13" );
        while ( currentDate.isBefore( endDate ) ) {
            AllocationList staffAllocations = new AllocationList(
                    cloudbedsService.getAllStaffAllocationsDaily( webClient, currentDate ) );
            staffAllocations.forEach( a -> a.setJobId( 440052 ) );
            LOGGER.info( "Inserting {} staff allocations.", staffAllocations.size() );
            dao.insertAllocations( staffAllocations );
            currentDate = currentDate.plusDays( 1 );
        }
    }

    @Test
    public void testGetAllStaffAllocations() throws Exception {
        List<Allocation> alloc = cloudbedsService.getAllStaffAllocations( webClient, LocalDate.now().minusDays( 1 ) );
        alloc.forEach( a -> LOGGER.info( a.getRoom() + ": " + a.getBedName() + " -> " + a.getCheckinDate() + " to " + a.getCheckoutDate() ) );
    }

    @Test
    public void testCreateChargeHostelworldLateCancellationJobs() throws Exception {
        cloudbedsService.createChargeHostelworldLateCancellationJobs(
                webClient, LocalDate.now().minusDays( 1 ), LocalDate.now() );
    }

    @Test
    public void testCreateChargeHostelworldLateCancellationForAugustJobs() throws Exception {
        cloudbedsService.createChargeHostelworldLateCancellationJobsForAugust(
                webClient, LocalDate.now().minusDays( 7 ), LocalDate.now() );
    }

    @Test
    public void testSendHostelworldLateCancellationGmail() throws Exception {
        cloudbedsService.sendHostelworldLateCancellationGmail( webClient, "22702371", new BigDecimal( "11.22" ) );
    }

    @Test
    public void testSendHostelworldLateCancellationEmail() throws Exception {
        cloudbedsService.sendHostelworldLateCancellationEmail( webClient, "10568885", BigDecimal.ONE );
    }

    @Test
    public void testMarkCreditCardInvalidOnBDC() throws Exception {
        cloudbedsService.markCreditCardInvalidOnBDC( "25201392" );
    }

    @Test
    public void testLoginAndSaveSession() throws Exception {
        cloudbedsService.loginAndSaveSession( webClient );
    }

    @Test
    public void testSendTemplatedGmail() throws Exception {
        Map<String, String> replaceText = new HashMap<>();
        replaceText.put( "\\[charge amount\\]", "3.99" );
        replaceText.put( "\\[last four digits\\]", "1234" );
        cloudbedsService.sendTemplatedGmail( webClient, "10568885", "Payment Successful", replaceText, true );
    }

    @Test
    public void testCancelReservation() throws Exception {
        cloudbedsService.cancelReservation( "33055660", "Covid-19 - forced cancellation." );
    }

    @Test
    public void testCreateCanceledPrepaidBDCBookingsChargeJobs() throws Exception {
        cloudbedsService.createCanceledPrepaidBDCBookingsChargeJobs( webClient,
                LocalDate.now().withMonth( 8 ).withDayOfMonth( 31 ),
                LocalDate.now().withMonth( 11 ).withDayOfMonth( 16 ) );
    }

    @Test
    public void testGetAllVCCBookingsThatCanBeCharged() throws Exception {
        cloudbedsService.getAllVCCBookingsThatCanBeCharged()
                .forEach( r -> LOGGER.info( "Found reservation {}", r ) );
    }

    @Test
    public void testCreateSendCovidPrestayEmailJobs() throws Exception {
        cloudbedsService.createSendCovidPrestayEmailJobs( webClient, LocalDate.now().plusDays( 1 ) );
    }

    @Test
    public void testCreateSendGroupBookingApprovalRequiredEmailJobs() throws Exception {
        cloudbedsService.createSendGroupBookingApprovalRequiredEmailJobs( webClient, LocalDate.now().plusDays( -5 ), LocalDate.now() );
    }

    @Test
    public void testSendGroupBookingApprovalRequiredGmail() throws Exception {
        cloudbedsService.sendGroupBookingApprovalRequiredGmail( webClient, "41696492" );
    }

    @Test
    public void testCreateSendGroupBookingPaymentReminderEmailJobs() throws Exception {
        cloudbedsService.createSendGroupBookingPaymentReminderEmailJobs( webClient, LocalDate.now().plusDays( 1 ), LocalDate.now().plusDays( 7 ) );
    }

    @Test
    public void testCreateSendTemplatedEmailJobs() throws Exception {
        cloudbedsService.createSendTemplatedEmailJobs( webClient, "COVID-19 Guidance Update", null, null,
                LocalDate.parse( "2021-12-03" ), LocalDate.parse( "2021-12-04" ), null, null,
                "confirmed,not_confirmed", null, null );
    }

    @Test
    public void testSendCovidPrestayGmail() throws Exception {
        cloudbedsService.sendCovidPrestayGmail( webClient, "10568950" );
    }

    @Test
    public void testCreateSendPaymentLinkEmailJobs() throws Exception {
        cloudbedsService.createSendPaymentLinkEmailJobs( webClient, LocalDate.now().plusDays( 1 ) );
    }

    @Test
    public void testAddReservation() throws Exception {
        // room 10 - stephane
        cloudbedsService.createFixedRateReservation( webClient, "38059840",
                LocalDate.parse( "2021-01-04" ), LocalDate.parse( "2021-01-11" ), new BigDecimal( "10" ) );
    }

    @Test
    public void testCreateFixedRateLongTermReservations() throws Exception {
        cloudbedsService.createFixedRateLongTermReservations( webClient, LocalDate.parse( "2021-01-04" ), 7, new BigDecimal( 10 ) );
    }

    @Test
    public void testSendStripePaymentConfirmationGmail() throws Exception {
        cloudbedsService.sendStripePaymentConfirmationGmail( webClient, "CRH-INV-ABCDEFG-SDQE" );
    }

    @Test
    public void testCreateChargeHogmanayBookingJobs() throws Exception {
        cloudbedsService.createChargeHogmanayBookingJobs( webClient );
    }

    @Test
    public void testCreateEmailsForBookingsOnBlacklist() throws Exception {
        cloudbedsService.createEmailsForBookingsOnBlacklist( 543745 );
    }

    @Test
    public void testArchiveAllTransactionNotes() {
        cloudbedsService.archiveAllTransactionNotes( webClient, "87742801" );
    }

    @Test
    public void testVerifyDatabaseLogins() {
        LOGGER.info( "db.username = " + dbUsername );
        LOGGER.info( "db.password = " + dbPassword );
    }

    @Test
    public void testCreateBdcManualChargeJobs() throws Exception {
        // read each line in file
        IOUtils.readLines( this.getClass().getResourceAsStream( "/bdc_reservations_import.csv" ), Charset.defaultCharset() )
                .stream()
                .forEach( line -> {
                    LOGGER.info( line );
                    String cols[] = line.split( "," );
                    LOGGER.info( "Creating ChargeCustomAmountForBookingJob for BDC " + cols[0] + " and amount " + cols[2] );
                    Optional<Reservation> r = cloudbedsService.getReservationForBDC( cols[0] );
                    if ( r.isPresent() ) {
                        ChargeCustomAmountForBookingJob job = new ChargeCustomAmountForBookingJob();
                        job.setReservationId( r.get().getReservationId() );
                        job.setAmount( new BigDecimal( cols[2].replace( "£", "" ) ) );
                        job.setStatus( JobStatus.aborted );
                        dao.insertJob( job );
                    }
                    else {
                        LOGGER.error( "No reservation found for booking " + cols[0] );
                    }
                } );
    }
}