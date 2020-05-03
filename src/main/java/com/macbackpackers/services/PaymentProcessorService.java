
package com.macbackpackers.services;

import static com.macbackpackers.scrapers.AllocationsPageScraper.POUND;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlItalic;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.GuestDetails;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.beans.Payment;
import com.macbackpackers.beans.PxPostTransaction;
import com.macbackpackers.beans.StripeRefund;
import com.macbackpackers.beans.cloudbeds.responses.CloudbedsJsonResponse;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.beans.xml.TxnResponse;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.PaymentNotAuthorizedException;
import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.jobs.BDCMarkCreditCardInvalidJob;
import com.macbackpackers.jobs.SendDepositChargeDeclinedEmailJob;
import com.macbackpackers.jobs.SendDepositChargeSuccessfulEmailJob;
import com.macbackpackers.jobs.SendHostelworldLateCancellationEmailJob;
import com.macbackpackers.jobs.SendNonRefundableDeclinedEmailJob;
import com.macbackpackers.jobs.SendNonRefundableSuccessfulEmailJob;
import com.macbackpackers.jobs.SendRefundSuccessfulEmailJob;
import com.macbackpackers.scrapers.AgodaScraper;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingComScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.scrapers.ChromeScraper;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.HostelworldScraper;
import com.macbackpackers.scrapers.ReservationPageScraper;
import com.stripe.Stripe;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions.RequestOptionsBuilder;
import com.stripe.param.RefundCreateParams;

/**
 * Service for checking payments in LH, sending (deposit) payments through the payment gateway and
 * recording said payments in LH.
 */
@Service
public class PaymentProcessorService {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private static final FastDateFormat DATETIME_FORMAT = FastDateFormat.getInstance( "dd/MM/yyyy HH:mm:ss" );
    private static final FastDateFormat DATETIME_STANDARD = FastDateFormat.getInstance( "yyyy-MM-dd HH:mm:ss" );
    private static final int MAX_PAYMENT_ATTEMPTS = 3; // max number of transaction attempts

    // all allowable characters for lookup key
    private static String LOOKUPKEY_CHARSET = "2345678ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static int LOOKUPKEY_LENGTH = 7;

    @Value( "${stripe.apikey}" )
    private String STRIPE_API_KEY;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private ApplicationContext context;

    /** Amount to charge BDC bookings. Either a percentage between 0-1 or "first_night". */
    @Value( "${bdc.deposit.strategy}" )
    private String bdcDepositStrategy;

    @Autowired
    private ReservationPageScraper reservationPageScraper;

    @Autowired
    private BookingsPageScraper bookingsPageScraper;

    @Autowired
    private HostelworldScraper hostelworldScraper;

    @Autowired
    private AgodaScraper agodaScraper;

    @Autowired
    private WordPressDAO wordpressDAO;

    @Autowired
    private PxPostService pxPostService;

    @Autowired
    private ExpediaApiService expediaService;

    @Autowired
    private GmailService gmailService;

    @Autowired
    private CloudbedsScraper cloudbedsScraper;

    @Autowired
    private ChromeScraper chromeScraper;

    @Autowired
    private BookingComScraper bdcScraper;

    @Autowired
    private GenericObjectPool<WebDriver> driverFactory;

    /**
     * Attempt to charge the deposit payment for the given reservation. This method does nothing if
     * the payment outstanding != payment total. It also checks whether there was already a previous
     * attempt to charge the booking and updates LH payment details if it was previously successful.
     * To avoid multiple failed transactions, this method should only be called again if new card
     * details have been updated.
     * 
     * @param webClient web client to use
     * @param jobId current job id
     * @param bookingRef booking reference e.g. BDC-123456789
     * @param bookedOnDate date on which reservation was booked
     * @throws IOException on I/O error
     */
    public synchronized void processDepositPayment( WebClient webClient, int jobId, String bookingRef, Date bookedOnDate ) throws IOException {
        LOGGER.info( "Processing payment for booking " + bookingRef );
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageBookedOn( webClient, bookedOnDate, bookingRef );
        webClient.waitForBackgroundJavaScript( 30000 );
        HtmlPage reservationPage = reservationPageScraper.getReservationPage( webClient, bookingsPage, bookingRef );
        int reservationId = reservationPageScraper.getReservationId( reservationPage );

        // check if we've already received any payment on the payments tab
        HtmlSpan totalRecieved = reservationPage.getFirstByXPath( "//span[contains(@class,'total_received')]" );
        if ( false == "£0".equals( totalRecieved.getTextContent() ) ) {
            LOGGER.info( "Payment of " + totalRecieved.getTextContent() + " already received for booking " + bookingRef + "." );
            return; // nothing to do
        }

        // only process bookings at "Confirmed" or "Checked-in"
        HtmlSpan statusSpan = reservationPage.getFirstByXPath( "//td[@class='status']/span" );
        if ( false == Arrays.asList( "Confirmed", "Checked-in" ).contains( statusSpan.getTextContent() ) ) {
            LOGGER.info( "Booking " + bookingRef + " is at status "
                    + (statusSpan == null ? null : statusSpan.getTextContent())
                    + "; skipping payment processing" );
            return; // nothing to do
        }

        // group bookings must be approved/charged manually
        int numberGuests = countNumberOfGuests( reservationPage );
        if ( numberGuests >= wordpressDAO.getGroupBookingSize() ) {
            LOGGER.info( "Booking " + bookingRef + " has " + numberGuests + " guests. Payment must be done manually for groups." );
            return;
        }

        // If we need the card details available, just do this ahead of time.
        // This only needs to be done once in the currently active session.
        // There was a problem before where the card details were being cleared out
        // on the LH page whenever we saved the record after going through the steps
        // to unhide the card details; we do this here and reload the page to avoid this
        if ( bookingRef.startsWith( "BDC-" ) ) {
            HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@class='view-card-details']" );

            // BDC may have a cancellation grace period; don't charge within this period
            if ( isWithinCancellationGracePeriod( reservationPage ) ) {
                LOGGER.info( "Booking " + bookingRef + " is within the cancellation grace period. Skipping charge for now..." );
                return;
            }

            // if the "view card details" link is available; we don't yet have secure access yet
            if ( viewCcDetails != null ) {
                LOGGER.info( "Card details currently hidden; requesting security access" );
                reservationPageScraper.enableSecurityAccess( reservationPage, reservationId );
            }
        }

