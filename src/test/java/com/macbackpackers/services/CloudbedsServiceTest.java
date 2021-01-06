
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
    public void testCreatePrepaidChargeJobs() throws Exception {
        cloudbedsService.createBDCPrepaidChargeJobs();
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
        final LocalDate START_DATE = LocalDate.parse( "2021-01-04" );
        final LocalDate END_DATE = LocalDate.parse( "2021-01-11" );
        final BigDecimal DAILY_RATE = new BigDecimal( "10" );

        // room 23 - javier
        cloudbedsService.addReservation( "35723699", START_DATE, END_DATE,
                DAILY_RATE, "16x", "16 Bed Mixed Dormitory", 1, 200707, "112559-3" );

        // fabian
        cloudbedsService.addReservation( "35723625", START_DATE, END_DATE,
                DAILY_RATE, "16x", "16 Bed Mixed Dormitory", 1, 200707, "112559-7" );

        // patxi
        cloudbedsService.addReservation( "36065694", START_DATE, END_DATE,
                DAILY_RATE, "16x", "16 Bed Mixed Dormitory", 1, 200707, "112559-9" );

        // alejandro cano
        cloudbedsService.addReservation( "35723764", START_DATE, END_DATE,
                DAILY_RATE, "16x", "16 Bed Mixed Dormitory", 1, 200707, "112559-13" );

        // ramon
        cloudbedsService.addReservation( "35723785", START_DATE, END_DATE,
                DAILY_RATE, "16x", "16 Bed Mixed Dormitory", 1, 200707, "112559-15" );

        // room 24 - alok
        cloudbedsService.addReservation( "35836043", START_DATE, END_DATE,
                DAILY_RATE, "8x", "8 Bed Mixed Dormitory", 1, 200779, "112591-3" );

        // santiago
        cloudbedsService.addReservation( "35885166", START_DATE, END_DATE,
                DAILY_RATE, "8x", "8 Bed Mixed Dormitory", 1, 200779, "112591-5" );

        // room 12 - alison
      cloudbedsService.addReservation( "35554090", START_DATE, END_DATE,
              DAILY_RATE, "LTf", "xLT Female Dorm", 1, 200802, "112612-12" );

        // beatrice
        cloudbedsService.addReservation( "35554068", START_DATE, END_DATE,
                DAILY_RATE, "LTf", "xLT Female Dorm", 1, 200802, "112612-19" );

        // dan
        cloudbedsService.addReservation( "35445451", START_DATE, END_DATE,
                DAILY_RATE, "LTf", "xLT Female Dorm", 1, 200802, "112612-13" );

        // room 20 - ann
        cloudbedsService.addReservation( "10466732", START_DATE, END_DATE,
                DAILY_RATE, "12X", "12 Bed Mixed Dormitory", 1, 199831, "112188-1" );

        // aida
        cloudbedsService.addReservation( "24870746", START_DATE, END_DATE,
                DAILY_RATE, "12X", "12 Bed Mixed Dormitory", 1, 199831, "112188-3" );

        // somer
        cloudbedsService.addReservation( "34991482", START_DATE, END_DATE,
                DAILY_RATE, "12X", "12 Bed Mixed Dormitory", 1, 199831, "112188-5" );

        // carla
        cloudbedsService.addReservation( "35178547", START_DATE, END_DATE,
                DAILY_RATE, "12X", "12 Bed Mixed Dormitory", 1, 199831, "112188-7" );

        // room 21 - mercedes
        cloudbedsService.addReservation( "35380453", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-3" );

        // montse
        cloudbedsService.addReservation( "35455596", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-5" );

        // kshitee
        cloudbedsService.addReservation( "35697697", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-7" );

        // amelia
        cloudbedsService.addReservation( "36047352", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-9" );

        // ryan
        cloudbedsService.addReservation( "35749319", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-11" );

        // matthieu
        cloudbedsService.addReservation( "34865244", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-13" );

        // room 22 - djiby
        cloudbedsService.addReservation( "35275113", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-17" );

        // jorge
        cloudbedsService.addReservation( "35221003", START_DATE, END_DATE,
                DAILY_RATE, "10x", "10 Bed Mixed Dormitory", 1, 199406, "112014-19" );

        // room 10 - stephane
        cloudbedsService.addReservation( "16105225", START_DATE, END_DATE,
                DAILY_RATE, "LTM", "xLT Male Dorm - Rm. 10", 1, 200809, "112619-7" );

        // room 14 - isaac
        cloudbedsService.addReservation( "35445442", START_DATE, END_DATE,
                DAILY_RATE, "LTM", "xLT Male Dorm- Rm. 14", 1, 200814, "112621-3" );

        // nacho
        cloudbedsService.addReservation( "11377358", START_DATE, END_DATE,
                DAILY_RATE, "LTM", "xLT Male Dorm- Rm. 14", 1, 200814, "112621-5" );

        // carlos
        cloudbedsService.addReservation( "35554194", START_DATE, END_DATE,
                DAILY_RATE, "LTM", "xLT Male Dorm- Rm. 14", 1, 200814, "112621-7" );

        // adam
        cloudbedsService.addReservation( "23530168", START_DATE, END_DATE,
                DAILY_RATE, "LTM", "xLT Male Dorm- Rm. 14", 1, 200814, "112621-11" );

    }
}
