
package com.macbackpackers.beans.json;

import java.math.BigDecimal;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RecordPaymentRequestTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Test
    public void testSerialize() {
        Gson gson = new GsonBuilder().setDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSS+00:00" ).create();
        RecordPaymentRequest req = new RecordPaymentRequest( 
                1234567, "MasterCard", new BigDecimal( "2.34" ), "i wantz my monies.", true );

        String json = gson.toJson( req );
        LOGGER.info( json );

        req = new RecordPaymentRequest( 
                1234567, "Other", new BigDecimal( "2.34" ), "i wantz my monies.", false );

        json = gson.toJson( req );
        LOGGER.info( json );
    }
}
