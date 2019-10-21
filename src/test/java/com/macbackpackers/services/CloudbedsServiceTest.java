
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
        cloudbedsService.createBulkEmailJob(webClient, "Hogmanay at Castle Rock!",
                LocalDate.now().withMonth( 12 ).withDayOfMonth( 31 ), 
                LocalDate.now().withMonth( 12 ).withDayOfMonth( 31 ),
                null, null, "confirmed,not_confirmed" );
    }

    @Test
    public void testSendTemplatedGmail() throws Exception {
        cloudbedsService.sendTemplatedGmail( webClient, "10568885", "Hogmanay at Castle Rock!" );
    }

}
