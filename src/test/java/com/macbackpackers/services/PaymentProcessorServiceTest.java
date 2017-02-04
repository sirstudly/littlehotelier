
package com.macbackpackers.services;

import java.util.Calendar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class PaymentProcessorServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    PaymentProcessorService paymentService;

    @Autowired
    WordPressDAO dao;

    @Test
    public void testPayment() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, 0 );
        c.set( Calendar.DATE, 22 );
        paymentService.processDepositPayment( "BDC-1850350118", c.getTime() );
    }

}
