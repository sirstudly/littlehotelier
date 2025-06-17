package com.macbackpackers.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.macbackpackers.beans.Payment;
import com.macbackpackers.config.LittleHotelierConfig;

@ExtendWith( SpringExtension.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class ExpediaApiServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private ExpediaApiService service;

    @Test
    public void testReturnCardDetailsForBooking() throws Exception {
        Payment cardDetails = service.returnCardDetailsForBooking( "819608075" );
        LOGGER.debug( cardDetails.getCardDetails().getName() );
    }
}
