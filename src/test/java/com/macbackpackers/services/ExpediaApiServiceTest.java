
package com.macbackpackers.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.DepositPayment;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ExpediaApiServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    
    @Autowired
    private ExpediaApiService service;

    @Test
    public void testReturnCardDetailsForBooking() throws Exception {
        DepositPayment cardDetails = service.returnCardDetailsForBooking( "819608075" );
        LOGGER.debug( cardDetails.getCardDetails().getName() );
    }

}