        try {
            // get the deposit amount and full card details
            Payment depositPayment = retrieveCardDetails( bookingRef, reservationId, reservationPage );

            // if for some reason, we fucked up our calculation, this will throw an error!
            HtmlSpan outstandingTotalSpan = reservationPage.getFirstByXPath( "//span[contains(@class,'total_outstanding')]" );
            BigDecimal outstandingTotal = new BigDecimal( outstandingTotalSpan.getTextContent().replaceAll( POUND, "" ) );
            if ( false == depositPayment.isVirtual() && depositPayment.getAmount().compareTo( outstandingTotal ) > 0 ) {
                throw new UnrecoverableFault( "Calculated amount to charge " + depositPayment.getAmount() + " exceeds LH total outstanding (" + outstandingTotal + ")." );
            }

            processPxPostTransaction( jobId, bookingRef, depositPayment, true, reservationPage );
        }
        catch ( MissingUserDataException ex ) {
            if ( ex.getMessage().contains( "Amex not enabled" ) ) {
                HtmlTextArea notes = reservationPage.getFirstByXPath( "//textarea[@id='notes']" );
                if ( notes.getText().contains( "Amex not enabled" ) ) {
                    return; // already in notes; ignore...
                }
            }
            reservationPageScraper.appendNote( reservationPage,
                    ex.getMessage() + " - " + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
    }

    /**
     * Attempt to charge the deposit payment for the given reservation. This method does nothing if
     * the payment outstanding != payment total.
     * 
     * @param webClient web client to use
     * @param reservationId unique CB reservation
     * @throws IOException on I/O error
     * @throws MessagingException
     */
    public synchronized void processDepositPayment( WebClient webClient, String reservationId ) throws IOException, MessagingException {
        LOGGER.info( "Processing deposit payment for reservation " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        LOGGER.info( cbReservation.getThirdPartyIdentifier() + ": "
                + cbReservation.getFirstName() + " " + cbReservation.getLastName() );
        LOGGER.info( "Source: " + cbReservation.getSourceName() );
        LOGGER.info( "Status: " + cbReservation.getStatus() );
        LOGGER.info( "Checkin: " + cbReservation.getCheckinDate() );
        LOGGER.info( "Checkout: " + cbReservation.getCheckoutDate() );
        LOGGER.info( "Grand Total: " + cbReservation.getGrandTotal() );
        LOGGER.info( "Balance Due: " + cbReservation.getBalanceDue() );

        // check if we have anything to pay
        if ( cbReservation.getPaidValue().compareTo( BigDecimal.ZERO ) > 0 ) {
            LOGGER.info( "Booking has non-zero paid amount. Not charging deposit." );
            return;
        }
        else if ( "canceled".equals( cbReservation.getStatus() ) ) {
            LOGGER.info( "Booking is cancelled. Not charging." );
            return;
        }
        // group bookings must be approved/charged manually
        else if ( cbReservation.getNumberOfGuests() >= wordpressDAO.getGroupBookingSize() ) {
            LOGGER.info( "Reservation " + reservationId + " has " + cbReservation.getNumberOfGuests() + " guests. Payment must be done manually for groups." );
            return;
        }

        // check if card details exist in CB
        if ( false == cbReservation.isCardDetailsPresent() ) {
            throw new MissingUserDataException( "Missing card details found for reservation " + cbReservation.getReservationId() + ". Unable to continue." );
        }

        // either take first night, or a percentage amount
        BigDecimal depositAmount;
        if ( false == "first_night".equals( bdcDepositStrategy ) && "Booking.com".equals( cbReservation.getSourceName() ) ) {
            BigDecimal percentToCharge = new BigDecimal( bdcDepositStrategy );
            depositAmount = cbReservation.getGrandTotal().multiply( percentToCharge ).setScale( 2, RoundingMode.HALF_UP );
            LOGGER.info( "Deposit amount due: " + depositAmount );
        }
        else {
            depositAmount = cbReservation.getRateFirstNight( gson );
            LOGGER.info( "First night due: " + depositAmount );
        }

        if ( depositAmount.compareTo( BigDecimal.ZERO ) <= 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount must be greater than 0." );
        }
        if ( depositAmount.compareTo( cbReservation.getBalanceDue() ) > 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount exceeds balance due." );
        }

        try {
            // should have credit card details at this point; attempt autocharge
            cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, depositAmount );
            cloudbedsScraper.addNote( webClient, reservationId,
                    "Successfully charged deposit of £" + cloudbedsScraper.getCurrencyFormat().format( depositAmount ) );

            // send email if successful
            SendDepositChargeSuccessfulEmailJob job = new SendDepositChargeSuccessfulEmailJob();
            job.setReservationId( reservationId );
            job.setAmount( depositAmount );
            job.setStatus( JobStatus.submitted );
            wordpressDAO.insertJob( job );
        }
        catch ( PaymentNotAuthorizedException payEx ) {
            LOGGER.info( "Unable to process payment: " + payEx.getMessage() );

            if ( cloudbedsScraper.getEmailLastSentDate( webClient, reservationId,
                    CloudbedsScraper.TEMPLATE_DEPOSIT_CHARGE_DECLINED ).isPresent() ) {
                LOGGER.info( "Declined payment email already sent. Not going to do it again..." );
            }
            else {
                LOGGER.info( "Creating declined deposit charge email job" );
                String paymentURL = generateUniquePaymentURL( reservationId, depositAmount );
                SendDepositChargeDeclinedEmailJob job = new SendDepositChargeDeclinedEmailJob();
                job.setReservationId( reservationId );
                job.setAmount( depositAmount );
                job.setPaymentURL( paymentURL );
                job.setStatus( JobStatus.submitted );
                wordpressDAO.insertJob( job );

                if ( "Booking.com".equals( cbReservation.getSourceName() ) &&
                        false == cbReservation.containsNote( "Marking card ending in " + cbReservation.getCreditCardLast4Digits() + " as invalid for reservation" ) &&
                        wordpressDAO.getOption( "hbo_bdc_username" ) != null ) {
                    BDCMarkCreditCardInvalidJob j = new BDCMarkCreditCardInvalidJob();
                    j.setStatus( JobStatus.submitted );
                    j.setReservationId( reservationId );
                    wordpressDAO.insertJob( j );
                }
            }
        }

    }

    /**
     * Loads the given booking and processes any payments (if applicable).
     * 
     * @param webClient web client used for LH
     * @param jobId current job id
     * @param bookingRef booking reference e.g. BDC-123456789
     * @param checkinDate date on which reservation starts
     * @throws IOException on I/O error
     */
    public void processAgodaPayment( WebClient webClient, int jobId, String bookingRef, Date checkinDate ) throws IOException {
        LOGGER.info( "Processing payment for booking " + bookingRef );
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageArrivedOn( webClient, checkinDate, bookingRef );
        HtmlPage reservationPage = reservationPageScraper.getReservationPage( webClient, bookingsPage, bookingRef );

        // first, ensure LH and our PxPost table are in sync
        syncLastPxPostTransactionInLH( bookingRef, false, reservationPage );

        // check we have something to charge
        BigDecimal chargeAmount = getAgodaChargeAmount( reservationPage );
        if ( chargeAmount.compareTo( BigDecimal.ZERO ) <= 0 ) {
            LOGGER.info( "Nothing to charge!" );
        }
        else {
            processNewPxPostTransaction( jobId, bookingRef,
                    new Payment( chargeAmount, retrieveAgodaCardDetails( reservationPage, bookingRef ) ),
                    false, reservationPage );
        }
    }

    /**
     * Attempt to charge the no-show amount for the given reservation. This method fails fast if the
     * payment already received &gt= amount to charge . If there was already a previous attempt to
     * charge the booking, this will update LH payment details so it is synced.
     * 
     * @param lhWebClient web client to use for little hotelier
     * @param jobId current job id
     * @param bookingRef booking reference e.g. BDC-123456789
     * @param amountToCharge charge amount
     * @param message a wee message to put in the notes (optional)
     * @param useCardDetailsFromLH override the card details with those taken from LH
     * @throws IOException on I/O error
     * @throws ParseException on parse error
     */
    public synchronized void processManualPayment( WebClient lhWebClient, int jobId,
            String bookingRef, BigDecimal amountToCharge, String message, boolean useCardDetailsFromLH ) throws IOException, ParseException {
        LOGGER.info( "Processing manual payment for booking " + bookingRef + " for " + amountToCharge );
        LOGGER.info( message );

        // attempt to charge less than or equal to zero?!
        if ( amountToCharge.compareTo( BigDecimal.ZERO ) <= 0 ) {
            throw new IllegalArgumentException( "Why the fuck are you trying to charge " + amountToCharge + "?!" );
        }

        // search from 1 month prior (card details only kept 1 week after checkin date for HW bookings)
        // to 6 months in future (usually we only scan 4 months in the future)
        Date dateFrom = Date.from( Instant.now().minus( Duration.ofDays( 30 ) ) );
        Date dateTo = Date.from( Instant.now().plus( Duration.ofDays( 180 ) ) );

        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageForArrivals( lhWebClient, dateFrom, dateTo, bookingRef, null );
        HtmlPage reservationPage = reservationPageScraper.getReservationPage( lhWebClient, bookingsPage, bookingRef );

        // cannot charge more than what is outstanding
        HtmlSpan outstandingTotalSpan = reservationPage.getFirstByXPath( "//span[contains(@class,'total_outstanding')]" );
        BigDecimal outstandingTotal = new BigDecimal( outstandingTotalSpan.getTextContent().replaceAll( POUND, "" ) );
        if ( amountToCharge.compareTo( outstandingTotal ) > 0 ) {
            throw new UnrecoverableFault( "Request to charge " + amountToCharge + " exceeds LH total outstanding (" + outstandingTotal + ")." );
        }

        // check if we've already received any payment on the payments tab
        for ( Object paymentDiv : reservationPage.getByXPath( "//div[contains(@class,'payment-row')]//div[contains(@class,'payment-label-bottom')]" ) ) {
            if ( HtmlDivision.class.cast( paymentDiv ).getTextContent().contains( "PxPost transaction" ) ) {
                throw new UnrecoverableFault( "Payment already exists. Has it already been charged?" );
            }
        }

        try {
            // get the full card details
            CardDetails ccDetails = null;
            if ( bookingRef.startsWith( "HWL-" ) && false == useCardDetailsFromLH ) {
                LOGGER.info( "Retrieving HWL customer card details" );
                ccDetails = retrieveHWCardDetails( bookingRef );
            }
            else if ( bookingRef.startsWith( "EXP-" ) && false == useCardDetailsFromLH ) {
                LOGGER.info( "Retrieving EXP customer card details" );
                ccDetails = expediaService.returnCardDetailsForBooking( bookingRef ).getCardDetails();
            }
            else if ( bookingRef.startsWith( "AGO-" ) && false == useCardDetailsFromLH ) {
                LOGGER.info( "Retrieving AGO customer card details" );
                ccDetails = retrieveAgodaCardDetails( reservationPage, bookingRef );
            }
            else {
                int reservationId = reservationPageScraper.getReservationId( reservationPage );
                HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@class='view-card-details']" );
                // if the "view card details" link is available; we don't yet have secure access yet
                if ( viewCcDetails != null ) {
                    LOGGER.info( "Card details currently hidden; requesting security access" );
                    reservationPageScraper.enableSecurityAccess( reservationPage, reservationId );
                }
                // get the full card details
                ccDetails = reservationPageScraper.getCardDetails( reservationPage );
            }
            Payment payment = new Payment( amountToCharge, ccDetails );
            processPxPostTransaction( jobId, bookingRef, payment, false, reservationPage );
            reservationPageScraper.appendNote( reservationPage,
                    message + " --" + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
        catch ( MissingUserDataException ex ) {
            reservationPageScraper.appendNote( reservationPage,
                    ex.getMessage() + " - " + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
    }

    /**
     * Copies the card details from LH (or HWL/AGO/EXP) to CB if it doesn't already exist.
     * 
     * @param cbWebClient web client for cloudbeds
     * @param bookingRef the booking ref to copy
     * @param checkinDate checkin date of this booking
     * @throws IOException on i/o error
     * @throws ParseException on bonehead error
     * @throws Exception on web page load error
     */
    public void copyCardDetailsFromLHtoCB( WebClient cbWebClient, String bookingRef, Date checkinDate ) throws IOException, ParseException, Exception {
        LOGGER.info( "Processing " + bookingRef + " checking in on " + AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );

        // lookup guest in LH
        GuestDetails guest = chromeScraper.retrieveGuestDetailsFromLH( bookingRef, checkinDate );

        // check if payment exists in CB
        Reservation cbReservation = cloudbedsScraper.findBookingByLHBookingRef(
                cbWebClient, bookingRef, checkinDate, guest.getFullName() );
        if ( cbReservation.isCardDetailsPresent() ) {
            LOGGER.info( "Card details found for reservation " + cbReservation.getReservationId() + "; skipping copy" );
            return;
        }

        // extra sanity check that we have the correct booking
        if ( false == guest.getFirstName().equalsIgnoreCase( cbReservation.getFirstName() )
                || false == guest.getLastName().equalsIgnoreCase( cbReservation.getLastName() ) ) {
            throw new UnrecoverableFault( "Mismatch on booking name! Found "
                    + cbReservation.getFirstName() + " " + cbReservation.getLastName()
                    + " when expected " + guest.getFullName() );
        }

        CardDetails ccDetails = guest.getCardDetails();
        boolean isCardDetailsBlank = ccDetails == null;

        // only look up on HWL portal if we don't have it available in LH
        if ( bookingRef.startsWith( "HWL-" ) && isCardDetailsBlank ) {
            LOGGER.info( "Retrieving HWL customer card details" );
            ccDetails = retrieveHWCardDetails( bookingRef );
        }
        else if ( bookingRef.startsWith( "EXP-" ) && isCardDetailsBlank ) {
            LOGGER.info( "Retrieving EXP customer card details" );
            ccDetails = expediaService.returnCardDetailsForBooking( bookingRef ).getCardDetails();
        }
//        else if ( bookingRef.startsWith( "AGO-" ) && isCardDetailsBlank ) {
//            LOGGER.info( "Retrieving AGO customer card details" );
//            // I don't think this actually works anymore but give it a go anyways
//            ccDetails = retrieveAgodaCardDetails( reservationPage, bookingRef );
//        }
        else if ( isCardDetailsBlank ) {
            LOGGER.info( "No card details found for " + bookingRef + " checking in on "
                    + AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
            return;
        }
        LOGGER.info( "Retrieved card: " + new BasicCardMask().applyCardMask( ccDetails.getCardNumber() )
                + " for " + guest.getFullName() );

        // if we're missing the cardholder name; just use the guest name
        if ( StringUtils.isBlank( ccDetails.getName() ) ) {
            ccDetails.setName( guest.getFullName() );
        }
        cloudbedsScraper.addCardDetails( cbWebClient, cbReservation.getReservationId(), ccDetails );
    }

    /**
     * Copies the card details (for HWL/BDC/EXP) to CB if it doesn't already exist.
     * 
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @return the reloaded reservation
     * @throws Exception on failure
     */
    public Reservation copyCardDetailsToCloudbeds( WebClient webClient, String reservationId ) throws Exception {
        return copyCardDetailsToCloudbeds( webClient, cloudbedsScraper.getReservationRetry( webClient, reservationId ) );
    }

    /**
     * Copies the card details (for HWL/BDC/EXP) to CB if it doesn't already exist.
     * 
     * @param webClient web client for cloudbeds
     * @param cbReservation the loaded cloudbeds reservation
     * @return the reloaded reservation
     * @throws Exception on failure
     */
    public Reservation copyCardDetailsToCloudbeds( WebClient webClient, Reservation cbReservation ) throws Exception {
        LOGGER.info( "Copying card details for reservation " + cbReservation.getReservationId() );

        // check if card details exist in CB
        if ( cbReservation.isCardDetailsPresent() ) {
            LOGGER.info( "Card details found; skipping copy" );
            return cbReservation;
        }

        CardDetails ccDetails = null;
        if ( cbReservation.getSourceName().startsWith( "Hostelworld" ) ) {
            String hwlRef = "HWL-" + cbReservation.getThirdPartyIdentifier();
            LOGGER.info( "Retrieving HWL customer card details for " + hwlRef );
            ccDetails = retrieveHWCardDetails( hwlRef );
        }
        else if ( cbReservation.getSourceName().startsWith( "Expedia" ) ) {
            LOGGER.info( "Retrieving EXP customer card details" );
            ccDetails = expediaService.returnCardDetailsForBooking(
                    cbReservation.getThirdPartyIdentifier() ).getCardDetails();
        }
        else if ( "Booking.com".equals( cbReservation.getSourceName() ) ) {
            LOGGER.info( "Retrieving BDC customer card details for BDC#" + cbReservation.getThirdPartyIdentifier() );
            ccDetails = retrieveCardDetailsFromBDC( cbReservation );
        }
        //        else if ( bookingRef.startsWith( "AGO-" ) && isCardDetailsBlank ) {
        //            LOGGER.info( "Retrieving AGO customer card details" );
        //            // I don't think this actually works anymore but give it a go anyways
        //            ccDetails = retrieveAgodaCardDetails( reservationPage, bookingRef );
        //        }
        else {
            throw new UnsupportedOperationException( "Unsupported source " + cbReservation.getSourceName() );
        }
        LOGGER.info( "Retrieved card: " + new BasicCardMask().applyCardMask( ccDetails.getCardNumber() )
                + " for " + ccDetails.getName() );

        // if we're missing the cardholder name; just use the guest name
        if ( StringUtils.isBlank( ccDetails.getName() ) ) {
            ccDetails.setName( cbReservation.getFirstName() + cbReservation.getLastName() );
        }
        cloudbedsScraper.addCardDetails( webClient, cbReservation.getReservationId(), ccDetails );
        return cloudbedsScraper.getReservationRetry( webClient, cbReservation.getReservationId() ); // reload reservation
    }

    private CardDetails retrieveCardDetailsFromBDC( Reservation cbReservation ) throws Exception {
        CardDetails ccDetails;
        WebDriver driver = driverFactory.borrowObject();
        try {
            WebDriverWait wait = new WebDriverWait( driver, 30 );
            ccDetails = bdcScraper.returnCardDetailsForBooking( driver, wait, cbReservation.getThirdPartyIdentifier() );
        }
        catch ( Exception ex ) {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs( OutputType.FILE );
            String filename = "logs/bdc_" + cbReservation.getThirdPartyIdentifier() + "-" + System.currentTimeMillis() + ".png";
            FileUtils.copyFile( scrFile, new File( filename ) );
            LOGGER.info( driver.getPageSource() );
            LOGGER.error( "Screenshot saved in " + filename );
            throw ex;
        }
        finally {
            // we can't have more than one window open... especially if that window
            // contains credit card details (we don't want to mix it up with another booking)
            if ( driver.getWindowHandles().size() > 1 ) {
                driverFactory.invalidateObject( driver );
            }
            else {
                driverFactory.returnObject( driver );
            }
        }
        return ccDetails;
    }

    /**
     * Does a AUTHORIZE/CAPTURE on the card details on the booking for the balance remaining if the
     * Rate Plan is "Non-refundable".
     * 
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @throws Exception on any kind of error
     */
    public void chargeNonRefundableBooking( WebClient webClient, String reservationId ) throws Exception {
        LOGGER.info( "Processing charge of non-refundable booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        LOGGER.info( cbReservation.getThirdPartyIdentifier() + ": "
                + cbReservation.getFirstName() + " " + cbReservation.getLastName() );
        LOGGER.info( "Status: " + cbReservation.getStatus() );
        LOGGER.info( "Checkin: " + cbReservation.getCheckinDate() );
        LOGGER.info( "Checkout: " + cbReservation.getCheckoutDate() );
        LOGGER.info( "Grand Total: " + cbReservation.getGrandTotal() );
        LOGGER.info( "Balance Due: " + cbReservation.getBalanceDue() );

        // check if we have anything to pay
        if ( cbReservation.isPaid() ) {
            LOGGER.info( "Booking is paid. Nothing to do." );
            return;
        }

        if ( false == cbReservation.isNonRefundable() ) {
            throw new UnrecoverableFault( "ABORT! Attempting to charge a non non-refundable booking!" );
        }
        // group bookings must be approved/charged manually
        else if ( cbReservation.getNumberOfGuests() >= wordpressDAO.getGroupBookingSize() ) {
            LOGGER.info( "Reservation " + reservationId + " has " + cbReservation.getNumberOfGuests() + " guests. Payment must be done manually for groups." );
            return;
        }

        // check if card details exist in CB; copy over if req'd
        if ( false == cbReservation.isCardDetailsPresent() ) {
            final String NOTE = "Missing card details found for reservation " + cbReservation.getReservationId();

            if ( false == cbReservation.containsNote( NOTE ) ) {
                cloudbedsScraper.addNote( webClient, reservationId, NOTE );
            }

            cbReservation = copyCardDetailsToCloudbeds( webClient, cbReservation );

            // if still not available, something has gone wrong somewhere
            if ( false == cbReservation.isCardDetailsPresent() ) {
                throw new MissingUserDataException( "Missing card details found for reservation " + cbReservation.getReservationId() + ". Unable to continue." );
            }
        }

        try {
            // should have credit card details at this point; attempt autocharge
            cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, cbReservation.getBalanceDue() );
            cloudbedsScraper.addNote( webClient, reservationId, "Outstanding balance of "
                    + cloudbedsScraper.getCurrencyFormat().format( cbReservation.getBalanceDue() ) + " successfully charged." );

            // mark booking as fully paid in Hostelworld
            if ( "Hostelworld & Hostelbookers".equals( cbReservation.getSourceName() ) ) {
                try (WebClient hwlWebClient = context.getBean( "webClientForHostelworld", WebClient.class )) {
                    hostelworldScraper.acknowledgeFullPaymentTaken( hwlWebClient, cbReservation.getThirdPartyIdentifier() );
                }
                catch ( Exception ex ) {
                    LOGGER.error( "Failed to acknowledge payment in HWL. Meh...", ex );
                }
            }

            // send email if successful
            SendNonRefundableSuccessfulEmailJob job = new SendNonRefundableSuccessfulEmailJob();
            job.setReservationId( reservationId );
            job.setAmount( cbReservation.getBalanceDue() );
            job.setStatus( JobStatus.submitted );
            wordpressDAO.insertJob( job );
        }
        catch ( PaymentNotAuthorizedException payEx ) {
            LOGGER.info( "Unable to process payment: " + payEx.getMessage() );

            if ( cloudbedsScraper.getEmailLastSentDate( webClient, reservationId,
                    CloudbedsScraper.TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED ).isPresent() ||
                    cbReservation.containsNote( CloudbedsScraper.TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED ) ) {
                LOGGER.info( "Declined payment email already sent. Not going to do it again..." );
            }
            else {
                LOGGER.info( "Sending declined payment email" );
                String paymentURL = generateUniquePaymentURL( reservationId, null );

                SendNonRefundableDeclinedEmailJob job = new SendNonRefundableDeclinedEmailJob();
                job.setReservationId( reservationId );
                job.setAmount( cbReservation.getBalanceDue() );
                job.setPaymentURL( paymentURL );
                job.setStatus( JobStatus.submitted );
                wordpressDAO.insertJob( job );
            }
        }
    }

    /**
     * Creates a new payment URL for the given reservation.
     * 
     * @param reservationId unique Cloudbeds reservation ID
     * @param paymentRequested (optional) payment requested
     * @return payment URL
     */
    private String generateUniquePaymentURL( String reservationId, BigDecimal paymentRequested ) {
        String lookupKey = generateRandomLookupKey( LOOKUPKEY_LENGTH );
        String paymentURL = wordpressDAO.getBookingPaymentsURL() + lookupKey;
        wordpressDAO.insertBookingLookupKey( reservationId, lookupKey, paymentRequested );
        return paymentURL;
    }

    /**
     * Returns a random lookup key with the given length.
     * 
     * @param keyLength length of lookup key
     * @return string generated key
     */
    private String generateRandomLookupKey( int keyLength ) {
        StringBuffer result = new StringBuffer();
        Random r = new Random();
        for ( int i = 0 ; i < keyLength ; i++ ) {
            result.append( LOOKUPKEY_CHARSET.charAt( r.nextInt( LOOKUPKEY_CHARSET.length() ) ) );
        }
        return result.toString();
    }

    /**
     * Does a AUTHORIZE/CAPTURE on the card details on the prepaid BDC booking.
     * 
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @throws Exception
     */
    public synchronized void processPrepaidBooking( WebClient webClient, String reservationId ) throws Exception {
        LOGGER.info( "Processing full payment for booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        LOGGER.info( cbReservation.getThirdPartyIdentifier() + ": "
                + cbReservation.getFirstName() + " " + cbReservation.getLastName() );
        LOGGER.info( "Source: " + cbReservation.getSourceName() );
        LOGGER.info( "Status: " + cbReservation.getStatus() );
        LOGGER.info( "Checkin: " + cbReservation.getCheckinDate() );
        LOGGER.info( "Checkout: " + cbReservation.getCheckoutDate() );
        LOGGER.info( "Grand Total: " + cbReservation.getGrandTotal() );
        LOGGER.info( "Balance Due: " + cbReservation.getBalanceDue() );

        // check if we have anything to pay
        if ( cbReservation.isPaid() ) {
            LOGGER.warn( "Booking is paid! Attempting to charge VCC anyways..." );
        }

        if ( false == cbReservation.isCardDetailsPresent() ) {
            cbReservation = copyCardDetailsToCloudbeds( webClient, cbReservation );
        }
        if ( false == "Booking.com".equals( cbReservation.getSourceName() ) ) {
            throw new MissingUserDataException( "Unsupported source " + cbReservation.getSourceName() );
        }
        if ( false == cbReservation.isPrepaid() ) {
            throw new MissingUserDataException( "Booking not identified as a prepaid booking" );
        }

        // should have credit card details at this point; attempt AUTHORIZE/CAPTURE
        // if we have BDC login details, try to get the VCC balance to charge
        if ( wordpressDAO.getOption( "hbo_bdc_username" ) != null ) {
            if ( cbReservation.containsNote( "VCC has been charged for the full amount" ) ) {
                LOGGER.info( "I think this has already been charged... Nothing to do." );
                return;
            }
            LOGGER.info( "Looks like a prepaid card... Looking up actual value to charge on BDC" );
            WebDriver driver = driverFactory.borrowObject();
            try {
                WebDriverWait wait = new WebDriverWait( driver, 60 );
                final BigDecimal amountToCharge = bdcScraper.getVirtualCardBalance( driver, wait, cbReservation.getThirdPartyIdentifier() );
                final BigDecimal MINIMUM_CHARGE_AMOUNT = new BigDecimal( "0.3" );
                final String MODIFIED_OUTSIDE_OF_BDC = "IMPORTANT: The PREPAID booking seems to have been modified outside of BDC. VCC has been charged for the full amount so any outstanding balance should be PAID BY THE GUEST ON ARRIVAL.";
                LOGGER.info( "VCC balance is " + cloudbedsScraper.getCurrencyFormat().format( amountToCharge ) + "." );
                if ( amountToCharge.compareTo( MINIMUM_CHARGE_AMOUNT ) > 0 ) {
                    cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, amountToCharge );

                    if ( 0 != cbReservation.getBalanceDue().compareTo( amountToCharge )
                            && false == "canceled".equals( cbReservation.getStatus() ) ) {
                        cloudbedsScraper.addNote( webClient, reservationId, MODIFIED_OUTSIDE_OF_BDC );
                    }
                }
                else {
                    final String MINIMUM_CHARGE_NOTE = "Minimum charge amount not reached. Remaining balance not charged.";
                    if ( cbReservation.getBalanceDue().compareTo( MINIMUM_CHARGE_AMOUNT ) > 0
                            && false == "canceled".equals( cbReservation.getStatus() ) ) {
                        cloudbedsScraper.addNote( webClient, reservationId, MODIFIED_OUTSIDE_OF_BDC );
                    }
                    else if ( cbReservation.containsNote( MINIMUM_CHARGE_NOTE ) ) {
                        LOGGER.info( MINIMUM_CHARGE_NOTE );
                    }
                    else {
                        cloudbedsScraper.addNote( webClient, reservationId, MINIMUM_CHARGE_NOTE );
                    }
                }
            }
            finally {
                driverFactory.returnObject( driver );
            }
        }
        else {
            cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, cbReservation.getBalanceDue() );
        }
    }

    /**
     * Does a AUTHORIZE/CAPTURE on the card details on the booking for the amount of the first
     * night.
     * 
     * @param webClient web client for cloudbeds
     * @param reservationId the unique cloudbeds reservation ID
     * @param amount amount to charge
     * @throws IOException on i/o error
     */
    public synchronized void processHostelworldLateCancellationCharge( WebClient webClient, String reservationId ) throws IOException {
        LOGGER.info( "Processing payment for 1st night of booking: " + reservationId );
        Reservation cbReservation = cloudbedsScraper.getReservationRetry( webClient, reservationId );

        LOGGER.info( cbReservation.getThirdPartyIdentifier() + ": "
                + cbReservation.getFirstName() + " " + cbReservation.getLastName() );
        LOGGER.info( "Status: " + cbReservation.getStatus() );
        LOGGER.info( "Checkin: " + cbReservation.getCheckinDate() );
        LOGGER.info( "Checkout: " + cbReservation.getCheckoutDate() );
        LOGGER.info( "Grand Total: " + cbReservation.getGrandTotal() );
        LOGGER.info( "Balance Due: " + cbReservation.getBalanceDue() );

        // check if we've paid anything
        if ( false == BigDecimal.ZERO.equals( cbReservation.getPaidValue() ) ) {
            LOGGER.info( "Booking has already been charged " + cbReservation.getPaidValue() );
            return;
        }

        if ( false == "canceled".equals( cbReservation.getStatus() ) ) {
            throw new UnrecoverableFault( "Only bookings at canceled can be charged." );
        }

        // check if card details exist in CB
        if ( false == cbReservation.isCardDetailsPresent() ) {
            throw new MissingUserDataException( "Missing card details found for reservation " + cbReservation.getReservationId() + ". Unable to continue." );
        }

        // should have credit card details at this point; attempt AUTHORIZE/CAPTURE
        BigDecimal amountToCharge = getLateCancellationAmountToCharge( cbReservation );
        cloudbedsScraper.chargeCardForBooking( webClient, cbReservation, amountToCharge );
        cloudbedsScraper.addNote( webClient, reservationId, "Late cancellation. Successfully charged £"
                + cloudbedsScraper.getCurrencyFormat().format( amountToCharge ) );

        // send email if successful
        SendHostelworldLateCancellationEmailJob job = new SendHostelworldLateCancellationEmailJob();
        job.setReservationId( reservationId );
        job.setAmount( amountToCharge );
        job.setStatus( JobStatus.submitted );
        wordpressDAO.insertJob( job );
    }

    /**
     * Returns the late cancellation amount to charge for a booking.
     * 
     * @param cbReservation reservation being checked
     * @return value greater than 0
     */
    private BigDecimal getLateCancellationAmountToCharge( Reservation cbReservation ) {

        // august for HSH/RMB - charge the full amount
        if ( cbReservation.isCheckinDateInAugust() && false == wordpressDAO.getOption( "siteurl" ).contains( "castlerock" ) ) {
            if ( cbReservation.getBalanceDue().compareTo( BigDecimal.ZERO ) <= 0 ) {
                throw new IllegalStateException( "Some weirdness here. Outstanding balance must be greater than 0." );
            }
            return cbReservation.getBalanceDue();
        }

        // otherwise, for non-August or CRH (incl. August) - just the first night
        BigDecimal firstNightAmount = cbReservation.getRateFirstNight( gson );
        LOGGER.info( "First night due: " + firstNightAmount );
        if ( firstNightAmount.compareTo( BigDecimal.ZERO ) <= 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount must be greater than 0." );
        }
        if ( firstNightAmount.compareTo( cbReservation.getBalanceDue() ) > 0 ) {
            throw new IllegalStateException( "Some weirdness here. First night amount exceeds balance due." );
        }
        return firstNightAmount;
    }

    /**
     * Does the nitty-gritty of posting the transaction, or recovering if we've already attempted to
     * do a post, updates the payment details in LH or leaves a note on the booking if the
     * transaction was not approved. If we've already successfully charged this {@code bookingRef},
     * then this method won't do anything.
     * 
     * @param jobId current job id
     * @param bookingRef booking ref
     * @param payment amount being charged and card details
     * @param isDeposit tick the deposit checkbox on LH if this is true
     * @param reservationPage reservation page
     * @throws IOException on I/O error
     */
    private void processPxPostTransaction( int jobId, String bookingRef,
            Payment payment, boolean isDeposit, HtmlPage reservationPage ) throws IOException {

        // first, ensure LH and our PxPost table are in sync
        syncLastPxPostTransactionInLH( bookingRef, isDeposit, reservationPage );

        // lookup transaction id(s) on payment table from booking ref
        PxPostTransaction pxpost = wordpressDAO.getLastPxPost( bookingRef );

        // if this is the first time we're charging this booking or last attempt failed...
        if ( pxpost == null || Boolean.FALSE.equals( pxpost.getSuccessful() ) ) {

            if ( pxpost == null ) {
                LOGGER.info( "We haven't charged this booking yet" );
            }
            else {
                // abort if we have reached the maximum number of attempts
                // unless checkin date is today/tomorrow (in which case, try again)
                Calendar tomorrow = Calendar.getInstance();
                tomorrow.add( Calendar.DATE, 1 );
                LOGGER.info( "Last payment attempt failed" );
                if ( wordpressDAO.getPreviousNumberOfFailedTxns(
                        pxpost.getBookingReference(),
                        pxpost.getMaskedCardNumber() ) >= MAX_PAYMENT_ATTEMPTS
                        && reservationPageScraper.getCheckinDate( reservationPage ).after( tomorrow.getTime() ) ) {

                    LOGGER.info( "Max number of payment attempts reached. Aborting." );
                    return;
                }
            }

            int txnId = wordpressDAO.insertNewPxPostTransaction( jobId, bookingRef, payment.getAmount() );
            processTransaction( reservationPageScraper, reservationPage, txnId, bookingRef,
                    payment.getCardDetails(), payment.getAmount(), isDeposit );
        }
        // we actually did get a successful payment thru
        else if ( Boolean.TRUE.equals( pxpost.getSuccessful() ) ) {
            LOGGER.info( "Previous PX Post transaction was successful; nothing to do" );
        }
        else { // pxpost.getSuccessful() == null
               // which should never happen as this should've been updated in syncLastPxPostTransactionInLH
            throw new UnrecoverableFault( "Unexpected result in PxPost id: " + pxpost.getId() );
        }
    }

    /**
     * Similar to {@link #processPxPostTransaction(int, String, int, Payment, boolean, HtmlPage)}
     * but specifically for handling bookings which allow for multiple authorizations (possibly,
     * once for the first night and a secondary auth for the remainder). This method will always
     * initiate a new transaction for the given payment amount.
     * 
     * @param jobId current job id
     * @param bookingRef booking ref
     * @param payment amount being charged and card details
     * @param isDeposit tick the deposit checkbox on LH if this is true
     * @param reservationPage reservation page
     * @throws IOException on I/O error
     */
    private void processNewPxPostTransaction( int jobId, String bookingRef, Payment payment,
            boolean isDeposit, HtmlPage reservationPage ) throws IOException {

        // abort if we have reached the maximum number of attempts
        if ( wordpressDAO.getPreviousNumberOfFailedTxns( bookingRef, new PxPostCardMask()
                .replaceCardWith( payment.getCardDetails().getCardNumber() ) ) >= MAX_PAYMENT_ATTEMPTS ) {
            LOGGER.info( "Max number of payment attempts reached. Aborting." );
        }
        else {
            int txnId = wordpressDAO.insertNewPxPostTransaction( jobId, bookingRef, payment.getAmount() );
            processTransaction( reservationPageScraper, reservationPage, txnId, bookingRef,
                    payment.getCardDetails(), payment.getAmount(), isDeposit );
        }
    }

    /**
     * Makes sure that the last PxPost transaction for the given booking reference. This method does
     * not process any new/existing transactions; it only updates the PxPost table (with a
     * previously processed transaction) and makes sure LH is up-to-date with this table.
     * 
     * @param bookingRef booking ref
     * @param isDeposit tick the deposit checkbox on LH if this is true
     * @param reservationPage reservation page
     * @throws IOException on I/O error
     */
    public void syncLastPxPostTransactionInLH( String bookingRef, boolean isDeposit,
            HtmlPage reservationPage ) throws IOException {

        // lookup the last transaction id(s) on payment table from booking ref
        PxPostTransaction pxpost = wordpressDAO.getLastPxPost( bookingRef );

        if ( pxpost == null ) {
            LOGGER.info( "We haven't charged this booking yet" );
        }
        else if ( Boolean.FALSE.equals( pxpost.getSuccessful() ) ) {
            LOGGER.info( "Last payment attempt failed" );
        }
        // we actually did get a successful payment thru so check if it has been logged in LH
        else if ( Boolean.TRUE.equals( pxpost.getSuccessful() ) ) {

            LOGGER.info( "Last transaction was successful; checking if payment was logged in LH" );
            List<?> paymentDivs = ListUtils.union(
                    reservationPage.getByXPath( "//div[@class='payment-row']/div[@class='row']/div" ),
                    reservationPage.getByXPath( "//div[@class='payment-label-bottom']" ) );
            if ( paymentDivs.stream().anyMatch( p -> HtmlDivision.class.cast( p ).getTextContent()
                    .contains( "PxPost transaction " + pxpost.getId() + " successful" ) ) ) {
                LOGGER.info( "Previous transaction was recorded in LH. Ok to continue." );
            }
            else {
                LOGGER.info( "Previous transaction was *NOT* recorded in LH. Updating now." );
                reservationPageScraper.addPayment( reservationPage, pxpost.getPaymentAmount(), "Other", isDeposit,
                        "PxPost transaction " + pxpost.getId() + " successful. - "
                                + DATETIME_FORMAT.format( pxpost.getCreatedDate() ) );
            }
        }
        else { // pxpost.isSuccessful() == null
               // not sure if the last payment went through...
            LOGGER.info( "Not sure if the last payment went through; requesting status of txn" );

            // first get px status (just in case we timed-out last time); verify not yet paid
            CaptureHttpResponse statusResponse = new CaptureHttpResponse();
            TxnResponse postStatus = pxPostService.getStatus( bookingRef, statusResponse );

            // update the pxpost record
            wordpressDAO.updatePxPostStatus( pxpost.getId(),
                    postStatus.getTransaction() == null ? null : postStatus.getTransaction().getCardNumber(),
                    postStatus.isSuccessful(), statusResponse.getBody() );

            if ( postStatus.isSuccessful() ) {
                LOGGER.info( "Last transaction was successful, updating payment details in LH" );
                reservationPageScraper.addPayment( reservationPage,
                        new BigDecimal( postStatus.getTransaction().getAmount() ),
                        postStatus.getTransaction().getCardName(), isDeposit,
                        "PxPost transaction " + pxpost.getId() + " successful. - " + DATETIME_FORMAT.format( new Date() ) );
            }
            else {
                LOGGER.info( "Last transaction wasn't successful; not updating payment info in LH." );
            }
        }
    }

    /**
     * Retrieve full card details for the given booking.
     * 
     * @param bookingRef booking ref, e.g. BDC-12345678
     * @param reservationId LH id for this reservation
     * @param reservationPage the HtmlPage of the current reservation
     * @return full card details (not-null)
     * @throws IOException on page load error
     * @throws MissingUserDataException if unable to retrieve card details
     */
    private Payment retrieveCardDetails( String bookingRef, int reservationId,
            HtmlPage reservationPage ) throws IOException, MissingUserDataException {

        // Booking.com
        if ( bookingRef.startsWith( "BDC-" ) ) {

            // quick check if it's AMEX (not supported)
            HtmlItalic cardSpan = HtmlItalic.class.cast( reservationPage.getFirstByXPath( "//span[@class='card-logo']/i" ) );
            if ( cardSpan != null && cardSpan.getAttribute( "class" ).contains( "fa-cc-amex" ) ) {
                throw new MissingUserDataException( "Amex not enabled. Charge manually using EFTPOS terminal." );
            }

            LOGGER.info( "Retrieving customer card details" );
            CardDetails ccDetails = reservationPageScraper.getCardDetails( reservationPage );

            try {
                LOGGER.info( "Retrieving card security code" );
                CardDetails ccDetailsFromGmail = gmailService.fetchBdcCardDetailsFromBookingRef( bookingRef );
                ccDetails.setExpiry( ccDetailsFromGmail.getExpiry() ); // copying over expiry
                ccDetails.setCvv( ccDetailsFromGmail.getCvv() ); // copying over security code
                ccDetails.setCardType( ccDetailsFromGmail.getCardType() );
            }
            catch ( MissingUserDataException ex ) {
                LOGGER.warn( "Unable to retrieve card security code. Proceeding without it...", ex );
            }

            // AMEX cards not supported
            if ( "AX".equals( ccDetails.getCardType() ) ) {
                throw new MissingUserDataException( "Amex not enabled. Charge manually using EFTPOS terminal." );
            }
            Payment depositCharge = new Payment( ccDetails );
            updatePaymentWithBdcDepositChargeAmount( depositCharge, reservationPage );

            // on occasion, this is not the correct CVC for virtual payments
            if ( depositCharge.isVirtual() && "000".equals( depositCharge.getCardDetails().getCvv() ) ) {
                LOGGER.warn( "CVC of 000 found. This is probably a fuck-up on BDC. Blanking out CVC." );
                depositCharge.getCardDetails().setCvv( null );
            }
            return depositCharge;

        }
        // Expedia
        else if ( bookingRef.startsWith( "EXP-" ) ) {
            return expediaService.returnCardDetailsForBooking( bookingRef );
        }
        else {
            throw new IllegalStateException( "Attempting to retrieve unsupported booking! " + bookingRef );
        }
    }

    /**
     * Retrieve card details for the given HW booking.
     * 
     * @param bookingRef e.g. HWL-555-123485758
     * @return full card details (not-null); amount is null
     * @throws IOException on page load error
     * @throws ParseException on parse error
     * @throws MissingUserDataException if card details not found
     */
    private CardDetails retrieveHWCardDetails( String bookingRef ) throws ParseException, IOException {
        try (WebClient hwlWebClient = context.getBean( "webClientForHostelworld", WebClient.class )) {
            return hostelworldScraper.getCardDetails( hwlWebClient, bookingRef );
        }
    }

    /**
     * Send through a PX Post transaction for this booking. Update LH payment details on success or
     * add note to notes section on failure.
     * 
     * @param reservationPageScraper scraper for reservations page
     * @param reservationPage the current reservation
     * @param txnId our unique transaction ID
     * @param bookingRef the booking ref (merchant reference in px post)
     * @param ccDetails customer card details
     * @param amountToPay amount being charged
     * @param isDeposit tick the deposit checkbox on LH if this is true
     * @throws IOException on I/O error
     */
    private void processTransaction( ReservationPageScraper reservationPageScraper, HtmlPage reservationPage,
            int txnId, String bookingRef, CardDetails ccDetails, BigDecimal amountToPay, boolean isDeposit ) throws IOException {
        LOGGER.info( "Processing transaction " + bookingRef + " for £" + amountToPay );

        // send through the payment
        CaptureHttpRequest paymentRequest = new CaptureHttpRequest();
        CaptureHttpResponse paymentResponse = new CaptureHttpResponse();
        TxnResponse paymentTxn = pxPostService.processPayment( String.valueOf( txnId ),
                bookingRef, ccDetails, amountToPay, paymentRequest, paymentResponse );

        // update our records
        LOGGER.info( "PX Post complete; updating our records." );
        wordpressDAO.updatePxPostTransaction( txnId,
                paymentTxn.getTransaction().getCardNumber(),
                paymentRequest.getBody(),
                paymentResponse.getStatus().value(),
                paymentResponse.getBody(),
                paymentTxn.isSuccessful(),
                paymentTxn.getHelpText() );

        if ( paymentTxn.isSuccessful() ) {
            LOGGER.info( "PX Post was successful, updating LH payment details" );
            reservationPageScraper.addPayment( reservationPage, amountToPay,
                    paymentTxn.getTransaction().getCardName(), isDeposit,
                    "PxPost transaction " + txnId + " successful. "
                            + "DpsTxnRef: " + paymentTxn.getTransaction().getDpsTxnRef()
                            + " - " + DATETIME_FORMAT.format( new Date() ) );
        }
        else {
            LOGGER.info( "PX Post was NOT successful; updating LH notes" );
            reservationPageScraper.appendNote( reservationPage,
                    "~~~ " + DATETIME_FORMAT.format( new Date() ) + "\n"
                            + "Transaction ID: " + txnId + "\n"
                            + "DpsTxnRef: " + paymentTxn.getTransaction().getDpsTxnRef() + "\n"
                            + "Card: " + paymentTxn.getTransaction().getCardNumber() + "\n"
                            + "Response Code (" + paymentTxn.getResponseCode() + "): "
                            + StringUtils.trimToEmpty( paymentTxn.getResponseCodeText() ) + "\n"
                            + "Details: " + paymentTxn.getHelpText() + "\n"
                            + "~~~\n" );
        }

    }

    /**
     * Counts the number of guests.
     * 
     * @param reservationPage the current reservation
     * @return number of guests
     */
    private int countNumberOfGuests( HtmlPage reservationPage ) {
        List<?> guests = reservationPage.getByXPath( "//input[@id='number_adults']" );
        int numberGuests = 0;
        for ( Object elem : guests ) {
            HtmlInput guestInput = HtmlInput.class.cast( elem );
            numberGuests += Integer.parseInt( guestInput.getAttribute( "value" ) );
        }
        LOGGER.info( "found " + numberGuests + " guests" );
        return numberGuests;
    }

    /**
     * Retrieves the deposit amount to charge for the given BDC reservation and updates the amount
     * on the given Payment object.
     * 
     * @param reservationPage the HtmlPage of the current reservation
     * @return deposit amount to charge reservation
     */
    public void updatePaymentWithBdcDepositChargeAmount( Payment payment, HtmlPage reservationPage ) {

        // calculate how much we need to change
        HtmlSpan totalOutstanding = reservationPage.getFirstByXPath( "//span[contains(@class,'total_outstanding')]" );
        BigDecimal amountOutstanding = new BigDecimal( totalOutstanding.getTextContent().replaceAll( "£", "" ) );
        HtmlTextArea guestComments = reservationPage.getFirstByXPath( "//textarea[@id='guest_comments']" );

        // guest can pay for everything up-front; in which case we charge that amount
        if ( StringUtils.trimToEmpty( guestComments.getText() ).contains(
                "You have received a virtual credit card for this reservation" ) ||
                StringUtils.trimToEmpty( guestComments.getText() ).contains(
                        "THIS RESERVATION HAS BEEN PRE-PAID" ) ) {
            Pattern p = Pattern.compile( "The amount the guest prepaid to Booking\\.com is GBP ([0-9]+\\.[0-9]{2})" );
            Matcher m = p.matcher( guestComments.getText() );
            if ( m.find() ) {
                // some weirdness where this may exceed the outstanding amount??
                payment.setAmount( new BigDecimal( m.group( 1 ) ) );
                payment.setVirtual( true );
            }
            else {
                throw new MissingUserDataException( "Missing prepaid amount in guest comments when attempting to charge deposit." );
            }
        }
        // either take first night, or a percentage amount
        else if ( "first_night".equals( bdcDepositStrategy ) ) {
            payment.setAmount( reservationPageScraper.getAmountPayableForFirstNight( reservationPage ) );
        }
        else {
            BigDecimal percentToCharge = new BigDecimal( bdcDepositStrategy );
            payment.setAmount( amountOutstanding.multiply( percentToCharge ).setScale( 2, RoundingMode.HALF_UP ) );
        }

        // extra sanity checks
        if ( payment.getAmount().compareTo( BigDecimal.ZERO ) <= 0 ) {
            throw new IllegalStateException( "Amount payable is £" + payment.getAmount() + "???" );
        }
        else if ( false == payment.isVirtual() && payment.getAmount().compareTo( amountOutstanding ) > 0 ) {
            throw new IllegalStateException( "Amount to pay " + payment.getAmount() +
                    " exceeds amount outstanding of " + amountOutstanding + "???" );
        }
    }

    /**
     * Calculates amount to charge and returns it. If amount has been paid or not applicable, then
     * this returns 0.
     * 
     * @param reservationPage loaded agoda booking page in LH
     * @return non-null amount to charge
     */
    private BigDecimal getAgodaChargeAmount( HtmlPage reservationPage ) {

        // calculate the amount we need to charge:
        // 1) if booking is checked-in, we need to charge the total amount outstanding
        // 2) if booking is confirmed (not checked-in), we need to charge the first night
        // In both these cases, the reservation should be in the past (but we don't check this here)

        if ( reservationPage.getFirstByXPath( "//span[@class='checked-in-status' or @class='checked-out-status']" ) != null ) {
            HtmlSpan outstandingTotalSpan = reservationPage.getFirstByXPath( "//span[contains(@class,'total_outstanding')]" );
            return new BigDecimal( outstandingTotalSpan.getTextContent().replaceAll( POUND, "" ) );
        }

        // charge first night (less anything already paid)
        if ( reservationPage.getFirstByXPath( "//span[@class='confirmed-status']" ) != null ) {
            HtmlSpan totalRecievedSpan = reservationPage.getFirstByXPath( "//span[contains(@class,'total_received')]" );
            BigDecimal totalReceived = new BigDecimal( totalRecievedSpan.getTextContent().replaceAll( POUND, "" ) );
            BigDecimal amountFirstNight = reservationPageScraper.getAmountPayableForFirstNight( reservationPage );
            return amountFirstNight.subtract( totalReceived ).max( BigDecimal.ZERO );
        }
        HtmlLabel label = reservationPage.getFirstByXPath( "//div[@class='rrd-info-panel']/label" );
        LOGGER.info( "Booking is at " + label.getTextContent() + "; There is nothing payable." );

        return BigDecimal.ZERO;
    }

    /**
     * Retrieves the card details for the given Agoda reservation.
     * 
     * @param reservationPage the HtmlPage of the current reservation
     * @return agoda card details
     * @throws IOException on I/O error processing webpage
     */
    public CardDetails retrieveAgodaCardDetails( HtmlPage reservationPage, String bookingRef ) throws IOException {
        try (WebClient agodaWebClient = context.getBean( "webClient", WebClient.class )) {
            return agodaScraper.getAgodaCardDetails( agodaWebClient, bookingRef.substring( "AGO-".length() ) );
        }
    }

    /**
     * Checks whether the given BDC reservation is within the cancellation grace period.
     * 
     * @param reservationPage pre-loaded reservation page
     * @return true if within grace period, false otherwise
     * @throws IOException on parse error
     */
    private boolean isWithinCancellationGracePeriod( HtmlPage reservationPage ) throws IOException {
        HtmlTextArea guestComments = reservationPage.getFirstByXPath( "//textarea[@id='guest_comments']" );
        Pattern p = Pattern.compile( "Reservation has a cancellation grace period. Do not charge if cancelled before (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})" );
        Matcher m = p.matcher( guestComments.getText() );
        if ( m.find() ) {
            try {
                Date earliestChargeDate = DATETIME_STANDARD.parse( m.group( 1 ) );
                return earliestChargeDate.after( new Date() );
            }
            catch ( ParseException e ) {
                throw new IOException( e );
            }
        }
        return false;
    }

    /**
     * Processes a refund (on Cloudbeds) if card is still active. Otherwise, process refund on
     * Stripe and records it against the given Cloudbeds booking.
     * 
     * @param jobId job id (for idempotency)
     * @param refundTxnId primary key on StripeRefund
     * @throws IOException
     * @throws StripeException
     * @throws RecordPaymentFailedException
     */
    public void processStripeRefund( int jobId, int refundTxnId ) throws IOException, StripeException, RecordPaymentFailedException {
        try (WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class )) {
            StripeRefund refund = wordpressDAO.fetchStripeRefund( refundTxnId );
            String refundAmount = cloudbedsScraper.getCurrencyFormat().format( refund.getAmount() );
            LOGGER.info( "Attempting to process refund for reservation " + refund.getReservationId() + " and txn " + refundTxnId + " for £" + refundAmount );
            Reservation res = cloudbedsScraper.getReservationRetry( webClient, refund.getReservationId() );
            TransactionRecord authTxn = cloudbedsScraper.getStripeTransaction( webClient, res, refund.getCloudbedsTxId() );

            // temporarily adding a optional flag to test how "pending" refunds work
            // (whether they get automatically moved on in cloudbeds or not)
            // "pending" refunds don't get a record in the folio page (will this get updated??)
            // BDC examples where refunds are pending done thru cloudbeds: 3243495048, 3854720449
            if ( "true".equals( wordpressDAO.getOption( "force_stripe_refund_via_cloudbeds" ) ) ) {
                LOGGER.info( "Attempting to process refund for transaction " + authTxn.getId() );
                String jsonResponse = cloudbedsScraper.performRefund( webClient, authTxn, refund.getAmount() );
                if ( StringUtils.isNotBlank( jsonResponse ) ) {
                    CloudbedsJsonResponse response = cloudbedsScraper.fromJson( jsonResponse, CloudbedsJsonResponse.class );
                    wordpressDAO.updateStripeRefund( refundTxnId, authTxn.getGatewayAuthorization(), jsonResponse, response.isSuccess() ? "succeeded" : "failed" );
                    if ( response.isFailure() ) {
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                                " for £" + refundAmount + ". See logs for details." );
                    }
                    else {
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(),
                                "Refund completed for £" + refundAmount + "."
                                        + (refund.getDescription() == null ? "" : " " + refund.getDescription()) );
    
                        // send email if successful
                        createSendRefundSuccessfulEmailJob( refund );
                    }
                }
            }
            // if the transaction we are refunding matches the *active* CC we have on file on Cloudbeds
            // then process the refund via Cloudbeds
            else if ( res.getCreditCardId() != null && res.getCreditCardId().equals( authTxn.getCreditCardId() ) ) {
                LOGGER.info( "Attempting to process refund for transaction " + authTxn.getId() );
                String jsonResponse = cloudbedsScraper.processRefund( webClient, res, authTxn, refund.getAmount(), refund.getDescription() );
                if ( StringUtils.isNotBlank( jsonResponse ) ) {
                    CloudbedsJsonResponse response = cloudbedsScraper.fromJson( jsonResponse, CloudbedsJsonResponse.class );
                    wordpressDAO.updateStripeRefund( refundTxnId, authTxn.getGatewayAuthorization(), jsonResponse, response.isSuccess() ? "succeeded" : "failed" );
                    if ( response.isFailure() ) {
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                                " for £" + refundAmount + ". See logs for details." );
                    }
                    else {
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(),
                                "Refund completed for £" + refundAmount + "."
                                        + (refund.getDescription() == null ? "" : " " + refund.getDescription()) );
    
                        // send email if successful
                        createSendRefundSuccessfulEmailJob( refund );
                    }
                }
                else {
                    throw new RecordPaymentFailedException( "Blank response from Cloudbeds?" );
                }
            }
            else if ( StringUtils.isBlank( authTxn.getGatewayAuthorization() ) ) {
                cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                        " for £" + refundAmount + ". Unable to find original transaction to refund." );
                throw new MissingUserDataException( "Unable to find original transaction to refund." );
            }

            else { // we don't have the card available in Cloudbeds; call Stripe server and record result manually
                String idempotentKey = wordpressDAO.getMandatoryOption( "hbo_sagepay_vendor_prefix" )
                        + res.getIdentifier() + "-RF-" + jobId;
                LOGGER.info( "Attempting to process refund for charge " + authTxn.getGatewayAuthorization() );
                Stripe.apiKey = STRIPE_API_KEY;
                try {
                    Refund stripRefund = Refund.create( RefundCreateParams.builder()
                            .setCharge( authTxn.getGatewayAuthorization() )
                            .setAmount( refund.getAmountInBaseUnits() ).build(),
                            // set idempotency key so we can re-run safely in case of previous failure
                            new RequestOptionsBuilder().setIdempotencyKey( idempotentKey ).build() );
                    LOGGER.info( "Response: " + stripRefund.toJson() );
                    wordpressDAO.updateStripeRefund( refundTxnId, authTxn.getGatewayAuthorization(), stripRefund.toJson(), stripRefund.getStatus() );

                    // record refund in Cloudbeds so we're in sync
                    if ( "succeeded".equals( stripRefund.getStatus() ) ) {
                        cloudbedsScraper.addRefund( webClient, res, refund.getAmount(), refundTxnId + " ("
                                + authTxn.getGatewayAuthorization() + "): " + authTxn.getOriginalDescription() + " x"
                                + authTxn.getCardNumber() + " refunded on Stripe. -RONBOT" );

                        cloudbedsScraper.addNote( webClient, refund.getReservationId(),
                                "Refund completed for £" + refundAmount + "."
                                        + (refund.getDescription() == null ? "" : " " + refund.getDescription()) );

                        // send email if successful
                        createSendRefundSuccessfulEmailJob( refund );
                    }
                    else if ( "pending".equals( stripRefund.getStatus() ) ) {
                        LOGGER.info( "Refund pending" );
                        cloudbedsScraper.addRefund( webClient, res, refund.getAmount(), refundTxnId + " ("
                                + authTxn.getGatewayAuthorization() + "): " + authTxn.getOriginalDescription() + " x"
                                + authTxn.getCardNumber() + " refund PENDING on Stripe. -RONBOT" );
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(),
                                "Refund PENDING (should usually be ok) for £" + refundAmount + "."
                                        + (refund.getDescription() == null ? "" : " " + refund.getDescription()) );
                        createSendRefundSuccessfulEmailJob( refund );
                    }
                    else {
                        LOGGER.error( "Unexpected response during refund: " + stripRefund.toJson() );
                        wordpressDAO.updateStripeRefund( refundTxnId, authTxn.getGatewayAuthorization(), stripRefund.toJson(), "failed" );
                        cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                                " for £" + refundAmount + ". See logs for details." );
                    }
                }
                catch ( InvalidRequestException ex ) {
                    LOGGER.error( ex.getMessage() );
                    wordpressDAO.updateStripeRefund( refundTxnId, authTxn.getGatewayAuthorization(), ex.getMessage(), "failed" );
                    cloudbedsScraper.addNote( webClient, refund.getReservationId(), "Failed to refund transaction " +
                            " for £" + refundAmount + ". " + ex.getMessage() );
                }
            }
        }
    }

    /**
     * Reloads the Stripe refund status and response by looking up from the original auth
     * transaction.
     * 
     * @param refundTxnId PK of Refund table
     * @throws IOException
     * @throws StripeException
     */
    public void refreshStripeRefundStatus( int refundTxnId ) throws IOException, StripeException {
        try (WebClient webClient = context.getBean( "webClientForCloudbeds", WebClient.class )) {
            Stripe.apiKey = STRIPE_API_KEY;
            StripeRefund refund = wordpressDAO.fetchStripeRefund( refundTxnId );
            LOGGER.info( "Updating refund status for reservation " + refund.getReservationId() + " and txn " + refundTxnId );
            Reservation res = cloudbedsScraper.getReservationRetry( webClient, refund.getReservationId() );
            TransactionRecord authTxn = cloudbedsScraper.getStripeTransaction( webClient, res, refund.getCloudbedsTxId() );
            Charge charge = Charge.retrieve( authTxn.getGatewayAuthorization() );
            if ( charge.getRefunds().getData().size() == 0 ) {
                throw new MissingUserDataException( "Unable to find refund transaction." );
            }
            String[] status = new String[1];
            charge.getRefunds().getData().forEach( r -> {
                if ( "succeeded".equals( r.getStatus() ) || status[0] == null ) {
                    status[0] = r.getStatus();
                }
            } );
            wordpressDAO.updateStripeRefund( refundTxnId, authTxn.getGatewayAuthorization(),
                    charge.getRefunds().getData().stream().map( r -> r.toJson() ).collect( Collectors.joining( ",\n" ) ), status[0] );
        }
    }

    /**
     * Send email on successful refund.
     * 
     * @param refund refund that has been processed
     */
    private void createSendRefundSuccessfulEmailJob( StripeRefund refund ) {
        LOGGER.info( "Creating SendRefundSuccessfulEmailJob for " + refund.getReservationId() );
        SendRefundSuccessfulEmailJob job = new SendRefundSuccessfulEmailJob();
        job.setReservationId( refund.getReservationId() );
        job.setAmount( refund.getAmount() );
        job.setTxnId( refund.getId() );
        job.setStatus( JobStatus.submitted );
        wordpressDAO.insertJob( job );
    }

    /**
     * Queries Stripe for refund details.
     * 
     * @param refundTxnId PK of Refund table
     * @return non-null refund object
     * @throws StripeException
     */
    public Refund retrieveStripeRefund( int refundTxnId ) throws StripeException {
        Stripe.apiKey = STRIPE_API_KEY;
        StripeRefund refund = wordpressDAO.fetchStripeRefund( refundTxnId );
        JsonElement refundElem = gson.fromJson( refund.getResponse(), JsonElement.class );
        String refundId = refundElem.getAsJsonObject().get( "id" ).getAsString();
        if ( refundId != null ) {
            return Refund.retrieve( refundId );
        }
        throw new MissingUserDataException( "Refund ID not found for txn " + refundTxnId );
    }
}
