
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlHeading3;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.services.AuthenticationService;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.GmailService;

/**
 * Scrapes an individual reservation page
 *
 */
@Component
@Scope( "prototype" )
public class ReservationPageScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private GmailService gmailService;
    
    @Autowired
    private FileService fileService;

    @Autowired
    private BookingsPageScraper bookingsPageScraper;

    @Value( "${lilhotelier.url.reservation}" )
    private String reservationUrl;

    @Value( "${lilhotelier.url.cardnumber}" )
    private String cardNumberLookupUrl;

    public static final String POUND = AllocationsPageScraper.POUND;
    /**
     * Goes to the given reservation page.
     * 
     * @param webClient the web client to use
     * @param reservationId ID of reservation
     * @return non-null page
     * @throws IOException on I/O error
     */
    public HtmlPage goToReservationPage( WebClient webClient, int reservationId ) throws IOException {
        String pageURL = getReservationURL( reservationId );
        LOGGER.info( "Loading reservations page: " + pageURL );
        HtmlPage nextPage = authService.goToPage( pageURL, webClient );
        webClient.waitForBackgroundJavaScript( 30000 ); // wait for page to load
        return nextPage;
    }

    /**
     * Returns the reservation URL for the given ID.
     * 
     * @param reservationId ID of reservation to lookup
     * @return URL of individual reservation
     */
    private String getReservationURL( int reservationId ) {
        return reservationUrl.replaceAll( "__RESERVATION_ID__", String.valueOf( reservationId ) );
    }

    /**
     * Returns the card lookup URL for the given ID.
     * 
     * @param reservationId ID of reservation to lookup
     * @return URL of individual reservation
     */
    private String getCardLookupURL( int reservationId ) {
        return cardNumberLookupUrl.replaceAll( "__RESERVATION_ID__", String.valueOf( reservationId ) );
    }

    /**
     * Tick the "add payment" button for the automated deposit. Does nothing if already saved.
     * 
     * @param reservationPage the individual reservation page we're looking at.
     * @throws IOException on comms error
     */
    public void tickDeposit( HtmlPage reservationPage ) throws IOException {
        
        HtmlAnchor addPayment = reservationPage.getFirstByXPath( "//a[@data-click='pendingPayment.recordPayment']" );
        if(addPayment != null) {
            LOGGER.info( "Clicking update button on reservation" );
            addPayment.click();
            reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 );
            HtmlTextArea descriptionTxt = HtmlTextArea.class.cast( reservationPage.getElementById( "description" )); 
            descriptionTxt.type( "HW automated deposit" );
            HtmlButton paymentBtn = reservationPage.getFirstByXPath( "//button[@data-click='payment.create']" );
            paymentBtn.click();
        }
        else{
            LOGGER.info( "Payment button not found or already clicked " + reservationPage.getUrl() );
        }
    }

    /**
     * Adds the following payment on the payments tab.
     * 
     * @param reservationPage current reservation
     * @param amount amount to record
     * @param cardType type of card (if not on list, "other" is selected.
     * @param isDeposit true to tick "deposit" checkbox
     * @param note note on the payment
     * @throws IOException on I/O error
     */
    public void addPayment( HtmlPage reservationPage, BigDecimal amount, String cardType, 
            boolean isDeposit, String note) throws IOException {

        // virtual CCs (for BDC) will say 'Pending' rather than 'Completed Payments'
        HtmlDivision div = reservationPage.getFirstByXPath( "//div[@class='payment-row']/div[1]" );
        boolean isVirtualPayment = div != null && StringUtils.trimToEmpty( div.getTextContent() ).startsWith( "To Be Taken" );

        // virtual CCs will have a different "add payment" button
        HtmlAnchor addPayment = reservationPage.getFirstByXPath(
                isVirtualPayment ? "//a[@data-click='pendingPayment.recordPayment']" : "//a[@data-click='payment.add']" );

        LOGGER.info( "Clicking add payment button." + (isVirtualPayment ? " Prepaid amount to be taken." : "") );
        HtmlPage currentPage = addPayment.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
        
        HtmlTextInput amountInput = HtmlTextInput.class.cast( currentPage.getElementById( "amount" ) );
        amountInput.setValueAttribute( amount.toString() );

        // check if the card type is in our list first
        // do any replacement(s) beforehand
        LOGGER.info( "Card type is " + cardType );
        String matchCardType = cardType.replaceAll( "MasterCard", "Mastercard" );
        HtmlOption selectedCardType = currentPage.getFirstByXPath( 
                String.format( "//select[@id='card_type']/option[@value='%s']", matchCardType ) );
        HtmlSelect paymentSelect = currentPage.getFirstByXPath( "//select[@id='payment_method']" );
        if(selectedCardType != null) {
            // select the "Card" input first
            HtmlOption cardOption = paymentSelect.getOptionByValue( "Card" );
            paymentSelect.setSelectedAttribute( cardOption, true );
    
            // then the card type
            HtmlSelect cardTypeSelect = HtmlSelect.class.cast( currentPage.getElementById( "card_type" ) );
            cardTypeSelect.setSelectedAttribute( selectedCardType, true );
        }
        else {
            // card type not in our list, just select "Other"
            HtmlOption otherOption = paymentSelect.getOptionByValue( "Other" );
            paymentSelect.setSelectedAttribute( otherOption, true );
        }
        
        if( isDeposit ) {
            LOGGER.info( "Ticking deposit checkbox" );
            HtmlCheckBoxInput depositCheckbox = HtmlCheckBoxInput.class.cast( currentPage.getElementById( "payment_type" ) );
            if ( false == depositCheckbox.isChecked() ) {
                currentPage = depositCheckbox.click();
                currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
            }
        }
        
        HtmlTextArea descriptionTxt = HtmlTextArea.class.cast( currentPage.getElementById( "description" )); 
        descriptionTxt.type( note );

        LOGGER.info( "Clicking on create payment button" );
        HtmlButton paymentBtn = currentPage.getFirstByXPath( "//button[@data-click='payment.create']" );
        currentPage = paymentBtn.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
    }
    
    /**
     * Appends a message to the notes section of the current reservation page.
     * 
     * @param reservationPage the currently loaded reservation page.
     * @param note the note to be appended to the end
     * @throws IOException on I/O error
     */
    public void appendNote( HtmlPage reservationPage, String note ) throws IOException {
        LOGGER.info( "Appending note to reservation: " + note );
        
        HtmlPage currentPage = reservationPage;
        HtmlTextArea notesTxt = HtmlTextArea.class.cast( 
                currentPage.getElementById( "reservation_notes" ));
        if ( notesTxt == null ) {
            LOGGER.warn( "Unable to find reservation_notes element, attempting to reload page." );
            String rawHtml = currentPage.asXml();
            LOGGER.info( rawHtml.replaceAll( "[0-9]{16}", "XXXXXXXXXXXXXXXX" ) );
            currentPage = HtmlPage.class.cast( reservationPage.getWebClient().getCurrentWindow().getEnclosedPage() );
            LOGGER.info( "Reloading page..." );
            currentPage = HtmlPage.class.cast( currentPage.refresh() );
            LOGGER.info( "Waiting for javascript..." );
            currentPage.getWebClient().waitForBackgroundJavaScript( 60000 ); // wait for page to update
            LOGGER.info( "Finished waiting for JS..." );
            notesTxt = HtmlTextArea.class.cast( currentPage.getElementById( "reservation_notes" ) );
            if( notesTxt == null ) {
                LOGGER.info( "Hiya! Failed to append note. But everything else worked ok." );
                rawHtml = currentPage.asXml();
                LOGGER.info( rawHtml.replaceAll( "[0-9]{16}", "XXXXXXXXXXXXXXXX" ) );
            }
        }

        // we only need to show this message once
        if ( note.startsWith( "Amex not enabled" ) && notesTxt.getText().contains( "Amex not enabled" ) ) {
            LOGGER.info( "Amex message already present; skipping..." );
            return;
        }
        notesTxt.type( note + "\n" ); // need JS event change
        currentPage.setFocusedElement( null ); // remove focus on textarea to trigger onchange
        currentPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to update
        HtmlSubmitInput saveButton = currentPage.getFirstByXPath( "//input[@value='Save']" );
        if ( saveButton == null ) {
            LOGGER.info( "Unable to update notes, but everything else is OK!" );
        }
        else {
            saveButton.click(); // CRH job 240247 NPE (there was a JS pause of 15 sec prior to calling this)
            currentPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load
        }
    }

    /**
     * Returns the card details from the reservation page. Expiry is only populated if available.
     * 
     * @param reservationPage the currently open reservation page
     * @return non-null card number
     * @throws IOException
     */
    public CardDetails getCardDetails( HtmlPage reservationPage, int reservationId ) throws IOException {
        CardDetails cardDetails = new CardDetails();

        // we should already have secure access so should be able to see the card details directly
        HtmlInput cardNumber = reservationPage.getFirstByXPath( "//input[@id='reservation_payment_card_number']" );
        if ( false == NumberUtils.isDigits( cardNumber.getValueAttribute() ) ) {
            throw new MissingUserDataException( "Unable to retrieve card number." );
        }
        cardDetails.setCardNumber( cardNumber.getValueAttribute() );

        HtmlOption expiryMonth = reservationPage.getFirstByXPath( "//select[@id='reservation_payment_card_expiry_month']/option[@selected='selected']" );
        String expMonth = expiryMonth == null ? null : expiryMonth.getValueAttribute();
        String validatedExpiryMonth = null;
        if ( expMonth == null || false == NumberUtils.isDigits( expMonth ) ) {
            LOGGER.warn( "Unable to find card expiry month(" + expMonth + ") : " + reservationPage.getUrl() );
        }
        else {
            validatedExpiryMonth = StringUtils.leftPad( expMonth, 2, "0" );
        }
        
        HtmlOption expiryYear = reservationPage.getFirstByXPath( "//select[@id='reservation_payment_card_expiry_year']/option[@selected='selected']" );
        String expYear = expiryYear == null ? null : expiryYear.getValueAttribute();
        if ( expYear == null || false == NumberUtils.isDigits( expYear ) ) {
            LOGGER.warn( "Unable to find card expiry year(" + expYear + ") : " + reservationPage.getUrl() );
        }
        else if ( validatedExpiryMonth != null ) {
            // only populate if we have a valid month/year
            cardDetails.setExpiry( validatedExpiryMonth + expYear.substring( 2 ) );
        }

        HtmlInput cardholderName = reservationPage.getFirstByXPath( "//input[@id='reservation_payment_card_name']" );
        if ( cardholderName == null ) {
            throw new MissingUserDataException( "Missing cardholder name on booking." );
        }
        cardDetails.setName( cardholderName.getValueAttribute() );
        
        return cardDetails;
    }

    /**
     * Returns the first night payable on the reservations page.
     * 
     * @param reservationPage reservations page
     * @return non-null amount payable on first night
     */
    public BigDecimal getAmountPayableForFirstNight( HtmlPage reservationPage ) {
        List<?> divs = reservationPage.getByXPath( "//div[1]/input[@id='reservation_reservation_room_types__reservation_room_dates__date']/../div[2]" );
        return divs.stream().map( o -> {
            HtmlDivision div = HtmlDivision.class.cast( o );
            String amount = StringUtils.trim( StringUtils.replaceChars( div.getTextContent(), POUND, "" ) );
            LOGGER.info( "Accumulating amount: " + amount );
            return new BigDecimal( amount );
        } ).reduce( BigDecimal.ZERO, BigDecimal::add );
    }

    /**
     * Clicks on the "view card details" link on the reservation page and fills in the
     * security token dialog with the email sent. Returns the card number for the current
     * reservation.
     * 
     * @param reservationPage the current reservation we're looking at
     * @param reservationId the unique reservation ID for this reservation
     * @return non-null card number
     * @throws IOException on I/O error
     * @throws MissingUserDataException if card details could not be retrieved
     */
    public String enableSecurityAccess( HtmlPage reservationPage, int reservationId ) throws IOException, MissingUserDataException {
        HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@id='view_cc_details']" );
        HtmlPage currentPage = viewCcDetails.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 15000 ); // wait for email to be delivered
        HtmlInput securityToken = reservationPage.getFirstByXPath( "//input[@id='security_token']" );
        securityToken.type( gmailService.fetchLHSecurityToken() );
        HtmlInput submitToken = reservationPage.getFirstByXPath( "//input[@class='confirm_token']" );
        currentPage = submitToken.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
        
        // couldn't get the card number from the reservation page
        // this will lookup the card number using the JSON page (found using the data-url from the control)
        Page cardNumberPage = currentPage.getWebClient().openWindow(
                new URL( getCardLookupURL( reservationId ) ), "newJsonWindow" ).getEnclosedPage();

        WebResponse webResponse = cardNumberPage.getWebResponse();
        if ( webResponse.getContentType().equals( "application/json" ) ) {
            String cardNumberJson = cardNumberPage.getWebResponse().getContentAsString();
            Map<String, String> cardNumberResponseMap = new Gson().fromJson(
                    cardNumberJson, new TypeToken<Map<String, String>>() {}.getType());
            
            String cardNum = cardNumberResponseMap.get( "cc_number" );
            if ( false == NumberUtils.isDigits( cardNum ) ) {
                throw new MissingUserDataException( "Unable to retrieve card number : " + reservationPage.getUrl() );
            }

            // we have the card details; save the current web client so we don't have to go through this again
            fileService.writeCookiesToFile( reservationPage.getWebClient() );
            return cardNum;
        }
        else { // we didn't get a JSON response; the page has probably updated directly so reload it
            currentPage = HtmlPage.class.cast( currentPage.refresh() );
            currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
            HtmlInput cardNumber = reservationPage.getFirstByXPath( "//input[@id='reservation_payment_card_number']" );
            if ( false == NumberUtils.isDigits( cardNumber.getValueAttribute() ) ) {
                throw new MissingUserDataException( "Unable to retrieve card number : " + reservationPage.getUrl() );
            }
            return cardNumber.getValueAttribute();
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
    public HtmlPage getReservationPage( WebClient webClient, HtmlPage bookingsPage, String bookingRef ) throws IOException {

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
     * Adds the no-charge note to the given Agoda booking.
     * 
     * @param webClient the web client
     * @param bookingRef e.g. AGO-12345678
     * @param bookedDate the date the reservation was booked
     * @throws IOException on I/O error
     */
    public void updateGuestCommentsAndNotesForAgoda( WebClient webClient, String bookingRef, Date bookedDate ) throws IOException {
        HtmlPage bookingsPage = bookingsPageScraper.goToBookingPageBookedOn( webClient, bookedDate, bookingRef );
        HtmlPage reservationPage = getReservationPage( webClient, bookingsPage, bookingRef );
        HtmlTextArea guestComments = reservationPage.getFirstByXPath( "//textarea[@id='reservation_guest_comments']" );

        boolean hasChanges = false;
        if ( false == guestComments.getTextContent().contains( AgodaScraper.NO_CHARGE_NOTE ) ) {
            guestComments.type( AgodaScraper.NO_CHARGE_NOTE + "\n" ); // need JS event change
            reservationPage.setFocusedElement( null ); // remove focus on textarea to trigger onchange
            hasChanges = true;
        }

        HtmlTextArea reservationNotes = reservationPage.getFirstByXPath( "//textarea[@id='reservation_notes']" );
        if ( false == reservationNotes.getTextContent().contains( AgodaScraper.NO_CHARGE_NOTE ) ) {
            reservationNotes.type( AgodaScraper.NO_CHARGE_NOTE + "\n" ); // need JS event change
            reservationPage.setFocusedElement( null ); // remove focus on textarea to trigger onchange
            hasChanges = true;
        }

        if( hasChanges ) {
            reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to update
            HtmlSubmitInput saveButton = reservationPage.getFirstByXPath( "//input[@value='Save']" );
            saveButton.click();
            reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load
        }
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

}
