package com.macbackpackers.services;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.math.BigDecimal;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.xml.TxnResponse;
import com.macbackpackers.config.LittleHotelierConfig;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class PxPostServiceTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    
    private static final String VISA_SUCCESS = "4111111111111111";
    private static final String MASTERCARD_SUCCESS = "5431111111111111";
    private static final String AMEX_NOT_SUPPORTED = "371111111111114";
    private static final String DINERS_SUCCESS = "36000000000008";
    private static final String CARD_FLAGGED_FOR_RETRY = "4999999999999202";
    private static final String DECLINED_CARD_RESPONSE_CODE_05 = "4999999999999236";
    private static final String DECLINED_CARD_RESPONSE_CODE_12 = "4999999999999269"; 
    private static final String DECLINED_CARD_RESPONSE_CODE_30 = "5431111111111301"; 
    private static final String DECLINED_CARD_RESPONSE_CODE_51 = "5431111111111228"; 

    @Autowired
    private PxPostService processor;
    
    @Test
    public void testGetStatus() {
        processor.getStatus( "1481407022530", null );
    }

    @Test
    public void testPaymentPurchaseVisa() {
        testSuccessfulAuthTransaction( VISA_SUCCESS, "Visa" );
    }

    @Test
    public void testPaymentPurchaseMastercard() {
        testSuccessfulAuthTransaction( MASTERCARD_SUCCESS, "MasterCard" );
    }
    
    @Test
    public void testPaymentPurchaseDiners() {
        testSuccessfulAuthTransaction( DINERS_SUCCESS, "Diners" );
    }
    
    @Test
    public void testPaymentPurchaseTransactionFlaggedForRetry() {
        String txnRef = String.valueOf( System.currentTimeMillis() );
        CardDetails cc = new CardDetails();
        cc.setName( "Bob McDonald" );
        cc.setCardNumber( CARD_FLAGGED_FOR_RETRY );
        cc.setExpiry( "0921" );
        cc.setCvv( "123" );
        TxnResponse txnResponse = processor.processPayment( 
                txnRef, "BDC-12345678", cc, new BigDecimal("123.45"), null, null );
        
        assertThat(txnResponse.getTransaction(), is(notNullValue()));
        assertThat(txnResponse.getTransaction().getAuthorised(), is("0"));
        assertThat(txnResponse.getTransaction().getRetry(), is("1"));
        assertThat(StringUtils.trimToNull( txnResponse.getTransaction().getDpsTxnRef() ), is(notNullValue()));
        
        assertThat(txnResponse.getTxnRef(), is(txnRef));
        assertThat(txnResponse.getResponseCode(), is("U9"));
        assertThat(txnResponse.getResponseText(), is("DECLINED (U9)"));
        assertThat(txnResponse.getSuccess(), is("0"));

        // now verify the status is the same
        TxnResponse txnResponseStatus = processor.getStatus( txnRef, null );
        assertThat(txnResponse.getTransaction(), is(notNullValue()));
        assertThat(txnResponse.getTransaction().getAuthorised(), is("0"));
        assertThat(StringUtils.trimToNull( txnResponse.getTransaction().getDpsTxnRef() ), is(notNullValue()));
        
        assertThat(txnResponse.getTxnRef(), is(txnRef));
        assertThat(txnResponse.getResponseCode(), is("U9"));
        assertThat(txnResponse.getResponseText(), is("DECLINED (U9)"));
        assertThat(txnResponse.getSuccess(), is("0"));
        assertThat(txnResponse.getTransaction().getDpsTxnRef(), is(txnResponseStatus.getTransaction().getDpsTxnRef()));
    }
    
    @Test
    public void testPaymentPurchaseAmexNotConfigured() {
        testDeclinedTransaction( AMEX_NOT_SUPPORTED, "AQ", "AMEX NOT CONFIGURED" );
    }
    
    @Test
    public void testPaymentPurchaseFailedWithResponseCode05() {
        testDeclinedTransaction( DECLINED_CARD_RESPONSE_CODE_05, "05", "DO NOT HONOUR" );
    }

    @Test
    public void testPaymentPurchaseFailedWithResponseCode30() {
        testDeclinedTransaction( DECLINED_CARD_RESPONSE_CODE_30, "30", "DECLINED" );
    }

    @Test
    public void testPaymentPurchaseFailedWithResponseCode51() {
        testDeclinedTransaction( DECLINED_CARD_RESPONSE_CODE_51, "51", "DECLINED" );
    }

    private void testSuccessfulAuthTransaction( String cardNumber, String cardName ) {
        String txnRef = String.valueOf( System.currentTimeMillis() );
        CardDetails cc = new CardDetails();
        cc.setName( "Bob McDonald" );
        cc.setCardNumber( cardNumber );
        cc.setExpiry( "0921" );
        cc.setCvv( "123" );
        TxnResponse txnResponse = processor.processPayment(
                txnRef, "BDC-12345678", cc, new BigDecimal( "123.45" ), null, null );
        assertSuccessfulAuth( txnRef, txnResponse );
        assertThat( txnResponse.getTransaction().getCardName(), is( cardName ) );

        // now verify the status is the same
        TxnResponse txnResponseStatus = processor.getStatus( txnRef, null );
        assertSuccessfulAuthStatus( txnRef, txnResponseStatus );
        assertThat( txnResponse.getTransaction().getDpsTxnRef(), is( txnResponseStatus.getTransaction().getDpsTxnRef() ) );
        assertThat( txnResponseStatus.getTransaction().getCardName(), is( cardName ) );
    }

    private void assertSuccessfulAuth( String txnRef, TxnResponse txnResponse ) {
        assertSuccessfulAuthStatus( txnRef, txnResponse );
        assertThat( txnResponse.getTransaction().getCvc2ResultCode(), is( "U" ) );
    }

    private void assertSuccessfulAuthStatus( String txnRef, TxnResponse txnResponse ) {
        assertThat( txnResponse.getTransaction(), is( notNullValue() ) );
        assertThat( txnResponse.getTransaction().getAuthorised(), is( "1" ) );
        assertThat( StringUtils.trimToNull( txnResponse.getTransaction().getDpsTxnRef() ), is( notNullValue() ) );

        assertThat( txnResponse.getTxnRef(), is( txnRef ) );
        assertThat( txnResponse.getResponseCode(), is( "00" ) );
        assertThat( txnResponse.getResponseText(), is( "APPROVED" ) );
        assertThat( txnResponse.getSuccess(), is( "1" ) );
    }

    private void testDeclinedTransaction( String cardNumber,
            String expectedResponseCode, String expectedResponseText ) {
        String txnRef = String.valueOf( System.currentTimeMillis() );
        CardDetails cc = new CardDetails();
        cc.setName( "Bob McDonald" );
        cc.setCardNumber( cardNumber );
        cc.setExpiry( "0921" );
        cc.setCvv( "123" );
        TxnResponse txnResponse = processor.processPayment(
                txnRef, "BDC-12345678", cc, new BigDecimal( "123.45" ), null, null );

        assertThat( txnResponse.getTransaction(), is( notNullValue() ) );
        assertThat( txnResponse.getTransaction().getAuthorised(), is( "0" ) );
        assertThat( StringUtils.trimToNull( txnResponse.getTransaction().getDpsTxnRef() ), is( notNullValue() ) );

        assertThat( txnResponse.getTxnRef(), is( txnRef ) );
        assertThat( txnResponse.getResponseCode(), is( expectedResponseCode ) );
        assertThat( txnResponse.getResponseText(), is( expectedResponseText ) );
        assertThat( txnResponse.getSuccess(), is( "0" ) );

        // now verify the status is the same
        TxnResponse txnResponseStatus = processor.getStatus( txnRef, null );
        assertThat( txnResponse.getTransaction(), is( notNullValue() ) );
        assertThat( txnResponse.getTransaction().getAuthorised(), is( "0" ) );
        assertThat( StringUtils.trimToNull( txnResponse.getTransaction().getDpsTxnRef() ), is( notNullValue() ) );

        assertThat( txnResponse.getTxnRef(), is( txnRef ) );
        assertThat( txnResponse.getResponseCode(), is( expectedResponseCode ) );
        assertThat( txnResponse.getResponseText(), is( expectedResponseText ) );
        assertThat( txnResponse.getSuccess(), is( "0" ) );
        assertThat( txnResponse.getTransaction().getDpsTxnRef(), is( txnResponseStatus.getTransaction().getDpsTxnRef() ) );
    }
}
