
package com.macbackpackers.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.AllocationList;
import com.macbackpackers.beans.SagepayTransaction;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.CloudbedsScraper;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class CloudbedsServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    CloudbedsService cloudbedsService;

    @Autowired
    CloudbedsScraper cloudbedsScraper;

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
        LocalDate currentDate = LocalDate.parse("2018-05-25");
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
        alloc.stream().forEach( a -> LOGGER.info( a.getRoom() + ": " + a.getBedName() + " -> " + a.getCheckinDate() + " to " + a.getCheckoutDate() ) );
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
    public void testSendSagepayPaymentConfirmationEmail() throws Exception {
        SagepayTransaction txn = dao.fetchSagepayTransaction( 192 );
        cloudbedsService.sendSagepayPaymentConfirmationGmail( webClient, txn.getReservationId(), 192 );
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
    public void testCreateBulkEmailJob() throws Exception {
        cloudbedsService.createBulkEmailJob(webClient, CloudbedsScraper.TEMPLATE_COVID19_CLOSING,
                null, null,
                LocalDate.now().plusDays( 1 ), LocalDate.parse( "2020-04-30" ), "confirmed,not_confirmed" );
    }

    @Test
    public void testSendTemplatedGmail() throws Exception {
        cloudbedsService.sendTemplatedGmail( webClient, "10568885", CloudbedsScraper.TEMPLATE_COVID19_CLOSING );
    }

    @Test
    public void testCancelReservation() throws Exception {
        cloudbedsService.cancelReservation( "33055660", "Covid-19 - forced cancellation." );
    }

    @Test
    public void testCreateCanceledPrepaidBDCBookingsChargeJobs() throws Exception {
        cloudbedsService.createCanceledPrepaidBDCBookingsChargeJobs( webClient, 
                LocalDate.now().withMonth( 8 ).withDayOfMonth( 31 ),
                LocalDate.now().withMonth( 11 ).withDayOfMonth( 16 ));
    }

    @Test
    public void testGetAllVCCBookingsThatCanBeCharged() throws Exception {
        cloudbedsService.getAllVCCBookingsThatCanBeCharged()
                .stream()
                .forEach( r -> LOGGER.info( "Found reservation {}", r ) );
    }

    @Test
    public void testSendDepositChargeJobRetractionEmails() {
        cloudbedsService.sendDepositChargeJobRetractionEmails( webClient, 469807, 469929 );
    }

    @Test
    public void testCreateSendCovidPrestayEmailJobs() throws Exception {
        cloudbedsService.createSendCovidPrestayEmailJobs( webClient, LocalDate.now().plusDays( 1 ) );
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
}
