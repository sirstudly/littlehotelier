
package com.macbackpackers.services;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegexMatchTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( RegexMatchTest.class );

    @Test
    public void testExpediaLoginMask() throws Exception {
        RegexMask mask = new RegexMask(
                "(username=\")([^\"]*)(\")", m -> {
                    return m.find() ? m.group( 1 ) + "********" + m.group( 3 ) : null;
                } );
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><BookingRetrievalRQ xmlns=\"http://www.expediaconnect.com/EQC/BR/2014/01\"><Authentication username=\"EQC999911RDX\" password=\"Pa55w0rd\"/><Hotel id=\"12345678\"/><ParamSet><Booking id=\"90990990\"/></ParamSet></BookingRetrievalRQ>";
        String userMasked = mask.applyMask( input );
        LOGGER.info( userMasked );
        mask = new RegexMask(
                "(password=\")([^\"]*)(\")", m -> {
                    return m.find() ? m.group( 1 ) + "********" + m.group( 3 ) : null;
                } );
        String passMasked = mask.applyMask( userMasked );
        LOGGER.info( passMasked );
    }

    @Test
    public void testPxPostLoginMask() throws Exception {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Txn><PostUsername>MyPostUser</PostUsername><PostPassword>MyP05tPa55w0rd</PostPassword><CardHolderName>Joe Bloggs</CardHolderName><CardNumber>444433........11</CardNumber><Amount>14.30</Amount><DateExpiry>1119</DateExpiry><Cvc2>...</Cvc2><Cvc2Presence>1</Cvc2Presence><InputCurrency>GBP</InputCurrency><TxnType>Purchase</TxnType><TxnId>7717717</TxnId><MerchantReference>EXP-98765432</MerchantReference></Txn>";
        RegexMask mask = new RegexMask(
                "(<PostUsername>)(.*)(</PostUsername>)", m -> {
                    return m.find() ? m.group( 1 ) + "********" + m.group( 3 ) : null;
                } );
        String userMasked = mask.applyMask( input );
        LOGGER.info( userMasked );
        mask = new RegexMask(
                "(<PostPassword>)(.*)(</PostPassword>)", m -> {
                    return m.find() ? m.group( 1 ) + "********" + m.group( 3 ) : null;
                } );
        String passMasked = mask.applyMask( userMasked );
        LOGGER.info( passMasked );
    }

    @Test
    public void testLHJsonCardMask() throws Exception {
        LOGGER.info( new LHJsonCardMask().applyCardMask(
                FileUtils.readFileToString( new File( "test_load_page.json" ), "UTF-8" ) ) );
    }

    @Test
    public void testExpediaCardMask() throws Exception {
        ExpediaCardMask cm = new ExpediaCardMask();
        LOGGER.info( cm.applyCardSecurityCodeMask( cm.applyCardMask(
                FileUtils.readFileToString( new File( "expedia_response_ex.xml" ), "UTF-8" ) ) ) );
    }

    @Test
    public void testBasicCardMask() throws Exception {
        BasicCardMask cm = new BasicCardMask();
        Assert.assertThat( cm.applyCardMask( "1234567890123456" ), Matchers.is( "123456........56" ) );
        Assert.assertThat( cm.applyCardSecurityCodeMask( "123" ), Matchers.is( "..." ) );
    }
}
