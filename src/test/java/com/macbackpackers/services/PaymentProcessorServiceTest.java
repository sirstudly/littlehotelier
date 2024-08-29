
package com.macbackpackers.services;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTableRow;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.macbackpackers.beans.Payment;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.scrapers.BookingsPageScraper;
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

    @Autowired
    WordPressDAO dao;

    @Autowired
    BookingsPageScraper bookingScraper;

    @Value( "${stripe.apikey}" )
    String STRIPE_API_KEY;

    @Test
    public void testPayment() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 26 );
        paymentService.processDepositPayment( lhWebClient, 0, "BDC-1264569063", c.getTime() );
        LOGGER.info( "done" );
    }

    @Test
    public void testDepositChargeAmount() throws Exception {
        String bookingRef = "BDC-1192773406";

        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, 5 ); // 0-based
        c.set( Calendar.DATE, 7 );

        LOGGER.info( "Processing payment for booking " + bookingRef );
        HtmlPage bookingsPage = bookingScraper.goToBookingPageBookedOn( lhWebClient, c.getTime(), bookingRef );

        List<?> rows = bookingsPage.getByXPath(
                "//div[@id='content']/div[@class='reservations']/div[@class='data']/table/tbody/tr[@class!='group_header']" );
        if ( rows.size() != 1 ) {
            throw new IncorrectResultSizeDataAccessException( "Unable to find unique booking " + bookingRef, 1 );
        }
        // need the LH reservation ID before clicking on the row
        HtmlTableRow row = HtmlTableRow.class.cast( rows.get( 0 ) );
        int reservationId = Integer.parseInt( row.getAttribute( "data-id" ) );
        LOGGER.info( "Reservation ID: " + reservationId );

        // click on the only reservation on the page
        HtmlPage reservationPage = row.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load

        Payment deposit = new Payment();
        paymentService.updatePaymentWithBdcDepositChargeAmount( deposit, reservationPage );
        LOGGER.info( "Amount to charge: " + deposit.getAmount() );
    }

    @Test
    public void testSyncLastPxPostTransactionInLH() throws Exception {

        // This is a valid successful txn in PxPost (UAT) : 1508959296186
        String bookingRef = "EXP-924636178";
        Calendar c = Calendar.getInstance();
        c.set( Calendar.YEAR, 2017 );
        c.set( Calendar.MONTH, Calendar.OCTOBER );
        c.set( Calendar.DATE, 13 );

        HtmlPage bookingsPage = bookingScraper.goToBookingPageBookedOn( lhWebClient, c.getTime(), bookingRef );

        List<?> rows = bookingsPage.getByXPath(
                "//div[@id='content']/div[@class='reservations']/div[@class='data']/table/tbody/tr/td[@class='booking_reference' and text()='" + bookingRef + "']/.." );
        if ( rows.size() != 1 ) {
            throw new IncorrectResultSizeDataAccessException( "Unable to find unique booking " + bookingRef, 1 );
        }
        // need the LH reservation ID before clicking on the row
        HtmlTableRow row = HtmlTableRow.class.cast( rows.get( 0 ) );

        // click on the only reservation on the page
        HtmlPage reservationPage = row.click();

        // this should add the payment details to the LH reservation above
        bookingRef = "HWL-123-1234567";
        paymentService.syncLastPxPostTransactionInLH( bookingRef, true, reservationPage );
    }

    @Test
    public void testProcessManualPayment() throws Exception {
        paymentService.processManualPayment( lhWebClient, 0,
                "HWL-551-336647714", new BigDecimal( "0.01" ), "Testing -rc", true );
    }

    @Test
    public void testCopyCardDetailsFromLHtoCB() throws Exception {
        Calendar c = Calendar.getInstance();
        c.set( Calendar.DATE, 28 );
        c.set( Calendar.MONTH, Calendar.AUGUST );
        paymentService.copyCardDetailsFromLHtoCB( cbWebClient, "LH1802108246113", c.getTime() );
    }

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
