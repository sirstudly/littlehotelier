
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
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
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    private AuthenticationService authService;

    @Autowired
    private GmailService gmailService;
    
    @Autowired
    private FileService fileService;

    @Value( "${lilhotelier.url.reservation}" )
    private String reservationUrl;

    @Value( "${lilhotelier.url.cardnumber}" )
    private String cardNumberLookupUrl;

    /**
     * Goes to the given reservation page.
     * 
     * @param reservationId ID of reservation
     * @return non-null page
     * @throws IOException on I/O error
     */
    public HtmlPage goToReservationPage( int reservationId ) throws IOException {
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
        HtmlAnchor addPayment = reservationPage.getFirstByXPath( "//a[@data-click='payment.add']" );
        LOGGER.info( "Clicking add payment button" );
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
            // FIXME: not sure why this isn't working; default always sticks as Card/Visa
            HtmlOption otherOption = paymentSelect.getOptionByValue( "Other" );
            paymentSelect.setSelectedAttribute( otherOption, true );
        }
        
        if( isDeposit ) {
            LOGGER.info( "Ticking deposit checkbox" );
            HtmlCheckBoxInput depositCheckbox = HtmlCheckBoxInput.class.cast( currentPage.getElementById( "payment_type" ) );
            currentPage = depositCheckbox.click();
            currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
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
        HtmlTextArea notesTxt = HtmlTextArea.class.cast( 
                reservationPage.getElementById( "reservation_notes" ));
        notesTxt.type( note + "\n" ); // need JS event change
        HtmlSubmitInput saveButton = reservationPage.getFirstByXPath( "//input[@value='Save']" );
        saveButton.click();
    }

    /**
     * Returns the card details from the reservation page.
     * 
     * @param reservationPage the currently open reservation page
     * @return non-null card number
     * @throws IOException
     */
    public CardDetails getCardDetails( HtmlPage reservationPage, int reservationId ) throws IOException {
        CardDetails cardDetails = new CardDetails();
        HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@id='view_cc_details']" );
        
        // if the "view card details" link is available; we don't yet have secure access yet
        // so enable it if not (and get the card number)
        if(viewCcDetails != null) {
            LOGGER.info( "Card details currently hidden ); requesting security access" );
            cardDetails.setCardNumber( enableSecurityAccess( reservationPage, reservationId ) );
        } 
        else { // we already have secure access so should be able to see the card details directly
            HtmlInput cardNumber = reservationPage.getFirstByXPath( "//input[@id='reservation_payment_card_number']" );
            if ( false == NumberUtils.isDigits( cardNumber.getValueAttribute() ) ) {
                throw new MissingUserDataException( "Unable to retrieve card number : " + reservationPage.getUrl() );
            }
            cardDetails.setCardNumber( cardNumber.getValueAttribute() );
        }

        HtmlOption expiryMonth = reservationPage.getFirstByXPath( "//select[@id='reservation_payment_card_expiry_month']/option[@selected='selected']" );
        String expMonth = expiryMonth.getValueAttribute();
        if(false == NumberUtils.isDigits( expMonth )) {
            throw new MissingUserDataException( "Unable to find card expiry month(" + expMonth + ") : " + reservationPage.getUrl() );
        }
        
        HtmlOption expiryYear = reservationPage.getFirstByXPath( "//select[@id='reservation_payment_card_expiry_year']/option[@selected='selected']" );
        String expYear = expiryYear.getValueAttribute();
        if(false == NumberUtils.isDigits( expYear )) {
            throw new MissingUserDataException( "Unable to find card expiry year(" + expYear + " : " + reservationPage.getUrl() );
        }
        cardDetails.setExpiry( StringUtils.leftPad( expMonth, 2, "0" ) + expYear.substring( 2 ) );
        
        HtmlInput firstName = reservationPage.getFirstByXPath( "//input[@id='reservation_guest_first_name']" );
        HtmlInput lastName = reservationPage.getFirstByXPath( "//input[@id='reservation_guest_last_name']" );
        cardDetails.setName( firstName.getValueAttribute() + " " + lastName.getValueAttribute() );
        
        return cardDetails;
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
    private String enableSecurityAccess(HtmlPage reservationPage, int reservationId) throws IOException, MissingUserDataException {
        HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@id='view_cc_details']" );
        HtmlPage currentPage = viewCcDetails.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 15000 ); // wait for email to be delivered
        HtmlInput securityToken = reservationPage.getFirstByXPath( "//input[@id='security_token']" );
        securityToken.type( gmailService.fetchLHSecurityToken() );
        HtmlInput submitToken = reservationPage.getFirstByXPath( "//input[@class='confirm_token']" );
        currentPage = submitToken.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 10000 );
        
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
}
