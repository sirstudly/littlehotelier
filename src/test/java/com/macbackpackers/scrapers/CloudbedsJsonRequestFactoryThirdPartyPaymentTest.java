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

public class CloudbedsJsonRequestFactoryThirdPartyPaymentTest {

    @Test
    public void addThirdPartyPaymentTemplate_containsTerceiros() throws Exception {
        String template = new String(
                getClass().getClassLoader().getResourceAsStream( "add_third_party_payment_data.json" )
                        .readAllBytes(),
                StandardCharsets.UTF_8 );
        assertThat( template, containsString( "\"payment_method\": \"Terceiros\"" ) );
        assertThat( template, containsString( "\"payment_type\": \"Terceiros\"" ) );
        assertThat( template, containsString( "__PAYMENT_AMOUNT__" ) );
        assertThat( template, containsString( "__DESCRIPTION__" ) );
    }

    @Test
    public void createAddThirdPartyPaymentRequest_embedsAmountAndDescription() throws Exception {
        WordPressDAO dao = mock( WordPressDAO.class );
        when( dao.getOption( "hbo_cloudbeds_property_id" ) ).thenReturn( "17363" );
        when( dao.getOption( "hbo_cloudbeds_useragent" ) ).thenReturn( "Mozilla/5.0 test" );
        when( dao.getOption( "hbo_cloudbeds_cookies" ) ).thenReturn( "session=test" );

        CloudbedsJsonRequestFactory factory = new CloudbedsJsonRequestFactory();
        java.lang.reflect.Field daoField = CloudbedsJsonRequestFactory.class.getDeclaredField( "dao" );
        daoField.setAccessible( true );
        daoField.set( factory, dao );

        WebRequest req = factory.createAddThirdPartyPaymentRequest( "244283011", new BigDecimal( "34.43" ),
                CloudbedsScraper.HOSTELWORLD_PAYNOW_PAYMENT_DESCRIPTION );

        String paymentJson = req.getRequestParameters().stream()
                .filter( p -> "payment".equals( p.getName() ) )
                .map( NameValuePair::getValue )
                .findFirst()
                .orElse( "" );
        assertThat( paymentJson, containsString( "Terceiros" ) );
        assertThat( paymentJson, containsString( "34.43" ) );
        assertThat( paymentJson, containsString( CloudbedsScraper.HOSTELWORLD_PAYNOW_PAYMENT_DESCRIPTION ) );
        assertThat( paymentJson, containsString( "17363" ) );
    }
}
