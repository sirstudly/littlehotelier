package com.macbackpackers.services;

import static com.macbackpackers.scrapers.AllocationsPageScraper.POUND;

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
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlHiddenInput;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlItalic;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.Payment;
import com.macbackpackers.beans.PxPostTransaction;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.xml.TxnResponse;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.AgodaScraper;
import com.macbackpackers.scrapers.AllocationsPageScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.scrapers.HostelworldScraper;
import com.macbackpackers.scrapers.ReservationPageScraper;

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

    /**
     * Attempt to charge the deposit payment for the given reservation. This method
     * does nothing if the payment outstanding != payment total. It also checks whether
     * there was already a previous attempt to charge the booking and updates LH payment
     * details if it was previously successful. To avoid multiple failed transactions, this 
     * method should only be called again if new card details have been updated.
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
        if( false == "£0".equals( totalRecieved.getTextContent() )) {
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
        if( numberGuests >= wordpressDAO.getGroupBookingSize() ) {
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
     * Attempt to charge the no-show amount for the given reservation. This method
     * fails fast if the payment already received &gt= amount to charge . If
     * there was already a previous attempt to charge the booking, this will update LH payment
     * details so it is synced. 
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
            if( bookingRef.startsWith( "HWL-" ) && false == useCardDetailsFromLH ) {
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
                    message + " --"  + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
        catch ( MissingUserDataException ex ) {
            reservationPageScraper.appendNote( reservationPage,
                    ex.getMessage() + " - " + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
    }

    private final ReentrantLock CARD_LOCK = new ReentrantLock();
    private boolean isCardDetailsVisible = false; // for efficiency, we only need the CARD_LOCK if this is false

    /**
     * Copies the card details from LH (or HWL/AGO/EXP) to CB if it doesn't already exist.
     * 
     * @param lhWebClient web client for lil hotelier
     * @param cbWebClient web client for cloudbeds
     * @param bookingRef the booking ref to copy
     * @param checkinDate checkin date of this booking
     * @throws IOException on i/o error
     * @throws ParseException on bonehead error
     */
    public void copyCardDetailsFromLHtoCB( WebClient lhWebClient, WebClient cbWebClient, String bookingRef, Date checkinDate ) throws IOException, ParseException {

        LOGGER.info( "Processing " + bookingRef + " checking in on " + AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );

        // before we start, we latch this method so we only have to enable security access once
        // even if we have a bunch of jobs that hit this method at the same time
        CARD_LOCK.lock();

        // if we can already see card details, we don't need the lock anymore
        // anyone can come in here....
        if ( isCardDetailsVisible ) {
            CARD_LOCK.unlock();
        }

        try {
            HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageArrivedOn( lhWebClient, checkinDate, bookingRef );
            HtmlPage reservationPage = reservationPageScraper.getReservationPage( lhWebClient, bookingsPage, bookingRef );
            
            HtmlTextInput nameField = reservationPage.getFirstByXPath( "//input[@id='guest_first_name']" );
            String firstName = nameField.getValueAttribute();
            nameField = reservationPage.getFirstByXPath( "//input[@id='guest_last_name']" );
            String lastName = nameField.getValueAttribute();

            // check if payment exists in CB
            Reservation cbReservation = cloudbedsScraper.findBookingByLHBookingRef( cbWebClient, bookingRef, firstName + " " + lastName );
            if ( cbReservation.isCardDetailsPresent() ) {
                LOGGER.info( "Card details found for reservation " + cbReservation.getReservationId() + "; skipping copy" );
                return;
            }
            // extra sanity check that we have the correct booking
            if ( false == firstName.equalsIgnoreCase( cbReservation.getFirstName() )
                    || false == lastName.equalsIgnoreCase( cbReservation.getLastName() ) ) {
                throw new UnrecoverableFault( "Mismatch on booking name! Found "
                        + cbReservation.getFirstName() + " " + cbReservation.getLastName()
                        + " when expected " + firstName + " " + lastName );
            }

            // extra sanity check on the correct date
            HtmlHiddenInput checkinDateInput = reservationPage.getFirstByXPath( "//input[@id='check_in_date']" );
            if ( false == cbReservation.getCheckinDate().equals( checkinDateInput.getValueAttribute() ) ) {
                throw new UnrecoverableFault( "Mismatch on checkin date. Found "
                        + cbReservation.getCheckinDate() + " but expected "
                        + checkinDateInput.getValueAttribute() );
            }

            // get the full card details
            CardDetails ccDetails = null;
            HtmlTextInput paymentCardField = reservationPage.getFirstByXPath( "//input[@id='payment_card_number']" );
            boolean isCardDetailsBlank = StringUtils.isBlank( paymentCardField.getValueAttribute() );

            // only look up on HWL portal if we don't have it available in LH
            if ( bookingRef.startsWith( "HWL-" ) && isCardDetailsBlank ) {
                LOGGER.info( "Retrieving HWL customer card details" );
                ccDetails = retrieveHWCardDetails( bookingRef );
            }
            else if ( bookingRef.startsWith( "EXP-" ) && isCardDetailsBlank ) {
                LOGGER.info( "Retrieving EXP customer card details" );
                ccDetails = expediaService.returnCardDetailsForBooking( bookingRef ).getCardDetails();
            }
            else if ( bookingRef.startsWith( "AGO-" ) && isCardDetailsBlank ) {
                LOGGER.info( "Retrieving AGO customer card details" );
                // I don't think this actually works anymore but give it a go anyways
                ccDetails = retrieveAgodaCardDetails( reservationPage, bookingRef );
            }
            else if ( isCardDetailsBlank ) {
                throw new MissingUserDataException( "No card details found for " + bookingRef
                        + " checking in on " + AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ) );
            }
            else {
                int reservationId = reservationPageScraper.getReservationId( reservationPage );
                HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@class='view-card-details']" );
                // if the "view card details" link is available; we don't yet have secure access yet
                if ( viewCcDetails != null ) {
                    LOGGER.info( "Card details currently hidden; requesting security access" );
                    reservationPageScraper.enableSecurityAccess( reservationPage, reservationId );

                    // this method call is now a free-for-all to any and all threads
                    isCardDetailsVisible = true;
                }
                // get the full card details
                ccDetails = reservationPageScraper.getCardDetails( reservationPage );
                LOGGER.info( "CC details so far: " + ccDetails.getCardNumber().substring( 11 ) );
            }
            cloudbedsScraper.addCardDetails( cbWebClient, cbReservation.getReservationId(), ccDetails );
        }
        finally {
            if ( CARD_LOCK.isHeldByCurrentThread() ) {
                CARD_LOCK.unlock();
            }
        }
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
        if ( pxpost == null || Boolean.FALSE.equals( pxpost.getSuccessful() )) {
            
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

            if( postStatus.isSuccessful() ) {
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
     * @param lhWebClient web client for hostelworld
     * @param bookingRef e.g. HWL-555-123485758 
     * @return full card details (not-null); amount is null
     * @throws IOException on page load error
     * @throws ParseException on parse error
     * @throws MissingUserDataException if card details not found
     */
    private CardDetails retrieveHWCardDetails( String bookingRef ) throws ParseException, IOException {
        try( WebClient hwlWebClient = context.getBean( "webClientForHostelworld", WebClient.class ) ) {
            return hostelworldScraper.getCardDetails( hwlWebClient, bookingRef );
        }
    }

    /**
     * Send through a PX Post transaction for this booking. Update LH payment details on success
     * or add note to notes section on failure.
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
                paymentTxn.getHelpText());
        
        if(paymentTxn.isSuccessful()) {
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
        for(Object elem : guests) {
            HtmlInput guestInput = HtmlInput.class.cast( elem );
            numberGuests += Integer.parseInt( guestInput.getAttribute( "value" ));
        }
        LOGGER.info("found " + numberGuests + " guests");
        return numberGuests;
    }

    /**
     * Retrieves the deposit amount to charge for the given BDC reservation
     * and updates the amount on the given Payment object.
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
        try( WebClient agodaWebClient = context.getBean( "webClient", WebClient.class ) ) {
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
}
