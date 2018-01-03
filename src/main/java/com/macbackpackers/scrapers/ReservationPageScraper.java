
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlHeading4;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlMeta;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.json.HWLDepositPaymentRequest;
import com.macbackpackers.beans.json.RecordPaymentRequest;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.PaymentCardNotAcceptedException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.AuthenticationService;
import com.macbackpackers.services.FileService;
import com.macbackpackers.services.GmailService;
import com.macbackpackers.services.LHJsonCardMask;

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

    @Autowired
    private Gson gson;

    @Value( "${lilhotelier.propertyid}" )
    private String lhPropertyId;

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
        return String.format( "https://app.littlehotelier.com/extranet/properties/%s/reservations/%d/edit",
                lhPropertyId, reservationId );
    }

    /**
     * Returns the card lookup URL for the given ID.
     * 
     * @param reservationId ID of reservation to lookup
     * @return URL of individual reservation
     */
    private String getCardLookupURL( int reservationId ) {
        return String.format( "https://app.littlehotelier.com/extranet/properties/%s/reservations/%d/display_card_number",
                lhPropertyId, reservationId );
    }

    /**
     * Returns the payments recoring URL for the given ID.
     * 
     * @param reservationId ID of reservation to lookup
     * @return URL of individual reservation
     */
    private String getRecordPaymentsURL( int reservationId ) {
        return String.format( "https://app.littlehotelier.com/api/v2/properties/%s/reservations/%d/payments/record",
                lhPropertyId, reservationId );
    }

    /**
     * Returns the URL to load a reservation's details using JSON.
     * 
     * @param reservationId unique ID of reservation
     * @return the URL
     */
    private String getReservationLoadURL( int reservationId ) {
        return String.format( "https://app.littlehotelier.com/api/v2/properties/%s/reservations/%d?form-info=true",
                lhPropertyId, reservationId );
    }

    /**
     * Returns the URL to POST a reservation's details using JSON.
     * 
     * @param reservationId unique ID of reservation
     * @return the URL
     */
    private String getReservationPostURL( int reservationId ) {
        return String.format( "https://app.littlehotelier.com/api/v2/properties/%s/reservations/%d",
                lhPropertyId, reservationId );
    }

    /**
     * Tick the "add payment" button for the automated deposit. Does nothing if already saved.
     * 
     * @param bookingsPage the booking page; doesn't have to be this reservation, just need the CSRF
     *            token
     * @param reservationId the reservation to check
     * @throws IOException on comms error
     */
    public void tickDeposit( HtmlPage bookingsPage, int reservationId ) throws IOException {

        // send a GET request for this reservation so we have the current details
        URL reservationLoadURL = new URL( getReservationLoadURL( reservationId ) );
        JsonObject reservationRoot = sendJsonRequest( bookingsPage.getWebClient(),
                constructDefaultGetRequest( reservationLoadURL, bookingsPage ) );

        JsonArray arr = reservationRoot.getAsJsonArray( "pending_payments" );
        if ( arr.size() == 0 ) {
            LOGGER.info( "No pending payments; nothing to do.." );
            return;
        }
        else if ( arr.size() > 1 ) {
            LOGGER.error( arr.toString() );
            throw new UnrecoverableFault( "WTF? There's more than one pending payment?" );
        }
        JsonObject pendingPayment = arr.get( 0 ).getAsJsonObject();
        LOGGER.info( "Pending payment found" );
        LOGGER.debug( gson.toJson( pendingPayment ) );

        // now construct a request to update the pending payment (deposit)
        HWLDepositPaymentRequest depositReq = new HWLDepositPaymentRequest(
                pendingPayment.get( "id" ).getAsInt(),
                pendingPayment.get( "paid_at" ).getAsString(),
                new BigDecimal( pendingPayment.get( "amount" ).getAsString().replaceAll( "£", "" ) ) );
        String jsonRequest = gson.toJson( depositReq );

        URL paymentsURL = new URL( getRecordPaymentsURL( reservationId ) );
        LOGGER.info( "Attempting POST to " + paymentsURL + " for reservation " + reservationId );

        sendJsonRequest( bookingsPage.getWebClient(),
                constructDefaultRequest( HttpMethod.POST, paymentsURL, bookingsPage, jsonRequest ) );
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
    @Deprecated
    public void addPaymentDeprecated( HtmlPage reservationPage, BigDecimal amount, String cardType,
            boolean isDeposit, String note ) throws IOException {

        // click on Payments tab
        HtmlAnchor paymentsTab = reservationPage.getFirstByXPath( "//a[@href='#payments']" );
        HtmlPage currentPage = paymentsTab.click();

        // virtual CCs (for BDC) will say 'Pending' rather than 'Completed Payments'
        HtmlDivision div = reservationPage.getFirstByXPath( "//div[@class='payment-row']/div[1]" );
        boolean isVirtualPayment = div != null && StringUtils.trimToEmpty( div.getTextContent() ).startsWith( "To Be Taken" );

        // virtual CCs will have a different "add payment" button
        HtmlButton addPayment = reservationPage.getFirstByXPath(
                isVirtualPayment ? "//button[text()='Confirm'" : "//button[contains(.,'Add Payment')]" );

        LOGGER.info( "Clicking add payment button." + (isVirtualPayment ? " Prepaid amount to be taken." : "") );
        currentPage = addPayment.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );

        // so many options! click on the one with the dialog that is opened
        final String BASE_DIALOG = "//div[contains(@class,'payment-panel-container')]/div[contains(@class,'payment-panel') and contains(@class,'opened')]";
        HtmlButton recordPaymentButton = currentPage.getFirstByXPath( BASE_DIALOG + "//button[text()='Record']" );
        currentPage = recordPaymentButton.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );

        HtmlTextInput amountInput = currentPage.getFirstByXPath( BASE_DIALOG + "//input[@id='amount']" );
        amountInput.setValueAttribute( amount.toString() );

        // check if the card type is in our list first
        // do any replacement(s) beforehand
        LOGGER.info( "Card type is " + cardType );
        HtmlOption selectedCardType = currentPage.getFirstByXPath(
                String.format( BASE_DIALOG + "//select[@id='card_type']/option[@value='%s']", cardType ) );
        HtmlSelect paymentSelect = currentPage.getFirstByXPath( BASE_DIALOG + "//select[@id='payment_method']" );
        if ( selectedCardType != null ) {
            // select the "Card" input first
            HtmlOption cardOption = paymentSelect.getOptionByValue( "Card" );
            paymentSelect.setSelectedAttribute( cardOption, true );

            // then the card type
            HtmlSelect cardTypeSelect = currentPage.getFirstByXPath( BASE_DIALOG + "//select[@id='card_type']" );
            cardTypeSelect.setSelectedAttribute( selectedCardType, true );
        }
        else {
            // card type not in our list, just select "Other"
            HtmlOption otherOption = paymentSelect.getOptionByValue( "Other" );
            paymentSelect.setSelectedAttribute( otherOption, true );
        }

        if ( isDeposit ) {
            LOGGER.info( "Ticking deposit checkbox" );
            HtmlCheckBoxInput depositCheckbox = currentPage.getFirstByXPath( BASE_DIALOG + "//input[@type='checkbox' and @id='payment_type']" );
            if ( false == depositCheckbox.isChecked() ) {
                currentPage = depositCheckbox.click();
                currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
            }
        }

        HtmlTextInput descriptionTxt = currentPage.getFirstByXPath( BASE_DIALOG + "//input[@id='description']" );
        descriptionTxt.type( note );

        LOGGER.info( "Clicking on create payment button" );
        HtmlButton paymentBtn = currentPage.getFirstByXPath( BASE_DIALOG + "//button[text()='Record']" );
        currentPage = paymentBtn.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 30000 );
    }

    /**
     * Adds the following payment on the payments tab. This uses a JSON request as I couldn't get it
     * to work using the web page directly.
     * 
     * @param reservationPage current reservation
     * @param amount amount to record
     * @param cardType type of card (if not on list, "other" is selected.
     * @param isDeposit true to tick "deposit" checkbox
     * @param note note on the payment
     * @throws IOException on I/O error
     */
    public void addPayment( HtmlPage reservationPage, BigDecimal amount, String cardType,
            boolean isDeposit, String note ) throws IOException {

        int reservationId = getReservationId( reservationPage );
        URL paymentsURL = new URL( getRecordPaymentsURL( reservationId ) );
        LOGGER.info( "Attempting POST to " + paymentsURL + " for reservation " + reservationId );
        String jsonRequest = gson.toJson( new RecordPaymentRequest( reservationId, cardType, amount, note, isDeposit ) );

        LOGGER.info( "Recording payment in LH" );
        sendJsonRequest( reservationPage.getWebClient(),
                constructDefaultRequest( HttpMethod.POST, paymentsURL, reservationPage, jsonRequest ) );
    }

    /**
     * Logs/masks the given web response and returns the contents.
     * 
     * @param webResponse web response
     * @return text content
     */
    private String logJsonWebResponse( WebResponse webResponse ) {
        String response = webResponse.getContentAsString();
        LOGGER.info( new LHJsonCardMask().applyCardMask( response ) );
        return response;
    }

    /**
     * Constructs a default POST request for Little Hotelier.
     * 
     * @param httpMethod either PUT or POST
     * @param url the destination address
     * @param currentPage the current page in LH
     * @param requestBody the content of the POST request
     * @return constructed request
     */
    private WebRequest constructDefaultRequest( HttpMethod httpMethod, URL url, HtmlPage currentPage, String requestBody ) {
        WebRequest paymentsRequest = new WebRequest( url, httpMethod );
        paymentsRequest.setCharset( Charset.forName( "UTF-8" ) ); // important! or error on server
        paymentsRequest.setAdditionalHeader( "Accept", "application/json, text/javascript, */*; q=0.01" );
        paymentsRequest.setAdditionalHeader( "Content-Type", "application/json" );
        paymentsRequest.setAdditionalHeader( "Accept-Language", "en-GB,en-US;q=0.8,en;q=0.6" );
        paymentsRequest.setAdditionalHeader( "Accept-Encoding", "gzip, deflate, br" );
        paymentsRequest.setAdditionalHeader( "X-Requested-With", "XMLHttpRequest" );
        paymentsRequest.setAdditionalHeader( "Host", "app.littlehotelier.com" );
        paymentsRequest.setAdditionalHeader( "Origin", "https://app.littlehotelier.com" );

        HtmlMeta csrfToken = currentPage.getFirstByXPath( "//meta[@name='csrf-token']" );
        paymentsRequest.setAdditionalHeader( "X-CSRF-Token", csrfToken.getContentAttribute() );

        HtmlMeta accessToken = currentPage.getFirstByXPath( "//meta[@name='access-token']" );
        paymentsRequest.setAdditionalHeader( "Access-Token", accessToken.getContentAttribute() );
        paymentsRequest.setRequestBody( requestBody );
        return paymentsRequest;
    }

    /**
     * Constructs a default POST request for Little Hotelier.
     * 
     * @param url the destination address
     * @param currentPage the current page in LH
     * @param requestBody the content of the POST request
     * @return constructed request
     */
    private WebRequest constructDefaultGetRequest( URL url, HtmlPage currentPage ) {
        WebRequest paymentsRequest = new WebRequest( url, HttpMethod.GET );
        paymentsRequest.setAdditionalHeader( "Accept", "*/*" );
        paymentsRequest.setAdditionalHeader( "Accept-Language", "en-GB,en-US;q=0.8,en;q=0.6" );
        paymentsRequest.setAdditionalHeader( "Accept-Encoding", "gzip, deflate, br" );
        paymentsRequest.setAdditionalHeader( "X-Requested-With", "XMLHttpRequest" );
        paymentsRequest.setAdditionalHeader( "Host", "app.littlehotelier.com" );
        paymentsRequest.setAdditionalHeader( "Extranet-Request", "true" );

        HtmlMeta csrfToken = currentPage.getFirstByXPath( "//meta[@name='csrf-token']" );
        paymentsRequest.setAdditionalHeader( "X-CSRF-Token", csrfToken.getContentAttribute() );

        HtmlMeta accessToken = currentPage.getFirstByXPath( "//meta[@name='access-token']" );
        paymentsRequest.setAdditionalHeader( "Access-Token", accessToken.getContentAttribute() );
        return paymentsRequest;
    }

    /**
     * Posts the given web request and parses the response as a JsonObject.
     * 
     * @param webClient the web client
     * @param webRequest request to POST
     * @return non-null parsed response
     * @throws IOException on I/O error
     * @throws IllegalStateException if we get any validation errors when sending the JSON request
     * @throws PaymentCardNotAcceptedException if LH complains card details aren't correct
     */
    private JsonObject sendJsonRequest( WebClient webClient, WebRequest webRequest ) throws IOException, PaymentCardNotAcceptedException {

        LOGGER.info( "Sending " + webRequest.getHttpMethod() + " request to " + webRequest.getUrl() );
        if ( webRequest.getHttpMethod() == HttpMethod.POST
                || webRequest.getHttpMethod() == HttpMethod.PUT ) {
            LOGGER.debug( new LHJsonCardMask().applyCardMask( webRequest.getRequestBody() ) );
        }

        Page redirectPage = webClient.getPage( webRequest );
        WebResponse webResponse = redirectPage.getWebResponse();
        if ( webResponse.getContentType().equalsIgnoreCase( "application/json" ) ) {
            LOGGER.info( "Received application/json response" );
            JsonObject responseObj = gson.fromJson( logJsonWebResponse( webResponse ), JsonElement.class ).getAsJsonObject();
            if ( responseObj.get( "errors" ) != null && false == responseObj.get( "errors" ).getAsJsonObject().entrySet().isEmpty() ) {
                
                // bloody LH rejecting AMEX cards cause we don't have it enabled
                if ( "card is not accepted".equals( responseObj.get( "errors" ).getAsJsonObject()
                        .get( "payment_card_number" ).getAsJsonArray().get( 0 ).getAsString() ) ) {
                    throw new PaymentCardNotAcceptedException();
                }
                throw new IllegalStateException( "One or more errors found in response." );
            }
            return responseObj;
        }
        else {
            LOGGER.error( webResponse.getContentAsString() );
            throw new IOException( "Unexpected response type: " + webResponse.getContentType() );
        }
    }

    /**
     * Transforms the given reservation node (loaded when opening up a new reservation) into an
     * "update" node, so we can modify individual fields on the reservation.
     * 
     * @param reservationNode the root element from loading a reservation
     * @return the object used to update the reservation
     */
    private JsonObject transformReservationToUpdate( JsonObject reservationNode ) {
        // now construct an update response based on the current state
        // this was determined by enabling network logging on Chrome

        // remove all the cruft we don't need
        reservationNode.remove( "payments" );
        reservationNode.remove( "invoices" );
        reservationNode.remove( "property" );
        reservationNode.remove( "cards_on_file" );

        reservationNode.getAsJsonArray( "reservation_room_types" ).forEach(
                e -> {
                    e.getAsJsonObject().remove( "room_type_name" );
                    e.getAsJsonObject().remove( "rate_plan_name" );
                    e.getAsJsonObject().remove( "rate_plan" );
                    e.getAsJsonObject().remove( "room_name" );
                    e.getAsJsonObject().remove( "pictures" );
                    e.getAsJsonObject().remove( "extra_occupants_total" );
                    e.getAsJsonObject().remove( "rate_plans" );
                    e.getAsJsonObject().remove( "rooms" );
                } );

        JsonObject newRoot = new JsonObject();
        newRoot.add( "form-info", new JsonPrimitive( true ) );
        newRoot.add( "reservation", gson.toJsonTree( reservationNode ) );
        return newRoot;
    }

    /**
     * Appends a message to the notes section of the current reservation page.
     * 
     * @param reservationPage the currently loaded reservation page.
     * @param note the note to be prepended
     * @throws IOException on I/O error
     */
    public void appendNote( HtmlPage reservationPage, String note ) throws IOException {
        appendCommentsAndNote( reservationPage, null, note );
    }

    /**
     * Appends a message to the guest comments/notes section of the current reservation page.
     * 
     * @param reservationPage the currently loaded reservation page.
     * @param comments guest comments to be prepended (optional)
     * @param note the note to be prepended (optional)
     * @throws IOException on I/O error
     */
    public void appendCommentsAndNote( HtmlPage reservationPage, String comments, String note ) throws IOException {
        
        if ( note != null ) {
            LOGGER.info( "Appending note to reservation: " + note );
        }
        if ( comments != null ) {
            LOGGER.info( "Appending guest-comments to reservation: " + note );
        }
        if( note == null && comments == null ) {
            LOGGER.info( "Nothing to do..." );
            return;
        }

        // send a GET request for this reservation so we have a starting point
        int reservationId = getReservationId( reservationPage );
        URL reservationLoadURL = new URL( getReservationLoadURL( reservationId ) );
        JsonObject reservationRoot = sendJsonRequest( reservationPage.getWebClient(),
                constructDefaultGetRequest( reservationLoadURL, reservationPage ) );

        // this is now a new JSON object we can manipulate
        JsonObject updateJson = transformReservationToUpdate( reservationRoot );
        reservationRoot = updateJson.get( "reservation" ).getAsJsonObject();

        // replace "notes" property
        if ( note != null ) {
            String existingNotes = reservationRoot.get( "notes" ).getAsString();
            reservationRoot.getAsJsonObject().addProperty( "notes", note + "\n" + existingNotes );
        }

        // replace "guest_comments" property
        if ( comments != null ) {
            String existingComments = reservationRoot.get( "guest_comments" ).getAsString();
            reservationRoot.getAsJsonObject().addProperty( "guest_comments", comments + "\n" + existingComments );
        }

        // now send the update!
        URL reservationPostURL = new URL( getReservationPostURL( reservationId ) );
        try {
            sendJsonRequest( reservationPage.getWebClient(),
                    constructDefaultRequest( HttpMethod.PUT, reservationPostURL,
                            reservationPage, gson.toJson( updateJson ) ) );
        }
        catch ( PaymentCardNotAcceptedException ex ) {
            // to bypass issue with saving a note when an Amex card is currently visible
            // because we don't support Amex cards (but we may still get one from another channel)
            // LH won't save card details if they're masked
            LOGGER.info( "LH complaining with card validation error; trying again with masked card details" );
            reservationRoot.getAsJsonObject().addProperty( "payment_card_number", "XXXX-XXXX-XXXX-XXXX" );
            sendJsonRequest( reservationPage.getWebClient(),
                    constructDefaultRequest( HttpMethod.PUT, reservationPostURL,
                            reservationPage, gson.toJson( updateJson ) ) );
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
        HtmlInput cardNumber = reservationPage.getFirstByXPath( "//input[@id='payment_card_number']" );
        String validatedCardNumber = StringUtils.replaceAll( cardNumber.getValueAttribute(), "\\s", "" );
        if ( false == NumberUtils.isDigits( validatedCardNumber ) ||
                StringUtils.isBlank( validatedCardNumber ) ) {
            throw new MissingUserDataException( "Unable to retrieve card number." );
        }
        cardDetails.setCardNumber( validatedCardNumber );

        HtmlSelect expiryMonth = reservationPage.getFirstByXPath( "//select[@id='payment_card_expiry_month']" );
        String expMonth = expiryMonth == null ? null : expiryMonth.getDefaultValue();
        String validatedExpiryMonth = null;
        if ( expMonth == null || false == NumberUtils.isDigits( expMonth ) ) {
            LOGGER.warn( "Unable to find card expiry month(" + expMonth + ") : " + reservationPage.getUrl() );
        }
        else {
            validatedExpiryMonth = StringUtils.leftPad( expMonth, 2, "0" );
        }

        HtmlSelect expiryYear = reservationPage.getFirstByXPath( "//select[@id='payment_card_expiry_year']" );
        String expYear = expiryYear == null ? null : expiryYear.getDefaultValue();
        if ( expYear == null || false == NumberUtils.isDigits( expYear ) ) {
            LOGGER.warn( "Unable to find card expiry year(" + expYear + ") : " + reservationPage.getUrl() );
        }
        else if ( validatedExpiryMonth != null ) {
            // only populate if we have a valid month/year
            cardDetails.setExpiry( validatedExpiryMonth + expYear.substring( 2 ) );
        }

        HtmlInput cardholderName = reservationPage.getFirstByXPath( "//input[@id='payment_card_name']" );
        if ( cardholderName == null ) {
            throw new MissingUserDataException( "Missing cardholder name on booking." );
        }
        cardDetails.setName( cardholderName.getValueAttribute() );

        return cardDetails;
    }

    /**
     * Returns the reservation ID from the reservation page.
     * 
     * @param reservationPage pre-loaded reservation page
     * @return reservation ID
     */
    public int getReservationId( HtmlPage reservationPage ) {
        // there are 2 forms on the page
        // select the one were the method attribute doesn't exist (not sure how else to do it)
        HtmlForm editform = reservationPage.getFirstByXPath( "//form[not(@method)]" );
        String postActionUrl = editform.getAttribute( "action" );
        LOGGER.info( "Hopefully, this is the direct URL for the reservation: " + postActionUrl );
        int reservationId = Integer.parseInt( postActionUrl.substring( postActionUrl.lastIndexOf( '/' ) + 1 ) );
        return reservationId;
    }

    /**
     * Returns the checkin date for the reservation.
     * 
     * @param reservationPage the pre-loaded reservation page
     * @return non-null checkin date
     * @throws IllegalStateException if checkin date not available on page
     */
    public Date getCheckinDate( HtmlPage reservationPage ) throws IllegalStateException {
        try {
            HtmlInput checkinDate = reservationPage.getFirstByXPath( "//input[@id='check_in_date']" );
            if ( checkinDate == null ) {
                LOGGER.debug( reservationPage.asXml() );
                throw new IllegalStateException( "Unable to find checkin date on current page" );
            }
            return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( checkinDate.getValueAttribute() );
        }
        catch ( ParseException e ) {
            throw new IllegalStateException( e );
        }
    }

    /**
     * Returns the first night payable on the reservations page.
     * 
     * @param reservationPage reservations page
     * @return non-null amount payable on first night
     */
    public BigDecimal getAmountPayableForFirstNight( HtmlPage reservationPage ) {
        List<?> divs = reservationPage.getByXPath( "//div[contains(@class,'discount-body')]/div[1]/div[1]/div[2]/div/div" );
        return divs.stream().map( o -> {
            HtmlDivision div = HtmlDivision.class.cast( o );
            String amount = StringUtils.trim( StringUtils.replaceChars( div.getTextContent(), POUND, "" ) );
            LOGGER.info( "Accumulating amount: " + amount );
            return new BigDecimal( amount );
        } ).reduce( BigDecimal.ZERO, BigDecimal::add );
    }

    /**
     * Clicks on the "view card details" link on the reservation page and fills in the security
     * token dialog with the email sent. Returns the card number for the current reservation.
     * 
     * @param reservationPage the current reservation we're looking at
     * @param reservationId the unique reservation ID for this reservation
     * @throws IOException on I/O error
     * @throws MissingUserDataException if card details could not be retrieved
     */
    public void enableSecurityAccess( HtmlPage reservationPage, int reservationId ) throws IOException, MissingUserDataException {
        HtmlAnchor viewCcDetails = reservationPage.getFirstByXPath( "//a[@class='view-card-details']" );
        HtmlPage currentPage = viewCcDetails.click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 15000 ); // wait for email to be delivered
        HtmlInput securityToken = reservationPage.getFirstByXPath( "//input[@id='security_token']" );
        securityToken.type( gmailService.fetchLHSecurityToken() );
        HtmlAnchor submitToken = reservationPage.getFirstByXPath( "//a[text()='Confirm']" );
        currentPage = submitToken.click();
        currentPage.getWebClient().waitForBackgroundJavaScript( 45000 );

        // check if the view card details link has been removed
        viewCcDetails = reservationPage.getFirstByXPath( "//a[@class='view-card-details']" );
        if ( viewCcDetails != null ) {
            throw new MissingUserDataException( "Error attempting to enable card details in LH" );
        }

        // we have the card details; save the current web client so we don't have to go through this again
        fileService.writeCookiesToFile( currentPage.getWebClient() );

        // this will lookup the card number using the JSON page (found using the data-url from the control)
        /* This actually works still but I don't think it's necessary anymore
        Page cardNumberPage = currentPage.getWebClient().openWindow(
                new URL( getCardLookupURL( reservationId ) ), "newJsonWindow" ).getEnclosedPage();

        WebResponse webResponse = cardNumberPage.getWebResponse();
        if ( webResponse.getContentType().equals( "application/json" ) ) {
            String cardNumberJson = cardNumberPage.getWebResponse().getContentAsString();
            Map<String, String> cardNumberResponseMap = new Gson().fromJson(
                    cardNumberJson, new TypeToken<Map<String, String>>() {}.getType() );

            String cardNum = StringUtils.replaceAll( cardNumberResponseMap.get( "cc_number" ), "\\s", "" );
            if ( false == NumberUtils.isDigits( cardNum ) ) {
                throw new MissingUserDataException( "Unable to retrieve card number : " + reservationPage.getUrl() );
            }

            // we have the card details; save the current web client so we don't have to go through this again
            fileService.writeCookiesToFile( reservationPage.getWebClient() );
            return cardNum;
        }
        */
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

        List<HtmlAnchor> reservationRows = bookingsPage.getByXPath( "//a[text()='" + bookingRef + "']" );
        if ( reservationRows.size() != 1 ) {
            throw new IncorrectResultSizeDataAccessException( "Unable to find unique booking " + bookingRef, 1 );
        }

        // click on the only reservation on the page
        HtmlPage reservationPage = reservationRows.get( 0 ).click();
        reservationPage.getWebClient().waitForBackgroundJavaScript( 60000 ); // wait for page to load

        // need to make sure this dialog is open, otherwise no point continuing
        // not sure why, it won't always "click" and open the dialog
        // try a few times until we get it
        HtmlHeading4 heading = reservationPage.getFirstByXPath( "//h4[starts-with(.,'Edit Reservation')]" );
        for ( int i = 0 ; i < 5 && heading == null ; i++ ) {
            LOGGER.info( "Unable to open reservation page: attempt " + (i + 1) );
            reservationPage = reservationRows.get( 0 ).click(); // not working all the time?
            reservationPage.getWebClient().waitForBackgroundJavaScript( 30000 );
            heading = reservationPage.getFirstByXPath( "//h4[starts-with(.,'Edit Reservation')]" );
        }

        // extra-paranoid; making sure booking ref matches the editing window
        String editingBookingRef = getBookingRef( reservationPage );
        if ( false == bookingRef.equals( editingBookingRef ) ) {
            throw new IllegalStateException( "Booking references don't match! " + bookingRef + " <==> " + editingBookingRef );
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
        HtmlTextArea guestComments = reservationPage.getFirstByXPath( "//textarea[@id='guest_comments']" );

        String newComment = null;
        String newNote = null;
        if ( false == guestComments.getTextContent().contains( AgodaScraper.NO_CHARGE_NOTE ) ) {
            newComment = AgodaScraper.NO_CHARGE_NOTE;
        }

        HtmlTextArea reservationNotes = reservationPage.getFirstByXPath( "//textarea[@id='notes']" );
        if ( false == reservationNotes.getTextContent().contains( AgodaScraper.NO_CHARGE_NOTE ) ) {
            newNote = AgodaScraper.NO_CHARGE_NOTE;
        }

        // append comment/note if applicable
        if ( newComment != null || newNote != null ) {
            appendCommentsAndNote( reservationPage, newComment, newNote );
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
        HtmlHeading4 heading = reservationPage.getFirstByXPath( "//h4[starts-with(.,'Edit Reservation')]" );
        if ( heading == null ) {
            throw new MissingUserDataException( "Unable to determine booking reference from " + reservationPage.getBaseURL() );
        }

        Pattern p = Pattern.compile( "Edit Reservation - (.*) for .*" );
        Matcher m = p.matcher( heading.getTextContent() ); // CRH job 240239 fails with NPE here
        String bookingRef;
        if ( m.find() ) {
            bookingRef = m.group( 1 );
        }
        else {
            throw new MissingUserDataException( "Unable to determine booking reference from " + reservationPage.getBaseURL() );
        }
        return bookingRef;
    }

}
