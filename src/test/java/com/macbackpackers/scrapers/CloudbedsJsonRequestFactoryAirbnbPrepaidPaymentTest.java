package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;

import com.macbackpackers.dao.WordPressDAO;

public class CloudbedsJsonRequestFactoryAirbnbPrepaidPaymentTest {

    @Test
    public void addAirbnbPrepaidPaymentTemplate_containsAirbnbMethod() throws Exception {
        String template = new String(
                getClass().getClassLoader().getResourceAsStream( "add_airbnb_prepaid_payment_data.json" )
                        .readAllBytes(),
                StandardCharsets.UTF_8 );
        assertThat( template, containsString( "\"payment_method\": \"airbnb\"" ) );
        assertThat( template, containsString( "\"payment_type\": \"airbnb\"" ) );
        assertThat( template, containsString( "__PAYMENT_AMOUNT__" ) );
        assertThat( template, containsString( "__DESCRIPTION__" ) );
    }

    @Test
    public void createAddAirbnbPrepaidPaymentRequest_embedsAmountAndDescription() throws Exception {
        WordPressDAO dao = mock( WordPressDAO.class );
        when( dao.getOption( "hbo_cloudbeds_property_id" ) ).thenReturn( "17363" );
        when( dao.getOption( "hbo_cloudbeds_useragent" ) ).thenReturn( "Mozilla/5.0 test" );
        when( dao.getOption( "hbo_cloudbeds_cookies" ) ).thenReturn( "session=test" );

        CloudbedsJsonRequestFactory factory = new CloudbedsJsonRequestFactory();
        java.lang.reflect.Field daoField = CloudbedsJsonRequestFactory.class.getDeclaredField( "dao" );
        daoField.setAccessible( true );
        daoField.set( factory, dao );

        WebRequest req = factory.createAddAirbnbPrepaidPaymentRequest( "243912807", new BigDecimal( "304.20" ),
                CloudbedsScraper.AIRBNB_PREPAID_PAYMENT_DESCRIPTION );

        String paymentJson = req.getRequestParameters().stream()
                .filter( p -> "payment".equals( p.getName() ) )
                .map( NameValuePair::getValue )
                .findFirst()
                .orElse( "" );
        assertThat( paymentJson, containsString( "airbnb" ) );
        assertThat( paymentJson, containsString( "304.20" ) );
        assertThat( paymentJson, containsString( CloudbedsScraper.AIRBNB_PREPAID_PAYMENT_DESCRIPTION ) );
        assertThat( paymentJson, containsString( "17363" ) );
    }
}
