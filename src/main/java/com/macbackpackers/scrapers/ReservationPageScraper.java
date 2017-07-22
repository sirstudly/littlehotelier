
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
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
    private AuthenticationService authService;

    @Autowired
    private GmailService gmailService;
    
    @Autowired
    private FileService fileService;

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
        HtmlTextArea notesTxt = HtmlTextArea.class.cast( 
                reservationPage.getElementById( "reservation_notes" ));
        notesTxt.type( note + "\n" ); // need JS event change
        reservationPage.setFocusedElement( null ); // remove focus on textarea to trigger onchange
        reservationPage.getWebClient().waitForBackgroundJavaScript( 2000 ); // wait for page to update
        HtmlSubmitInput saveButton = reservationPage.getFirstByXPath( "//input[@value='Save']" );
        saveButton.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 ); // wait for page to load
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

        // we should already have secure access so should be able to see the card details directly
        HtmlInput cardNumber = reservationPage.getFirstByXPath( "//input[@id='reservation_payment_card_number']" );
        if ( false == NumberUtils.isDigits( cardNumber.getValueAttribute() ) ) {
            throw new MissingUserDataException( "Unable to retrieve card number." );
        }
        cardDetails.setCardNumber( cardNumber.getValueAttribute() );

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

        HtmlInput cardholderName = reservationPage.getFirstByXPath( "//input[@id='reservation_payment_card_name']" );
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
