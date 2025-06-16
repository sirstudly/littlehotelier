
package com.macbackpackers.services;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.htmlunit.WebClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.config.LittleHotelierConfig;
import com.stripe.Stripe;
import com.stripe.model.Charge;
import com.stripe.model.Refund;
import com.stripe.model.Token;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;
import com.stripe.param.ChargeCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TokenCreateParams;

@RunWith( SpringJUnit4ClassRunner.class )
@ContextConfiguration( classes = LittleHotelierConfig.class )
public class PaymentProcessorServiceTest {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    PaymentProcessorService paymentService;

    @Autowired
    @Qualifier( "webClient" )
    WebClient lhWebClient;

    @Autowired
    @Qualifier( "webClientForCloudbeds" )
    WebClient cbWebClient;

    @Test
    public void testCopyCardDetailsToCloudbeds() throws Exception {
        paymentService.copyCardDetailsToCloudbeds( cbWebClient, "11109575" );
    }

    @Test
    public void testChargeNonRefundableBooking() throws Exception {
        paymentService.chargeNonRefundableBooking( cbWebClient, "11068108" );
    }
    
    @Test
    public void testProcessPrepaidBooking() throws Exception {
        paymentService.processPrepaidBooking( cbWebClient, "22970476" );
    }

    @Test
    public void testProcessStripeRefund() throws Exception {

        Stripe.apiKey = "sk_test_4eC39HqLyjWDarjtT1zdp7dc";

        Token token = Token.create( TokenCreateParams.builder()
                .setCard( TokenCreateParams.Card.builder()
                        .setNumber( "4242424242424242" )
                        .setCvc( "314" )
                        .setExpMonth( "4" )
                        .setExpYear( "2021" )
                        .build() )
                .build() );

        Charge charge = Charge.create( ChargeCreateParams.builder()
                .setSource( token.getId() )
                .setCurrency( "usd" )
                .setAmount( 1099L ).build() );
        LOGGER.info( "Charge: " + charge.toJson() );

        Refund refund = Refund.create( RefundCreateParams.builder().setCharge( charge.getId() ).build(),
                // set idempotency key so we can re-run safely in case of previous failure
                new RequestOptionsBuilder().setIdempotencyKey( token.getId() ).build() );
        LOGGER.info( "Refund: " + refund.toJson() );
    }

    @Test
    public void testProcessStripeRefund2() throws Exception {
        paymentService.processStripeRefund( 3, 2 );
    }

    @Test
    public void testFetchStripeRefund() throws Exception {
        Refund r = paymentService.retrieveStripeRefund( 15 );
        LOGGER.info( ToStringBuilder.reflectionToString( r ) );
    }

    @Test
    public void testRetrieveStripeRefund() throws Exception {
        Refund r = paymentService.retrieveStripeRefund( 15 );
        LOGGER.info( ToStringBuilder.reflectionToString( r ) );
    }

    @Test
    public void refreshStripeRefundStatus() throws Exception {
        paymentService.refreshStripeRefundStatus( 1 );
        paymentService.refreshStripeRefundStatus( 2 );
        paymentService.refreshStripeRefundStatus( 3 );
        paymentService.refreshStripeRefundStatus( 5 );
        paymentService.refreshStripeRefundStatus( 6 );
        paymentService.refreshStripeRefundStatus( 7 );
        paymentService.refreshStripeRefundStatus( 9 );
    }

    @Test
    public void testProcessStripePayment() throws Exception {
        paymentService.processStripePayment( "CRH-INV-ABCDEFG-SDQE" );
    }
}
