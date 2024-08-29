
package com.macbackpackers.services;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class SagepayServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    SagepayService paymentService;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    WebClient cbWebClient;

    @Autowired
    @Qualifier( "gsonForSagepay" )
    Gson gson;

    @Autowired
    WordPressDAO dao;

    @Value( "${sagepay.integration.key}" )
    String SAGEPAY_INTEGRATION_KEY;

    @Value( "${sagepay.integration.password}" )
    String SAGEPAY_INTEGRATION_PASSWORD;

    @Test
    public void testSendSagepayEmail() throws Exception {
        paymentService.sendSagepayPaymentConfirmationEmail( cbWebClient, dao.fetchSagepayTransaction( 34 ) );
    }

    @Test
    public void testProcessSagepayRefund() throws Exception {
//        String txnId = sendSagepayTestPayment();
        paymentService.processSagepayRefund( 4, 3 );
    }

    private String getAuthorizationHeader() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (SAGEPAY_INTEGRATION_KEY + ":" + SAGEPAY_INTEGRATION_PASSWORD).getBytes() );
    }

    private String getSagepayTestMerchantSessionKey() throws IOException {
        WebRequest webRequest = new WebRequest( new URL( "https://pi-test.sagepay.com/api/v1/merchant-session-keys" ), HttpMethod.POST );
        webRequest.setAdditionalHeader( "Content-Type", "application/json; charset=UTF-8" );
        webRequest.setAdditionalHeader( "Authorization", getAuthorizationHeader() );
        webRequest.setCharset( StandardCharsets.UTF_8 );
        webRequest.setRequestBody( "{\"vendorName\": \"macbackpackersl\"}" );

        Page redirectPage = cbWebClient.getPage( webRequest );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

        return gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class )
                .getAsJsonObject().get( "merchantSessionKey" ).getAsString();
    }

    private String getSagepayTestCardIdentifier( String merchantSessionKey ) throws IOException {
        WebRequest webRequest = new WebRequest( new URL( "https://pi-test.sagepay.com/api/v1/card-identifiers" ), HttpMethod.POST );
        webRequest.setAdditionalHeader( "Content-Type", "application/json; charset=UTF-8" );
        webRequest.setAdditionalHeader( "Authorization", "Bearer " + merchantSessionKey );
        webRequest.setCharset( StandardCharsets.UTF_8 );
        webRequest.setRequestBody( "{\"cardDetails\":{" +
                "\"cardholderName\": \"SAM JONES\"," +
                "\"cardNumber\": \"4929000000006\"," +
                "\"expiryDate\": \"0326\"," +
                "\"securityCode\": \"123\"}}" );

        Page redirectPage = cbWebClient.getPage( webRequest );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

        return gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class )
                .getAsJsonObject().get( "cardIdentifier" ).getAsString();
    }
    
    String sendSagepayTestPayment() throws IOException {
        String merchantSessionKey = getSagepayTestMerchantSessionKey();
        String vendorTxCode = merchantSessionKey;
        WebRequest webRequest = new WebRequest( new URL( "https://pi-test.sagepay.com/api/v1/transactions" ), HttpMethod.POST );
        webRequest.setAdditionalHeader( "Content-Type", "application/json; charset=UTF-8" );
        webRequest.setAdditionalHeader( "Authorization", getAuthorizationHeader() );
        webRequest.setCharset( StandardCharsets.UTF_8 );
        webRequest.setRequestBody( "{" + 
                "    \"transactionType\": \"Payment\"," + 
                "    \"paymentMethod\": {" + 
                "        \"card\": {" + 
                "            \"merchantSessionKey\": \"" + merchantSessionKey + "\"," + 
                "            \"cardIdentifier\": \"" + getSagepayTestCardIdentifier( merchantSessionKey ) + "\"," + 
                "            \"save\": false" + 
                "        }" + 
                "    }," + 
                "    \"vendorTxCode\": \"" + vendorTxCode + "\"," + 
                "    \"amount\": 231," + 
                "    \"currency\": \"GBP\"," + 
                "    \"description\": \"Demo Payment\"," + 
                "    \"apply3DSecure\": \"Disable\"," + 
                "    \"customerFirstName\": \"Sam\"," + 
                "    \"customerLastName\": \"Jones\"," + 
                "    \"billingAddress\": {" + 
                "        \"address1\": \"407 St. John Street\"," + 
                "        \"city\": \"London\"," + 
                "        \"postalCode\": \"EC1V 4AB\"," + 
                "        \"country\": \"GB\"" + 
                "    }," + 
                "    \"entryMethod\": \"Ecommerce\"" + 
                "}" );

        Page redirectPage = cbWebClient.getPage( webRequest );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

        return gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class )
                .getAsJsonObject().get( "transactionId" ).getAsString();
    }
}
