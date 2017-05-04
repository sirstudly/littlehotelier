package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlHeading3;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.DepositPayment;
import com.macbackpackers.beans.PxPostTransaction;
import com.macbackpackers.beans.xml.TxnResponse;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.scrapers.BookingsPageScraper;
import com.macbackpackers.scrapers.ReservationPageScraper;

/**
 * Service for checking payments in LH, sending (deposit) payments through the payment gateway and
 * recording said payments in LH.
 */
@Service
public class PaymentProcessorService {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    
    private static final FastDateFormat DATETIME_FORMAT = FastDateFormat.getInstance( "dd/MM/yyyy HH:mm:ss" );
    private static final int MAX_PAYMENT_ATTEMPTS = 3; // max number of transaction attempts

    @Autowired
    private ReservationPageScraper reservationPageScraper;

    @Autowired
    private BookingsPageScraper bookingsPageScraper;

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
     * @param bookingRef booking reference e.g. BDC-123456789
     * @param bookedOnDate date on which reservation was booked
     * @throws IOException on I/O error
     */
    public void processDepositPayment( WebClient webClient, String bookingRef, Date bookedOnDate ) throws IOException {
        LOGGER.info( "Processing payment for booking " + bookingRef );
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageBookedOn( webClient, bookedOnDate, bookingRef );
        
        List<?> rows = bookingsPage.getByXPath( 
                "//div[@id='content']/div[@class='reservations']/div[@class='data']/table/tbody/tr[@class!='group_header']" );
        if(rows.size() != 1) {
            throw new IncorrectResultSizeDataAccessException("Unable to find unique booking " + bookingRef, 1);
        }
        // need the LH reservation ID before clicking on the row
        HtmlTableRow row = HtmlTableRow.class.cast( rows.get( 0 ) );
        int reservationId = Integer.parseInt( row.getAttribute( "data-id" ) );
        
        // click on the only reservation on the page
        HtmlPage reservationPage = row.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load
        
        // extra-paranoid; making sure booking ref matches the editing window
        if ( false == bookingRef.equals( getBookingRef( reservationPage ) ) ) {
            throw new IllegalStateException( "Booking references don't match!" );
        }
        
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

        try {
            // get the deposit amount and full card details
            DepositPayment depositPayment = retrieveCardDetails( reservationPageScraper, bookingRef, reservationId, reservationPage );
            
            // see if we've tried to do this already
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
                            pxpost.getMaskedCardNumber()) > MAX_PAYMENT_ATTEMPTS ) {
                        throw new UnrecoverableFault( "Max number of payment attempts reached" );
                    }
                }
    
                int txnId = wordpressDAO.insertNewPxPostTransaction( bookingRef, depositPayment.getDepositAmount() );
                processTransaction( reservationPageScraper, reservationPage, txnId, bookingRef, depositPayment.getCardDetails(), 
                        depositPayment.getDepositAmount() ); 
            }
            // we actually did get a successful payment thru so LH is out of sync
            else if ( Boolean.TRUE.equals( pxpost.getSuccessful() ) ) {
                LOGGER.info( "Previous PX Post transaction was successful; updating LH" );
                reservationPageScraper.addPayment( reservationPage, pxpost.getPaymentAmount(), "Other", true, 
                        "PxPost transaction " + pxpost.getId() + " successful. - " 
                        + DATETIME_FORMAT.format( new Date() ) );
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
                            postStatus.getTransaction().getCardName(), true, 
                            "PxPost transaction " + pxpost.getId() + " successful. - " + DATETIME_FORMAT.format( new Date() ) );
                }
                else { 
                    LOGGER.info( "Last transaction wasn't successful; trying again with the same card details" );
                    processTransaction( reservationPageScraper, reservationPage, pxpost.getId(), bookingRef, depositPayment.getCardDetails(), 
                            new BigDecimal( postStatus.getTransaction().getAmount() ) ); 
                }
            }
        }
        catch ( MissingUserDataException ex ) {
            reservationPageScraper.appendNote( reservationPage,
                    ex.getMessage() + " - " + DATETIME_FORMAT.format( new Date() ) + "\n" );
        }
    }

    /**
     * Retrieve full card details for the given booking.
     * 
     * @param reservationPageScraper scraper for reservations page
     * @param bookingRef booking ref, e.g. BDC-12345678
     * @param reservationId LH id for this reservation
     * @param reservationPage the HtmlPage of the current reservation 
     * @return full card details (not-null)
     * @throws IOException on page load error
     */
    private DepositPayment retrieveCardDetails( ReservationPageScraper reservationPageScraper, 
            String bookingRef, int reservationId, HtmlPage reservationPage ) throws IOException {
        
        // Booking.com
        if ( bookingRef.startsWith( "BDC-" ) ) {
            LOGGER.info( "Retrieving customer card details" );
            CardDetails ccDetails = reservationPageScraper.getCardDetails( reservationPage, reservationId );

            LOGGER.info( "Retrieving card security code" );
            CardDetails ccDetailsFromGmail = gmailService.fetchBdcCardDetailsFromBookingRef( bookingRef );
            ccDetails.setCvv( ccDetailsFromGmail.getCvv() ); // copying over security code

            // calculate how much we need to change
            HtmlSpan totalOutstanding = reservationPage.getFirstByXPath( "//div[@class='outstanding_total']/span" );
            BigDecimal amountToPay = new BigDecimal( totalOutstanding.getTextContent().replaceAll( "£", "" ) );
            amountToPay = amountToPay.multiply( new BigDecimal( "0.2" ) ).setScale( 2, RoundingMode.HALF_UP ); // 20% is deposit
            if ( amountToPay.compareTo( new BigDecimal( "0" ) ) == 0 ) {
                throw new IllegalStateException( "Amount payable is £0???" );
            }
            return new DepositPayment( amountToPay, ccDetails );
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
     * Send through a PX Post transaction for this booking. Update LH payment details on success
     * or add note to notes section on failure.
     * 
     * @param reservationPageScraper scraper for reservations page
     * @param reservationPage the current reservation
     * @param txnId our unique transaction ID
     * @param bookingRef the booking ref (merchant reference in px post)
     * @param ccDetails customer card details
     * @param amountToPay amount being charged
     * @throws IOException on I/O error
     */
    private void processTransaction( ReservationPageScraper reservationPageScraper, HtmlPage reservationPage, 
            int txnId, String bookingRef, CardDetails ccDetails, BigDecimal amountToPay ) throws IOException {
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
                    paymentTxn.getTransaction().getCardName(), true, 
                    "PxPost transaction " + txnId + " successful. - " + DATETIME_FORMAT.format( new Date() ) );
        } 
        else {
            LOGGER.info( "PX Post was NOT successful; updating LH notes" );
            reservationPageScraper.appendNote( reservationPage, 
                "~~~ " + DATETIME_FORMAT.format( new Date() ) + "\n" 
                + "Transaction ID: " + txnId + "\n"
                + "Card: " + paymentTxn.getTransaction().getCardNumber() + "\n"
                + "Response Code: " + paymentTxn.getResponseCode() + "\n"
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
        Matcher m = p.matcher( heading.getTextContent() );
        String bookingRef;
        if(m.find()) {
            bookingRef = m.group( 1 );
        }
        else {
            throw new MissingUserDataException( "Unable to determine booking reference from " + reservationPage.getBaseURL());
        }
        return bookingRef;
    }
}
