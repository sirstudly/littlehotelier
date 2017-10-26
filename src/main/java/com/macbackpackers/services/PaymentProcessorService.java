package com.macbackpackers.services;

import static com.macbackpackers.scrapers.AllocationsPageScraper.POUND;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlHeading3;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.Payment;
import com.macbackpackers.beans.PxPostTransaction;
import com.macbackpackers.beans.xml.TxnResponse;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.AgodaScraper;
import com.macbackpackers.scrapers.BookingsPageScraper;
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
        HtmlPage reservationPage = getReservationPage( webClient, bookingsPage, bookingRef );
        int reservationId = getReservationId( reservationPage );

        // check if we've already received any payment on the payments tab
        HtmlSpan totalRecieved = reservationPage.getFirstByXPath( "//div[@class='received_total']/span" );
        if( false == "£0".equals( totalRecieved.getTextContent() )) {
            LOGGER.info( "Payment of " + totalRecieved.getTextContent() + " already received for booking " + bookingRef + "." );
            return; // nothing to do
        } 

        // only process bookings at "Confirmed"
        HtmlSpan statusSpan = reservationPage.getFirstByXPath( "//span[@class='status']/span" );
        if ( statusSpan == null || false == "Confirmed".equals( statusSpan.getTextContent() ) ) {
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
            HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@id='view_cc_details']" );

            // BDC may have a cancellation grace period; don't charge within this period
            if ( isWithinCancellationGracePeriod( reservationPage ) ) {
                LOGGER.info( "Booking " + bookingRef + " is within the cancellation grace period. Skipping charge for now..." );
                return;
            }

            // if the "view card details" link is available; we don't yet have secure access yet
            if ( viewCcDetails != null ) {
                LOGGER.info( "Card details currently hidden ); requesting security access" );
                try {
                    String cardnum = reservationPageScraper.enableSecurityAccess( reservationPage, reservationId );
                    if ( false == NumberUtils.isDigits( cardnum ) ) {
                        // This is outside the try block as we don't want to modify the LH record if it fails here
                        // problems with the card number being wiped out when we enable security access and then save it
                        throw new MissingUserDataException( "Error retrieving card number from LH" );
                    }

                    // we should have access to the card details without having to press the "view" link now
                    // so reload the reservation page and continue...
                    bookingsPage = bookingsPageScraper.goToBookingPageBookedOn( webClient, bookedOnDate, bookingRef );
                    reservationPage = getReservationPage( webClient, bookingsPage, bookingRef );
                }
                catch ( MissingUserDataException ex ) {
                    // enableSecurityAccess messes up the current page so reload the reservation page and continue...
                    bookingsPage = bookingsPageScraper.goToBookingPageBookedOn( webClient, bookedOnDate, bookingRef );
                    reservationPage = getReservationPage( webClient, bookingsPage, bookingRef );
                    reservationPageScraper.appendNote( reservationPage,
                            ex.getMessage() + " - " + DATETIME_FORMAT.format( new Date() ) + "\n" );
                }
            }
        }

        try {
            // get the deposit amount and full card details
            Payment depositPayment = retrieveCardDetails( bookingRef, reservationId, reservationPage );
            processPxPostTransaction( jobId, bookingRef, depositPayment, true, reservationPage );
        }
        catch ( MissingUserDataException ex ) {
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
        HtmlPage reservationPage = getReservationPage( webClient, bookingsPage, bookingRef );

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
     * @param hwlWebClient web client to use for hostelworld
     * @param jobId current job id
     * @param bookingRef booking reference e.g. BDC-123456789
     * @param amountToCharge charge amount
     * @param message a wee message to put in the notes (optional)
     * @throws IOException on I/O error
     * @throws ParseException on parse error
     */
    public synchronized void processManualPayment( WebClient lhWebClient, WebClient hwlWebClient, int jobId, 
            String bookingRef, BigDecimal amountToCharge, String message ) throws IOException, ParseException {
        LOGGER.info( "Processing no-show payment for booking " + bookingRef );

        // only support HWL at the moment
        if ( false == bookingRef.startsWith( "HWL-" ) ) {
            throw new UnrecoverableFault( "Unsupported booking source type" );
        }

        // attempt to charge less than or equal to zero?!
        if ( amountToCharge.compareTo( BigDecimal.ZERO ) <= 0 ) {
            throw new IllegalArgumentException( "Why the fuck are you trying to charge " + amountToCharge + "?!" );
        }

        // search from 1 month prior (card details only kept 1 week after checkin date for HW bookings)
        // to 6 months in future (usually we only scan 4 months in the future)
        Date dateFrom = Date.from( Instant.now().minus( Duration.ofDays( 30 ) ) );
        Date dateTo = Date.from( Instant.now().plus( Duration.ofDays( 180 ) ) );

        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageForArrivals( lhWebClient, dateFrom, dateTo, bookingRef, null );
        HtmlPage reservationPage = getReservationPage( lhWebClient, bookingsPage, bookingRef );

        // cannot charge more than what is outstanding
        HtmlSpan outstandingTotalSpan = reservationPage.getFirstByXPath( "//div[@class='outstanding_total']/span" );
        BigDecimal outstandingTotal = new BigDecimal( outstandingTotalSpan.getTextContent().replaceAll( POUND, "" ) );
        if ( amountToCharge.compareTo( outstandingTotal ) > 0 ) {
            throw new UnrecoverableFault( "Request to charge " + amountToCharge + " exceeds LH total outstanding (" + outstandingTotal + ")." );
        }
        
        // check if we've already received any payment on the payments tab
        for ( Object paymentDiv : reservationPage.getByXPath( "//div[@class='payment-row']/div[@class='row']/div" ) ) {
            if ( HtmlDivision.class.cast( paymentDiv ).getTextContent().contains( "PxPost transaction" ) ) {
                throw new UnrecoverableFault( "Payment already exists. Has it already been charged?" );
            }
        }

        try {
            // get the full card details
            Payment payment = retrieveHWCardDetails( hwlWebClient, bookingRef );
            payment.setAmount( amountToCharge );
            processPxPostTransaction( jobId, bookingRef, payment, false, reservationPage );
            reservationPageScraper.appendNote( reservationPage,
                    message + " --"  + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
        catch ( MissingUserDataException ex ) {
            reservationPageScraper.appendNote( reservationPage,
                    ex.getMessage() + " - " + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
    }
    
    /**
     * Loads the reservation page for the given reservation.
     * 
     * @param webClient web client to use
     * @param bookingsPage page with the current booking
     * @param bookingRef booking reference e.g. BDC-123456789
     * @param bookedOnDate date on which reservation was booked
     * @return reservation page
     * @throws IOException on I/O error
     */
    private HtmlPage getReservationPage( WebClient webClient, HtmlPage bookingsPage, String bookingRef ) throws IOException {

        List<?> rows = bookingsPage.getByXPath(
                "//div[@id='content']/div[@class='reservations']/div[@class='data']/table/tbody/tr/td[@class='booking_reference' and text()='" + bookingRef + "']/.." );
        if ( rows.size() != 1 ) {
            throw new IncorrectResultSizeDataAccessException( "Unable to find unique booking " + bookingRef, 1 );
        }
        // need the LH reservation ID before clicking on the row
        HtmlTableRow row = HtmlTableRow.class.cast( rows.get( 0 ) );

        // click on the only reservation on the page
        HtmlPage reservationPage = row.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load

        // extra-paranoid; making sure booking ref matches the editing window
        if ( false == bookingRef.equals( getBookingRef( reservationPage ) ) ) {
            throw new IllegalStateException( "Booking references don't match!" );
        }
        return reservationPage;
    }

    /**
     * Returns the reservation ID from the reservation page.
     * 
     * @param reservationPage pre-loaded reservation page
     * @return reservation ID
     */
    private int getReservationId( HtmlPage reservationPage ) {
        HtmlForm editform = reservationPage.getFirstByXPath( "//form[@id='edit_reservation']" );
        String postActionUrl = editform.getAttribute( "action" );
        int reservationId = Integer.parseInt( postActionUrl.substring( postActionUrl.lastIndexOf( '/' ) + 1 ) );
        return reservationId;
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
                LOGGER.info( "Last payment attempt failed" );
                if( wordpressDAO.getPreviousNumberOfFailedTxns(
                        pxpost.getBookingReference(), 
                        pxpost.getMaskedCardNumber()) >= MAX_PAYMENT_ATTEMPTS ) {
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
            List<?> paymentDivs = reservationPage.getByXPath( "//div[@class='payment-row']/div[@class='row']/div" );
            if ( paymentDivs.stream().anyMatch( p -> HtmlDivision.class.cast( p ).getTextContent()
                    .contains( "PxPost transaction " + pxpost.getId() + " successful" ) ) ) {
                LOGGER.info( "Previous transaction was recorded in LH. Ok to continue." );
            }
            else {
                LOGGER.info( "Previous transaction was *NOT* recorded in LH. Updating now." );
            reservationPageScraper.addPayment( reservationPage, pxpost.getPaymentAmount(), "Other", isDeposit, 
                    "PxPost transaction " + pxpost.getId() + " successful. - " 
                    + DATETIME_FORMAT.format( new Date() ) );
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
            HtmlSpan cardSpan = HtmlSpan.class.cast( reservationPage.getFirstByXPath(
                    "//input[@id='reservation_payment_card_number']/following-sibling::span" ) );
            if ( cardSpan != null && cardSpan.getAttribute( "class" ).contains( "fa-cc-amex" ) ) {
                throw new MissingUserDataException( "Amex not enabled. Charge manually using EFTPOS terminal." );
            }

            LOGGER.info( "Retrieving card security code" );
            CardDetails ccDetailsFromGmail = gmailService.fetchBdcCardDetailsFromBookingRef( bookingRef );

            // AMEX cards not supported
            if( "AX".equals( ccDetailsFromGmail.getCardType() ) ) {
                throw new MissingUserDataException( "Amex not enabled. Charge manually using EFTPOS terminal." );
            }

            LOGGER.info( "Retrieving customer card details" );
            CardDetails ccDetails = reservationPageScraper.getCardDetails( reservationPage, reservationId );
            ccDetails.setExpiry( ccDetailsFromGmail.getExpiry() ); // copying over expiry
            ccDetails.setCvv( ccDetailsFromGmail.getCvv() ); // copying over security code
            return new Payment( getBdcDepositChargeAmount( reservationPage ), ccDetails );
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
     * @param webClient web client for hostelworld
     * @param bookingRef e.g. HWL-555-123485758 
     * @return full card details (not-null); amount is null
     * @throws IOException on page load error
     * @throws ParseException on parse error
     * @throws MissingUserDataException if card details not found
     */
    private Payment retrieveHWCardDetails( WebClient webClient, String bookingRef )
            throws IOException, ParseException, MissingUserDataException {

        if ( bookingRef.startsWith( "HWL-" ) ) {
            LOGGER.info( "Retrieving customer card details" );
            CardDetails ccDetails = hostelworldScraper.getCardDetails( webClient, bookingRef );
            return new Payment( null, ccDetails );
        }
        throw new UnrecoverableFault( "Not a HostelWorld booking: " + bookingRef );
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
        List<?> guests = reservationPage.getByXPath( "//input[@id='reservation_reservation_room_types__number_adults']" );
        int numberGuests = 0;
        for(Object elem : guests) {
            HtmlInput guestInput = HtmlInput.class.cast( elem );
            numberGuests += Integer.parseInt( guestInput.getAttribute( "value" ));
        }
        LOGGER.info("found " + numberGuests + " guests");
        return numberGuests;
    }

    /**
     * Retrieves to booking reference from the given reservation page.
     * 
     * @param reservationPage the page to check
     * @return non-null booking reference
     * @throws MissingUserDataException if booking ref not found
     */
    private String getBookingRef( HtmlPage reservationPage ) throws MissingUserDataException {
        HtmlHeading3 heading = reservationPage.getFirstByXPath( "//h3[@class='webui-popover-title']" );
        Pattern p = Pattern.compile( "Edit Reservation - (.*)" );
        Matcher m = p.matcher( heading.getTextContent() ); // CRH job 240239 fails with NPE here
        String bookingRef;
        if(m.find()) {
            bookingRef = m.group( 1 );
        }
        else {
            throw new MissingUserDataException( "Unable to determine booking reference from " + reservationPage.getBaseURL());
        }
        return bookingRef;
    }

    /**
     * Retrieves the deposit amount to charge for the given BDC reservation.
     * 
     * @param reservationPage the HtmlPage of the current reservation
     * @return deposit amount to charge reservation
     */
    public BigDecimal getBdcDepositChargeAmount( HtmlPage reservationPage ) {

        // calculate how much we need to change
        HtmlSpan totalOutstanding = reservationPage.getFirstByXPath( "//div[@class='outstanding_total']/span" );
        BigDecimal amountOutstanding = new BigDecimal( totalOutstanding.getTextContent().replaceAll( "£", "" ) );
        HtmlTextArea guestComments = reservationPage.getFirstByXPath( "//textarea[@id='reservation_guest_comments']" );

        // either take first night, or a percentage amount
        BigDecimal amountToPay;
        if ( StringUtils.trimToEmpty( guestComments.getText() ).contains( 
                "You have received a virtual credit card for this reservation" ) ) {
            Pattern p = Pattern.compile( "The amount the guest prepaid to Booking\\.com is GBP ([0-9]+\\.[0-9]{2})" );
            Matcher m = p.matcher( guestComments.getText() );
            if ( m.find() ) {
                amountToPay = new BigDecimal( m.group( 1 ) );
            }
            else {
                throw new MissingUserDataException( "Missing prepaid amount in guest comments when attempting to charge deposit." );
            }
        }
        else if ( "first_night".equals( bdcDepositStrategy ) ) {
            amountToPay = reservationPageScraper.getAmountPayableForFirstNight( reservationPage );
        }
        else {
            BigDecimal percentToCharge = new BigDecimal( bdcDepositStrategy );
            amountToPay = amountOutstanding.multiply( percentToCharge ).setScale( 2, RoundingMode.HALF_UP );
        }

        // extra sanity checks
        if ( amountToPay.compareTo( BigDecimal.ZERO ) == 0 ) {
            throw new IllegalStateException( "Amount payable is £0???" );
        }
        else if ( amountToPay.compareTo( amountOutstanding ) > 0 ) {
            throw new IllegalStateException( "Amount to pay " + amountToPay +
                    " exceeds amount outstanding of " + amountOutstanding + "???" );
        }
        return amountToPay;
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

        HtmlSpan statusSpan = reservationPage.getFirstByXPath( "//span[@class='status']/span" );
        if ( "Checked in".equals( statusSpan.getTextContent() ) ) {
            HtmlSpan outstandingTotalSpan = reservationPage.getFirstByXPath( "//div[@class='outstanding_total']/span" );
            return new BigDecimal( outstandingTotalSpan.getTextContent().replaceAll( POUND, "" ) );
        }

        // charge first night (less anything already paid)
        if ( "Confirmed".equals( statusSpan.getTextContent() ) ) {
            HtmlSpan totalRecievedSpan = reservationPage.getFirstByXPath( "//div[@class='received_total']/span" );
            BigDecimal totalReceived = new BigDecimal( totalRecievedSpan.getTextContent().replaceAll( POUND, "" ) );
            BigDecimal amountFirstNight = reservationPageScraper.getAmountPayableForFirstNight( reservationPage );
            return amountFirstNight.subtract( totalReceived ).max( BigDecimal.ZERO );
        }
        
        LOGGER.info( "Booking is at status " + statusSpan.getTextContent()
                + "; There is nothing payable." );

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
        HtmlTextArea guestComments = reservationPage.getFirstByXPath( "//textarea[@id='reservation_guest_comments']" );
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
