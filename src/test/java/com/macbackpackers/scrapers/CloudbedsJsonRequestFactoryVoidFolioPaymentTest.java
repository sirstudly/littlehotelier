package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;

import com.macbackpackers.dao.WordPressDAO;

public class CloudbedsJsonRequestFactoryVoidFolioPaymentTest {

    @Test
    public void voidTemplate_hasVoidTransactionType() throws Exception {
        String template = new String(
                getClass().getClassLoader().getResourceAsStream( "void_terceiros_payment_data.json" )
                        .readAllBytes(),
                StandardCharsets.UTF_8 );
        assertThat( template, containsString( "\"transaction_type\": \"Void\"" ) );
        assertThat( template, containsString( "__PARENT_PAYMENT_ID__" ) );
        assertThat( template, containsString( "__PAYMENT_METHOD__" ) );
        assertThat( template, containsString( "__PAYMENT_AMOUNT__" ) );
    }

    @Test
    public void createVoidFolioPaymentRequest_embedsParentAndReservationInventory() throws Exception {
        WordPressDAO dao = mock( WordPressDAO.class );
        when( dao.getOption( "hbo_cloudbeds_property_id" ) ).thenReturn( "18265" );
        when( dao.getOption( "hbo_cloudbeds_useragent" ) ).thenReturn( "Mozilla/5.0 test" );
        when( dao.getOption( "hbo_cloudbeds_cookies" ) ).thenReturn( "csrf_accessa_cookie=abc123" );
        when( dao.getCsrfToken() ).thenReturn( "abc123" );

        CloudbedsJsonRequestFactory factory = new CloudbedsJsonRequestFactory();
        java.lang.reflect.Field daoField = CloudbedsJsonRequestFactory.class.getDeclaredField( "dao" );
        daoField.setAccessible( true );
        daoField.set( factory, dao );

        WebRequest req = factory.createVoidFolioPaymentRequest(
                "190469923", "249878824", new BigDecimal( "17.24" ), "Terceiros" );

        assertThat( req.getUrl().toString(), containsString( "/connect/payment/add" ) );

        String paymentJson = req.getRequestParameters().stream()
                .filter( p -> "payment".equals( p.getName() ) )
                .map( NameValuePair::getValue )
                .findFirst()
                .orElse( "" );
        assertThat( paymentJson, containsString( "\"transaction_type\": \"Void\"" ) );
        assertThat( paymentJson, containsString( "249878824" ) );
        assertThat( paymentJson, containsString( "17.24" ) );
        assertThat( paymentJson, containsString( "Terceiros" ) );
        assertThat( paymentJson, containsString( "18265" ) );
        assertThat( paymentJson, not( containsString( "Refund" ) ) );

        String inventory = req.getRequestParameters().stream()
                .filter( p -> "inventory_object".equals( p.getName() ) )
                .map( NameValuePair::getValue )
                .findFirst()
                .orElse( "" );
        assertThat( inventory, containsString( "\"type\":\"reservation\"" ) );
        assertThat( inventory, containsString( "190469923" ) );
        assertThat( inventory, not( containsString( "reservation_room" ) ) );
    }
}
