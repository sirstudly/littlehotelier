package com.macbackpackers.scrapers;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.gson.Gson;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = LittleHotelierConfig.class)
public class CloudbedsScraperTest {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    CloudbedsScraper cloudbedsService;
    
    @Autowired
    WordPressDAO dao;
    
    @Autowired 
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Test
    public void testDoLogin() throws Exception {
        cloudbedsService.login( "test@cloudbeds.com", "testpassword" );
    }
    
    @Test
    public void testLoadDashboard() throws Exception {
        cloudbedsService.loadDashboard();
    }

    @Test
    public void testLoadBooking() throws Exception {
        Reservation r = cloudbedsService.getReservation( "9813914" );
        LOGGER.info( ToStringBuilder.reflectionToString( r ) );
    }

    @Test
    public void testGetCustomers() throws Exception {
        List<Customer> results = cloudbedsService.getCustomers( 
                LocalDate.now().withDayOfMonth( 1 ), LocalDate.now().withDayOfMonth( 2 ) );
        results.stream().forEach( t -> LOGGER.info( t.toString() ) );
    }

    @Test
    public void testGetReservations() throws Exception {
        List<Customer> results = cloudbedsService.getReservations( 
                LocalDate.now().withDayOfMonth( 1 ), LocalDate.now().withDayOfMonth( 30 ) );
        results.stream().forEach( t -> LOGGER.info( t.toString() ) );
    }
    
    @Test
    public void testAddPayment() throws Exception {
        cloudbedsService.addPayment( "9814194", "visa", new BigDecimal( "0.15" ), "Test payment XYZ" );
    }
    
    @Test
    public void testAddNote() throws Exception {
        cloudbedsService.addNote( "9897593", "Test Note& with <> Special characters\n\t ?" );
    }
    
    @Test
    public void testPing() throws Exception {
        cloudbedsService.ping();
    }
    
    @Test
    public void testDumpAllocations() throws Exception {
        dao.deleteAllocations( 9042 );
        cloudbedsService.dumpAllocationsFrom( 9042, 
                LocalDate.now().withDayOfMonth( 1 ), LocalDate.now().withDayOfMonth( 30 ) );;
    }
    
    @Test
    public void testDeserialise() throws Exception {
        Reservation reservation = gson.fromJson( FileUtils.readFileToString( new File( "test.json" ), Charset.defaultCharset() ), Reservation.class );
        LOGGER.info( reservation.toString() );
    }
}